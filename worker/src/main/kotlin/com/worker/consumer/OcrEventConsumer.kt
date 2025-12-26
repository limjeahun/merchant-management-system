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
     * OCR 이벤트 처리 파이프라인
     * 1. 이미지 다운로드
     * 2. PaddleOCR로 텍스트 추출
     * 3. Gemma2로 OCR 오류 보정
     * 4. Gemma2로 문서 분류 및 필드 파싱
     * 5. Redis에 결과 저장
     */
    @KafkaListener(topics = ["mms.ocr.business-license.request"], groupId = "mms.ocr.worker-group")
    fun consumeOcrRequest(event: OcrRequestEvent) {
        logger.info("Processing OCR Event: ${event.requestId}")

        try {
            // 1. 이미지 다운로드 (URL인 경우) 또는 Base64 디코딩
            val imageBytes = downloadOrDecodeImage(event.imageUrl)

            // 2. PaddleOCR로 텍스트 추출
            val ocrResult = ocrPort.extractText(imageBytes)

            if (!ocrResult.success) {
                throw RuntimeException("OCR extraction failed: ${ocrResult.errorMessage}")
            }

            logger.info("OCR extracted ${ocrResult.fullText} lines for ${event.requestId}")

            // 3. Gemma2로 OCR 오류 보정
            val correctedText = textProcessorPort.correctOcrErrors(ocrResult.fullText)
            logger.info("OCR text corrected $correctedText for ${event.requestId}")

            // 4. Gemma2로 문서 분류
            val documentType = textProcessorPort.classifyDocument(correctedText)
            logger.info("Document classified as $documentType for ${event.requestId}")

            // 5. Gemma2로 필드 파싱
            val parsedData = textProcessorPort.parseBusinessLicense(correctedText, documentType)
            logger.info("Fields parsed for ${event.requestId}: ${parsedData.toMap().keys}")

            // 6. 결과 저장
            val result =
                    OcrDocument(
                            requestId = event.requestId,
                            status = "COMPLETED",
                            rawJson = parsedData.toJson(),
                            parsedData = parsedData.toMap()
                    )
            ocrCacheRepository.save(result)

            logger.info("OCR processing completed for ${event.requestId}")
        } catch (e: Exception) {
            logger.error("OCR processing failed for ${event.requestId}: ${e.message}", e)

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
