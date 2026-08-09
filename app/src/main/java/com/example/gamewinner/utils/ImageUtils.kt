package com.example.gamewinner.utils

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
import android.util.Log
import androidx.camera.core.ImageProxy
import java.io.ByteArrayOutputStream
import kotlin.math.abs
import kotlin.math.min

/**
 * Utility functions for image processing in the camera → OCR pipeline.
 */
object ImageUtils {

    private const val TAG = "ImageUtils"

    /**
     * Converts an [ImageProxy] from CameraX to a [Bitmap], handling rotation.
     *
     * CameraX delivers frames in YUV_420_888 format. We convert to NV21,
     * then to JPEG, then decode to Bitmap and apply rotation.
     */
    fun imageProxyToBitmap(imageProxy: ImageProxy): Bitmap? {
        return try {
            val nv21 = yuv420ToNv21(imageProxy)
            val yuvImage = YuvImage(
                nv21,
                ImageFormat.NV21,
                imageProxy.width,
                imageProxy.height,
                null
            )

            val outputStream = ByteArrayOutputStream()
            yuvImage.compressToJpeg(
                Rect(0, 0, imageProxy.width, imageProxy.height),
                85,
                outputStream
            )

            val jpegBytes = outputStream.toByteArray()
            val bitmap = BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size)

            // Apply rotation from image metadata
            val rotationDegrees = imageProxy.imageInfo.rotationDegrees
            if (rotationDegrees != 0 && bitmap != null) {
                val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
                Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            } else {
                bitmap
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to convert ImageProxy to Bitmap", e)
            null
        }
    }

    /**
     * Converts YUV_420_888 format from CameraX to NV21 byte array.
     */
    private fun yuv420ToNv21(imageProxy: ImageProxy): ByteArray {
        val yPlane = imageProxy.planes[0]
        val uPlane = imageProxy.planes[1]
        val vPlane = imageProxy.planes[2]

        val yBuffer = yPlane.buffer
        val uBuffer = uPlane.buffer
        val vBuffer = vPlane.buffer

        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()

        val nv21 = ByteArray(ySize + uSize + vSize)

        // Copy Y plane
        yBuffer.get(nv21, 0, ySize)

        // Interleave V and U planes for NV21 format
        val uvPixelStride = vPlane.pixelStride
        val uvRowStride = vPlane.rowStride
        val width = imageProxy.width
        val height = imageProxy.height

        var pos = ySize
        for (row in 0 until height / 2) {
            for (col in 0 until width / 2) {
                val vIndex = row * uvRowStride + col * uvPixelStride
                val uIndex = row * uvRowStride + col * uvPixelStride

                if (vIndex < vSize) {
                    nv21[pos++] = vBuffer.get(vIndex)
                }
                if (uIndex < uSize) {
                    nv21[pos++] = uBuffer.get(uIndex)
                }
            }
        }

        return nv21
    }

    /**
     * Resizes a bitmap to fit within [maxWidth] while maintaining aspect ratio.
     * Used to limit OCR processing size for performance.
     */
    fun resizeBitmap(bitmap: Bitmap, maxWidth: Int = Constants.MAX_OCR_IMAGE_WIDTH): Bitmap {
        if (bitmap.width <= maxWidth) return bitmap

        val scale = maxWidth.toFloat() / bitmap.width
        val newHeight = (bitmap.height * scale).toInt()

        return Bitmap.createScaledBitmap(bitmap, maxWidth, newHeight, true)
    }

    /**
     * Calculates similarity between two bitmaps using pixel sampling.
     * Returns a value between 0.0 (completely different) and 1.0 (identical).
     *
     * Uses random sampling for performance — doesn't compare every pixel.
     */
    fun calculateSimilarity(bitmap1: Bitmap?, bitmap2: Bitmap?): Float {
        if (bitmap1 == null || bitmap2 == null) return 0f
        if (bitmap1.width != bitmap2.width || bitmap1.height != bitmap2.height) return 0f

        val width = bitmap1.width
        val height = bitmap1.height
        val sampleCount = min(Constants.PIXEL_SAMPLE_COUNT, width * height)

        var matchCount = 0
        val stepX = width / 10
        val stepY = height / 10

        if (stepX == 0 || stepY == 0) return 0f

        var samples = 0
        for (y in 0 until height step stepY) {
            for (x in 0 until width step stepX) {
                if (samples >= sampleCount) break

                val pixel1 = bitmap1.getPixel(x, y)
                val pixel2 = bitmap2.getPixel(x, y)

                // Compare RGB channels with tolerance
                val rDiff = abs(((pixel1 shr 16) and 0xFF) - ((pixel2 shr 16) and 0xFF))
                val gDiff = abs(((pixel1 shr 8) and 0xFF) - ((pixel2 shr 8) and 0xFF))
                val bDiff = abs((pixel1 and 0xFF) - (pixel2 and 0xFF))

                if (rDiff < 30 && gDiff < 30 && bDiff < 30) {
                    matchCount++
                }
                samples++
            }
        }

        return if (samples > 0) matchCount.toFloat() / samples else 0f
    }
}
