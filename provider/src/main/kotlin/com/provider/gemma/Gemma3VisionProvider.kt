package com.provider.gemma

import com.application.port.out.VisionAiPort
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
 * Gemma3 (Ollama) 기반 Vision AI Provider
 *
 * Gemma3의 Vision 기능을 활용하여 이미지 분석 및 텍스트 추출을 수행합니다. OCR 외에도 이미지 분류, 설명 생성 등 다양한 Vision AI 작업을 지원합니다.
 */
@Component
class Gemma3VisionProvider(
        @Value("\${ollama.base-url:http://localhost:11434}") private val ollamaBaseUrl: String,
        @Value("\${ollama.model-name:gemma3:12b}") private val modelName: String,
        @Value("\${ollama.timeout:120s}") private val timeout: String,
        @Value("\${ollama.temperature:0.1}") private val temperature: Double
) : VisionAiPort {

    private val logger = LoggerFactory.getLogger(Gemma3VisionProvider::class.java)

    private lateinit var chatModel: OllamaChatModel

    @PostConstruct
    fun initialize() {
        try {
            logger.info("Initializing Gemma3 Vision Provider with Ollama at: $ollamaBaseUrl")
            logger.info("Model: $modelName, Timeout: $timeout")

            // 타임아웃 파싱 (예: "120s" → 120초, "5m" → 5분)
            val timeoutSeconds =
                    when {
                        timeout.endsWith("s", ignoreCase = true) ->
                                timeout.dropLast(1).toLongOrNull() ?: 300L
                        timeout.endsWith("m", ignoreCase = true) ->
                                (timeout.dropLast(1).toLongOrNull() ?: 5L) * 60
                        else -> 300L // 기본 5분
                    }

            chatModel =
                    OllamaChatModel.builder()
                            .baseUrl(ollamaBaseUrl)
                            .modelName(modelName)
                            .timeout(Duration.ofSeconds(timeoutSeconds))
                            .temperature(temperature)
                            .build()

            logger.info(
                    "Gemma3 Vision Provider initialized successfully (timeout: ${timeoutSeconds}s)"
            )
        } catch (e: Exception) {
            logger.error("Failed to initialize Gemma3 Vision Provider: ${e.message}", e)
        }
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

            val response = chatModel.generate(userMessage)
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
