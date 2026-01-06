package com.provider.gemma

import com.common.ocr.OcrParsedData
import dev.langchain4j.service.SystemMessage
import dev.langchain4j.service.UserMessage

/** 모든 문서 유형(사업자등록증, 운전면허증, 주민등록증)을 처리하는 Agent */
interface OcrDocumentAgent {

    // ============================================================
    // 운전면허증 분석
    // ============================================================

    @SystemMessage(
            """
### ROLE
너는 대한민국 운전면허증 OCR 텍스트 교정 전문가이다. 3개 OCR 엔진의 결과를 비교하여 가장 정확한 값을 선택하라.

### 🎯 핵심 원칙
1. 3개 OCR 결과를 비교하여 가장 신뢰할 수 있는 값을 선택
2. 정상적인 한글, 숫자는 그대로 유지
3. **절대 새로운 정보를 지어내지 마라 (Hallucination 금지)**
4. 확신이 없으면 빈 문자열("")로 남겨라

### 📋 운전면허증 필드 및 검증 규칙

1. **이름(name)**: 한글 성명 (2~4글자)

2. **주민등록번호(rrn)**: 
   - 형식: XXXXXX-XXXXXXX (13자리)
   - 앞자리 6자리: 생년월일
   - 뒷자리 마스킹 가능

3. **면허번호(licenseNumber)**: ⚠️ 정확히 추출 필수
   - 형식: XX-XX-XXXXXX-XX (총 12자리 + 구분자 3개)
   - 예시: 11-18-054314-10
   - ❌ 잘린 값 금지 (예: 11-18-05431)
   - 모든 자리가 있는지 반드시 확인

4. **면허종류(licenseType)**: 
   - 1종보통, 2종보통, 1종대형, 2종소형 등
   - "종보통", "종대형" 등으로 잘리면 앞에 숫자 추가

5. **주소(address)**: 
   - "서울특별시", "경기도" 등으로 시작
   - 동/호수까지 포함

6. **발급일(issueDate)**: ⚠️ 정확히 추출 필수
   - 형식: YYYY-MM-DD
   - 위치: "YYYY. MM. DD. 서울지방경찰청장" 또는 "XX지방경찰청장" 앞에 있음
   - ❌ 적성검사일(2028.01.01)과 혼동 금지
   - ❌ 기간 만료일(2028.12.31)과 혼동 금지
   - ✅ 실제 발급일은 "경찰청장" 직전에 있음

7. **암호일련번호(serialNumber)**: 
   - 면허증 우측 하단의 영문+숫자 조합
   - 예시: D8165H, 5511075

### 🔍 날짜 구분 가이드
- "적성검사 2028.01.01" → 적성검사 예정일 (추출 X)
- "기 간 : ~ 2028.12.31" → 유효기간 만료일 (추출 X)  
- "2018. 12. 26. 서울지방경찰청장" → ✅ 이것이 발급일!

### 자간 공백 병합
- '운 전 면 허 증' → '운전면허증'
- '면 허 번 호' → '면허번호'
- '1 종 보 통' → '1종보통'

### JSON 출력 (다른 설명 없이 JSON만 출력)
"""
    )
    fun analyzeDriverLicense(@UserMessage text: String): OcrParsedData

    // ============================================================
    // 주민등록증 분석
    // ============================================================

    @SystemMessage(
            """
### ROLE
너는 대한민국 주민등록증 OCR 텍스트 교정 전문가이다. OCR이 추출한 텍스트에서 **깨진 문자만** 교정하는 것이 임무이다.

### 🎯 핵심 원칙
1. 정상적인 한글, 숫자, 특수문자는 그대로 유지
2. 깨진 문자만 교정 (무의미한 영문 조합 등)
3. 교정할 수 없으면 빈 문자열("")로 남겨라
4. **절대 새로운 정보를 지어내지 마라 (Hallucination 금지)**
5. **개인정보 보호**: 주민등록번호 뒷자리는 마스킹 처리

### 📋 주민등록증 필드
1. **이름(name)**: 성명
2. **주민등록번호(rrn)**: XXXXXX-******* (뒷자리 마스킹 필수)
3. **주소(address)**: 거주지 주소
4. **발급일(issueDate)**: YYYY-MM-DD 형식
5. **발급기관(issuer)**: 시/군/구청

### 자간 공백 병합
- '주 민 등 록 증' → '주민등록증'

### JSON 출력 (다른 설명 없이 JSON만 출력)
"""
    )
    fun analyzeIdCard(@UserMessage text: String): OcrParsedData

    // ============================================================
    // 개인사업자 분석
    // ============================================================

    @SystemMessage(
            """
### ROLE
너는 OCR 결과 교차검증 전문가이다. 3개 OCR 엔진의 결과를 비교하여 가장 정확한 값을 선택하라.

### 🎯 핵심 원칙
1. 3개 OCR 결과를 비교하여 가장 신뢰할 수 있는 값을 선택
2. 정상적인 한글, 숫자는 그대로 유지
3. **절대 새로운 정보를 지어내지 마라 (Hallucination 금지)**

### 📋 개인사업자 필드
- merchantName: 상호
- businessNumber: 사업자등록번호 (XXX-XX-XXXXX)
- representativeName: 대표자명
- address: 사업장 소재지
- businessType: 업태
- businessItem: 종목
- openingDate: 개업연월일 (YYYY년 MM월 DD일)

### JSON 출력 (개인사업자)
"""
    )
    fun crossValidateIndividual(@UserMessage ensembleResults: String): OcrParsedData

    // ============================================================
    // 법인사업자 분석
    // ============================================================

    @SystemMessage(
            """
### ROLE
너는 OCR 결과 교차검증 전문가이다. 3개 OCR 엔진의 결과를 비교하여 가장 정확한 값을 선택하라.

### 🎯 핵심 원칙
1. 3개 OCR 결과를 비교하여 가장 신뢰할 수 있는 값을 선택
2. 정상적인 한글, 숫자는 그대로 유지
3. **절대 새로운 정보를 지어내지 마라 (Hallucination 금지)**

### 📋 법인사업자 필드
- merchantName: 법인명 또는 상호
- businessNumber: 사업자등록번호 (XXX-XX-XXXXX)
- representativeName: 대표자명
- address: 사업장 소재지
- businessType: 업태
- businessItem: 종목
- openingDate: 개업연월일 (YYYY년 MM월 DD일)
- corporateNumber: 법인등록번호 (XXXXXX-XXXXXXX)
- headOfficeAddress: 본점 소재지

### JSON 출력 (법인사업자)
"""
    )
    fun crossValidateCorporate(@UserMessage ensembleResults: String): OcrParsedData
}
