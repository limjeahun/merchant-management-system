package com.provider.ensemble

import com.application.port.out.OcrPort
import com.common.ocr.OcrRawResult
import com.provider.easyocr.EasyOcrProvider
import com.provider.paddleocr.PaddleOcrApiProvider
import com.provider.pororo.PororoOcrProvider
import jakarta.annotation.PostConstruct
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

/**
 * 앙상블 OCR Provider
 *
 * 3개의 OCR 엔진(PaddleOCR, Pororo, EasyOCR)을 병렬로 실행하고 결과를 통합하여 Gemma3 교차검증에 전달
 */
@Component
class EnsembleOcrProvider(
        private val paddleOcrProvider: PaddleOcrApiProvider,
        private val pororoOcrProvider: PororoOcrProvider,
        private val easyOcrProvider: EasyOcrProvider,
        @Value("\${ensemble.timeout:90}") private val timeoutSeconds: Long,
        @Value("\${ensemble.enabled:true}") private val ensembleEnabled: Boolean
) : OcrPort {

    private val logger = LoggerFactory.getLogger(EnsembleOcrProvider::class.java)
    private lateinit var executor: ExecutorService

    @PostConstruct
    fun initialize() {
        executor = Executors.newFixedThreadPool(3)
        logger.info(
                "EnsembleOcrProvider initialized (enabled: $ensembleEnabled, timeout: ${timeoutSeconds}s)"
        )
    }

    /** 기본 OCR 추출 (앙상블 활성화시 PaddleOCR 결과 반환) */
    override fun extractText(imageBytes: ByteArray): OcrRawResult {
        return if (ensembleEnabled) {
            // 앙상블 모드: 모든 결과 중 최고 품질 반환
            val ensemble = extractTextParallel(imageBytes)
            ensemble.bestKoreanResult ?: ensemble.bestResult ?: ensemble.paddleOcr
        } else {
            // 단일 모드: PaddleOCR만 사용
            paddleOcrProvider.extractText(imageBytes)
        }
    }

    /** 3개 OCR 엔진 병렬 실행 */
    fun extractTextParallel(imageBytes: ByteArray): EnsembleOcrResult {
        logger.info("=== 앙상블 OCR 시작 (3개 엔진 병렬 실행) ===")
        val startTime = System.currentTimeMillis()

        // CompletableFuture로 병렬 실행
        val paddleFuture =
                CompletableFuture.supplyAsync(
                        {
                            runCatching {
                                logger.info("[PaddleOCR] 시작...")
                                val result = paddleOcrProvider.extractText(imageBytes)
                                logger.info("[PaddleOCR] 완료 (${result.lines.size}줄)")
                                result
                            }
                                    .getOrElse { e ->
                                        logger.error("[PaddleOCR] 실패: ${e.message}")
                                        OcrRawResult(
                                                success = false,
                                                errorMessage = e.message ?: "Unknown",
                                                engine = "paddleocr"
                                        )
                                    }
                        },
                        executor
                )

        val pororoFuture =
                CompletableFuture.supplyAsync(
                        {
                            runCatching {
                                logger.info("[Pororo] 시작...")
                                val result = pororoOcrProvider.extractText(imageBytes)
                                logger.info("[Pororo] 완료 (${result.lines.size}줄)")
                                result
                            }
                                    .getOrElse { e ->
                                        logger.error("[Pororo] 실패: ${e.message}")
                                        OcrRawResult(
                                                success = false,
                                                errorMessage = e.message ?: "Unknown",
                                                engine = "pororo"
                                        )
                                    }
                        },
                        executor
                )

        val easyOcrFuture =
                CompletableFuture.supplyAsync(
                        {
                            runCatching {
                                logger.info("[EasyOCR] 시작...")
                                val result = easyOcrProvider.extractText(imageBytes)
                                logger.info("[EasyOCR] 완료 (${result.lines.size}줄)")
                                result
                            }
                                    .getOrElse { e ->
                                        logger.error("[EasyOCR] 실패: ${e.message}")
                                        OcrRawResult(
                                                success = false,
                                                errorMessage = e.message ?: "Unknown",
                                                engine = "easyocr"
                                        )
                                    }
                        },
                        executor
                )

        // 모든 Future 완료 대기 (타임아웃 적용)
        val allFutures = CompletableFuture.allOf(paddleFuture, pororoFuture, easyOcrFuture)

        try {
            allFutures.get(timeoutSeconds, TimeUnit.SECONDS)
        } catch (e: Exception) {
            logger.warn("앙상블 OCR 타임아웃 또는 오류: ${e.message}")
        }

        val paddleResult =
                paddleFuture.getNow(
                        OcrRawResult(
                                success = false,
                                errorMessage = "Timeout",
                                engine = "paddleocr"
                        )
                )
        val pororoResult =
                pororoFuture.getNow(
                        OcrRawResult(success = false, errorMessage = "Timeout", engine = "pororo")
                )
        val easyOcrResult =
                easyOcrFuture.getNow(
                        OcrRawResult(success = false, errorMessage = "Timeout", engine = "easyocr")
                )

        val elapsed = System.currentTimeMillis() - startTime
        val ensemble = EnsembleOcrResult(paddleResult, pororoResult, easyOcrResult)

        logger.info("=== 앙상블 OCR 완료 (${elapsed}ms) ===")
        logger.info("  성공: ${ensemble.successCount}/3")
        logger.info("  PaddleOCR: ${if (paddleResult.success) "✓" else "✗"}")
        logger.info("  Pororo: ${if (pororoResult.success) "✓" else "✗"}")
        logger.info("  EasyOCR: ${if (easyOcrResult.success) "✓" else "✗"}")

        return ensemble
    }

    /** 헬스체크 - 최소 1개 엔진 정상이면 healthy */
    fun checkHealth(): Map<String, Boolean> {
        return mapOf(
                "paddleocr" to
                        runCatching { paddleOcrProvider.extractText(ByteArray(0)).success }
                                .getOrDefault(false),
                "pororo" to pororoOcrProvider.isHealthy(),
                "easyocr" to easyOcrProvider.isHealthy()
        )
    }
}
