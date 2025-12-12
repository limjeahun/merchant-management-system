package com.api.presentation.ocr.request

import com.application.port.`in`.OcrCommand
import java.util.*

data class OcrRequest(
    val imageUrl: String,
    val type    : String, // "ID_CARD" or "BUSINESS_LICENSE"
) {

    fun toOcrCommand(): OcrCommand {
        return OcrCommand(
            requestId    = UUID.randomUUID().toString(),
            imageUrl     = this.imageUrl,
            documentType = this.type,
        )
    }

}
