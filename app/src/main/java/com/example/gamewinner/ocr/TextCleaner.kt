package com.example.gamewinner.ocr

import com.example.gamewinner.model.Question
import com.example.gamewinner.utils.Constants
import java.security.MessageDigest

/**
 * Cleans and structures raw OCR text into a [Question] object.
 *
 * Handles common OCR artifacts, detects question structure (body + options),
 * and generates a hash for cache lookup.
 */
object TextCleaner {

    // Regex patterns for question parsing
    private val QUESTION_NUMBER_PATTERN = Regex("""^\s*\d+[\.\)\:]?\s*""")
    private val OPTION_PATTERN = Regex("""^\s*([A-Da-d])[\.\)\:]?\s*(.+)""")
    private val MULTI_OPTION_PATTERN = Regex("""(?:^|\n)\s*([A-Da-d])[\.\)\:]?\s*(.+)""")

    // Common OCR character substitution mistakes
    private val OCR_CORRECTIONS = mapOf(
        "l(" to "I(",   // lowercase L → uppercase I
        "|" to "I",     // pipe → I
        "0" to "O",     // zero → O (only in word context)
        "\u201c" to "\"",  // smart quotes
        "\u201d" to "\"",
        "\u2018" to "'",
        "\u2019" to "'",
        "\u2014" to "-",  // em dash
        "\u2013" to "-",  // en dash
    )

    /**
     * Processes raw OCR text into a structured [Question].
     *
     * @param rawText The raw text from ML Kit OCR
     * @return A structured Question object, or null if text is too short/invalid
     */
    fun clean(rawText: String): Question? {
        if (rawText.length < Constants.MIN_QUESTION_LENGTH) return null

        // Step 1: Basic cleanup
        var cleaned = rawText
            .replace("\r\n", "\n")
            .replace("\r", "\n")
            .replace(Regex("""[ \t]+"""), " ")  // Collapse horizontal whitespace
            .replace(Regex("""\n{3,}"""), "\n\n") // Max 2 consecutive newlines
            .trim()

        // Step 2: Fix common OCR mistakes (selective)
        cleaned = fixOcrMistakes(cleaned)

        // Step 3: Remove question number prefix
        cleaned = cleaned.replace(QUESTION_NUMBER_PATTERN, "")

        // Step 4: Extract options (A/B/C/D) and question body
        val (questionBody, options) = extractQuestionParts(cleaned)

        // Step 5: Generate hash for caching
        val hash = generateHash(questionBody + options.joinToString("|"))

        return Question(
            rawText = rawText,
            cleanedText = cleaned,
            questionBody = questionBody.trim(),
            options = options,
            hash = hash
        )
    }

    /**
     * Extracts the question body and multiple-choice options from cleaned text.
     */
    private fun extractQuestionParts(text: String): Pair<String, List<String>> {
        val lines = text.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
        val options = mutableListOf<String>()
        val bodyLines = mutableListOf<String>()

        var inOptions = false

        for (line in lines) {
            val optionMatch = OPTION_PATTERN.matchEntire(line)
            if (optionMatch != null) {
                inOptions = true
                val letter = optionMatch.groupValues[1].uppercase()
                val optionText = optionMatch.groupValues[2].trim()
                options.add("$letter. $optionText")
            } else if (!inOptions) {
                bodyLines.add(line)
            } else {
                // After options started, treat remaining lines as continuation of last option
                if (options.isNotEmpty()) {
                    options[options.lastIndex] = "${options.last()} $line"
                }
            }
        }

        // If no options were found line-by-line, try to find inline options
        if (options.isEmpty()) {
            val fullText = lines.joinToString(" ")
            val inlineMatches = MULTI_OPTION_PATTERN.findAll(text)
            for (match in inlineMatches) {
                val letter = match.groupValues[1].uppercase()
                val optionText = match.groupValues[2].trim()
                options.add("$letter. $optionText")
            }

            if (options.isNotEmpty()) {
                // Remove options text from body
                var body = fullText
                for (match in MULTI_OPTION_PATTERN.findAll(text)) {
                    body = body.replace(match.value, "")
                }
                return Pair(body.trim(), options)
            }
        }

        val body = bodyLines.joinToString(" ")
        return Pair(body, options)
    }

    /**
     * Applies selective OCR character corrections.
     * Only corrects characters that are clearly wrong in context.
     */
    private fun fixOcrMistakes(text: String): String {
        var result = text

        // Fix smart quotes and special dashes
        result = result
            .replace("\u201c", "\"")
            .replace("\u201d", "\"")
            .replace("\u2018", "'")
            .replace("\u2019", "'")
            .replace("\u2014", "-")
            .replace("\u2013", "-")

        return result
    }

    /**
     * Generates an MD5 hash of the text for cache lookup.
     */
    private fun generateHash(text: String): String {
        val normalized = text.lowercase().replace(Regex("""\s+"""), " ").trim()
        val digest = MessageDigest.getInstance("MD5")
        val bytes = digest.digest(normalized.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
