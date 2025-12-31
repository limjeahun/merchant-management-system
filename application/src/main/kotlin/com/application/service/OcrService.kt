package com.application.service

import com.application.port.`in`.OcrCommand
import com.application.port.`in`.OcrUseCase
import com.application.port.out.OcrEventPort
import com.application.port.out.OcrResult
import com.common.event.OcrRequestEvent
import com.domain.documents.OcrDocument
import com.domain.model.Merchant
import com.domain.repository.MerchantRepository
import com.domain.repository.OcrCacheRepository
import org.springframework.stereotype.Service

@Service
class OcrService(
        private val ocrEventPort: OcrEventPort,
        private val ocrCacheRepository: OcrCacheRepository,
        private val ocrDocumentRepository: MerchantRepository,
) : OcrUseCase {
    /**
     * 사업자등록증 OCR 요청 제출
     * 1. 요청 -> Kafka 이벤트 발행 -> requestId 반환
     */
    override fun submitBusinessLicenseOcr(ocrCommand: OcrCommand): String {
        val event =
                OcrRequestEvent(
                        requestId = ocrCommand.requestId,
                        imageUrl = ocrCommand.imageUrl,
                        documentType = ocrCommand.documentType,
                        businessType = ocrCommand.businessType
                )
        // 초기 상태 Redis 저장
        ocrCacheRepository.save(OcrDocument(event.requestId, "PROCESSING"))
        ocrEventPort.publishBusinessLicenseOcrRequest(event)
        return event.requestId
    }

    /** 4. 결과 확인 (Polling) */
    override fun getOcrResult(requestId: String): OcrResult {
        return OcrResult.from(ocrCacheRepository.findByRequestId(requestId))
    }

    /** 5. 결과 확인 후 DB 저장 */
    override fun confirmAndSave(requestId: String): Merchant {
        val cachedResult =
                ocrCacheRepository.findByRequestId(requestId)
                        ?: throw IllegalArgumentException("Result not found or expired")

        if (cachedResult.status != "COMPLETED") {
            throw IllegalStateException("Processing not finished")
        }

        val document =
                Merchant(
                        requestId = requestId,
                        merchantName = cachedResult.parsedData["name"] ?: "",
                        businessNumber = cachedResult.parsedData["number"] ?: "",
                        representativeName = cachedResult.parsedData["name"] ?: "",
                        address = "",
                        verified = true // 검증 로직 추가 가능
                )
        return ocrDocumentRepository.save(document)
    }
}
