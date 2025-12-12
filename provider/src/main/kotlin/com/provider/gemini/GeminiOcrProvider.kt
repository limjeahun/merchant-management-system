package com.provider.gemini

import com.application.port.out.ExternalOcrPort
import org.springframework.stereotype.Component

@Component
class GeminiOcrProvider: ExternalOcrPort {
    /**
     * 실제로는 WebClient 또는 RestClient를 사용하여 Gemini API 호출
     */
    override fun extractText(imageUrl: String): String {
        println("Calling Gemini 2.0 Flash for image: $imageUrl")
        // TODO: Google Vertex AI or Gemini API Implementation
        // Mock Response
        return """
            {
                "text": "Name: John Doe, No: 123456-1234567",
                "confidence": 0.98
            }
        """.trimIndent()
    }

}