package com.worker.consumer

import com.application.port.out.OcrPort
import com.application.port.out.TextProcessorPort
import com.common.event.OcrRequestEvent
import com.domain.documents.OcrDocument
import com.domain.repository.OcrCacheRepository
import java.util.Base64
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient

@Component
class OcrEventConsumer(
        private val ocrPort: OcrPort,
        private val textProcessorPort: TextProcessorPort,
        private val ocrCacheRepository: OcrCacheRepository,
) {
    private val logger = LoggerFactory.getLogger(OcrEventConsumer::class.java)
    private val restClient = RestClient.create()

    /**
     * OCR 이벤트 처리 파이프라인 (간소화)
     * 1. 이미지 다운로드
     * 2. RapidOCR로 텍스트 추출
     * 3. Gemma3로 OCR 보정 + 필드 파싱 (businessType 기반 프롬프트 사용)
     * 4. Redis에 결과 저장
     */
    @KafkaListener(topics = ["mms.ocr.business-license.request"], groupId = "mms.ocr.worker-group")
    fun consumeOcrRequest(event: OcrRequestEvent) {
        logger.info("=== OCR 처리 시작 ===")
        logger.info("RequestId: ${event.requestId}")
        logger.info("BusinessType: ${event.businessType}")

        try {
            // 1. 이미지 다운로드 (URL인 경우) 또는 Base64 디코딩
            val rawImageBytes = downloadOrDecodeImage(event.imageUrl)
            logger.info("이미지 로드 완료 (${rawImageBytes.size} bytes)")

            // 1.5. 이미지 전처리 (Gemma3 분석 정확도 향상)
            val preprocessedBytes = com.provider.image.ImagePreprocessor.preprocess(rawImageBytes)
            logger.info("이미지 전처리 완료 (${preprocessedBytes.size} bytes)")

            // 2. RapidOCR로 텍스트 추출 (원본 이미지 사용)
            val ocrResult = ocrPort.extractText(rawImageBytes)

            if (!ocrResult.success) {
                throw RuntimeException("OCR extraction failed: ${ocrResult.errorMessage}")
            }

            logger.info("=== RapidOCR 추출 결과 ===")
            logger.info(ocrResult.fullText)
            logger.info("==========================")

            // 3. Gemma3로 OCR 보정 + 필드 파싱 (전처리된 이미지 사용)
            val parsedData =
                    textProcessorPort.correctAndParse(
                            text = ocrResult.fullText,
                            businessType = event.businessType,
                            imageBytes = preprocessedBytes
                    )

            logger.info("=== 최종 파싱 결과 [${event.requestId}] ===")
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
