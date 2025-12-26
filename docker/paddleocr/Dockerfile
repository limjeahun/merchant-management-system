FROM python:3.9-slim

WORKDIR /app

# Install minimal system dependencies for OpenCV headless
RUN apt-get update && \
    apt-get install -y --no-install-recommends \
    libglib2.0-0 \
    libgomp1 \
    libgl1 \
    && apt-get clean \
    && rm -rf /var/lib/apt/lists/*

# Install dependencies (headless OpenCV)
RUN pip install --no-cache-dir \
    opencv-python-headless \
    rapidocr-onnxruntime \
    flask \
    flask-cors \
    pyyaml

# Create models directory
RUN mkdir -p /app/models

# Copy PP-OCRv5 Korean OCR models from provider resources
COPY provider/src/main/resources/models/paddleocr/korean_PP-OCRv5_rec.onnx /app/models/
COPY provider/src/main/resources/models/paddleocr/ppocr_v5_korean_dict.txt /app/models/
COPY provider/src/main/resources/models/paddleocr/PP-OCRv5_det.onnx /app/models/

# Copy config and server script
COPY docker/paddleocr/config.yaml /app/
COPY docker/paddleocr/rapidocr_server.py /app/

EXPOSE 9003

CMD ["python", "rapidocr_server.py"]
