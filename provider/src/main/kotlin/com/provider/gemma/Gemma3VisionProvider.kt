package com.provider.gemma

import com.application.port.out.VisionAiPort
import com.common.util.getLogger
import dev.langchain4j.data.message.ImageContent
import dev.langchain4j.data.message.TextContent
import dev.langchain4j.data.message.UserMessage
import dev.langchain4j.model.ollama.OllamaChatModel
import java.util.Base64
import org.springframework.stereotype.Component

/**
 * Gemma3 (Ollama) 기반 Vision AI Provider
 *
 * Gemma3의 Vision 기능을 활용하여 이미지 분석 및 텍스트 추출을 수행합니다. OCR 외에도 이미지 분류, 설명 생성 등 다양한 Vision AI 작업을 지원합니다.
 */
@Component
class Gemma3VisionProvider(private val ollamaChatModel: OllamaChatModel) : VisionAiPort {

    private val logger = getLogger()

    init {
        logger.info("Gemma3 Vision Provider initialized with DI")
    }

    /** 이미지를 분석하고 프롬프트에 따른 결과를 반환합니다. */
    override fun analyzeImage(imageBytes: ByteArray, prompt: String): String {
        return try {
            logger.info("Starting image analysis with Gemma3...")

            val base64Image = Base64.getEncoder().encodeToString(imageBytes)

            val userMessage =
                    UserMessage.from(
                            ImageContent.from(base64Image, "image/png"),
                            TextContent.from(prompt)
                    )

            val response = ollamaChatModel.generate(userMessage)
            val result = response.content().text().trim()

            logger.info("Image analysis completed, result length: ${result.length}")
            result
        } catch (e: Exception) {
            logger.error("Image analysis failed: ${e.message}", e)
            ""
        }
    }

    /** 이미지에서 텍스트를 추출합니다. (OCR 용도) */
    override fun extractTextFromImage(imageBytes: ByteArray): String {
        val prompt =
                """
            이 이미지에서 모든 텍스트를 정확하게 추출해주세요.
            
            규칙:
            1. 이미지에 보이는 모든 텍스트를 그대로 추출하세요.
            2. 줄바꿈을 유지하세요.
            3. 숫자, 특수문자, 한글, 영문 모두 정확하게 인식하세요.
            4. 추가 설명 없이 텍스트만 출력하세요.
            
            추출된 텍스트:
        """.trimIndent()

        return analyzeImage(imageBytes, prompt)
    }
}
