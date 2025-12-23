package com.provider.paddleocr

import ai.djl.inference.Predictor
import ai.djl.modality.cv.Image
import ai.djl.modality.cv.ImageFactory
import ai.djl.ndarray.NDList
import ai.djl.repository.zoo.Criteria
import ai.djl.repository.zoo.ZooModel
import ai.djl.training.util.ProgressBar
import com.application.port.out.ExternalOcrPort
import com.common.ocr.OcrLine
import com.common.ocr.OcrRawResult
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.io.ByteArrayInputStream
import java.nio.file.Paths
import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy

/**
 * PaddleOCR ONNX 모델을 사용한 텍스트 추출 Provider
 * DJL(Deep Java Library)의 ONNX Runtime 엔진을 사용합니다.
 */
@Component
class PaddleOcrProvider(
    @Value("\${paddleocr.model-path:\${user.home}/.paddleocr/models}")
    private val modelPath: String,
    
    @Value("\${paddleocr.detection-model:ch_PP-OCRv4_det_infer.onnx}")
    private val detectionModelName: String,
    
    @Value("\${paddleocr.recognition-model:ch_PP-OCRv4_rec_infer.onnx}")
    private val recognitionModelName: String
) : ExternalOcrPort {
    
    private val logger = LoggerFactory.getLogger(PaddleOcrProvider::class.java)
    
    private var detectionModel: ZooModel<Image, NDList>? = null
    private var recognitionModel: ZooModel<Image, NDList>? = null
    
    @PostConstruct
    fun initialize() {
        try {
            logger.info("Initializing PaddleOCR models from: $modelPath")
            loadModels()
            logger.info("PaddleOCR models loaded successfully")
        } catch (e: Exception) {
            logger.warn("Failed to load PaddleOCR models: ${e.message}. OCR will use fallback mode.")
        }
    }
    
    @PreDestroy
    fun cleanup() {
        detectionModel?.close()
        recognitionModel?.close()
        logger.info("PaddleOCR models released")
    }
    
    private fun loadModels() {
        val detectionUrl = resolveModelPath(detectionModelName)
        val recognitionUrl = resolveModelPath(recognitionModelName)
        
        logger.info("Loading detection model from: $detectionUrl")
        logger.info("Loading recognition model from: $recognitionUrl")
        
        // Detection 모델 로드
        val detectionCriteria = Criteria.builder()
            .setTypes(Image::class.java, NDList::class.java)
            .optModelUrls(detectionUrl)
            .optEngine("OnnxRuntime")
            .optProgress(ProgressBar())
            .build()
        detectionModel = detectionCriteria.loadModel()
        
        // Recognition 모델 로드
        val recognitionCriteria = Criteria.builder()
            .setTypes(Image::class.java, NDList::class.java)
            .optModelUrls(recognitionUrl)
            .optEngine("OnnxRuntime")
            .optProgress(ProgressBar())
            .build()
        recognitionModel = recognitionCriteria.loadModel()
    }
    
    /**
     * 모델 경로를 해석합니다.
     * - classpath: 접두사: classpath 리소스에서 로드
     * - 그 외: 파일 시스템 경로로 처리
     */
    private fun resolveModelPath(modelName: String): String {
        return if (modelPath.startsWith("classpath:")) {
            // Classpath 리소스 경로
            val resourcePath = modelPath.removePrefix("classpath:")
            val resource = javaClass.classLoader.getResource("$resourcePath/$modelName")
            resource?.toString() ?: throw IllegalStateException(
                "Model not found in classpath: $resourcePath/$modelName. " +
                "Please download ONNX models to provider/src/main/resources/$resourcePath/"
            )
        } else {
            // 파일 시스템 경로
            Paths.get(modelPath, modelName).toUri().toString()
        }
    }
    
    override fun extractText(imageBytes: ByteArray): OcrRawResult {
        if (detectionModel == null || recognitionModel == null) {
            logger.warn("PaddleOCR models not loaded, returning empty result")
            return OcrRawResult.error("OCR models not initialized")
        }
        
        return try {
            val image = ImageFactory.getInstance().fromInputStream(ByteArrayInputStream(imageBytes))
            val ocrLines = performOcr(image)
            
            val fullText = ocrLines.joinToString("\n") { it.text }
            
            OcrRawResult(
                fullText = fullText,
                lines = ocrLines,
                success = true
            )
        } catch (e: Exception) {
            logger.error("OCR extraction failed: ${e.message}", e)
            OcrRawResult.error("OCR extraction failed: ${e.message}")
        }
    }
    
    private fun performOcr(image: Image): List<OcrLine> {
        val results = mutableListOf<OcrLine>()
        
        detectionModel?.let { detModel ->
            recognitionModel?.let { recModel ->
                detModel.newPredictor().use { detPredictor ->
                    recModel.newPredictor().use { recPredictor ->
                        // 1. Detection: 텍스트 영역 검출
                        val detOutput = detPredictor.predict(image)
                        
                        // 2. 검출된 영역 처리 (Simplified - 실제 구현 시 bbox 파싱 필요)
                        // 여기서는 전체 이미지를 Recognition에 전달하는 간단한 구현
                        val recOutput = recPredictor.predict(image)
                        
                        // 3. Recognition 결과 파싱
                        // TODO: 실제 PaddleOCR 출력 형식에 맞게 파싱 로직 구현
                        // 현재는 placeholder로 전체 텍스트 반환
                        results.add(
                            OcrLine(
                                text = parseRecognitionOutput(recOutput),
                                confidence = 0.95f
                            )
                        )
                    }
                }
            }
        }
        
        return results
    }
    
    private fun parseRecognitionOutput(output: NDList): String {
        // NDList에서 텍스트 추출 로직
        // 실제 PaddleOCR 출력 형식에 맞게 구현 필요
        return try {
            val textArray = output.firstOrNull()
            textArray?.toStringArray()?.joinToString("") ?: ""
        } catch (e: Exception) {
            logger.warn("Failed to parse recognition output: ${e.message}")
            ""
        }
    }
    
    @Deprecated("Use extractText(ByteArray) instead", ReplaceWith("extractText(imageBytes)"))
    override fun extractText(imageUrl: String): String {
        logger.warn("Deprecated method called. Please migrate to extractText(ByteArray)")
        return ""
    }
}
