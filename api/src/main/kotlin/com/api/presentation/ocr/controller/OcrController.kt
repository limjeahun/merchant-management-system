package com.api.presentation.ocr.controller


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
class OcrController(
    private val ocrUseCase: OcrUseCase
) {
    // 1. 신분증/사업자등록증 촬영 및 요청 (Async)
    @PostMapping("/request")
    fun requestOcr(@RequestBody request: OcrRequest): ResponseEntity<String> {
        val requestId = ocrUseCase.requestOcrProcessing(request.toOcrCommand())
        return ResponseEntity.ok(requestId)
    }

    // 4. 결과 확인 (Polling)
    @GetMapping("/result/{requestId}")
    fun getResult(@PathVariable requestId: String): ResponseEntity<OrcResponse> {
        val result = ocrUseCase.getOcrResult(requestId)
        return ResponseEntity.ok(OrcResponse.from(result))
    }

    // 5. 결과 확인 후 DB 저장 (사용자 최종 컨펌)
    @PostMapping("/confirm/{requestId}")
    fun confirmAndSave(@PathVariable requestId: String): ResponseEntity<Any> {
        val savedDoc = ocrUseCase.confirmAndSave(requestId)
        return ResponseEntity.ok(savedDoc)
    }


}
