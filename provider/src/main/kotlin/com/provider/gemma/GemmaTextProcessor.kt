package com.provider.gemma

import com.application.port.out.TextProcessorPort
import com.common.ocr.OcrParsedData
import com.common.util.getLogger
import org.springframework.stereotype.Component

/**
 * LangChain4j AiServices를 사용한 Gemma3 텍스트 처리 Provider
 *
 * 문서 유형(사업자등록증/운전면허증/주민등록증)과 사업자 유형(개인/법인)에 따라 다른 프롬프트를 사용하여 OCR 보정 및 파싱 수행
 */
@Component
class GemmaTextProcessor(private val ocrDocumentAgent: OcrDocumentAgent) : TextProcessorPort {

    private val logger = getLogger()

    /** 앙상블 OCR 결과를 교차검증하여 필드를 파싱합니다. 문서 유형에 따라 적절한 Agent 메서드를 호출합니다. */
    override fun crossValidateAndParse(
            ensembleResults: String,
            documentType: String,
            businessType: String
    ): OcrParsedData {
        return try {
            logger.info(
                    "=== Gemma3 교차검증 시작 (documentType: $documentType, businessType: $businessType) ==="
            )

            val result =
                    when (documentType.uppercase()) {
                        "DRIVER_LICENSE" -> {
                            logger.info("운전면허증 분석 수행")
                            ocrDocumentAgent.analyzeDriverLicense(ensembleResults)
                        }
                        "ID_CARD" -> {
                            logger.info("주민등록증 분석 수행")
                            ocrDocumentAgent.analyzeIdCard(ensembleResults)
                        }
                        "BUSINESS_LICENSE" -> {
                            when (businessType.uppercase()) {
                                "CORPORATE" -> {
                                    logger.info("법인사업자 분석 수행")
                                    ocrDocumentAgent.crossValidateCorporate(ensembleResults)
                                }
                                else -> {
                                    logger.info("개인사업자 분석 수행")
                                    ocrDocumentAgent.crossValidateIndividual(ensembleResults)
                                }
                            }
                        }
                        else -> {
                            logger.warn("알 수 없는 문서 유형: $documentType, 법인사업자로 처리")
                            ocrDocumentAgent.crossValidateCorporate(ensembleResults)
                        }
                    }

            logger.info("=== Gemma3 교차검증 완료 ===")
            logger.info("결과: $result")

            result
        } catch (e: Exception) {
            logger.error("교차검증 실패: ${e.message}", e)
            OcrParsedData.empty()
        }
    }
}
