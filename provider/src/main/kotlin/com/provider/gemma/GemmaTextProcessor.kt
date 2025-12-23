package com.provider.gemma

import com.application.port.out.TextProcessorPort
import com.common.ocr.BusinessLicenseData
import com.common.ocr.DocumentType
import dev.langchain4j.model.chat.ChatLanguageModel
import dev.langchain4j.model.ollama.OllamaChatModel
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import jakarta.annotation.PostConstruct
import java.time.Duration

/**
 * LangChain4j + Ollama를 사용한 Gemma2 텍스트 처리 Provider
 * 문서 분류, 필드 파싱, OCR 오류 보정을 담당합니다.
 */
@Component
class GemmaTextProcessor(
    @Value("\${ollama.base-url:http://localhost:11434}")
    private val ollamaBaseUrl: String,
    
    @Value("\${ollama.model-name:gemma2:2b}")
    private val modelName: String,
    
    @Value("\${ollama.timeout:60s}")
    private val timeoutStr: String
) : TextProcessorPort {
    
    private val logger = LoggerFactory.getLogger(GemmaTextProcessor::class.java)
    
    private lateinit var chatModel: ChatLanguageModel
    
    @PostConstruct
    fun initialize() {
        val timeout = Duration.parse("PT${timeoutStr.uppercase().replace("S", "S")}")
        
        chatModel = OllamaChatModel.builder()
            .baseUrl(ollamaBaseUrl)
            .modelName(modelName)
            .timeout(timeout)
            .build()
        
        logger.info("GemmaTextProcessor initialized with model: $modelName at $ollamaBaseUrl")
    }
    
    override fun classifyDocument(text: String): DocumentType {
        return try {
            val prompt = PromptTemplates.render(
                PromptTemplates.DOCUMENT_CLASSIFICATION,
                mapOf("text" to text)
            )
            
            val response = chatModel.generate(prompt).trim().uppercase()
            
            when {
                response.contains("CORPORATE") -> DocumentType.CORPORATE
                response.contains("INDIVIDUAL") -> DocumentType.INDIVIDUAL
                else -> DocumentType.UNKNOWN
            }
        } catch (e: Exception) {
            logger.error("Document classification failed: ${e.message}", e)
            DocumentType.UNKNOWN
        }
    }
    
    override fun parseBusinessLicense(text: String, documentType: DocumentType): BusinessLicenseData {
        return try {
            val template = when (documentType) {
                DocumentType.CORPORATE -> PromptTemplates.CORPORATE_FIELD_PARSING
                DocumentType.INDIVIDUAL -> PromptTemplates.INDIVIDUAL_FIELD_PARSING
                DocumentType.UNKNOWN -> PromptTemplates.INDIVIDUAL_FIELD_PARSING // 기본값
            }
            
            val prompt = PromptTemplates.render(template, mapOf("text" to text))
            val response = chatModel.generate(prompt)
            
            // JSON 응답 정제
            val jsonStr = extractJson(response)
            
            BusinessLicenseData.fromJson(jsonStr)
        } catch (e: Exception) {
            logger.error("Business license parsing failed: ${e.message}", e)
            BusinessLicenseData.empty()
        }
    }
    
    override fun correctOcrErrors(text: String): String {
        return try {
            val prompt = PromptTemplates.render(
                PromptTemplates.OCR_CORRECTION,
                mapOf("text" to text)
            )
            
            chatModel.generate(prompt).trim()
        } catch (e: Exception) {
            logger.error("OCR correction failed: ${e.message}", e)
            text // 실패 시 원본 반환
        }
    }
    
    /**
     * LLM 응답에서 JSON 부분만 추출합니다.
     */
    private fun extractJson(response: String): String {
        val trimmed = response.trim()
        
        // Markdown code block 제거
        val withoutCodeBlock = trimmed
            .replace(Regex("```json\\s*"), "")
            .replace(Regex("```\\s*"), "")
            .trim()
        
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
