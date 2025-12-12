package com.domain.repository

import com.domain.model.Merchant


interface MerchantRepository {
    fun save(merchant: Merchant): Merchant
}