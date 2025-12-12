package com.application.port.out

import com.domain.documents.OcrDocument

data class OcrResult(
    val requestId : String,
    val status    : String, // PROCESSING, COMPLETED, FAILED
    val rawText   : String? = null,
    val parsedData: Map<String, String> = emptyMap()
) {
    companion object {
        fun from(document: OcrDocument): OcrResult {
            return OcrResult(
                requestId  = document.requestId,
                status     = document.status,
                rawText    = document.rawText,
                parsedData = document.parsedData,
            )
        }

    }
}
