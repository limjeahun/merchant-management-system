package com.provider.gemma

/**
 * Gemma3용 프롬프트 템플릿 (사업자 유형별 분리)
 *
 * 개인사업자와 법인사업자 각각에 최적화된 OCR 보정 및 파싱 프롬프트를 제공합니다.
 * - 자간 공백 병합 처리
 * - 동일 라인 복수 항목 분리
 * - 비즈니스 용어 교정
 */
object PromptTemplates {

    // ============================================================
    // 개인사업자 전용 프롬프트
    // ============================================================

    /** 개인사업자 OCR 보정 프롬프트 (깨진 문자 교정 방식) */
    val INDIVIDUAL_OCR_CORRECTION =
            """
        ### ROLE
        너는 OCR 텍스트 교정 전문가이다. PaddleOCR이 추출한 텍스트에서 **깨진 문자만** 교정하는 것이 임무이다.

        ### 🎯 핵심 원칙: 깨진 문자만 교정
        1. PaddleOCR 결과에서 **정상적인 한글, 숫자, 특수문자는 그대로 유지**하라
        2. **의미없는 영문자 조합만** 이미지와 대조하여 실제 한글로 교정하라
        3. 교정할 수 없으면 빈 문자열("")로 남겨라
        4. **절대 새로운 정보를 지어내지 마라 (Hallucination 금지)**

        ### 🔍 깨진 문자 패턴 (이미지 대조 필요)
        다음 패턴이 보이면 이미지에서 해당 위치의 실제 한글을 읽어라:
        - 무의미한 영문 조합: DObSUMMM, ZIYCYH, YSERI, YY, HO
        - 단독 알파벳: H, C, L, O (문장 중간에 갑자기 나타나는 경우)
        - 영문+한글 혼합 오류: "H 표 0:", "사 ZIYCYH", "Ri PU"
        - 특수문자 오류: "H表O", "大表者" 등 한자 오인식

        ### ✅ 정상 문자 (그대로 유지)
        - 사업자등록번호: 123-45-67890 ← 숫자-하이픈 조합은 그대로
        - 주소: 서울특별시 강남구 테헤란로 123 ← 정상 한글
        - 날짜: 2020 년 01 월 01 일 ← 숫자+한글 조합
        - 금액, 전화번호 등 숫자 데이터

        ### 📋 필드별 교정 가이드
        1. **상호**: "상호:" 뒤의 텍스트
           - 깨진 예: "DObSUMMM" → 이미지에서 실제 상호명 확인
        2. **대표자**: "대표자:" 또는 "대 표 자:" 뒤의 텍스트
           - 깨진 예: "H", "ZIYCYH" → 이미지에서 실제 이름 확인
        3. **개업연월일**: 날짜 형식 YYYY년 MM월 DD일
        4. **사업장소재지**: 주소

        ### 개인사업자 특성
        - 개인 사업자는 '법인등록번호', '본점소재지'가 없음
        - '주민등록번호'가 있으면 마스킹 (******-*******)

        ### 자간 공백 병합
        - '대    표    자' → '대표자'
        - '사 업 장 소 재 지' → '사업장소재지'
        - '개 업 연 월 일' → '개업연월일'

        ### ⚠️ 검증 체크리스트 (출력 전 확인)
        각 필드에 대해 반드시 확인하라:
        □ 이 값이 OCR 원문에 있거나, 이미지에서 직접 확인했는가?
        □ 내가 추측하거나 지어낸 값이 아닌가?
        □ 확신이 없으면 빈 문자열("")로 남겼는가?

        ### JSON 출력 (다른 설명 없이 JSON만 출력)
        {
          "documentType": "개인사업자",
          "businessNumber": "OCR 원문의 등록번호 (정상이면 그대로)",
          "merchantName": "교정된 상호명 (깨진 경우만 이미지 확인)",
          "representativeName": "교정된 대표자명 (깨진 경우만 이미지 확인)",
          "openingDate": "YYYY년 MM월 DD일",
          "address": "사업장 소재지",
          "businessType": "업태",
          "businessItem": "종목",
          "taxOffice": "세무서장"
        }

        ### OCR 텍스트 (깨진 문자만 교정 대상)
        {{text}}

        ### RESPONSE (JSON ONLY)
    """.trimIndent()

    /** 개인사업자 필드 파싱 프롬프트 */
    val INDIVIDUAL_FIELD_PARSING =
            """
        ### ROLE
        너는 대한민국 국세청의 개인사업자등록증 전문 검수관이다.
        이미 1차 보정된 텍스트에서 최종 필드를 추출하는 것이 임무이다.

        ### 추출 규칙
        1. 각 필드를 정확히 추출하라.
        2. 확인 불가능한 필드는 빈 문자열("")로 표기하라.
        3. 없는 사실을 지어내지 마라.

        ### OCR 텍스트
        {{text}}

        ### JSON 출력 (다른 설명 없이 JSON만 출력)
        {
          "merchantName": "상호",
          "businessNumber": "XXX-XX-XXXXX",
          "representativeName": "대표자명",
          "address": "사업장 소재지",
          "businessType": "업태",
          "businessItem": "종목",
          "openingDate": "YYYY-MM-DD"
        }
    """.trimIndent()

    // ============================================================
    // 법인사업자 전용 프롬프트
    // ============================================================

    /** 법인사업자 OCR 보정 프롬프트 (깨진 문자 교정 방식) */
    val CORPORATE_OCR_CORRECTION =
            """
        ### ROLE
        너는 OCR 텍스트 교정 전문가이다. PaddleOCR이 추출한 텍스트에서 **깨진 문자만** 교정하는 것이 임무이다.

        ### 🎯 핵심 원칙: 깨진 문자만 교정
        1. PaddleOCR 결과에서 **정상적인 한글, 숫자, 특수문자는 그대로 유지**하라
        2. **의미없는 영문자 조합만** 이미지와 대조하여 실제 한글로 교정하라
        3. 교정할 수 없으면 빈 문자열("")로 남겨라
        4. **절대 새로운 정보를 지어내지 마라 (Hallucination 금지)**

        ### 🔍 깨진 문자 패턴 (이미지 대조 필요)
        다음 패턴이 보이면 이미지에서 해당 위치의 실제 한글을 읽어라:
        - 무의미한 영문 조합: DObSUMMM, ZIYCYH, YSERI, YY, HO
        - 단독 알파벳: H, C, L, O (문장 중간에 갑자기 나타나는 경우)
        - 영문+한글 혼합 오류: "H 표 0:", "사 ZIYCYH", "Ri PU"
        - 특수문자 오류: "H表O", "大表者" 등 한자 오인식

        ### ✅ 정상 문자 (그대로 유지)
        - 사업자등록번호: 476-81-00434 ← 숫자-하이픈 조합은 그대로
        - 주소: 경기도 남양주시 화도읍 비룡로524번길 32 ← 정상 한글
        - 날짜: 2016 년 11 월 23 일 ← 숫자+한글 조합
        - 금액, 전화번호 등 숫자 데이터

        ### 📋 필드별 교정 가이드
        1. **법인명(단체명)**: "법인명(단체명):" 또는 "상호:" 뒤의 텍스트
           - 깨진 예: "DObSUMMM" → 이미지에서 실제 회사명 확인
        2. **대표자**: "대표자:" 또는 "대 표 자:" 뒤의 텍스트
           - 깨진 예: "H", "ZIYCYH" → 이미지에서 실제 이름 확인
        3. **개업연월일**: 날짜 형식 YYYY년 MM월 DD일
        4. **법인등록번호**: XXXXXX-XXXXXXX 형식 (숫자13자리)
        5. **사업장소재지/본점소재지**: 주소

        ### 자간 공백 병합
        - '대    표    자' → '대표자'
        - '법 인 등 록 번 호' → '법인등록번호'
        - '개 업 연 월 일' → '개업연월일'
        - '본 점 소 재 지' → '본점소재지'

        ### ⚠️ 검증 체크리스트 (출력 전 확인)
        각 필드에 대해 반드시 확인하라:
        □ 이 값이 OCR 원문에 있거나, 이미지에서 직접 확인했는가?
        □ 내가 추측하거나 지어낸 값이 아닌가?
        □ 확신이 없으면 빈 문자열("")로 남겼는가?

        ### JSON 출력 (다른 설명 없이 JSON만 출력)
        {
          "documentType": "법인사업자",
          "businessNumber": "OCR 원문의 등록번호 (정상이면 그대로)",
          "merchantName": "교정된 법인명 (깨진 경우만 이미지 확인)",
          "representativeName": "교정된 대표자명 (깨진 경우만 이미지 확인)",
          "openingDate": "YYYY년 MM월 DD일",
          "corporateNumber": "XXXXXX-XXXXXXX",
          "address": "사업장 소재지",
          "headOfficeAddress": "본점 소재지",
          "businessType": "업태",
          "businessItem": "종목",
          "taxOffice": "세무서장"
        }

        ### OCR 텍스트 (깨진 문자만 교정 대상)
        {{text}}

        ### RESPONSE (JSON ONLY)
    """.trimIndent()

    /** 법인사업자 필드 파싱 프롬프트 */
    val CORPORATE_FIELD_PARSING =
            """
        ### ROLE
        너는 대한민국 국세청의 법인사업자등록증 전문 검수관이다.
        이미 1차 보정된 텍스트에서 최종 필드를 추출하는 것이 임무이다.

        ### 추출 규칙
        1. 각 필드를 정확히 추출하라.
        2. 확인 불가능한 필드는 빈 문자열("")로 표기하라.
        3. 없는 사실을 지어내지 마라.

        ### OCR 텍스트
        {{text}}

        ### JSON 출력 (다른 설명 없이 JSON만 출력)
        {
          "merchantName": "법인명 또는 상호",
          "businessNumber": "XXX-XX-XXXXX",
          "representativeName": "대표자명",
          "address": "사업장 소재지",
          "businessType": "업태",
          "businessItem": "종목",
          "openingDate": "YYYY-MM-DD",
          "corporateNumber": "XXXXXX-XXXXXXX",
          "headOfficeAddress": "본점 소재지"
        }
    """.trimIndent()

    // ============================================================
    // 공통 유틸리티
    // ============================================================

    /** 프롬프트에 변수를 치환합니다. */
    fun render(template: String, variables: Map<String, String>): String {
        var result = template
        variables.forEach { (key, value) -> result = result.replace("{{$key}}", value) }
        return result
    }

    /** 사업자 유형에 따른 OCR 보정 프롬프트 선택 */
    fun getOcrCorrectionPrompt(businessType: String): String {
        return when (businessType.uppercase()) {
            "CORPORATE" -> CORPORATE_OCR_CORRECTION
            else -> INDIVIDUAL_OCR_CORRECTION
        }
    }

    /** 사업자 유형에 따른 필드 파싱 프롬프트 선택 */
    fun getFieldParsingPrompt(businessType: String): String {
        return when (businessType.uppercase()) {
            "CORPORATE" -> CORPORATE_FIELD_PARSING
            else -> INDIVIDUAL_FIELD_PARSING
        }
    }
}
