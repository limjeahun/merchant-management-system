package com.provider.paddleocr

import com.application.port.out.OcrPort
import com.common.ocr.OcrLine
import com.common.ocr.OcrRawResult
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Primary
import org.springframework.core.io.ByteArrayResource
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.client.RestTemplate

/**
 * RapidOCR Docker API Provider
 *
 * Docker 컨테이너로 실행되는 RapidOCR 서비스를 호출하여 OCR 수행 모델과 사전이 100% 일치하는 검증된 환경에서 실행됨
 */
@Primary
@Component
class PaddleOcrApiProvider(
        @Value("\${paddleocr.api-url:http://localhost:9003}") private val apiUrl: String
) : OcrPort {

    private val logger = LoggerFactory.getLogger(PaddleOcrApiProvider::class.java)
    private val restTemplate = RestTemplate()
    private val objectMapper = ObjectMapper()

    @PostConstruct
    fun initialize() {
        logger.info("RapidOCR API Provider initialized with URL: $apiUrl")
    }

    /** 이미지에서 텍스트를 추출합니다. */
    override fun extractText(imageBytes: ByteArray): OcrRawResult {
        return try {
            logger.info("Starting OCR extraction via RapidOCR API...")

            // Multipart form-data 설정
            val headers = HttpHeaders()
            headers.contentType = MediaType.MULTIPART_FORM_DATA

            // 이미지를 Resource로 변환
            val imageResource =
                    object : ByteArrayResource(imageBytes) {
                        override fun getFilename(): String = "image.png"
                    }

            val body = LinkedMultiValueMap<String, Any>()
            body.add("image_file", imageResource)

            val requestEntity = HttpEntity(body, headers)

            // API 호출
            val responseStr =
                    restTemplate.postForObject("$apiUrl/ocr", requestEntity, String::class.java)

            if (responseStr.isNullOrEmpty()) {
                logger.error("RapidOCR API returned empty response")
                return OcrRawResult.error("RapidOCR API returned empty response")
            }

            // 응답 파싱
            val response = objectMapper.readValue(responseStr, RapidOcrResponse::class.java)

            // 새 형식 (success 필드) 또는 기존 형식 (code 필드) 처리
            val isSuccess = response.success == true || response.code == "100"

            if (!isSuccess) {
                val errorMsg = response.msg ?: "Unknown error"
                logger.error("RapidOCR API error: code=${response.code}, msg=$errorMsg")
                return OcrRawResult.error("RapidOCR API error: $errorMsg")
            }

            // 결과 파싱 - 새 형식(lines) 또는 기존 형식(data) 사용
            val ocrLines =
                    when {
                        // 새 형식: lines 필드 사용
                        !response.lines.isNullOrEmpty() -> {
                            response.lines.map { item ->
                                OcrLine(
                                        text = item.text ?: "",
                                        confidence = item.confidence?.toFloat() ?: 0.9f
                                )
                            }
                        }
                        // 기존 형식: data 필드 사용
                        !response.data.isNullOrEmpty() -> {
                            response.data.map { item ->
                                OcrLine(
                                        text = item.text ?: "",
                                        confidence = item.score?.toFloat() ?: 0.9f
                                )
                            }
                        }
                        else -> emptyList()
                    }

            val fullText = response.text ?: ocrLines.joinToString("\n") { it.text }

            logger.info("OCR completed, extracted ${ocrLines.size} lines")

            OcrRawResult(fullText = fullText, lines = ocrLines, success = true)
        } catch (e: Exception) {
            logger.error("RapidOCR API call failed: ${e.message}", e)
            OcrRawResult.error("RapidOCR API call failed: ${e.message}")
        }
    }
}

// RapidOCR Response DTOs
@com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
data class RapidOcrResponse(
        @JsonProperty("code") val code: String? = null,
        @JsonProperty("msg") val msg: String? = null,
        @JsonProperty("data") val data: List<RapidOcrItem>? = null,
        @JsonProperty("elapse") val elapse: Double? = null,
        // 새로운 API 형식 필드
        @JsonProperty("success") val success: Boolean? = null,
        @JsonProperty("text") val text: String? = null,
        @JsonProperty("lines") val lines: List<RapidOcrLineItem>? = null,
        @JsonProperty("line_count") val lineCount: Int? = null,
        @JsonProperty("elapsed_time") val elapsedTime: Any? = null
)

@com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
data class RapidOcrItem(
        @JsonProperty("text") val text: String? = null,
        @JsonProperty("score") val score: Double? = null,
        @JsonProperty("text_region") val textRegion: Any? = null
)

@com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
data class RapidOcrLineItem(
        @JsonProperty("text") val text: String? = null,
        @JsonProperty("confidence") val confidence: Double? = null
)
