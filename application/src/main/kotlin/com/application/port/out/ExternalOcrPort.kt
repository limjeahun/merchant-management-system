package com.application.port.out

import com.common.ocr.OcrRawResult

interface ExternalOcrPort {
    /**
     * 이미지 바이트 배열에서 텍스트를 추출합니다.
     * @param imageBytes 이미지 바이트 배열
     * @return OCR 추출 결과
     */
    fun extractText(imageBytes: ByteArray): OcrRawResult
    
    /**
     * @deprecated 로컬 OCR로 마이그레이션됨. extractText(ByteArray)를 사용하세요.
     */
    @Deprecated("Use extractText(ByteArray) instead", ReplaceWith("extractText(imageBytes)"))
    fun extractText(imageUrl: String): String = ""
}