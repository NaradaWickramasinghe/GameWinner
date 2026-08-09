package com.example.gamewinner.utils

/**
 * Application-wide constants for configuration and tuning.
 */
object Constants {

    // ── Frame Processing ──
    /** Process every Nth frame from the camera (at 30 FPS, 6 = ~5 FPS effective) */
    const val FRAME_SKIP_COUNT = 6

    /** Similarity threshold (0.0-1.0) — skip frame if above this value */
    const val FRAME_SIMILARITY_THRESHOLD = 0.95f

    /** Number of pixel samples for frame comparison */
    const val PIXEL_SAMPLE_COUNT = 100

    // ── OCR ──
    /** Minimum text length to consider a valid question */
    const val MIN_QUESTION_LENGTH = 10

    /** Maximum image width for OCR processing (pixels) */
    const val MAX_OCR_IMAGE_WIDTH = 1280

    // ── Cache ──
    /** Maximum number of cached answers (LRU eviction) */
    const val ANSWER_CACHE_SIZE = 50

    // ── Overlay ──
    /** Duration to show answer overlay (milliseconds) */
    const val OVERLAY_DISPLAY_DURATION_MS = 8000L

    /** Animation duration for overlay slide-in (milliseconds) */
    const val OVERLAY_ANIMATION_DURATION_MS = 400L

    // ── Network ──
    /** HTTP connection timeout (seconds) */
    const val CONNECTION_TIMEOUT_SECONDS = 15L

    /** HTTP read timeout (seconds) */
    const val READ_TIMEOUT_SECONDS = 30L

    /** Maximum retry attempts for API calls */
    const val MAX_RETRIES = 2

    // ── Gemini API ──
    const val GEMINI_BASE_URL = "https://generativelanguage.googleapis.com/"

    // ── Pipeline ──
    /** Debounce interval between processing cycles (milliseconds) */
    const val PIPELINE_DEBOUNCE_MS = 500L
}
