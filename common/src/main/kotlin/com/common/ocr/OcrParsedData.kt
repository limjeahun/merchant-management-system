package com.common.ocr

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper

/**
 * 범용 OCR 파싱 결과 DTO 모든 문서 유형(사업자등록증, 운전면허증, 주민등록증)을 하나의 클래스로 처리 LLM 응답에 따라 필드가 누락되거나 null일 수 있으므로
 * Nullable 타입 사용
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class OcrParsedData(
        // === 공통 필드 ===
        /** 성명/대표자명 */
        val name: String? = null,

        /** 주소 */
        val address: String? = null,

        /** 발급일/개업연월일 */
        val issueDate: String? = null,

        // === 사업자등록증 전용 ===
        /** 상호(법인명) */
        val merchantName: String? = null,

        /** 사업자등록번호 */
        val businessNumber: String? = null,

        /** 대표자 성명 */
        val representativeName: String? = null,

        /** 업태 */
        val businessType: String? = null,

        /** 종목 */
        val businessItem: String? = null,

        /** 개업연월일 */
        val openingDate: String? = null,

        /** 법인등록번호 */
        val corporateNumber: String? = null,

        /** 본점 소재지 */
        val headOfficeAddress: String? = null,

        // === 운전면허증 전용 ===
        /** 주민등록번호 */
        val rrn: String? = null,

        /** 면허번호 */
        val licenseNumber: String? = null,

        /** 면허종류 (1종보통, 2종보통 등) */
        val licenseType: String? = null,

        /** 암호일련번호 */
        val serialNumber: String? = null,

        // === 주민등록증 전용 ===
        /** 발급기관 */
        val issuer: String? = null
) {
    fun toJson(): String = jacksonObjectMapper().writeValueAsString(this)

    fun toMap(): Map<String, String> {
        val map = mutableMapOf<String, String>()

        name?.let { if (it.isNotEmpty()) map["name"] = it }
        address?.let { if (it.isNotEmpty()) map["address"] = it }
        issueDate?.let { if (it.isNotEmpty()) map["issueDate"] = it }

        merchantName?.let { if (it.isNotEmpty()) map["merchantName"] = it }
        businessNumber?.let { if (it.isNotEmpty()) map["businessNumber"] = it }
        representativeName?.let { if (it.isNotEmpty()) map["representativeName"] = it }
        businessType?.let { if (it.isNotEmpty()) map["businessType"] = it }
        businessItem?.let { if (it.isNotEmpty()) map["businessItem"] = it }
        openingDate?.let { if (it.isNotEmpty()) map["openingDate"] = it }
        corporateNumber?.let { if (it.isNotEmpty()) map["corporateNumber"] = it }
        headOfficeAddress?.let { if (it.isNotEmpty()) map["headOfficeAddress"] = it }

        rrn?.let { if (it.isNotEmpty()) map["rrn"] = it }
        licenseNumber?.let { if (it.isNotEmpty()) map["licenseNumber"] = it }
        licenseType?.let { if (it.isNotEmpty()) map["licenseType"] = it }
        serialNumber?.let { if (it.isNotEmpty()) map["serialNumber"] = it }

        issuer?.let { if (it.isNotEmpty()) map["issuer"] = it }

        return map
    }

    companion object {
        private val mapper = jacksonObjectMapper()

        fun fromJson(json: String): OcrParsedData {
            return try {
                mapper.readValue(json, OcrParsedData::class.java)
            } catch (e: Exception) {
                OcrParsedData()
            }
        }

        fun empty() = OcrParsedData()
    }
}
