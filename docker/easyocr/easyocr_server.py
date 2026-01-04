"""
EasyOCR Flask Server
한국어 + 영어 OCR 서비스 (기존 OnnxTR API와 호환)
"""

import io
import logging
from flask import Flask, request, jsonify
from PIL import Image
import easyocr

# 로깅 설정
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

app = Flask(__name__)

# EasyOCR Reader 초기화 (한국어 + 영어)
logger.info("Initializing EasyOCR Reader (ko, en)...")
reader = easyocr.Reader(['ko', 'en'], gpu=False)
logger.info("EasyOCR Reader initialized successfully")


@app.route('/health', methods=['GET'])
def health():
    """헬스체크 엔드포인트"""
    return jsonify({"status": "healthy", "engine": "easyocr"})


@app.route('/ocr', methods=['POST'])
def ocr():
    """
    OCR 처리 엔드포인트
    
    Request: multipart/form-data (image_file)
    Response: JSON {success, text, lines, line_count, error}
    """
    try:
        # 이미지 파일 확인
        if 'image_file' not in request.files:
            return jsonify({
                "success": False,
                "error": "No image_file provided"
            }), 400
        
        file = request.files['image_file']
        if file.filename == '':
            return jsonify({
                "success": False,
                "error": "Empty filename"
            }), 400
        
        # 이미지 읽기
        image_bytes = file.read()
        image = Image.open(io.BytesIO(image_bytes))
        
        logger.info(f"Processing image: {image.size}, mode={image.mode}")
        
        # EasyOCR 실행
        results = reader.readtext(image_bytes)
        
        # 결과 파싱
        lines = []
        full_text_parts = []
        
        for (bbox, text, confidence) in results:
            lines.append({
                "text": text,
                "confidence": float(confidence)
            })
            full_text_parts.append(text)
        
        full_text = "\n".join(full_text_parts)
        
        # 한글 비율 계산
        korean_chars = sum(1 for c in full_text if '\uac00' <= c <= '\ud7a3')
        total_chars = len(full_text.replace(" ", "").replace("\n", ""))
        korean_ratio = korean_chars / total_chars if total_chars > 0 else 0.0
        
        logger.info(f"OCR completed: {len(lines)} lines, korean_ratio={korean_ratio:.2f}")
        
        return jsonify({
            "success": True,
            "text": full_text,
            "lines": lines,
            "line_count": len(lines),
            "korean_ratio": korean_ratio
        })
        
    except Exception as e:
        logger.error(f"OCR failed: {str(e)}", exc_info=True)
        return jsonify({
            "success": False,
            "error": str(e)
        }), 500


if __name__ == '__main__':
    logger.info("Starting EasyOCR server on port 9005...")
    app.run(host='0.0.0.0', port=9005)
