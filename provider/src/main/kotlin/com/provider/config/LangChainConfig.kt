package com.provider.config

import com.provider.gemma.BusinessLicenseAgent
import dev.langchain4j.model.ollama.OllamaChatModel
import dev.langchain4j.service.AiServices
import java.time.Duration
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * LangChain4j 설정
 *
 * OllamaChatModel과 BusinessLicenseAgent Bean을 정의합니다.
 */
@Configuration
class LangChainConfig(
        @Value("\${ollama.base-url:http://localhost:11434}") private val ollamaBaseUrl: String,
        @Value("\${ollama.model-name:gemma3:12b}") private val modelName: String,
        @Value("\${ollama.timeout:120s}") private val timeoutStr: String,
        @Value("\${ollama.temperature:0.1}") private val temperature: Double
) {

    /** OllamaChatModel Bean */
    @Bean
    fun ollamaChatModel(): OllamaChatModel {
        val timeout = parseTimeout(timeoutStr)

        return OllamaChatModel.builder()
                .baseUrl(ollamaBaseUrl)
                .modelName(modelName)
                .timeout(timeout)
                .temperature(temperature)
                .format("json") // JSON 모드 강제
                .build()
    }

    /** BusinessLicenseAgent Bean (AiServices 프록시) - 사업자등록증 전용 */
    @Bean
    fun businessLicenseAgent(chatModel: OllamaChatModel): BusinessLicenseAgent {
        return AiServices.builder(BusinessLicenseAgent::class.java)
                .chatLanguageModel(chatModel)
                .build()
    }

    /** OcrDocumentAgent Bean (AiServices 프록시) - 모든 문서 유형 */
    @Bean
    fun ocrDocumentAgent(chatModel: OllamaChatModel): com.provider.gemma.OcrDocumentAgent {
        return AiServices.builder(com.provider.gemma.OcrDocumentAgent::class.java)
                .chatLanguageModel(chatModel)
                .build()
    }

    /** 타임아웃 문자열 파싱 (예: "120s" → 120초, "5m" → 5분) */
    private fun parseTimeout(timeout: String): Duration {
        return when {
            timeout.endsWith("s", ignoreCase = true) ->
                    Duration.ofSeconds(timeout.dropLast(1).toLongOrNull() ?: 300L)
            timeout.endsWith("m", ignoreCase = true) ->
                    Duration.ofMinutes(timeout.dropLast(1).toLongOrNull() ?: 5L)
            else -> Duration.ofSeconds(300L)
        }
    }
}
