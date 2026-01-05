package com.application.service

import com.application.port.`in`.MerchantSaveCommand
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

        /** 5. 가맹점 정보 저장 (사용자 보정 데이터) */
        override fun saveMerchant(command: MerchantSaveCommand): Merchant {
                // 필수 필드 검증
                require(command.businessNumber.isNotBlank()) { "사업자등록번호는 필수입니다." }
                require(command.merchantName.isNotBlank()) { "상호(법인명)는 필수입니다." }
                require(command.representativeName.isNotBlank()) { "대표자명은 필수입니다." }

                val merchant =
                        Merchant(
                                requestId = command.requestId,
                                merchantName = command.merchantName,
                                businessNumber = command.businessNumber,
                                representativeName = command.representativeName,
                                address = command.address,
                                businessType = command.businessCategory,
                                businessItem = command.businessItem,
                                openingDate = command.openingDate,
                                verified = true
                        )

                return ocrDocumentRepository.save(merchant)
        }
}
