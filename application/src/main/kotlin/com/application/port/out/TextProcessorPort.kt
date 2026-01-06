package com.application.port.out

import com.common.ocr.OcrParsedData

/**
 * LLM 기반 텍스트 처리 Port Gemma3를 통한 OCR 오류 보정 및 필드 파싱을 담당 문서 유형(사업자등록증/운전면허증/주민등록증)과 사업자 유형(개인/법인)에 따라
 * 다른 프롬프트를 사용
 */
interface TextProcessorPort {

    /**
     * 앙상블 OCR 결과를 교차검증하여 필드를 파싱합니다.
     * @param ensembleResults 3개 OCR 엔진 결과 포맷 문자열
     * @param documentType 문서 유형 (BUSINESS_LICENSE, DRIVER_LICENSE, ID_CARD)
     * @param businessType 사업자 유형 (INDIVIDUAL or CORPORATE) - 사업자등록증에서만 사용
     * @return 파싱된 OCR 데이터
     */
    fun crossValidateAndParse(
            ensembleResults: String,
            documentType: String,
            businessType: String = "CORPORATE"
    ): OcrParsedData
}
