package com.provider.image

import java.awt.image.BufferedImage
import java.awt.image.RescaleOp
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import org.slf4j.LoggerFactory

/**
 * 이미지 전처리 유틸리티
 *
 * OCR 정확도 향상을 위해 이미지를 전처리합니다:
 * - Grayscale 변환: 색상 노이즈 제거
 * - 명암 대비 향상: 텍스트와 배경 분리
 * - 해상도 정규화: 너무 작거나 큰 이미지 조정
 */
object ImagePreprocessor {

    private val logger = LoggerFactory.getLogger(ImagePreprocessor::class.java)

    /**
     * OCR을 위한 이미지 전처리 수행
     *
     * @param imageBytes 원본 이미지 바이트 배열
     * @param applyGrayscale Grayscale 변환 적용 여부 (기본: true)
     * @param contrastFactor 명암 대비 배수 (기본: 1.3)
     * @return 전처리된 이미지 바이트 배열
     */
    fun preprocess(
            imageBytes: ByteArray,
            applyGrayscale: Boolean = true,
            contrastFactor: Float = 1.3f
    ): ByteArray {
        return try {
            logger.info("이미지 전처리 시작 (원본 크기: ${imageBytes.size} bytes)")

            // 1. BufferedImage로 변환
            val inputStream = ByteArrayInputStream(imageBytes)
            var image =
                    ImageIO.read(inputStream)
                            ?: throw IllegalArgumentException("Invalid image data")

            logger.info("원본 이미지 크기: ${image.width}x${image.height}")

            // 2. Grayscale 변환 (선택)
            if (applyGrayscale) {
                image = convertToGrayscale(image)
                logger.info("Grayscale 변환 완료")
            }

            // 3. 명암 대비 향상
            image = enhanceContrast(image, contrastFactor)
            logger.info("명암 대비 향상 완료 (factor: $contrastFactor)")

            // 4. ByteArray로 변환
            val outputStream = ByteArrayOutputStream()
            ImageIO.write(image, "png", outputStream)
            val result = outputStream.toByteArray()

            logger.info("이미지 전처리 완료 (결과 크기: ${result.size} bytes)")
            result
        } catch (e: Exception) {
            logger.error("이미지 전처리 실패: ${e.message}, 원본 반환", e)
            imageBytes // 실패 시 원본 반환
        }
    }

    /** Grayscale 변환 색상 노이즈를 제거하고 텍스트 인식률을 높입니다. */
    private fun convertToGrayscale(image: BufferedImage): BufferedImage {
        val grayscale = BufferedImage(image.width, image.height, BufferedImage.TYPE_BYTE_GRAY)
        val graphics = grayscale.createGraphics()
        graphics.drawImage(image, 0, 0, null)
        graphics.dispose()
        return grayscale
    }

    /**
     * 명암 대비 향상 RescaleOp를 사용하여 텍스트와 배경의 대비를 높입니다.
     *
     * @param factor 1.0 = 원본, >1.0 = 대비 증가, <1.0 = 대비 감소
     */
    private fun enhanceContrast(image: BufferedImage, factor: Float): BufferedImage {
        // RescaleOp: pixel = pixel * scaleFactor + offset
        val op = RescaleOp(factor, 0f, null)
        return op.filter(image, null)
    }

    /**
     * 이미지가 너무 작으면 확대 OCR 정확도를 위해 최소 해상도 보장
     *
     * @param minWidth 최소 너비 (기본: 1000px)
     */
    fun ensureMinimumSize(imageBytes: ByteArray, minWidth: Int = 1000): ByteArray {
        return try {
            val inputStream = ByteArrayInputStream(imageBytes)
            val image = ImageIO.read(inputStream) ?: return imageBytes

            if (image.width >= minWidth) {
                return imageBytes // 이미 충분히 큰 경우
            }

            val scale = minWidth.toDouble() / image.width
            val newWidth = minWidth
            val newHeight = (image.height * scale).toInt()

            logger.info("이미지 확대: ${image.width}x${image.height} → ${newWidth}x${newHeight}")

            val scaled = BufferedImage(newWidth, newHeight, image.type)
            val graphics = scaled.createGraphics()
            graphics.drawImage(
                    image.getScaledInstance(newWidth, newHeight, java.awt.Image.SCALE_SMOOTH),
                    0,
                    0,
                    null
            )
            graphics.dispose()

            val outputStream = ByteArrayOutputStream()
            ImageIO.write(scaled, "png", outputStream)
            outputStream.toByteArray()
        } catch (e: Exception) {
            logger.error("이미지 크기 조정 실패: ${e.message}", e)
            imageBytes
        }
    }
}
