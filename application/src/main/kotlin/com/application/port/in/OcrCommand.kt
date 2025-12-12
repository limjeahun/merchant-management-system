package com.application.port.`in`

data class OcrCommand(
    val requestId   : String,
    val imageUrl    : String,
    val documentType: String,
)
