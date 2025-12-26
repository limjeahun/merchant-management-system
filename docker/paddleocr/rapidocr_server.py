"""
RapidOCR REST API 서버 (PaddleOCR ONNX 기반)
한국어 OCR을 위한 Flask 기반 REST API
RapidOCR: https://github.com/RapidAI/RapidOCR

한국어 모델 사용 (PP-OCRv5 최신):
- Detection: PP-OCRv5_det.onnx (83.9MB, PP-OCRv5 검출)
- Recognition: korean_PP-OCRv5_rec.onnx (12.7MB, PP-OCRv5 한국어)
- Dictionary: ppocr_v5_korean_dict.txt (47KB, 모델과 매칭)
"""

import base64
import io
import os
import cv2
import numpy as np
from flask import Flask, request, jsonify
from flask_cors import CORS
from rapidocr_onnxruntime import RapidOCR

app = Flask(__name__)
CORS(app)

# 한국어 모델 경로 설정 (PP-OCRv5 최신)
MODEL_DIR = "/app/models"
DET_MODEL = os.path.join(MODEL_DIR, "PP-OCRv5_det.onnx")
REC_MODEL = os.path.join(MODEL_DIR, "korean_PP-OCRv5_rec.onnx")
REC_KEYS = os.path.join(MODEL_DIR, "ppocr_v5_korean_dict.txt")

# RapidOCR 초기화 (PP-OCRv5 한국어 모델 사용)
print("Initializing RapidOCR with Korean PP-OCRv5 models...")
print(f"  Detection model: {DET_MODEL}")
print(f"  Recognition model: {REC_MODEL}")
print(f"  Dictionary: {REC_KEYS}")

# 모델 파일 존재 여부 확인
if os.path.exists(REC_MODEL) and os.path.exists(REC_KEYS):
    print("Korean models found, using custom configuration...")
    ocr = RapidOCR(
        det_model_path=DET_MODEL if os.path.exists(DET_MODEL) else None,
        rec_model_path=REC_MODEL,
        rec_keys_path=REC_KEYS
    )
else:
    print("Korean models not found, using default models...")
    ocr = RapidOCR()

print("RapidOCR initialized successfully!")


def preprocess_image(image_bytes):
    """이미지 전처리: 대비 향상, 샤프닝, 노이즈 제거 (최적화됨)"""
    # 바이트 배열을 numpy 배열로 변환
    nparr = np.frombuffer(image_bytes, np.uint8)
    img = cv2.imdecode(nparr, cv2.IMREAD_COLOR)
    
    if img is None:
        return image_bytes
    
    # 1. 그레이스케일 변환
    gray = cv2.cvtColor(img, cv2.COLOR_BGR2GRAY)
    
    # 2. 대비 향상 (CLAHE - 강화된 파라미터)
    clahe = cv2.createCLAHE(clipLimit=3.0, tileGridSize=(8, 8))
    enhanced = clahe.apply(gray)
    
    # 3. 샤프닝 (Unsharp Masking - 텍스트 엣지 선명화)
    gaussian = cv2.GaussianBlur(enhanced, (0, 0), 3)
    sharpened = cv2.addWeighted(enhanced, 1.5, gaussian, -0.5, 0)
    
    # 4. 노이즈 제거 (Bilateral Filter - 엣지 보존)
    denoised = cv2.bilateralFilter(sharpened, 9, 75, 75)
    
    # 5. 다시 3채널 컬러로 변환 (RapidOCR 입력용)
    result = cv2.cvtColor(denoised, cv2.COLOR_GRAY2BGR)
    
    # 6. 업스케일 (과도한 확대 방지를 위해 1.5x 사용)
    h, w = result.shape[:2]
    if max(h, w) < 1500:
        scale = 1.5
        result = cv2.resize(result, None, fx=scale, fy=scale, interpolation=cv2.INTER_CUBIC)
    
    # numpy 배열을 바이트로 변환
    _, encoded = cv2.imencode('.png', result)
    return encoded.tobytes()


def estimate_ocr_success(lines, full_text):
    """
    OCR 성공률 추정
    - 한글 비율: 사업자등록증은 한글이 많아야 함
    - 평균 신뢰도: RapidOCR의 confidence 점수
    - 의미 있는 단어 비율: 알려진 키워드 매칭
    """
    if not lines or not full_text:
        return 0.0, "NO_TEXT"
    
    # 1. 한글 비율 계산
    korean_chars = sum(1 for c in full_text if '\uac00' <= c <= '\ud7a3')
    total_chars = len(full_text.replace('\n', '').replace(' ', ''))
    korean_ratio = korean_chars / max(total_chars, 1)
    
    # 2. 평균 신뢰도
    avg_confidence = sum(line['confidence'] for line in lines) / max(len(lines), 1)
    
    # 3. 키워드 매칭
    keywords = ['국세청', '사업자', '등록', '번호', '대표자', '법인', '개업', 
                '소재지', '업태', '종목', '제조', '도소매', '전화', '세무서']
    matched_keywords = sum(1 for kw in keywords if kw in full_text)
    keyword_score = min(matched_keywords / 10, 1.0)  # 최대 1.0
    
    # 4. 비정상 문자 패턴 (연속 영문 대문자 = 깨진 문자일 가능성)
    import re
    gibberish_patterns = re.findall(r'[A-Z]{3,}', full_text)
    gibberish_penalty = min(len(gibberish_patterns) * 0.1, 0.5)
    
    # 5. 종합 점수 계산
    # 한글 비율(40%) + 신뢰도(30%) + 키워드(30%) - 깨진 문자 페널티
    success_rate = (korean_ratio * 0.4 + avg_confidence * 0.3 + keyword_score * 0.3) - gibberish_penalty
    success_rate = max(0.0, min(1.0, success_rate))
    
    # 레벨 결정
    if success_rate >= 0.8:
        level = "EXCELLENT"
    elif success_rate >= 0.6:
        level = "GOOD"
    elif success_rate >= 0.4:
        level = "FAIR"
    elif success_rate >= 0.2:
        level = "POOR"
    else:
        level = "VERY_POOR"
    
    return success_rate, level, {
        "korean_ratio": round(korean_ratio, 3),
        "avg_confidence": round(avg_confidence, 3),
        "keyword_score": round(keyword_score, 3),
        "gibberish_penalty": round(gibberish_penalty, 3),
        "matched_keywords": matched_keywords
    }


@app.route('/health', methods=['GET'])
def health():
    """헬스체크 엔드포인트"""
    return jsonify({
        "status": "healthy", 
        "engine": "RapidOCR",
        "language": "korean (PP-OCRv5)" if os.path.exists(REC_MODEL) else "default"
    })


def process_ocr(image_bytes):
    """OCR 처리 공통 함수"""
    # 이미지 전처리
    processed_bytes = preprocess_image(image_bytes)
    
    result, elapsed = ocr(processed_bytes)
    
    lines = []
    full_text_parts = []
    
    if result:
        for line in result:
            text = line[1]
            confidence = float(line[2]) if len(line) > 2 else 0.9
            lines.append({
                "text": text,
                "confidence": confidence
            })
            full_text_parts.append(text)
    
    full_text = "\n".join(full_text_parts)
    
    # OCR 성공률 추정
    success_rate, level, details = estimate_ocr_success(lines, full_text)
    
    # 로깅
    print(f"=== OCR Result ===")
    print(f"  Lines detected: {len(lines)}")
    print(f"  Estimated OCR Success: {success_rate*100:.1f}% ({level})")
    print(f"  Details: korean_ratio={details['korean_ratio']:.1%}, "
          f"confidence={details['avg_confidence']:.1%}, "
          f"keywords={details['matched_keywords']}/14")
    if details['gibberish_penalty'] > 0:
        print(f"  Warning: Detected {int(details['gibberish_penalty']*10)} gibberish patterns")
    print(f"==================")
    
    # elapsed가 리스트인 경우 처리
    elapsed_value = elapsed
    if isinstance(elapsed, (list, tuple)):
        elapsed_value = sum(elapsed) if elapsed else 0.0
    
    return {
        "success": True,
        "text": full_text,
        "lines": lines,
        "line_count": len(lines),
        "elapsed_time": elapsed_value,
        # OCR 품질 추정
        "ocr_quality": {
            "success_rate": round(success_rate, 3),
            "level": level,
            "details": details
        },
        # 호환성을 위한 추가 필드
        "code": "100",
        "msg": "success",
        "data": [{"text": line["text"], "score": line["confidence"]} for line in lines]
    }


@app.route('/ocr', methods=['POST'])
def ocr_endpoint():
    """
    OCR 엔드포인트
    
    지원 형식:
    1. Multipart form-data: 'image_file' 필드로 이미지 파일 업로드
    2. JSON: 'image_base64' 필드로 Base64 인코딩된 이미지
    
    Response:
    - success: 성공 여부
    - text: 추출된 전체 텍스트
    - lines: 라인별 텍스트 및 신뢰도
    - code: "100" (성공), 호환성용
    - data: [{text, score}] 형식, 호환성용
    """
    try:
        image_bytes = None
        
        # 1. Multipart form-data 방식 (파일 업로드)
        if 'image_file' in request.files:
            file = request.files['image_file']
            image_bytes = file.read()
        
        # 2. JSON 방식 (Base64 인코딩)
        elif request.is_json:
            data = request.get_json()
            if data and 'image_base64' in data:
                image_base64 = data['image_base64']
                # data:image/xxx;base64, prefix 제거
                if ',' in image_base64:
                    image_base64 = image_base64.split(',')[1]
                image_bytes = base64.b64decode(image_base64)
        
        # 3. multipart로 직접 전송된 바이너리 데이터
        elif request.content_type and 'multipart/form-data' in request.content_type:
            # 다른 필드명 시도
            for key in request.files:
                file = request.files[key]
                image_bytes = file.read()
                break
        
        if image_bytes is None:
            return jsonify({
                "success": False,
                "code": "400",
                "msg": "Missing image data. Use 'image_file' (multipart) or 'image_base64' (JSON)",
                "error": "Missing image data"
            }), 400
        
        # OCR 처리
        result = process_ocr(image_bytes)
        return jsonify(result)
        
    except Exception as e:
        import traceback
        traceback.print_exc()
        return jsonify({
            "success": False,
            "code": "500",
            "msg": str(e),
            "error": str(e)
        }), 500


if __name__ == '__main__':
    port = int(os.environ.get('PORT', 9003))
    print(f"Starting RapidOCR server on port {port}...")
    app.run(host='0.0.0.0', port=port, threaded=True)

