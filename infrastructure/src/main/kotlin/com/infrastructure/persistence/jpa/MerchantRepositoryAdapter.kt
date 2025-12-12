package com.infrastructure.persistence.jpa

import com.domain.model.Merchant
import com.domain.repository.MerchantRepository

class MerchantRepositoryAdapter(
    private val merchantJpaRepository: MerchantJpaRepository
): MerchantRepository {
    override fun save(merchant: Merchant): Merchant {
        return merchantJpaRepository.save(merchant)
    }
}