package com.common.ocr

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper

/**
 * 사업자등록증 파싱 결과 DTO
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class BusinessLicenseData(
    /** 상호(법인명) */
    val merchantName: String = "",
    
    /** 사업자등록번호 */
    val businessNumber: String = "",
    
    /** 대표자 성명 */
    val representativeName: String = "",
    
    /** 사업장 소재지 */
    val address: String = "",
    
    /** 업태 */
    val businessType: String = "",
    
    /** 종목 */
    val businessItem: String = "",
    
    /** 개업연월일 (YYYY-MM-DD) */
    val openingDate: String = "",
    
    /** 법인등록번호 (법인사업자인 경우) */
    val corporateNumber: String = "",
    
    /** 본점 소재지 (법인사업자인 경우) */
    val headOfficeAddress: String = ""
) {
    fun toJson(): String = jacksonObjectMapper().writeValueAsString(this)
    
    fun toMap(): Map<String, String> = mapOf(
        "merchantName" to merchantName,
        "businessNumber" to businessNumber,
        "representativeName" to representativeName,
        "address" to address,
        "businessType" to businessType,
        "businessItem" to businessItem,
        "openingDate" to openingDate,
        "corporateNumber" to corporateNumber,
        "headOfficeAddress" to headOfficeAddress
    ).filterValues { it.isNotEmpty() }
    
    companion object {
        private val mapper = jacksonObjectMapper()
        
        fun fromJson(json: String): BusinessLicenseData {
            return try {
                mapper.readValue(json, BusinessLicenseData::class.java)
            } catch (e: Exception) {
                BusinessLicenseData()
            }
        }
        
        fun empty() = BusinessLicenseData()
    }
}

/**
 * 문서 유형 분류
 */
enum class DocumentType {
    /** 개인사업자 */
    INDIVIDUAL,
    
    /** 법인사업자 */
    CORPORATE,
    
    /** 알 수 없음 */
    UNKNOWN
}
