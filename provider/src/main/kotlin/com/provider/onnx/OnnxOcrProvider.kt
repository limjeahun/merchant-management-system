package com.provider.onnx

import ai.onnxruntime.*
import com.application.port.out.OcrPort
import com.common.ocr.OcrLine
import com.common.ocr.OcrRawResult
import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.File
import java.nio.FloatBuffer
import javax.imageio.ImageIO
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

/** ONNX Runtime을 직접 사용한 PaddleOCR Provider Detection + Recognition 파이프라인으로 한국어 텍스트 추출 */
@Component
class OnnxOcrProvider(
        @Value("\${paddleocr.model-path:classpath:models/paddleocr}") private val modelPath: String,
        @Value("\${paddleocr.detection-model:ch_PP-OCRv4_det_infer.onnx}")
        private val detectionModelName: String,
        @Value("\${paddleocr.recognition-model:korean_PP-OCRv3_rec_infer.onnx}")
        private val recognitionModelName: String
) : OcrPort {

    private val logger = LoggerFactory.getLogger(OnnxOcrProvider::class.java)

    private var ortEnv: OrtEnvironment? = null
    private var detSession: OrtSession? = null
    private var recSession: OrtSession? = null
    private var vocabulary: List<String> = emptyList()
    private var modelsDirectory: String? = null

    companion object {
        // Detection 전처리 파라미터
        private const val DET_TARGET_SIZE = 960
        private val DET_MEAN = floatArrayOf(0.485f, 0.456f, 0.406f)
        private val DET_STD = floatArrayOf(0.229f, 0.224f, 0.225f)

        // Recognition 전처리 파라미터
        private const val REC_HEIGHT = 48
        private const val REC_MAX_WIDTH = 320
        private const val REC_MEAN = 0.5f
        private const val REC_STD = 0.5f

        // Detection 후처리 파라미터
        private const val THRESHOLD = 0.3f
        private const val MIN_BOX_SIZE = 5
    }

    @PostConstruct
    fun initialize() {
        try {
            logger.info("Initializing ONNX OCR Provider...")
            resolveModelPaths()
            loadModels()
            loadVocabulary()
            logger.info("ONNX OCR Provider initialized successfully")
        } catch (e: Exception) {
            logger.error("Failed to initialize ONNX OCR Provider: ${e.message}", e)
        }
    }

    @PreDestroy
    fun cleanup() {
        detSession?.close()
        recSession?.close()
        ortEnv?.close()
        logger.info("ONNX sessions released")
    }

    /** 모델 경로 해석 */
    private fun resolveModelPaths() {
        val projectRoot = System.getProperty("user.dir")
        val searchPaths =
                listOf(
                        "$projectRoot/worker/src/main/resources/models/paddleocr",
                        "$projectRoot/provider/src/main/resources/models/paddleocr"
                )

        for (searchPath in searchPaths) {
            val dir = File(searchPath)
            val dictFile = File(searchPath, "korean_dict.txt")
            val detModel = File(searchPath, detectionModelName)
            val recModel = File(searchPath, recognitionModelName)

            if (dir.exists() && dictFile.exists() && detModel.exists() && recModel.exists()) {
                modelsDirectory = dir.absolutePath
                logger.info("Found models in: $modelsDirectory")
                return
            }
        }

        throw IllegalStateException("OCR models not found in: $searchPaths")
    }

    /** ONNX 모델 로드 */
    private fun loadModels() {
        ortEnv = OrtEnvironment.getEnvironment()

        val detModelPath = "$modelsDirectory/$detectionModelName"
        val recModelPath = "$modelsDirectory/$recognitionModelName"

        logger.info("Loading detection model: $detModelPath")
        detSession = ortEnv!!.createSession(detModelPath, OrtSession.SessionOptions())

        logger.info("Loading recognition model: $recModelPath")
        recSession = ortEnv!!.createSession(recModelPath, OrtSession.SessionOptions())
    }

    /** 한국어 문자 사전 로드 공식 PaddleOCR 사전 형식: 각 줄에 한 문자, blank는 마지막 인덱스 */
    private fun loadVocabulary() {
        val keysFile = File("$modelsDirectory/korean_dict.txt")
        // 공식 사전 그대로 로드 (인덱스 0부터 사전 문자열)
        // PaddleOCR에서 blank token은 vocabulary.size (마지막 다음 인덱스)
        vocabulary = keysFile.readLines(Charsets.UTF_8)
        logger.info("Loaded vocabulary with ${vocabulary.size} characters")
    }

    /** 이미지에서 텍스트 추출 */
    override fun extractText(imageBytes: ByteArray): OcrRawResult {
        if (detSession == null || recSession == null) {
            return OcrRawResult.error("ONNX models not initialized")
        }

        return try {
            val image = ImageIO.read(ByteArrayInputStream(imageBytes))
            if (image == null) {
                return OcrRawResult.error("Failed to decode image")
            }

            performOcr(image)
        } catch (e: Exception) {
            logger.error("OCR extraction failed: ${e.message}", e)
            OcrRawResult.error("OCR extraction failed: ${e.message}")
        }
    }

    /** OCR 파이프라인 실행 */
    private fun performOcr(image: BufferedImage): OcrRawResult {
        // Step 1: Detection - 텍스트 영역 감지
        val boxes = detectTextRegions(image)
        logger.info("Detected ${boxes.size} text regions")

        if (boxes.isEmpty()) {
            return OcrRawResult(fullText = "", lines = emptyList(), success = true)
        }

        // Step 2: Recognition - 각 영역에서 텍스트 인식
        val ocrLines = mutableListOf<OcrLine>()

        for (box in boxes) {
            try {
                val croppedImage = cropImage(image, box)
                if (croppedImage.width > 0 && croppedImage.height > 0) {
                    val text = recognizeText(croppedImage)
                    if (text.isNotBlank()) {
                        ocrLines.add(OcrLine(text = text, confidence = 0.9f))
                    }
                }
            } catch (e: Exception) {
                logger.warn("Failed to recognize text in region: ${e.message}")
            }
        }

        val fullText = ocrLines.joinToString("\n") { it.text }
        logger.info("OCR completed, extracted ${ocrLines.size} lines")

        return OcrRawResult(fullText = fullText, lines = ocrLines, success = true)
    }

    /** Detection: 텍스트 영역 감지 */
    private fun detectTextRegions(image: BufferedImage): List<IntArray> {
        // 이미지 리사이즈 (비율 유지, 32의 배수로 맞춤)
        val scale =
                minOf(
                        DET_TARGET_SIZE.toFloat() / image.width,
                        DET_TARGET_SIZE.toFloat() / image.height
                )
        var newWidth = (image.width * scale).toInt()
        var newHeight = (image.height * scale).toInt()

        // 32의 배수로 맞춤 (PaddleOCR 요구사항)
        newWidth = ((newWidth + 31) / 32) * 32
        newHeight = ((newHeight + 31) / 32) * 32

        // 최소 크기 보장
        if (newWidth < 32) newWidth = 32
        if (newHeight < 32) newHeight = 32

        val resized = resizeImage(image, newWidth, newHeight)

        // 전처리: 정규화 및 CHW 변환
        val inputTensor = preprocessForDetection(resized)

        // Detection 모델 실행
        val inputName = detSession!!.inputNames.first()
        val inputs = mapOf(inputName to inputTensor)
        val results = detSession!!.run(inputs)

        // 출력 파싱
        val output = results[0].value as Array<*>
        val detectionMap = parseDetectionOutput(output, newWidth, newHeight)

        // 바운딩 박스 추출
        val boxes = extractBoxesFromMap(detectionMap, newWidth, newHeight)

        // 원본 크기로 좌표 변환
        val scaleX = image.width.toFloat() / newWidth
        val scaleY = image.height.toFloat() / newHeight

        return boxes.map { box ->
            intArrayOf(
                    (box[0] * scaleX).toInt(),
                    (box[1] * scaleY).toInt(),
                    (box[2] * scaleX).toInt(),
                    (box[3] * scaleY).toInt()
            )
        }
    }

    /** Detection 전처리 */
    private fun preprocessForDetection(image: BufferedImage): OnnxTensor {
        val width = image.width
        val height = image.height

        // CHW 형식으로 변환 (1, 3, H, W)
        val floatData = FloatArray(3 * height * width)

        for (y in 0 until height) {
            for (x in 0 until width) {
                val rgb = image.getRGB(x, y)
                val r = ((rgb shr 16) and 0xFF) / 255f
                val g = ((rgb shr 8) and 0xFF) / 255f
                val b = (rgb and 0xFF) / 255f

                // ImageNet 정규화
                val idx = y * width + x
                floatData[0 * height * width + idx] = (r - DET_MEAN[0]) / DET_STD[0]
                floatData[1 * height * width + idx] = (g - DET_MEAN[1]) / DET_STD[1]
                floatData[2 * height * width + idx] = (b - DET_MEAN[2]) / DET_STD[2]
            }
        }

        val shape = longArrayOf(1, 3, height.toLong(), width.toLong())
        return OnnxTensor.createTensor(ortEnv, FloatBuffer.wrap(floatData), shape)
    }

    /** Detection 출력 파싱 */
    private fun parseDetectionOutput(output: Array<*>, width: Int, height: Int): Array<FloatArray> {
        // 출력 형태: (1, 1, H, W) 또는 (1, H, W)
        val result = Array(height) { FloatArray(width) }

        try {
            @Suppress("UNCHECKED_CAST")
            when {
                output[0] is Array<*> -> {
                    val data = output[0] as Array<*>
                    if (data[0] is Array<*>) {
                        // (1, 1, H, W) 형태
                        val inner = data[0] as Array<FloatArray>
                        for (y in 0 until minOf(height, inner.size)) {
                            for (x in 0 until minOf(width, inner[y].size)) {
                                result[y][x] = inner[y][x]
                            }
                        }
                    } else if (data[0] is FloatArray) {
                        // (1, H, W) 형태
                        for (y in 0 until minOf(height, data.size)) {
                            val row = data[y] as FloatArray
                            for (x in 0 until minOf(width, row.size)) {
                                result[y][x] = row[x]
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            logger.warn("Failed to parse detection output: ${e.message}")
        }

        return result
    }

    /** Detection 맵에서 바운딩 박스 추출 */
    private fun extractBoxesFromMap(
            detMap: Array<FloatArray>,
            width: Int,
            height: Int
    ): List<IntArray> {
        val boxes = mutableListOf<IntArray>()
        val visited = Array(height) { BooleanArray(width) }

        for (y in 0 until height) {
            for (x in 0 until width) {
                if (detMap[y][x] > THRESHOLD && !visited[y][x]) {
                    val box = floodFillBox(detMap, visited, x, y, width, height)
                    if (box != null) {
                        boxes.add(box)
                    }
                }
            }
        }

        return boxes
    }

    /** Flood Fill로 연결 영역 찾기 */
    private fun floodFillBox(
            detMap: Array<FloatArray>,
            visited: Array<BooleanArray>,
            startX: Int,
            startY: Int,
            width: Int,
            height: Int
    ): IntArray? {
        var minX = startX
        var minY = startY
        var maxX = startX
        var maxY = startY
        var count = 0

        val stack = ArrayDeque<Pair<Int, Int>>()
        stack.add(Pair(startX, startY))

        while (stack.isNotEmpty()) {
            val (x, y) = stack.removeLast()

            if (x < 0 || x >= width || y < 0 || y >= height) continue
            if (visited[y][x] || detMap[y][x] <= THRESHOLD) continue

            visited[y][x] = true
            count++

            minX = minOf(minX, x)
            minY = minOf(minY, y)
            maxX = maxOf(maxX, x)
            maxY = maxOf(maxY, y)

            stack.add(Pair(x + 1, y))
            stack.add(Pair(x - 1, y))
            stack.add(Pair(x, y + 1))
            stack.add(Pair(x, y - 1))
        }

        // 너무 작은 영역 무시
        if ((maxX - minX) < MIN_BOX_SIZE || (maxY - minY) < MIN_BOX_SIZE) {
            return null
        }

        // 약간의 패딩 추가
        val padX = maxOf(3, (maxX - minX) / 10)
        val padY = maxOf(3, (maxY - minY) / 10)

        return intArrayOf(
                maxOf(0, minX - padX),
                maxOf(0, minY - padY),
                minOf(width - 1, maxX + padX),
                minOf(height - 1, maxY + padY)
        )
    }

    /** 이미지 크롭 */
    private fun cropImage(image: BufferedImage, box: IntArray): BufferedImage {
        val x = maxOf(0, box[0])
        val y = maxOf(0, box[1])
        val w = minOf(image.width - x, box[2] - box[0])
        val h = minOf(image.height - y, box[3] - box[1])

        if (w <= 0 || h <= 0) {
            return BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB)
        }

        return image.getSubimage(x, y, w, h)
    }

    /** Recognition: 텍스트 인식 */
    private fun recognizeText(image: BufferedImage): String {
        // 높이를 REC_HEIGHT로 리사이즈
        val scale = REC_HEIGHT.toFloat() / image.height
        var newWidth = (image.width * scale).toInt()
        if (newWidth > REC_MAX_WIDTH) newWidth = REC_MAX_WIDTH
        if (newWidth < 10) newWidth = 10

        val resized = resizeImage(image, newWidth, REC_HEIGHT)

        // 전처리
        val inputTensor = preprocessForRecognition(resized)

        // Recognition 모델 실행
        val inputName = recSession!!.inputNames.first()
        val inputs = mapOf(inputName to inputTensor)
        val results = recSession!!.run(inputs)

        // CTC 디코딩 (공식 사전은 완성형 한글이므로 추가 변환 불필요)
        val output = results[0].value
        return ctcDecode(output)
    }

    /** Recognition 전처리 */
    private fun preprocessForRecognition(image: BufferedImage): OnnxTensor {
        val width = image.width
        val height = image.height

        // CHW 형식으로 변환 (1, 3, H, W)
        val floatData = FloatArray(3 * height * width)

        for (y in 0 until height) {
            for (x in 0 until width) {
                val rgb = image.getRGB(x, y)
                val r = ((rgb shr 16) and 0xFF) / 255f
                val g = ((rgb shr 8) and 0xFF) / 255f
                val b = (rgb and 0xFF) / 255f

                // 정규화 (-1 ~ 1)
                val idx = y * width + x
                floatData[0 * height * width + idx] = (r - REC_MEAN) / REC_STD
                floatData[1 * height * width + idx] = (g - REC_MEAN) / REC_STD
                floatData[2 * height * width + idx] = (b - REC_MEAN) / REC_STD
            }
        }

        val shape = longArrayOf(1, 3, height.toLong(), width.toLong())
        return OnnxTensor.createTensor(ortEnv, FloatBuffer.wrap(floatData), shape)
    }

    /** CTC 그리디 디코딩 CTC 그리디 디코딩 PaddleOCR에서 blank 토큰은 인덱스 0 */
    private fun ctcDecode(output: Any): String {
        val result = StringBuilder()
        var prevIndex = -1
        var logged = false

        try {
            @Suppress("UNCHECKED_CAST")
            when (output) {
                is Array<*> -> {
                    // (1, T, num_classes) 형태
                    val batch = output[0] as? Array<FloatArray> ?: return ""

                    if (batch.isNotEmpty()) {
                        val numClasses = batch[0].size
                        val blankIndex = 0 // PaddleOCR: blank는 인덱스 0

                        // 첫 추론 시 클래스 수 로깅 (INFO 레벨)
                        if (!logged) {
                            logger.info(
                                    "CTC Decode: T=${batch.size}, num_classes=$numClasses, vocabulary_size=${vocabulary.size}"
                            )
                            logged = true
                        }

                        for (t in batch.indices) {
                            val probs = batch[t]
                            var maxIdx = 0
                            var maxProb = probs[0]

                            for (i in 1 until probs.size) {
                                if (probs[i] > maxProb) {
                                    maxProb = probs[i]
                                    maxIdx = i
                                }
                            }

                            // blank(인덱스 0) 또는 중복 무시
                            val isBlank = maxIdx == blankIndex

                            // vocabulary 인덱스는 maxIdx - 1 (blank가 0이므로)
                            val vocabIdx = maxIdx - 1

                            if (!isBlank &&
                                            maxIdx != prevIndex &&
                                            vocabIdx >= 0 &&
                                            vocabIdx < vocabulary.size
                            ) {
                                result.append(vocabulary[vocabIdx])
                            }

                            prevIndex = maxIdx
                        }
                    }
                }
            }
        } catch (e: Exception) {
            logger.warn("CTC decode failed: ${e.message}")
        }

        return result.toString().trim()
    }

    /** 이미지 리사이즈 */
    private fun resizeImage(image: BufferedImage, width: Int, height: Int): BufferedImage {
        val resized = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        val g = resized.createGraphics()
        g.drawImage(image, 0, 0, width, height, null)
        g.dispose()
        return resized
    }
}
