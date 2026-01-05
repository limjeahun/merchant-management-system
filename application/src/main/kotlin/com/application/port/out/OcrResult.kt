package com.application.port.out

import com.domain.documents.OcrDocument

data class OcrResult(
        val requestId: String,
        val status: String, // PROCESSING, COMPLETED, FAILED, NOT_FOUND
        val rawText: String? = null,
        val parsedData: Map<String, String> = emptyMap()
) {
    companion object {
        fun from(document: OcrDocument?): OcrResult {
            if (document == null) {
                return OcrResult(
                        requestId = "",
                        status = "NOT_FOUND",
                        rawText = null,
                        parsedData = emptyMap()
                )
            }
            return OcrResult(
                    requestId = document.requestId,
                    status = document.status,
                    rawText = document.rawJson,
                    parsedData = document.parsedData,
            )
        }
    }
}
