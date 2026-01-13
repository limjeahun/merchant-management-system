package com.provider.gemma

import com.application.port.out.VisionAiPort
import com.common.util.getLogger
import org.springframework.ai.chat.client.ChatClient
import org.springframework.context.annotation.Primary
import org.springframework.core.io.ByteArrayResource
import org.springframework.stereotype.Component
import org.springframework.util.MimeTypeUtils

/**
 * Spring AI 기반 Vision Provider
 *
 * Spring AI의 ChatClient를 활용하여 이미지 분석을 수행합니다. LangChain4j 구현체와의 기술 비교를 위해 Primary로 설정됩니다.
 */
@Primary
@Component
class SpringAiVisionProvider(chatClientBuilder: ChatClient.Builder) : VisionAiPort {

    private val logger = getLogger()
    private val chatClient: ChatClient = chatClientBuilder.build()

    init {
        logger.info("Spring AI Vision Provider initialized (Primary)")
    }

    /** 이미지를 분석하고 프롬프트에 따른 결과를 반환합니다. */
    override fun analyzeImage(imageBytes: ByteArray, prompt: String): String {
        return try {
            logger.info("Starting image analysis with Spring AI...")

            // ByteArrayResource로 변환 (Spring AI Multimodal API 요구사항)
            val imageResource =
                    object : ByteArrayResource(imageBytes) {
                        override fun getFilename(): String = "image.png"
                    }

            val response =
                    chatClient
                            .prompt()
                            .user { u ->
                                u.text(prompt)
                                u.media(MimeTypeUtils.IMAGE_PNG, imageResource)
                            }
                            .call()
                            .content()

            logger.info("Spring AI analysis completed, result length: ${response?.length ?: 0}")
            response ?: ""
        } catch (e: Exception) {
            logger.error("Spring AI image analysis failed: ${e.message}", e)
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
