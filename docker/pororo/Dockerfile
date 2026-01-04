# Pororo OCR Docker 이미지 (GPU 버전)
# Kakaobrain Pororo 기반 한국어 OCR/NLP 서비스
# PyTorch CUDA + Pororo 의존성

FROM pytorch/pytorch:1.13.1-cuda11.6-cudnn8-runtime

WORKDIR /app

# 환경 변수 설정
ENV DEBIAN_FRONTEND=noninteractive
ENV PYTHONUNBUFFERED=1
ENV PORORO_HOME=/root/.pororo

# 시스템 의존성 설치
RUN apt-get update && apt-get install -y --no-install-recommends \
    git \
    wget \
    libgl1 \
    libglib2.0-0 \
    libsm6 \
    libxext6 \
    libxrender1 \
    && rm -rf /var/lib/apt/lists/*

# Python 기본 의존성 설치
RUN pip install --no-cache-dir \
    flask \
    flask-cors \
    "numpy<2.0.0" \
    pillow \
    opencv-python-headless

# transformers/sentence-transformers 먼저 설치
RUN pip install --no-cache-dir \
    transformers==4.30.0 \
    sentence-transformers \
    konlpy

# Pororo 설치 시도 (pip에서)
RUN pip install --no-cache-dir pororo || echo "Pororo pip install failed, will try alternative"

# Pororo 실패 시 EasyOCR fallback
RUN pip install --no-cache-dir easyocr

# PyTorch 1.13.1과 호환되는 NumPy 버전 강제 설치 (EasyOCR이 덮어쓰는 것 방지)
# PyTorch 1.13.x는 NumPy 1.23.x와 호환됨
RUN pip install --no-cache-dir "numpy==1.23.5"

# 서버 스크립트 복사
COPY docker/pororo/pororo_server.py /app/

# 포트 노출
EXPOSE 9004

# 환경 변수
ENV PORT=9004

# 서버 실행
CMD ["python", "pororo_server.py"]
