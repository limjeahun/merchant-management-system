package com.application.port.`in`

import com.application.port.out.OcrResult
import com.domain.model.Merchant

interface OcrUseCase {
    fun requestOcrProcessing(ocrCommand: OcrCommand): String // Returns requestId
    fun getOcrResult(requestId: String): OcrResult
    fun confirmAndSave(requestId: String): Merchant
}