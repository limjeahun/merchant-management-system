FROM python:3.9-slim

# 시스템 의존성 설치 (OpenCV에 필요)
RUN apt-get update && apt-get install -y --no-install-recommends \
    libgl1 \
    libglib2.0-0 \
    && apt-get clean \
    && rm -rf /var/lib/apt/lists/*

# Python 패키지 설치
RUN pip install --no-cache-dir \
    easyocr \
    flask \
    pillow

# 한국어 + 영어 모델 사전 다운로드 (빌드 시점에 캐싱)
RUN python -c "import easyocr; easyocr.Reader(['ko', 'en'], gpu=False)"

WORKDIR /app
COPY docker/easyocr/easyocr_server.py .

EXPOSE 9005

CMD ["python", "easyocr_server.py"]
