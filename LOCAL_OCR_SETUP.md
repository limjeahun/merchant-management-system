# 로컬 OCR 환경 설정 가이드

PaddleOCR (ONNX) + LangChain4j (Gemma2) 기반 로컬 OCR 시스템 설정 가이드입니다.

---

## 1. Docker Compose 서비스 실행

### 전체 서비스 시작

```powershell
# 모든 서비스 실행 (MySQL, Redis, Kafka, Ollama)
docker-compose up -d

# 전체 컨테이너 상태
docker ps

# Ollama 헬스 상태 확인
docker inspect mms-ollama --format "{{.State.Health.Status}}"
# 결과: healthy (정상) / unhealthy (문제) / starting (시작 중)

# Gemma3에게 인사하기
docker exec -it mms-ollama ollama run gemma3:4b "안녕하세요"

```

### 서비스 구성

| 서비스 | 컨테이너명 | 포트 | 설명 |
|--------|-----------|------|------|
| MySQL | mms-mysql | 3306 | 데이터베이스 |
| Redis | mms-redis | 6379 | OCR 결과 캐시 |
| Zookeeper | mms-zookeeper | 2181 | Kafka 코디네이터 |
| Kafka | mms-kafka | 9092 | 이벤트 브로커 |
| **Ollama** | **mms-ollama** | **11434** | **Gemma2 LLM 서버** |
| Ollama Init | mms-ollama-init | - | Gemma2 모델 자동 다운로드 |

### Ollama 상태 확인

```powershell
# Ollama 컨테이너 로그 확인
docker logs mms-ollama

# Gemma2 모델 다운로드 상태 확인 (첫 실행 시 약 1.6GB)
docker logs mms-ollama-init -f

# 모델 목록 확인
docker exec mms-ollama ollama list

# Gemma2 테스트
docker exec -it mms-ollama ollama run gemma2:2b "안녕하세요"
```

### 개별 서비스 실행

```powershell
# Ollama만 실행
docker-compose up -d ollama ollama-init

# 인프라만 실행 (MySQL, Redis, Kafka)
docker-compose up -d mysql redis zookeeper kafka
```

---

## 2. PaddleOCR ONNX 모델 설정

### 개요

PaddleOCR은 ONNX 형식의 딥러닝 모델을 사용하여 이미지에서 텍스트를 추출합니다.

| 모델 | 파일명 | 용도 | 크기 |
|------|--------|------|------|
| Detection | `PP-OCRv5_det.onnx` | 텍스트 영역 검출 | ~84MB |
| Recognition | `korean_PP-OCRv5_rec.onnx` | 문자 인식 | ~13MB |

### 모델 저장 위치

**기본값 (권장): 프로젝트 내 저장**

```
provider/src/main/resources/models/paddleocr/
├── PP-OCRv5_det.onnx
├── korean_PP-OCRv5_rec.onnx
└── README.md
```

> ⚠️ ONNX 파일은 `.gitignore`에 의해 Git에서 제외됩니다. 첫 실행 전 반드시 다운로드하세요.

**대안: 외부 경로 사용**

환경 변수로 경로 변경 가능:
```powershell
$env:PADDLEOCR_MODEL_PATH = "D:\models\paddleocr"
```

### 모델 다운로드 (PowerShell) - 프로젝트 내 저장

```powershell
# 프로젝트 루트에서 실행
$modelPath = "provider\src\main\resources\models\paddleocr"

# Detection 모델 다운로드 (PP-OCRv5)
Write-Host "Downloading Detection Model..."
Invoke-WebRequest `
    -Uri "https://huggingface.co/marsena/paddleocr-onnx-models/resolve/main/PP-OCRv5_server_det_infer.onnx" `
    -OutFile "$modelPath\PP-OCRv5_det.onnx"

# Recognition 모델 다운로드 (PP-OCRv5 Korean)
Write-Host "Downloading Recognition Model..."
Invoke-WebRequest `
    -Uri "https://huggingface.co/monkt/paddleocr-onnx/resolve/main/languages/korean/rec.onnx" `
    -OutFile "$modelPath\korean_PP-OCRv5_rec.onnx"

# 다운로드 확인
Get-ChildItem $modelPath
```

### 모델 다운로드 (Bash - Linux/Mac)

```bash
# 프로젝트 루트에서 실행
MODEL_PATH="provider/src/main/resources/models/paddleocr"

# Detection 모델
curl -L -o "$MODEL_PATH/PP-OCRv5_det.onnx" \
    "https://huggingface.co/marsena/paddleocr-onnx-models/resolve/main/PP-OCRv5_server_det_infer.onnx"

# Recognition 모델
curl -L -o "$MODEL_PATH/korean_PP-OCRv5_rec.onnx" \
    "https://huggingface.co/monkt/paddleocr-onnx/resolve/main/languages/korean/rec.onnx"

# 다운로드 확인
ls -la $MODEL_PATH
```

### 모델 소스

| 출처 | URL |
|------|-----|
| Hugging Face (ONNX) | https://huggingface.co/onnx-community/PaddleOCR |
| PaddleOCR 공식 | https://github.com/PaddlePaddle/PaddleOCR |
| 모델 변환 가이드 | https://paddleocr.ai/docs/paddle2onnx.html |

---

## 3. 애플리케이션 빌드 및 실행

### 사전 요구사항

- **Java 21** 이상
- **Docker Desktop** (Windows/Mac) 또는 Docker Engine (Linux)

### 빌드

```powershell
# Gradle 빌드
./gradlew build

# 특정 모듈만 빌드
./gradlew :worker:build
./gradlew :api:build
```

### 실행

```powershell
# 1. Docker 서비스 실행 (필수)
docker-compose up -d

# 2. Worker 실행 (OCR 처리)
./gradlew :worker:bootRun

# 3. API 서버 실행 (별도 터미널)
./gradlew :api:bootRun
```

### 환경 변수 설정 (선택)

```powershell
# Ollama 연결 URL (Docker 외부에서 Worker 실행 시)
$env:OLLAMA_BASE_URL = "http://localhost:11434"

# PaddleOCR 모델 경로 (기본값 외 위치 사용 시)
$env:PADDLEOCR_MODEL_PATH = "D:\models\paddleocr"
```

---

## 4. 동작 확인

### Ollama API 테스트

```powershell
# 모델 목록 확인
curl http://localhost:11434/api/tags

# 텍스트 생성 테스트
curl -X POST http://localhost:11434/api/generate `
    -H "Content-Type: application/json" `
    -d '{"model": "gemma2:2b", "prompt": "안녕하세요", "stream": false}'
```

### OCR API 테스트

```powershell
# OCR 요청 (Base64 이미지)
curl -X POST http://localhost:8080/ocr `
    -H "Content-Type: application/json" `
    -d '{"imageUrl": "data:image/jpeg;base64,/9j/4AAQ...", "documentType": "BUSINESS_LICENSE"}'

# 결과 조회
curl http://localhost:8080/ocr/{requestId}
```

---

## 5. 트러블슈팅

### Ollama 연결 오류

```
Connection refused: localhost:11434
```

**해결:**
```powershell
# Ollama 컨테이너 상태 확인
docker ps | findstr ollama

# 컨테이너 재시작
docker-compose restart ollama
```

### Gemma2 모델 없음

```
model 'gemma2:2b' not found
```

**해결:**
```powershell
# 수동으로 모델 Pull
docker exec mms-ollama ollama pull gemma2:2b
```

### PaddleOCR 모델 로드 실패

```
Failed to load PaddleOCR models
```

**해결:**
1. 모델 파일 존재 확인: `dir $env:USERPROFILE\.paddleocr\models`
2. 파일 손상 시 재다운로드
3. 경로가 다른 경우 환경 변수 설정

---

## 6. GPU 가속 (선택)

NVIDIA GPU가 있는 경우 Ollama에서 GPU 가속을 사용할 수 있습니다.

### docker-compose.yml 수정

```yaml
ollama:
  image: ollama/ollama:latest
  # GPU 설정 주석 해제
  deploy:
    resources:
      reservations:
        devices:
          - driver: nvidia
            count: 1
            capabilities: [gpu]
```

### 필수 요구사항

- NVIDIA GPU
- NVIDIA Driver
- NVIDIA Container Toolkit

```powershell
# NVIDIA Container Toolkit 설치 (Linux)
# https://docs.nvidia.com/datacenter/cloud-native/container-toolkit/install-guide.html
```

```
Swagger UI: http://localhost:8080/swagger-ui.html
OpenAPI JSON: http://localhost:8080/v3/api-docs
```