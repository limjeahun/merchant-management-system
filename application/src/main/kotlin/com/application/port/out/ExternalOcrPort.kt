package com.application.port.out

interface ExternalOcrPort {
    fun extractText(imageUrl: String): String
}