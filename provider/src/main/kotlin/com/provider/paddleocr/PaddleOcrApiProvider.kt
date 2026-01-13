package com.provider.paddleocr

import com.application.port.out.OcrPort
import com.common.ocr.OcrRawResult
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.annotation.PostConstruct
import java.time.Duration
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Primary
import org.springframework.core.io.ByteArrayResource
import org.springframework.http.MediaType
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.client.RestClient

/**
 * RapidOCR Docker API Provider
 *
 * Docker 컨테이너로 실행되는 RapidOCR 서비스를 호출하여 OCR 수행 모델과 사전이 100% 일치하는 검증된 환경에서 실행됨
 *
 * Spring Boot 3.2+ 스타일의 RestClient 사용
 */
@Primary
@Component
class PaddleOcrApiProvider(
        @Value("\${paddleocr.api-url:http://localhost:9003}") private val apiUrl: String,
        @Value("\${paddleocr.timeout:60}") private val timeoutSeconds: Long = 60
) : OcrPort {

    private val logger = LoggerFactory.getLogger(PaddleOcrApiProvider::class.java)
    private val restClient: RestClient
    private val objectMapper = ObjectMapper()

    init {
        // 타임아웃 설정 (이미지 처리에 시간 소요되므로 충분히 길게)
        val factory =
                SimpleClientHttpRequestFactory().apply {
                    setConnectTimeout(Duration.ofSeconds(timeoutSeconds))
                    setReadTimeout(Duration.ofSeconds(timeoutSeconds))
                }
        restClient = RestClient.builder().baseUrl(apiUrl).requestFactory(factory).build()
    }

    @PostConstruct
    fun initialize() {
        logger.info("RapidOCR API Provider initialized with URL: $apiUrl (using RestClient)")
    }

    /** 이미지에서 텍스트를 추출합니다. */
    override fun extractText(imageBytes: ByteArray): OcrRawResult {
        return try {
            logger.info("Starting OCR extraction via RapidOCR API...")

            // 이미지를 Resource로 변환
            val imageResource =
                    object : ByteArrayResource(imageBytes) {
                        override fun getFilename(): String = "image.png"
                    }

            val body = LinkedMultiValueMap<String, Any>()
            body.add("image_file", imageResource)

            // RestClient를 사용한 Fluent API 호출
            val responseStr =
                    restClient
                            .post()
                            .uri("/ocr")
                            .contentType(MediaType.MULTIPART_FORM_DATA)
                            .body(body)
                            .retrieve()
                            .body(String::class.java)

            if (responseStr.isNullOrEmpty()) {
                logger.error("RapidOCR API returned empty response")
                return OcrRawResult.error("RapidOCR API returned empty response", "paddleocr")
            }

            // 응답 파싱
            val response = objectMapper.readValue(responseStr, RapidOcrResponse::class.java)

            // 새 형식 (success 필드) 또는 기존 형식 (code 필드) 처리
            val isSuccess = response.success == true || response.code == "100"

            if (!isSuccess) {
                val errorMsg = response.msg ?: "Unknown error"
                logger.error("RapidOCR API error: code=${response.code}, msg=$errorMsg")
                return OcrRawResult.error("RapidOCR API error: $errorMsg", "paddleocr")
            }

            // 결과 파싱 - 새 형식(lines) 또는 기존 형식(data) 사용
            val textLines: List<String> =
                    when {
                        // 새 형식: lines 필드 사용
                        !response.lines.isNullOrEmpty() -> {
                            response.lines.map { it.text ?: "" }
                        }
                        // 기존 형식: data 필드 사용
                        !response.data.isNullOrEmpty() -> {
                            response.data.map { it.text ?: "" }
                        }
                        else -> emptyList()
                    }

            // 평균 신뢰도 계산
            val avgConfidence =
                    when {
                        !response.lines.isNullOrEmpty() -> {
                            response.lines.mapNotNull { it.confidence }.average().takeIf {
                                !it.isNaN()
                            }
                                    ?: 0.0
                        }
                        !response.data.isNullOrEmpty() -> {
                            response.data.mapNotNull { it.score }.average().takeIf { !it.isNaN() }
                                    ?: 0.0
                        }
                        else -> 0.0
                    }

            val fullText = response.text ?: textLines.joinToString("\n")

            logger.info("OCR completed, extracted ${textLines.size} lines")

            OcrRawResult(
                    fullText = fullText,
                    lines = textLines,
                    success = true,
                    confidence = avgConfidence,
                    engine = "paddleocr"
            )
        } catch (e: Exception) {
            logger.error("RapidOCR API call failed: ${e.message}", e)
            OcrRawResult.error("RapidOCR API call failed: ${e.message}", "paddleocr")
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
