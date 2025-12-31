package com.provider.gemma

import com.application.port.out.TextProcessorPort
import com.common.ocr.BusinessLicenseData
import dev.langchain4j.data.message.ImageContent
import dev.langchain4j.data.message.TextContent
import dev.langchain4j.data.message.UserMessage
import dev.langchain4j.model.ollama.OllamaChatModel
import jakarta.annotation.PostConstruct
import java.time.Duration
import java.util.Base64
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

/**
 * LangChain4j + Ollama를 사용한 Gemma3 텍스트 처리 Provider 사업자 유형(개인/법인)에 따라 다른 프롬프트를 사용하여 OCR 보정 및 파싱 수행
 */
@Component
class GemmaTextProcessor(
        @Value("\${ollama.base-url:http://localhost:11434}") private val ollamaBaseUrl: String,
        @Value("\${ollama.model-name:gemma3:12b}") private val modelName: String,
        @Value("\${ollama.timeout:120s}") private val timeoutStr: String,
        @Value("\${ollama.temperature:0.1}") private val temperature: Double
) : TextProcessorPort {

    private val logger = LoggerFactory.getLogger(GemmaTextProcessor::class.java)

    private lateinit var chatModel: OllamaChatModel

    @PostConstruct
    fun initialize() {
        val timeout = Duration.parse("PT${timeoutStr.uppercase().replace("S", "S")}")

        chatModel =
                OllamaChatModel.builder()
                        .baseUrl(ollamaBaseUrl)
                        .modelName(modelName)
                        .timeout(timeout)
                        .temperature(temperature)
                        .build()

        logger.info("GemmaTextProcessor initialized with model: $modelName at $ollamaBaseUrl")
    }

    /** OCR 보정 + 필드 파싱을 한 번에 수행 businessType에 따라 개인/법인 전용 프롬프트 사용 */
    override fun correctAndParse(
            text: String,
            businessType: String,
            imageBytes: ByteArray?
    ): BusinessLicenseData {
        return try {
            logger.info("=== Gemma3 OCR 보정 시작 (businessType: $businessType) ===")

            // 1. 사업자 유형에 맞는 OCR 보정 프롬프트 선택
            val correctionPrompt = PromptTemplates.getOcrCorrectionPrompt(businessType)
            val prompt = PromptTemplates.render(correctionPrompt, mapOf("text" to text))

            // 2. 멀티모달 또는 텍스트만으로 보정+파싱 수행
            val response =
                    if (imageBytes != null) {
                        logger.info("멀티모달 분석 (이미지 크기: ${imageBytes.size} bytes)")
                        val base64Image = Base64.getEncoder().encodeToString(imageBytes)
                        val userMessage =
                                UserMessage.from(
                                        ImageContent.from(base64Image, "image/png"),
                                        TextContent.from(prompt)
                                )
                        chatModel.generate(userMessage).content().text()
                    } else {
                        chatModel.generate(prompt)
                    }

            logger.info("=== Gemma3 응답 ===")
            logger.info(response)
            logger.info("==================")

            // 3. JSON 추출 및 파싱
            val jsonStr = extractJson(response)
            BusinessLicenseData.fromJson(jsonStr)
        } catch (e: Exception) {
            logger.error("OCR 보정/파싱 실패: ${e.message}", e)
            BusinessLicenseData.empty()
        }
    }

    /** LLM 응답에서 JSON 부분만 추출합니다. */
    private fun extractJson(response: String): String {
        val trimmed = response.trim()

        // Markdown code block 제거
        val withoutCodeBlock =
                trimmed.replace(Regex("```json\\s*"), "").replace(Regex("```\\s*"), "").trim()

        // JSON 객체 추출
        val jsonStart = withoutCodeBlock.indexOf('{')
        val jsonEnd = withoutCodeBlock.lastIndexOf('}')

        return if (jsonStart >= 0 && jsonEnd > jsonStart) {
            withoutCodeBlock.substring(jsonStart, jsonEnd + 1)
        } else {
            "{}"
        }
    }
}
