package com.example.gamewinner.model

/**
 * Represents a parsed quiz/exam question extracted from OCR.
 */
data class Question(
    val rawText: String,
    val cleanedText: String,
    val questionBody: String,
    val options: List<String>,
    val hash: String
) {
    /**
     * Whether this question has multiple-choice options.
     */
    val hasOptions: Boolean get() = options.isNotEmpty()

    /**
     * Returns a formatted string for display/logging.
     */
    fun toDisplayString(): String {
        val sb = StringBuilder(questionBody)
        if (hasOptions) {
            sb.append("\n")
            options.forEach { sb.append("\n$it") }
        }
        return sb.toString()
    }

    companion object {
        fun empty() = Question(
            rawText = "",
            cleanedText = "",
            questionBody = "",
            options = emptyList(),
            hash = ""
        )
    }
}
