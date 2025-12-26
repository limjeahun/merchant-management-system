"""
PaddleOCR REST API 서버
한국어 OCR을 위한 간단한 Flask 기반 REST API
"""

import base64
import io
import os
from flask import Flask, request, jsonify
from flask_cors import CORS
from paddleocr import PaddleOCR

app = Flask(__name__)
CORS(app)

# PaddleOCR 초기화 (한국어)
print("Initializing PaddleOCR with Korean language...")
ocr = PaddleOCR(
    lang='korean',
    use_angle_cls=True,
    use_gpu=False,
    show_log=False
)
print("PaddleOCR initialized successfully!")


@app.route('/health', methods=['GET'])
def health():
    """헬스체크 엔드포인트"""
    return jsonify({"status": "healthy", "lang": "korean"})


@app.route('/ocr', methods=['POST'])
def ocr_endpoint():
    """
    OCR 엔드포인트
    
    Request Body (JSON):
    - image_base64: Base64 인코딩된 이미지
    
    Response:
    - success: 성공 여부
    - text: 추출된 전체 텍스트
    - lines: 라인별 텍스트 및 신뢰도
    """
    try:
        data = request.get_json()
        
        if not data or 'image_base64' not in data:
            return jsonify({
                "success": False,
                "error": "Missing 'image_base64' in request body"
            }), 400
        
        # Base64 디코딩
        image_base64 = data['image_base64']
        # data:image/xxx;base64, prefix 제거
        if ',' in image_base64:
            image_base64 = image_base64.split(',')[1]
        
        image_bytes = base64.b64decode(image_base64)
        
        # OCR 실행
        result = ocr.ocr(io.BytesIO(image_bytes), cls=True)
        
        # 결과 파싱
        lines = []
        full_text_parts = []
        
        if result and result[0]:
            for line in result[0]:
                if line and len(line) >= 2:
                    text = line[1][0]
                    confidence = float(line[1][1])
                    lines.append({
                        "text": text,
                        "confidence": confidence
                    })
                    full_text_parts.append(text)
        
        full_text = "\n".join(full_text_parts)
        
        return jsonify({
            "success": True,
            "text": full_text,
            "lines": lines,
            "line_count": len(lines)
        })
        
    except Exception as e:
        return jsonify({
            "success": False,
            "error": str(e)
        }), 500


if __name__ == '__main__':
    port = int(os.environ.get('PORT', 8866))
    print(f"Starting PaddleOCR server on port {port}...")
    app.run(host='0.0.0.0', port=port, threaded=True)
