package com.provider.ensemble

import com.application.port.out.OcrPort
import com.common.ocr.OcrRawResult
import com.provider.easyocr.EasyOcrProvider
import com.provider.paddleocr.PaddleOcrApiProvider
import com.provider.pororo.PororoOcrProvider
import jakarta.annotation.PostConstruct
import kotlinx.coroutines.*
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

/**
 * 앙상블 OCR Provider (Kotlin Coroutines 버전)
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

        // IO 작업용 Dispatcher (HTTP 호출에 최적화)
        private val ocrDispatcher = Dispatchers.IO

        @PostConstruct
        fun initialize() {
                logger.info(
                        "EnsembleOcrProvider initialized (enabled: $ensembleEnabled, timeout: ${timeoutSeconds}s, dispatcher: IO)"
                )
        }

        /** 기본 OCR 추출 (앙상블 활성화시 PaddleOCR 결과 반환) */
        override fun extractText(imageBytes: ByteArray): OcrRawResult {
                return if (ensembleEnabled) {
                        // 앙상블 모드: 모든 결과 중 최고 품질 반환
                        val ensemble = runBlocking { extractTextParallel(imageBytes) }
                        ensemble.bestKoreanResult ?: ensemble.bestResult ?: ensemble.paddleOcr
                } else {
                        // 단일 모드: PaddleOCR만 사용
                        paddleOcrProvider.extractText(imageBytes)
                }
        }

        /** 3개 OCR 엔진 병렬 실행 (Coroutines) */
        suspend fun extractTextParallel(imageBytes: ByteArray): EnsembleOcrResult = coroutineScope {
                logger.info("=== 앙상블 OCR 시작 (Coroutines 병렬 실행) ===")
                val startTime = System.currentTimeMillis()

                // 병렬 실행 with timeout
                val paddleDeferred = async(ocrDispatcher) { extractWithPaddle(imageBytes) }
                val pororoDeferred = async(ocrDispatcher) { extractWithPororo(imageBytes) }
                val easyOcrDeferred = async(ocrDispatcher) { extractWithEasyOcr(imageBytes) }

                // 전체 타임아웃 적용
                val (paddleResult, pororoResult, easyOcrResult) =
                        try {
                                withTimeout(timeoutSeconds * 1000) {
                                        Triple(
                                                paddleDeferred.await(),
                                                pororoDeferred.await(),
                                                easyOcrDeferred.await()
                                        )
                                }
                        } catch (e: TimeoutCancellationException) {
                                logger.warn("앙상블 OCR 타임아웃 (${timeoutSeconds}s)")
                                // 타임아웃 시 완료된 결과만 수집
                                Triple(
                                        paddleDeferred.getCompletedOrDefault(
                                                createTimeoutResult("paddleocr")
                                        ),
                                        pororoDeferred.getCompletedOrDefault(
                                                createTimeoutResult("pororo")
                                        ),
                                        easyOcrDeferred.getCompletedOrDefault(
                                                createTimeoutResult("easyocr")
                                        )
                                )
                        }

                val elapsed = System.currentTimeMillis() - startTime
                val ensemble = EnsembleOcrResult(paddleResult, pororoResult, easyOcrResult)

                logger.info("=== 앙상블 OCR 완료 (${elapsed}ms) ===")
                logger.info("  성공: ${ensemble.successCount}/3")
                logger.info(
                        "  PaddleOCR: ${if (paddleResult.success) "✓ (${paddleResult.lines.size}줄)" else "✗"}"
                )
                logger.info(
                        "  Pororo: ${if (pororoResult.success) "✓ (${pororoResult.lines.size}줄)" else "✗"}"
                )
                logger.info(
                        "  EasyOCR: ${if (easyOcrResult.success) "✓ (${easyOcrResult.lines.size}줄)" else "✗"}"
                )

                ensemble
        }

        // === Private helper functions ===

        private fun extractWithPaddle(imageBytes: ByteArray): OcrRawResult {
                return runCatching {
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
        }

        private fun extractWithPororo(imageBytes: ByteArray): OcrRawResult {
                return runCatching {
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
        }

        private fun extractWithEasyOcr(imageBytes: ByteArray): OcrRawResult {
                return runCatching {
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
        }

        private fun createTimeoutResult(engine: String) =
                OcrRawResult(success = false, errorMessage = "Timeout", engine = engine)

        private fun <T> Deferred<T>.getCompletedOrDefault(default: T): T {
                return if (isCompleted && !isCancelled) getCompleted() else default
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
