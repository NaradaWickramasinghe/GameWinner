package com.example.gamewinner.ai

import com.example.gamewinner.model.Question

/**
 * Constructs prompts for the Gemini API optimized for quiz/exam answering.
 *
 * The prompt design ensures:
 * - Structured JSON response for reliable parsing
 * - Concise answers (reduces token usage and latency)
 * - Handling of both multiple-choice and open-ended questions
 */
object PromptBuilder {

    /**
     * Builds the complete prompt string for a question.
     *
     * @param question The structured question from OCR
     * @param customPrompt Any additional instructions provided by the user
     * @return A formatted prompt string for the Gemini API
     */
    fun buildPrompt(question: Question, customPrompt: String = ""): String {
        val optionsText = if (question.hasOptions) {
            "\nParsed Options:\n" + question.options.joinToString("\n")
        } else {
            ""
        }

        val basePrompt = """You are an expert exam assistant. You will be given text extracted from a quiz screen.
The text often contains a question and several options (e.g., A, B, C, D) mixed together in a single block of text due to OCR imperfections.

Task:
1. Carefully read the extracted text to identify the core question.
2. Identify the possible options provided in the text (even if they are jumbled together).
3. Determine the correct answer.

Extracted Text:
${question.questionBody}$optionsText

Respond with ONLY a JSON object in this exact format, no other text:
{"answer":"[<LETTER>] <FULL ANSWER TEXT>","confidence":<0-100>,"reason":"<brief explanation>"}

Where:
- "answer" is the full text of the correct answer. ALWAYS prefix it with the corresponding option letter in brackets (e.g., "[B] Photosynthesis"). If no letters are present, just provide the answer text.
- "confidence" is your confidence percentage (0-100)
- "reason" is a brief 1-sentence explanation

Example: {"answer":"[B] Photosynthesis","confidence":95,"reason":"Photosynthesis converts CO2 to oxygen"}"""

        return if (customPrompt.isNotBlank()) {
            "$basePrompt\n\nAdditional User Instructions (MUST FOLLOW): $customPrompt"
        } else {
            basePrompt
        }
    }
}
