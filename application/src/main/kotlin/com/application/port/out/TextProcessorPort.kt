package com.application.port.out

import com.common.ocr.BusinessLicenseData
import com.common.ocr.DocumentType

/**
 * LLM 기반 텍스트 처리 Port
 * Gemma2를 통한 문서 분류, 필드 파싱, OCR 오류 보정을 담당
 */
interface TextProcessorPort {
    /**
     * OCR 텍스트를 기반으로 문서 유형을 분류합니다.
     * @param text OCR 추출 텍스트
     * @return 문서 유형 (INDIVIDUAL, CORPORATE, UNKNOWN)
     */
    fun classifyDocument(text: String): DocumentType
    
    /**
     * 사업자등록증 텍스트에서 필드를 파싱합니다.
     * @param text OCR 추출 텍스트
     * @param documentType 문서 유형 (파싱 로직 최적화용)
     * @return 파싱된 사업자등록증 데이터
     */
    fun parseBusinessLicense(text: String, documentType: DocumentType = DocumentType.UNKNOWN): BusinessLicenseData
    
    /**
     * OCR 오류를 보정합니다.
     * @param text OCR 추출 텍스트
     * @return 오류가 보정된 텍스트
     */
    fun correctOcrErrors(text: String): String
}
