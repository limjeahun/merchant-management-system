package com.domain.repository

import com.domain.documents.OcrDocument

interface OcrCacheRepository {
    fun save(result: OcrDocument)
    fun findByRequestId(requestId: String): OcrDocument?
}
