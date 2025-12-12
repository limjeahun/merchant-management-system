package com.infrastructure.persistence.redis

import com.domain.documents.OcrDocument
import com.domain.repository.OcrCacheRepository
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Repository
import java.time.Duration

@Repository
class OcrCacheAdapter(
    private val redisTemplate: RedisTemplate<String, OcrDocument>
): OcrCacheRepository {
    /**
     * OCR 정보 저장
     */
    override fun save(result: OcrDocument) {
        // 10분 후 만료
        redisTemplate.opsForValue()[/* key = */ result.requestId, /* value = */ result] = /* timeout = */ Duration.ofMinutes(10)
    }

    /**
     * requestId로 OCR 정보 조회
     */
    override fun findByRequestId(requestId: String): OcrDocument {
        return redisTemplate.opsForValue()[/* key = */ requestId]
            ?: throw NoSuchElementException("OCR 정보를 찾을 수 없습니다: $requestId")
    }
}