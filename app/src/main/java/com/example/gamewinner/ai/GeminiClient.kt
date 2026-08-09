package com.example.gamewinner.ai

import android.util.Log
import com.example.gamewinner.BuildConfig
import com.example.gamewinner.model.Answer
import com.example.gamewinner.model.Question
import com.example.gamewinner.network.ApiClient
import com.example.gamewinner.utils.Constants
import com.google.gson.JsonObject
import com.google.gson.JsonArray
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Retrofit interface for Gemini's generateContent API endpoint.
 */
interface GeminiApiService {
    @POST("v1beta/models/{model}:generateContent")
    fun generateContent(
        @retrofit2.http.Path("model") model: String,
        @Query("key") apiKey: String,
        @Body request: JsonObject
    ): Call<JsonObject>
}

/**
 * Client for Google's Gemini API.
 *
 * Uses Retrofit to call the Gemini generateContent endpoint.
 * Runs network calls on a background ExecutorService and delivers
 * results via callbacks on the calling thread.
 */
class GeminiClient(private val userApiKey: String) {

    private val TAG = "GeminiClient"

    private val apiService: GeminiApiService =
        ApiClient.retrofit.create(GeminiApiService::class.java)

    private val executor: ExecutorService = Executors.newSingleThreadExecutor()

    // Use the user-provided key if available, otherwise fallback to the hardcoded build config key
    private val apiKey: String = if (userApiKey.isNotBlank()) userApiKey else BuildConfig.GEMINI_API_KEY
    private val model: String = BuildConfig.GEMINI_MODEL

    /**
     * Sends a question to Gemini and returns the AI-generated answer.
     *
     * @param question The structured question to answer
     * @param onResult Callback with the parsed Answer
     * @param onError Callback if the API call fails
     */
    fun getAnswer(
        question: Question,
        customPrompt: String = "",
        onResult: (Answer) -> Unit,
        onError: (Exception) -> Unit
    ) {
        executor.execute {
            var lastException: Exception? = null

            for (attempt in 0..Constants.MAX_RETRIES) {
                try {
                    val prompt = PromptBuilder.buildPrompt(question, customPrompt)
                    
                    Log.d(TAG, "================ FULL PROMPT ================\n$prompt\n=============================================")
                    
                    val requestBody = buildRequestBody(prompt)

                    Log.d(TAG, "Calling Gemini API (attempt ${attempt + 1}/${Constants.MAX_RETRIES + 1})")

                    val response = apiService.generateContent(model, apiKey, requestBody).execute()

                    if (response.isSuccessful && response.body() != null) {
                        val responseBody = response.body()!!
                        val answer = ResponseParser.parse(responseBody)

                        if (answer != null) {
                            Log.d(TAG, "Gemini response: ${answer.toShortDisplay()}")
                            onResult(answer)
                            return@execute
                        } else {
                            lastException = Exception("Failed to parse Gemini response")
                        }
                    } else {
                        val code = response.code()
                        val errorBody = response.errorBody()?.string() ?: "Unknown error"
                        val errorMessage = if (code == 429) {
                            "API Quota Exceeded (429). Please check billing details or try again later."
                        } else {
                            "Gemini API error $code: $errorBody"
                        }
                        lastException = Exception(errorMessage)
                        Log.e(TAG, "API error: ${lastException!!.message}")
                    }

                } catch (e: Exception) {
                    lastException = e
                    Log.e(TAG, "Network error (attempt ${attempt + 1})", e)
                }

                // Wait before retry
                if (attempt < Constants.MAX_RETRIES) {
                    Thread.sleep(1000L * (attempt + 1))
                }
            }

            // All retries exhausted
            onError(lastException ?: Exception("Unknown error"))
        }
    }

    /**
     * Builds the Gemini API request body JSON.
     *
     * Format:
     * ```json
     * {
     *   "contents": [{
     *     "parts": [{ "text": "..." }]
     *   }],
     *   "generationConfig": {
     *     "temperature": 0.1,
     *     "maxOutputTokens": 256
     *   }
     * }
     * ```
     */
    private fun buildRequestBody(prompt: String): JsonObject {
        val textPart = JsonObject().apply {
            addProperty("text", prompt)
        }

        val parts = JsonArray().apply {
            add(textPart)
        }

        val content = JsonObject().apply {
            add("parts", parts)
        }

        val contents = JsonArray().apply {
            add(content)
        }

        val generationConfig = JsonObject().apply {
            addProperty("temperature", 0.1)
            addProperty("maxOutputTokens", 2048)
        }

        return JsonObject().apply {
            add("contents", contents)
            add("generationConfig", generationConfig)
        }
    }

    /**
     * Releases executor resources.
     */
    fun shutdown() {
        executor.shutdown()
    }
}
