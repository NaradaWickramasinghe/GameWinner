package com.example.gamewinner.model

/**
 * Represents an AI-generated answer to a quiz question.
 */
data class Answer(
    val answerLetter: String,
    val answerText: String,
    val confidence: Int,
    val reason: String,
    val timestamp: Long = System.currentTimeMillis()
) {
    /**
     * Confidence level category for UI color coding.
     */
    enum class ConfidenceLevel {
        HIGH,    // >= 80%
        MEDIUM,  // >= 50%
        LOW      // < 50%
    }

    val confidenceLevel: ConfidenceLevel
        get() = when {
            confidence >= 80 -> ConfidenceLevel.HIGH
            confidence >= 50 -> ConfidenceLevel.MEDIUM
            else -> ConfidenceLevel.LOW
        }

    /**
     * Returns a short display string like "B (95%)"
     */
    fun toShortDisplay(): String = "$answerLetter ($confidence%)"

    companion object {
        fun error(message: String) = Answer(
            answerLetter = "?",
            answerText = message,
            confidence = 0,
            reason = "Error processing question"
        )
    }
}
