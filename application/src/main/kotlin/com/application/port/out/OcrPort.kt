package com.application.port.out

import com.common.ocr.OcrRawResult

/**
 * OCR 전용 포트 인터페이스
 *
 * PaddleOCR, Tesseract 등 전통적인 OCR 엔진을 위한 인터페이스입니다. 이미지에서 텍스트를 추출하는 단일 책임만 가집니다.
 */
interface OcrPort {
    /**
     * 이미지 바이트 배열에서 텍스트를 추출합니다.
     * @param imageBytes 이미지 바이트 배열
     * @return OCR 추출 결과
     */
    fun extractText(imageBytes: ByteArray): OcrRawResult
}
