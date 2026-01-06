package com.worker.consumer

import com.application.port.out.TextProcessorPort
import com.common.event.OcrRequestEvent
import com.domain.documents.OcrDocument
import com.domain.repository.OcrCacheRepository
import com.provider.ensemble.EnsembleOcrProvider
import java.util.Base64
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient

@Component
class OcrEventConsumer(
        private val ensembleOcrProvider: EnsembleOcrProvider,
        private val textProcessorPort: TextProcessorPort,
        private val ocrCacheRepository: OcrCacheRepository,
        @Value("\${ensemble.enabled:true}") private val ensembleEnabled: Boolean
) {
    private val logger = LoggerFactory.getLogger(OcrEventConsumer::class.java)
    private val restClient = RestClient.create()

    /**
     * OCR 이벤트 처리 파이프라인 (앙상블 버전)
     * 1. 이미지 다운로드
     * 2. 3개 OCR 엔진 병렬 실행 (PaddleOCR, Pororo, EasyOCR)
     * 3. Gemma3로 교차검증 + 필드 파싱
     * 4. Redis에 결과 저장
     */
    @KafkaListener(topics = ["mms.ocr.business-license.request"], groupId = "mms.ocr.worker-group")
    fun consumeOcrRequest(event: OcrRequestEvent) {
        logger.info("=== OCR 처리 시작 (앙상블 모드: $ensembleEnabled) ===")
        logger.info("RequestId: ${event.requestId}")
        logger.info("BusinessType: ${event.businessType}")

        try {
            // 1. 이미지 다운로드 또는 Base64 디코딩
            val rawImageBytes = downloadOrDecodeImage(event.imageUrl)
            logger.info("이미지 로드 완료 (${rawImageBytes.size} bytes)")

            // 2. 앙상블 OCR 실행 (3개 엔진 병렬 - Coroutines)
            val ensembleResult =
                    kotlinx.coroutines.runBlocking {
                        ensembleOcrProvider.extractTextParallel(rawImageBytes)
                    }

            logger.info("=== 앙상블 OCR 결과 요약 ===")
            logger.info(
                    "  PaddleOCR: ${if (ensembleResult.paddleOcr.success) "${ensembleResult.paddleOcr.lines.size}줄" else "실패"}"
            )
            logger.info(
                    "  Pororo: ${if (ensembleResult.pororo.success) "${ensembleResult.pororo.lines.size}줄" else "실패"}"
            )
            logger.info(
                    "  EasyOCR: ${if (ensembleResult.easyOcr.success) "${ensembleResult.easyOcr.lines.size}줄" else "실패"}"
            )
            logger.info("============================")

            // 3. Gemma3로 교차검증 + 필드 파싱
            val ensembleResultsText = ensembleResult.toPromptFormat()
            logger.info("=== 앙상블 결과 (Gemma3 입력) ===")
            logger.info(ensembleResultsText)
            logger.info("================================")

            val parsedData =
                    textProcessorPort.crossValidateAndParse(
                            ensembleResults = ensembleResultsText,
                            documentType = event.documentType,
                            businessType = event.businessType
                    )
            parsedData.toMap().forEach { (key, value) -> logger.info("  $key: $value") }
            logger.info("========================================")

            // 4. 결과 저장
            val result =
                    OcrDocument(
                            requestId = event.requestId,
                            status = "COMPLETED",
                            rawJson = parsedData.toJson(),
                            parsedData = parsedData.toMap()
                    )
            ocrCacheRepository.save(result)

            logger.info("OCR 처리 완료: ${event.requestId}")
        } catch (e: Exception) {
            logger.error("OCR 처리 실패 [${event.requestId}]: ${e.message}", e)

            ocrCacheRepository.save(
                    OcrDocument(
                            requestId = event.requestId,
                            status = "FAILED",
                            rawJson = """{"error": "${e.message}"}"""
                    )
            )
        }
    }

    /** 이미지 URL에서 다운로드하거나 Base64 문자열을 디코딩합니다. */
    private fun downloadOrDecodeImage(imageUrl: String): ByteArray {
        return when {
            // Base64 데이터인 경우
            imageUrl.startsWith("data:image") -> {
                val base64Data = imageUrl.substringAfter("base64,")
                Base64.getDecoder().decode(base64Data)
            }
            // 순수 Base64인 경우 (MIME prefix 없이)
            !imageUrl.startsWith("http") && isBase64(imageUrl) -> {
                Base64.getDecoder().decode(imageUrl)
            }
            // URL인 경우 다운로드
            else -> {
                restClient.get().uri(imageUrl).retrieve().body(ByteArray::class.java)
                        ?: throw RuntimeException("Failed to download image from: $imageUrl")
            }
        }
    }

    /** 문자열이 Base64 인코딩인지 확인합니다. */
    private fun isBase64(str: String): Boolean {
        return try {
            Base64.getDecoder().decode(str)
            true
        } catch (e: Exception) {
            false
        }
    }
}
