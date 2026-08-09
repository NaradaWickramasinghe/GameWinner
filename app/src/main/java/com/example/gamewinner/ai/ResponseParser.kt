package com.example.gamewinner.ai

import android.util.Log
import com.example.gamewinner.model.Answer
import com.google.gson.JsonObject
import com.google.gson.JsonParser

/**
 * Parses Gemini API responses into [Answer] objects.
 *
 * Handles the Gemini response format:
 * ```json
 * {
 *   "candidates": [{
 *     "content": {
 *       "parts": [{ "text": "{\"answer\":\"B\",\"confidence\":95,\"reason\":\"...\"}" }]
 *     }
 *   }]
 * }
 * ```
 *
 * Includes a regex fallback for malformed JSON responses.
 */
object ResponseParser {

    private const val TAG = "ResponseParser"

    // Regex patterns for fallback parsing
    private val ANSWER_LETTER_PATTERN = Regex(""""answer"\s*:\s*"([^"]*?)"""")
    private val CONFIDENCE_PATTERN = Regex(""""confidence"\s*:\s*(\d+)""")
    private val REASON_PATTERN = Regex(""""reason"\s*:\s*"([^"]*?)"""")

    /**
     * Parses the Gemini API response into an [Answer] object.
     *
     * @param responseBody The full Gemini API response JSON
     * @return Parsed Answer or null if parsing fails entirely
     */
    fun parse(responseBody: JsonObject): Answer? {
        return try {
            // Extract the text content from Gemini response structure
            val textContent = extractTextContent(responseBody)

            if (textContent.isNullOrBlank()) {
                Log.e(TAG, "No text content in Gemini response")
                return Answer.error("No response from AI")
            }

            Log.d(TAG, "Gemini raw text: $textContent")

            // Try to parse the JSON answer from the text
            parseJsonAnswer(textContent) ?: parseWithRegex(textContent)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse Gemini response", e)
            Answer.error("Parse error: ${e.message}")
        }
    }

    /**
     * Extracts the text content from Gemini's nested response structure.
     */
    private fun extractTextContent(response: JsonObject): String? {
        return try {
            response
                .getAsJsonArray("candidates")
                ?.get(0)?.asJsonObject
                ?.getAsJsonObject("content")
                ?.getAsJsonArray("parts")
                ?.get(0)?.asJsonObject
                ?.get("text")?.asString
        } catch (e: Exception) {
            Log.e(TAG, "Failed to navigate Gemini response structure", e)
            null
        }
    }

    /**
     * Attempts to parse the AI's response as a JSON object.
     */
    private fun parseJsonAnswer(text: String): Answer? {
        return try {
            // Strip markdown code fences if present (```json ... ```)
            val cleaned = text
                .replace(Regex("""```json\s*"""), "")
                .replace(Regex("""```\s*"""), "")
                .trim()

            // Find JSON object in the text
            val jsonStart = cleaned.indexOf('{')
            val jsonEnd = cleaned.lastIndexOf('}')

            if (jsonStart == -1 || jsonEnd == -1 || jsonEnd <= jsonStart) {
                return null
            }

            val jsonStr = cleaned.substring(jsonStart, jsonEnd + 1)
            val json = JsonParser.parseString(jsonStr).asJsonObject

            val answer = json.get("answer")?.asString ?: return null
            val confidence = json.get("confidence")?.asInt ?: 85
            val reason = json.get("reason")?.asString ?: ""

            val letterMatch = Regex("[A-Za-z]").find(answer)
            val answerLetter = letterMatch?.value?.uppercase() ?: "?"

            Answer(
                answerLetter = answerLetter,
                answerText = answer,
                confidence = confidence.coerceIn(0, 100),
                reason = reason
            )
        } catch (e: Exception) {
            Log.w(TAG, "JSON parsing failed, trying regex fallback", e)
            null
        }
    }

    /**
     * Fallback parser using regex patterns for malformed JSON.
     */
    private fun parseWithRegex(text: String): Answer? {
        val answerMatch = ANSWER_LETTER_PATTERN.find(text)
        val confidenceMatch = CONFIDENCE_PATTERN.find(text)
        val reasonMatch = REASON_PATTERN.find(text)

        if (answerMatch == null) {
            Log.e(TAG, "Regex fallback: no answer found in response")
            return Answer.error("Could not parse AI response")
        }

        val answer = answerMatch.groupValues[1]
        val confidence = confidenceMatch?.groupValues?.get(1)?.toIntOrNull() ?: 75
        val reason = reasonMatch?.groupValues?.get(1) ?: ""

        val letterMatch = Regex("[A-Za-z]").find(answer)
        val answerLetter = letterMatch?.value?.uppercase() ?: "?"

        return Answer(
            answerLetter = answerLetter,
            answerText = answer,
            confidence = confidence.coerceIn(0, 100),
            reason = reason
        )
    }
}
