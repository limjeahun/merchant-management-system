package com.application.service

import com.application.port.`in`.OcrCommand
import com.application.port.`in`.OcrUseCase
import com.application.port.out.OcrEventPort
import com.application.port.out.OcrResult
import com.common.event.OcrRequestEvent
import com.domain.documents.OcrDocument
import com.domain.model.Merchant
import com.domain.repository.OcrCacheRepository
import com.domain.repository.MerchantRepository
import org.springframework.stereotype.Service

@Service
class OcrService(
    private val ocrEventPort         : OcrEventPort,
    private val ocrCacheRepository   : OcrCacheRepository,
    private val ocrDocumentRepository: MerchantRepository,
): OcrUseCase {
    /**
     * 1. 요청 -> Kafka 이벤트 발행 -> requestId 반환
     */
    override fun requestOcrProcessing(ocrCommand: OcrCommand): String {
        val event = OcrRequestEvent(
            requestId    = ocrCommand.requestId,
            imageUrl     = ocrCommand.imageUrl,
            documentType = ocrCommand.documentType
        )
        // 초기 상태 Redis 저장
        ocrCacheRepository.save(OcrDocument(event.requestId, "PROCESSING"))
        ocrEventPort.publishEvent(event)
        return event.requestId
    }

    /**
     * 4. 결과 확인 (Polling)
     */
    override fun getOcrResult(requestId: String): OcrResult {
        return OcrResult.from(ocrCacheRepository.findByRequestId(requestId))
    }

    /**
     * 5. 결과 확인 후 DB 저장
     */
    override fun confirmAndSave(requestId: String): Merchant {
        val cachedResult = ocrCacheRepository.findByRequestId(requestId)
            ?: throw IllegalArgumentException("Result not found or expired")

        if (cachedResult.status != "COMPLETED") {
            throw IllegalStateException("Processing not finished")
        }

        val document = Merchant(
            requestId = requestId,
            rawText = cachedResult.rawText ?: "",
            parsedName = cachedResult.parsedData["name"],
            parsedNumber = cachedResult.parsedData["number"],
            verified = true // 검증 로직 추가 가능
        )
        return ocrDocumentRepository.save(document)
    }


}