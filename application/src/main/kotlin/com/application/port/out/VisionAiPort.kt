package com.application.port.out

/**
 * Vision AI 전용 포트 인터페이스
 *
 * Gemma3, GPT-4 Vision 등 범용 AI Vision 모델을 위한 인터페이스입니다. 이미지 분석, 텍스트 추출, 객체 인식 등 다양한 Vision 기능을
 * 지원합니다.
 */
interface VisionAiPort {
    /**
     * 이미지를 분석하고 프롬프트에 따른 결과를 반환합니다.
     * @param imageBytes 이미지 바이트 배열
     * @param prompt 분석 요청 프롬프트
     * @return 분석 결과 텍스트
     */
    fun analyzeImage(imageBytes: ByteArray, prompt: String): String

    /**
     * 이미지에서 텍스트를 추출합니다. (OCR 용도)
     * @param imageBytes 이미지 바이트 배열
     * @return 추출된 텍스트
     */
    fun extractTextFromImage(imageBytes: ByteArray): String
}
