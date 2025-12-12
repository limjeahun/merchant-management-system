package com.common.event

data class OcrRequestEvent(
    val requestId   : String,
    val imageUrl    : String,
    val documentType: String, // "ID_CARD" or "BUSINESS_LICENSE"
)
