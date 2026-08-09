package com.example.gamewinner.ocr

import android.graphics.Bitmap
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

/**
 * Processes camera frame bitmaps through Google ML Kit's on-device OCR engine.
 *
 * Uses the Latin script recognizer which supports English and other Latin-based languages.
 * All processing happens on-device — no network call or API key required.
 */
class OCRProcessor {

    private val TAG = "OCRProcessor"
    private val recognizer: TextRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    /** Tracks the last successfully extracted text to avoid duplicate GPT calls */
    private var lastExtractedText: String = ""

    /**
     * Processes a bitmap through ML Kit OCR.
     *
     * @param bitmap The camera frame to process
     * @param onResult Callback with (extractedText, isDuplicate)
     *   - extractedText: The raw text found in the image
     *   - isDuplicate: True if the text matches the previous extraction
     */
    fun processImage(
        bitmap: Bitmap,
        onResult: (text: String, isDuplicate: Boolean) -> Unit,
        onError: (Exception) -> Unit
    ) {
        val inputImage = InputImage.fromBitmap(bitmap, 0)

        recognizer.process(inputImage)
            .addOnSuccessListener { visionText ->
                val rawText = visionText.text.trim()

                if (rawText.isEmpty()) {
                    Log.d(TAG, "OCR: No text detected in frame")
                    onResult("", true) // Treat empty as "no change"
                    return@addOnSuccessListener
                }

                // Check if text is substantially the same as last extraction
                val isDuplicate = isTextSimilar(rawText, lastExtractedText)

                if (!isDuplicate) {
                    lastExtractedText = rawText
                    Log.d(TAG, "OCR: New text detected (${rawText.length} chars)")
                } else {
                    Log.v(TAG, "OCR: Duplicate text detected, skipping")
                }

                onResult(rawText, isDuplicate)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "OCR processing failed", e)
                onError(e)
            }
    }

    /**
     * Compares two text strings for similarity using normalized Levenshtein-like approach.
     * Returns true if texts are more than 85% similar.
     */
    private fun isTextSimilar(text1: String, text2: String): Boolean {
        if (text1.isEmpty() || text2.isEmpty()) return false

        val normalized1 = text1.lowercase().replace("\\s+".toRegex(), " ").trim()
        val normalized2 = text2.lowercase().replace("\\s+".toRegex(), " ").trim()

        if (normalized1 == normalized2) return true

        // Quick length check — if lengths differ by more than 20%, they're different
        val lengthRatio = minOf(normalized1.length, normalized2.length).toFloat() /
                maxOf(normalized1.length, normalized2.length).toFloat()
        if (lengthRatio < 0.8f) return false

        // Check word overlap
        val words1 = normalized1.split(" ").toSet()
        val words2 = normalized2.split(" ").toSet()
        val commonWords = words1.intersect(words2).size
        val totalWords = words1.union(words2).size

        val similarity = if (totalWords > 0) commonWords.toFloat() / totalWords else 0f
        return similarity > 0.85f
    }

    /**
     * Resets the duplicate detection state.
     */
    fun reset() {
        lastExtractedText = ""
    }

    /**
     * Releases OCR resources.
     */
    fun close() {
        recognizer.close()
    }
}
