# Merchant Management System (Backend)

![Kotlin](https://img.shields.io/badge/Kotlin-1.9.25-7F52FF?style=flat&logo=kotlin&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5.8-6DB33F?style=flat&logo=spring-boot&logoColor=white)
![Apache Kafka](https://img.shields.io/badge/Apache%20Kafka-3.9.1-231F20?style=flat&logo=apache-kafka&logoColor=white)
![Redis](https://img.shields.io/badge/Redis-7.0-DC382D?style=flat&logo=redis&logoColor=white)
![Gemma3](https://img.shields.io/badge/Gemma3-LLM-8E75B2?style=flat&logo=google&logoColor=white)
![Docker](https://img.shields.io/badge/Docker-Compose-2496ED?style=flat&logo=docker&logoColor=white)

가맹점 관리 시스템의 Backend입니다. 사업자등록증 OCR 기능을 제공합니다.

## 주요 기능

- 사업자등록증/주민등록증/운전면허증 OCR
- 3개 OCR 엔진 앙상블 (PaddleOCR, Pororo, EasyOCR)
- LLM(Gemma3)으로 OCR 결과 교차 검증 및 필드 추출
- Kafka 기반 비동기 처리

## 기술 스택

- Kotlin 1.9 / Spring Boot 3.5
- Apache Kafka (비동기 처리)
- Redis (결과 캐싱)
- MySQL (가맹점 데이터)
- Docker Compose (OCR 엔진)
- Ollama + Gemma3 (LLM)

## 모듈 구조

```
├── api/         # REST API (OCR 요청/결과 조회)
├── worker/      # Kafka Consumer (OCR 처리)
├── provider/    # OCR 엔진 연동, LLM 프롬프트
├── domain/      # 엔티티
├── common/      # 공통 DTO
└── docker/      # OCR 엔진 Docker 설정
```

## 처리 흐름

```
1. Frontend에서 이미지 업로드
2. API 서버가 Kafka로 이벤트 발행 → 바로 requestId 반환
3. Worker가 이벤트 수신
4. 3개 OCR 엔진 병렬 호출 (Coroutines)
5. 품질 검사 (성공 엔진 수, 텍스트 길이, 한글 비율)
6. Gemma3로 결과 취합 및 필드 파싱
7. Redis에 결과 저장 (TTL 10분)
8. Frontend가 폴링으로 결과 조회
```

## 실행 방법

### 1. 인프라 실행

```bash
cd docker
docker-compose up -d
```

Kafka, Redis, MySQL, OCR 엔진들이 실행됩니다.

### 2. Backend 실행

```bash
# API 서버
./gradlew :api:bootRun

# Worker (별도 터미널)
./gradlew :worker:bootRun
```

### 3. Frontend 실행


## API

| Method | Endpoint | 설명 |
|--------|----------|------|
| POST | `/api/v1/ocr/request` | OCR 요청 |
| GET | `/api/v1/ocr/result/{requestId}` | 결과 조회 |
| POST | `/api/v1/ocr/save` | 가맹점 저장 |

### 응답 상태

- `PROCESSING`: 처리 중
- `COMPLETED`: 완료
- `FAILED`: 실패
- `LOW_QUALITY`: OCR 품질 미달

## OCR 엔진 구성

| 엔진 | 포트 | 비고 |
|------|------|------|
| PaddleOCR | 9001 | PP-OCRv5 한국어 |
| Pororo | 9004 | 카카오브레인, 한국어 특화 |
| EasyOCR | 9005 | 범용 |

3개 엔진을 병렬로 돌리고, LLM이 결과를 비교해서 필드별로 제일 정확해 보이는 값을 선택합니다.

우선순위: Pororo > EasyOCR > PaddleOCR

다만 우선순위가 높은 엔진이라도 형식이 안 맞으면 (예: 사업자번호가 XXX-XX-XXXXX가 아니면) 다른 엔진 값을 씁니다.

## 품질 검사

### Frontend (업로드 전)
- 문서 분류 신뢰도 70% 미만 → 거부
- 이미지 품질 50% 미만 → 경고

### Backend (OCR 후)
- 품질 점수 30% 미만 → LOW_QUALITY 반환
- 점수 = 성공 엔진 수(40%) + 텍스트 길이(30%) + 한글 비율(30%)

## 왜 이렇게 만들었나

**단일 OCR 한계**

한 엔진만 쓰면 이미지 상태에 따라 인식률 편차가 너무 컸습니다.
3개 돌리면 시간은 좀 더 걸리지만, 최소 하나는 제대로 읽어오는 경우가 많아졌습니다.

**비동기 처리**

OCR이 평균 3-5초 걸려서 동기 처리하면 타임아웃 나기 쉬웠습니다.
Kafka 넣고 폴링 방식으로 바꾸니까 안정적으로 돌아갑니다.

**LLM 검증**

OCR 결과가 비슷하게 틀리는 경우가 있어서 (예: 0과 O 혼동),
LLM한테 "사업자번호 형식은 이런 거야" 라고 알려주고 판단하게 했더니 정확도가 올라갔습니다.
