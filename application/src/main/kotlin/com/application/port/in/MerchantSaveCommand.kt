package com.application.port.`in`

import java.time.LocalDate

/** 가맹점 정보 저장 커맨드 */
data class MerchantSaveCommand(
        /** OCR 요청 ID */
        val requestId: String,

        /** 사업자 유형: INDIVIDUAL(개인) / CORPORATE(법인) */
        val businessType: String,

        /** 상호(법인명) */
        val merchantName: String,

        /** 사업자등록번호 */
        val businessNumber: String,

        /** 대표자명 */
        val representativeName: String,

        /** 사업장 주소 */
        val address: String,

        /** 업태 */
        val businessCategory: String? = null,

        /** 종목 */
        val businessItem: String? = null,

        /** 개업일 */
        val openingDate: LocalDate? = null
)
