package com.api.presentation.ocr.request

import com.application.port.`in`.OcrCommand
import io.swagger.v3.oas.annotations.media.Schema
import java.util.*

@Schema(description = "OCR 요청 DTO")
data class OcrRequest(
        @Schema(
                description = "이미지 데이터 (URL 또는 Base64 Data URI)",
                example =
                        "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==",
                required = true
        )
        val imageUrl: String,
        @Schema(
                description = "문서 유형",
                example = "BUSINESS_LICENSE",
                allowableValues = ["ID_CARD", "BUSINESS_LICENSE", "DRIVER_LICENSE"],
                required = true
        )
        val type: String,
        @Schema(
                description = "사업자 유형 (개인/법인)",
                example = "CORPORATE",
                allowableValues = ["INDIVIDUAL", "CORPORATE"],
                required = true
        )
        val businessType: String = "CORPORATE",
) {

        fun toOcrCommand(): OcrCommand {
                return OcrCommand(
                        requestId = UUID.randomUUID().toString(),
                        imageUrl = this.imageUrl,
                        documentType = this.type,
                        businessType = this.businessType,
                )
        }
}
