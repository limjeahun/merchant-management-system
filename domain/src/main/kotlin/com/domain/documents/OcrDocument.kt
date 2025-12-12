package com.domain.documents

data class OcrDocument(
    val requestId: String,
    val status: String, // PROCESSING, COMPLETED, FAILED
    val rawText: String? = null,
    val parsedData: Map<String, String> = emptyMap()
)
