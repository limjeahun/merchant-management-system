package com.infrastructure.persistence.jpa

import com.domain.model.Merchant
import org.springframework.data.jpa.repository.JpaRepository

interface MerchantJpaRepository: JpaRepository<Merchant, Long> {

}