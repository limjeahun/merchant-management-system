package com.worker.consumer

import com.application.port.out.ExternalOcrPort
import com.common.event.OcrRequestEvent
import com.domain.documents.OcrDocument
import com.domain.repository.OcrCacheRepository
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component

@Component
class OcrEventConsumer(
    private val externalOcrPort: ExternalOcrPort,
    private val ocrCacheRepository: OcrCacheRepository,
) {
    /**
     * 2 & 3. 이벤트 수신 -> OCR 처리 -> Redis 저장
     */
    @KafkaListener(topics = ["ocr-request-topic"], groupId = "ocr-worker-group")
    fun consumeOcrRequest(event: OcrRequestEvent) {
        println("Processing Event: ${event.requestId}")

        try {
            // Gemini 호출
            val rawText = externalOcrPort.extractText(event.imageUrl)
            // 파싱 로직 (간단한 예시)
            val parsedData = parseOcrText(rawText, event.documentType)
            // 결과 생성
            val result = OcrDocument(
                requestId = event.requestId,
                status = "COMPLETED",
                rawText = rawText,
                parsedData = parsedData
            )
            // Redis 저장
            ocrCacheRepository.save(result)
        } catch (e: Exception) {
            // 실패 시 Redis에 실패 상태 저장
            ocrCacheRepository.save(
                OcrDocument(event.requestId, "FAILED")
            )
        }
    }

    private fun parseOcrText(text: String, type: String): Map<String, String> {
        // 실제로는 정규식 등을 이용해 이름, 주민번호 등을 파싱
        return mapOf(
            "name" to "홍길동",
            "number" to "123456-1234567"
        )
    }


}