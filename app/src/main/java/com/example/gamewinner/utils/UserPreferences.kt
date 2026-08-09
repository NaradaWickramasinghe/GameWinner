package com.example.gamewinner.utils

import android.content.Context
import android.content.SharedPreferences

/**
 * Manages saving and loading of user preferences using SharedPreferences.
 */
object UserPreferences {
    private const val PREFS_NAME = "GameWinnerPrefs"
    private const val KEY_CUSTOM_PROMPT = "custom_prompt"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * Saves the user's custom AI prompt to local storage.
     */
    fun setCustomPrompt(context: Context, prompt: String) {
        getPrefs(context).edit().putString(KEY_CUSTOM_PROMPT, prompt).apply()
    }

    /**
     * Retrieves the user's saved custom AI prompt. Returns an empty string if none exists.
     */
    fun getCustomPrompt(context: Context): String {
        return getPrefs(context).getString(KEY_CUSTOM_PROMPT, "") ?: ""
    }

    private const val KEY_GEMINI_API_KEY = "gemini_api_key"

    fun setGeminiApiKey(context: Context, key: String) {
        getPrefs(context).edit().putString(KEY_GEMINI_API_KEY, key).apply()
    }

    fun getGeminiApiKey(context: Context): String {
        return getPrefs(context).getString(KEY_GEMINI_API_KEY, "") ?: ""
    }
}
