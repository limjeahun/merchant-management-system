package com.domain.model

import jakarta.persistence.*

@Entity
@Table(name = "merchant")
class Merchant(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
    val requestId: String,
    @Column(columnDefinition = "TEXT")
    val rawText: String,
    val parsedName: String?,
    val parsedNumber: String?, // 주민번호 or 사업자번호
    val verified: Boolean,
) {

}