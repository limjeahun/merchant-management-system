package com.api.presentation.ocr.response

import com.application.port.out.OcrResult

data class OrcResponse(
    val requestId : String,
    val status    : String, // PROCESSING, COMPLETED, FAILED
    val rawText   : String? = null,
    val parsedData: Map<String, String> = emptyMap()
) {
    companion object {
        fun from(result: OcrResult): OrcResponse {
            return OrcResponse(
                requestId  = result.requestId,
                status     = result.status,
                rawText    = result.rawText,
                parsedData = result.parsedData,
            )
        }
    }
}
