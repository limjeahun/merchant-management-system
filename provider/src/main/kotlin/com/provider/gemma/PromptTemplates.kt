package com.provider.gemma

/** Gemma2용 프롬프트 템플릿 사업자등록증 분류, 필드 파싱, OCR 오류 보정에 사용됩니다. */
object PromptTemplates {

    /** 문서 유형 분류 프롬프트 */
    val DOCUMENT_CLASSIFICATION =
            """
        당신은 한국의 사업자등록증을 분석하는 전문가입니다.
        다음 OCR 텍스트가 어떤 유형의 사업자등록증인지 분류하세요.
        
        [분류 기준]
        - INDIVIDUAL: 개인사업자 (법인등록번호가 없음)
        - CORPORATE: 법인사업자 (법인등록번호가 있음, 법인명/단체명 표기)
        - UNKNOWN: 사업자등록증이 아니거나 판별 불가
        
        [OCR 텍스트]
        {{text}}
        
        위 텍스트를 분석하고, 반드시 다음 중 하나만 출력하세요: INDIVIDUAL, CORPORATE, UNKNOWN
    """.trimIndent()

    /** 개인사업자등록증 필드 파싱 프롬프트 */
    val INDIVIDUAL_FIELD_PARSING =
            """
        당신은 한국의 개인사업자등록증을 분석하는 OCR 전문가입니다.
        다음 OCR 텍스트에서 정보를 추출하여 JSON으로 반환하세요.
        Markdown code block 없이 순수 JSON만 반환하세요.
        
        [추출 항목]
        - merchantName: 상호
        - businessNumber: 사업자등록번호 (XXX-XX-XXXXX 형식)
        - representativeName: 대표자 성명
        - address: 사업장 소재지
        - businessType: 업태
        - businessItem: 종목
        - openingDate: 개업연월일 (YYYY-MM-DD 형식으로 변환)
        
        [OCR 텍스트]
        {{text}}
        
        JSON 형식으로만 응답하세요:
    """.trimIndent()

    /** 법인사업자등록증 필드 파싱 프롬프트 */
    val CORPORATE_FIELD_PARSING =
            """
        당신은 한국의 법인사업자등록증을 분석하는 OCR 전문가입니다.
        다음 OCR 텍스트에서 정보를 추출하여 JSON으로 반환하세요.
        Markdown code block 없이 순수 JSON만 반환하세요.
        
        [추출 항목]
        - merchantName: 법인명(단체명) 또는 상호
        - businessNumber: 사업자등록번호 (XXX-XX-XXXXX 형식)
        - representativeName: 대표자 성명
        - address: 사업장 소재지
        - businessType: 업태
        - businessItem: 종목
        - openingDate: 개업연월일 (YYYY-MM-DD 형식으로 변환)
        - corporateNumber: 법인등록번호 (XXXXXX-XXXXXXX 형식)
        - headOfficeAddress: 본점 소재지 (있는 경우)
        
        [OCR 텍스트]
        {{text}}
        
        JSON 형식으로만 응답하세요:
    """.trimIndent()

    /** OCR 오류 보정 프롬프트 (사업자등록증 최적화) */
    val OCR_CORRECTION =
            """
        당신은 한국어 OCR 오류 보정 전문가입니다.
        다음 OCR 텍스트에서 인식 오류를 보정하세요.
        
        [기본 보정 규칙]
        1. 한글 자모가 잘못 인식된 경우 수정 (예: 들록 → 등록, 법인둥록번호 → 법인등록번호)
        2. 숫자와 문자가 혼동된 경우 수정 (예: O → 0, l → 1, I → 1)
        3. 띄어쓰기 오류 수정
        4. 문맥에 맞지 않는 단어 수정
        5. 사업자등록증 관련 용어 교정
        
        [사업자등록증 특화 보정]
        - 국비철, 국세청, 굿세청, 국셰청 → 국세청
        - 등족번호, 둥록번호, 등록버호, 등록번흐 → 등록번호
        - 법인둥록, 법인들록, 법인등룩, 번인등록 → 법인등록
        - 사업잦소재지, 사업장소재치, 업장소재지 → 사업장소재지
        - 본점소개지, 본점소재치, 본정소재지 → 본점소재지
        - 개업연얼일, 개업년월일, 개업연윌일 → 개업연월일
        - 대포자, 대표지, 대푯자 → 대표자
        - 법인멍, 법인녕, 법인몀 → 법인명
        - 단체용폼, 단체용퓸, 담체용품 → 단체용품
        - 사업의종류, 사업의촌류, 업의종류 → 사업의종류
        - 업태소매, 업태소메, 엽태소매 → 업태소매
        - 종목, 촌목, 종뫅 → 종목
        
        [숫자 형식 보정]
        - 전화번호: 031-XXX-XXXX 또는 02-XXXX-XXXX 형식으로 보정
        - 사업자등록번호: XXX-XX-XXXXX 형식으로 보정 (하이픈 추가)
        - 법인등록번호: XXXXXX-XXXXXXX 형식으로 보정 (하이픈 추가)
        - 날짜: YYYY년 MM월 DD일 형식으로 보정
        
        [원본 OCR 텍스트]
        {{text}}
        
        보정된 텍스트만 출력하세요 (설명 없이):
    """.trimIndent()

    /** 프롬프트에 변수를 치환합니다. */
    fun render(template: String, variables: Map<String, String>): String {
        var result = template
        variables.forEach { (key, value) -> result = result.replace("{{$key}}", value) }
        return result
    }
}
