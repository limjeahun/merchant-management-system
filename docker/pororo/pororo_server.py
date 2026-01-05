"""
Pororo OCR REST API 서버 (GPU 버전)
Kakaobrain Pororo 기반 한국어 OCR/NLP
포트: 9004
"""

import base64
import os
from flask import Flask, request, jsonify
from flask_cors import CORS

app = Flask(__name__)
CORS(app)

# OCR 엔진 초기화
print("=" * 50)
print("Initializing Pororo Korean OCR engine...")
print("=" * 50)

ocr = None
engine_name = None

# 1. Pororo 시도 (OCR 태스크)
try:
    from pororo import Pororo
    
    # Pororo OCR 태스크로 초기화
    print("Loading Pororo OCR model...")
    ocr = Pororo(task="ocr", lang="ko")
    engine_name = "pororo"
    print("✓ Pororo OCR initialized successfully!")
    
except ImportError as e:
    print(f"✗ Pororo import failed: {e}")
except Exception as e:
    print(f"✗ Pororo initialization failed: {e}")

# 2. EasyOCR fallback (CPU 모드 우선 - GPU 메모리를 Ollama와 공유하므로)
if ocr is None:
    print("Trying EasyOCR fallback (CPU mode to avoid GPU OOM)...")
    try:
        import easyocr
        # CPU 모드를 기본으로 사용 (GPU는 Ollama가 점유)
        ocr = easyocr.Reader(['ko', 'en'], gpu=False)
        engine_name = "easyocr-cpu"
        print("✓ EasyOCR (CPU) initialized successfully!")
    except Exception as e:
        print(f"✗ EasyOCR CPU failed: {e}")
        ocr = None
        engine_name = None

print("=" * 50)
print(f"Final engine: {engine_name}")
print("=" * 50)


def calculate_korean_ratio(text):
    """한글 비율 계산"""
    if not text:
        return 0.0
    korean_chars = sum(1 for c in text if '\uac00' <= c <= '\ud7a3')
    total_chars = len(text.replace('\n', '').replace(' ', ''))
    return korean_chars / max(total_chars, 1)


@app.route('/health', methods=['GET'])
def health():
    """헬스체크 엔드포인트"""
    import torch
    return jsonify({
        "status": "healthy" if ocr else "unhealthy",
        "engine": engine_name or "none",
        "language": "korean",
        "gpu_available": torch.cuda.is_available(),
        "gpu_name": torch.cuda.get_device_name(0) if torch.cuda.is_available() else None
    })


@app.route('/ocr', methods=['POST'])
def ocr_endpoint():
    """
    OCR 엔드포인트
    
    지원 형식:
    1. Multipart form-data: 'image_file' 필드로 이미지 파일 업로드
    2. JSON: 'image_base64' 필드로 Base64 인코딩된 이미지
    """
    if not ocr:
        return jsonify({
            "success": False,
            "error": "OCR engine not initialized"
        }), 500
    
    try:
        image_bytes = None
        
        # 1. Multipart form-data 방식
        if 'image_file' in request.files:
            file = request.files['image_file']
            image_bytes = file.read()
        
        # 2. JSON 방식 (Base64)
        elif request.is_json:
            data = request.get_json()
            if data and 'image_base64' in data:
                image_base64 = data['image_base64']
                if ',' in image_base64:
                    image_base64 = image_base64.split(',')[1]
                image_bytes = base64.b64decode(image_base64)
        
        # 3. multipart 다른 필드명
        elif request.content_type and 'multipart/form-data' in request.content_type:
            for key in request.files:
                file = request.files[key]
                image_bytes = file.read()
                break
        
        if image_bytes is None:
            return jsonify({
                "success": False,
                "error": "Missing image data"
            }), 400
        
        # 임시 파일로 저장 (OCR 엔진은 파일 경로 필요)
        import tempfile
        with tempfile.NamedTemporaryFile(suffix='.png', delete=False) as f:
            f.write(image_bytes)
            temp_path = f.name
        
        try:
            lines = []
            full_text = ""
            
            if engine_name == "pororo":
                # Pororo OCR 실행
                result = ocr(temp_path)
                
                # 결과 처리 - Pororo는 문자열 또는 리스트 반환
                if isinstance(result, str):
                    full_text = result
                    lines = [{"text": line, "confidence": 0.95} for line in result.split('\n') if line.strip()]
                elif isinstance(result, list):
                    # 리스트인 경우 각 요소 처리
                    text_items = []
                    for item in result:
                        if isinstance(item, tuple) and len(item) >= 2:
                            # (box, text) 또는 (box, text, confidence) 형태
                            text_items.append(str(item[1]))
                            conf = float(item[2]) if len(item) > 2 else 0.95
                            lines.append({"text": str(item[1]), "confidence": conf})
                        else:
                            text_items.append(str(item))
                            lines.append({"text": str(item), "confidence": 0.95})
                    full_text = '\n'.join(text_items)
                else:
                    full_text = str(result)
                    lines = [{"text": full_text, "confidence": 0.95}]
                    
            elif engine_name in ["easyocr", "easyocr-cpu"]:
                # EasyOCR 실행 - CUDA OOM 발생 시 CPU fallback
                try:
                    result = ocr.readtext(temp_path)
                except RuntimeError as cuda_err:
                    if "CUDA" in str(cuda_err) or "out of memory" in str(cuda_err):
                        print(f"GPU memory error, falling back to CPU: {cuda_err}")
                        # GPU 메모리 정리
                        import torch
                        if torch.cuda.is_available():
                            torch.cuda.empty_cache()
                        # CPU로 재시도
                        import easyocr
                        cpu_reader = easyocr.Reader(['ko', 'en'], gpu=False)
                        result = cpu_reader.readtext(temp_path)
                    else:
                        raise cuda_err
                        
                if result:
                    lines = [{"text": item[1], "confidence": float(item[2])} for item in result]
                    full_text = '\n'.join([item[1] for item in result])
            
            korean_ratio = calculate_korean_ratio(full_text)
            
            return jsonify({
                "success": True,
                "text": full_text,
                "lines": lines,
                "line_count": len(lines),
                "korean_ratio": round(korean_ratio, 3),
                "engine": engine_name
            })
            
        finally:
            # 임시 파일 삭제
            os.unlink(temp_path)
        
    except Exception as e:
        import traceback
        traceback.print_exc()
        return jsonify({
            "success": False,
            "error": str(e)
        }), 500


if __name__ == '__main__':
    port = int(os.environ.get('PORT', 9004))
    print(f"Starting Pororo OCR server on port {port}...")
    print(f"Engine: {engine_name}")
    app.run(host='0.0.0.0', port=port, threaded=True)
