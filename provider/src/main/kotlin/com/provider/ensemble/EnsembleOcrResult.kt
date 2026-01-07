package com.provider.ensemble

import com.common.ocr.OcrRawResult

/** 앙상블 OCR 결과 3개 OCR 엔진의 결과를 담는 데이터 클래스 */
data class EnsembleOcrResult(
        val paddleOcr: OcrRawResult,
        val pororo: OcrRawResult,
        val easyOcr: OcrRawResult
) {
    /** 모든 엔진이 성공했는지 확인 */
    val allSuccess: Boolean
        get() = paddleOcr.success && pororo.success && easyOcr.success

    /** 성공한 엔진 수 */
    val successCount: Int
        get() = listOf(paddleOcr, pororo, easyOcr).count { it.success }

    /** 성공한 결과만 필터링 */
    val successfulResults: List<OcrRawResult>
        get() = listOf(paddleOcr, pororo, easyOcr).filter { it.success }

    /** 가장 높은 신뢰도의 결과 반환 */
    val bestResult: OcrRawResult?
        get() = successfulResults.maxByOrNull { it.confidence }

    /** 한글 비율이 가장 높은 결과 반환 */
    val bestKoreanResult: OcrRawResult?
        get() = successfulResults.maxByOrNull { calculateKoreanRatio(it.fullText) }

    /** Gemma3 프롬프트용 포맷된 결과 */
    fun toPromptFormat(): String = buildString {
        appendLine("[PaddleOCR]")
        appendLine(if (paddleOcr.success) paddleOcr.fullText else "(실패: ${paddleOcr.errorMessage})")
        appendLine()
        appendLine("[Pororo]")
        appendLine(if (pororo.success) pororo.fullText else "(실패: ${pororo.errorMessage})")
        appendLine()
        appendLine("[EasyOCR]")
        appendLine(if (easyOcr.success) easyOcr.fullText else "(실패: ${easyOcr.errorMessage})")
    }

    /**
     * OCR 품질 점수 계산 (0.0 ~ 1.0)
     * - 성공한 엔진 수: 40%
     * - 평균 텍스트 길이: 30%
     * - 평균 한글 비율: 30%
     */
    fun calculateQualityScore(): Double {
        // 1. 성공한 엔진 비율 (0~1)
        val engineSuccessRate = successCount / 3.0

        // 2. 평균 텍스트 길이 점수 (최소 100자 이상이면 1.0)
        val avgTextLength =
                successfulResults.map { it.fullText.length }.average().takeIf { !it.isNaN() } ?: 0.0
        val textLengthScore = (avgTextLength / 100.0).coerceAtMost(1.0)

        // 3. 평균 한글 비율
        val avgKoreanRatio =
                successfulResults.map { calculateKoreanRatio(it.fullText) }.average().takeIf {
                    !it.isNaN()
                }
                        ?: 0.0

        // 가중 평균
        return (engineSuccessRate * 0.4) + (textLengthScore * 0.3) + (avgKoreanRatio * 0.3)
    }

    /** 품질이 낮은지 확인 (점수 < 0.3) */
    val isLowQuality: Boolean
        get() = calculateQualityScore() < 0.3

    /** 품질 점수와 함께 상세 로그용 문자열 반환 */
    fun getQualityReport(): String {
        val score = calculateQualityScore()
        val avgKorean =
                successfulResults.map { calculateKoreanRatio(it.fullText) }.average().takeIf {
                    !it.isNaN()
                }
                        ?: 0.0
        val avgLength =
                successfulResults.map { it.fullText.length }.average().takeIf { !it.isNaN() } ?: 0.0
        return "품질점수: ${String.format("%.1f", score * 100)}%, 엔진성공: $successCount/3, 평균길이: ${avgLength.toInt()}자, 한글비율: ${String.format("%.1f", avgKorean * 100)}%"
    }

    companion object {
        /** 한글 비율 계산 */
        fun calculateKoreanRatio(text: String): Double {
            if (text.isBlank()) return 0.0
            val koreanChars = text.count { it in '\uAC00'..'\uD7A3' }
            val totalChars = text.replace(Regex("[\\s\\n]"), "").length
            return if (totalChars > 0) koreanChars.toDouble() / totalChars else 0.0
        }
    }
}
