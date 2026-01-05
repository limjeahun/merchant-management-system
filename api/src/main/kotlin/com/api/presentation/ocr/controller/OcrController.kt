package com.api.presentation.ocr.controller

import com.api.presentation.ocr.request.MerchantSaveRequest
import com.api.presentation.ocr.request.OcrRequest
import com.api.presentation.ocr.response.OrcResponse
import com.application.port.`in`.OcrUseCase
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/orc")
class OcrController(private val ocrUseCase: OcrUseCase) {
    // 1. 신분증/사업자등록증 촬영 및 요청 (Async)
    @PostMapping("/request")
    fun requestOcr(@RequestBody request: OcrRequest): ResponseEntity<String> {
        val requestId = ocrUseCase.submitBusinessLicenseOcr(request.toOcrCommand())
        return ResponseEntity.ok(requestId)
    }

    // 4. 결과 확인 (Polling)
    @GetMapping("/result/{requestId}")
    fun getResult(@PathVariable requestId: String): ResponseEntity<OrcResponse> {
        val result = ocrUseCase.getOcrResult(requestId)
        return ResponseEntity.ok(OrcResponse.from(result))
    }

    // 5. 가맹점 정보 저장 (사용자가 보정한 데이터)
    @PostMapping("/save")
    fun saveMerchant(@RequestBody request: MerchantSaveRequest): ResponseEntity<Any> {
        val savedMerchant = ocrUseCase.saveMerchant(request.toCommand())
        return ResponseEntity.ok(savedMerchant)
    }
}
