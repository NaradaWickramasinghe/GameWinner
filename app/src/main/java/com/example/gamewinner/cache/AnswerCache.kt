package com.example.gamewinner.cache

import android.util.Log
import android.util.LruCache
import com.example.gamewinner.model.Answer
import com.example.gamewinner.utils.Constants

/**
 * In-memory LRU cache for quiz answers.
 *
 * Prevents redundant Gemini API calls when the same question is
 * detected again (e.g., user holds camera steady on the same question).
 *
 * Key: MD5 hash of the cleaned question text
 * Value: The AI-generated Answer
 */
object AnswerCache {

    private const val TAG = "AnswerCache"

    private val cache = LruCache<String, Answer>(Constants.ANSWER_CACHE_SIZE)

    /** Total number of cache lookups */
    private var lookupCount = 0

    /** Number of cache hits */
    private var hitCount = 0

    /**
     * Checks if an answer exists in the cache for the given question hash.
     *
     * @param hash MD5 hash of the cleaned question text
     * @return Cached Answer or null if not found
     */
    fun get(hash: String): Answer? {
        lookupCount++
        val cached = cache.get(hash)

        if (cached != null) {
            hitCount++
            Log.d(TAG, "Cache HIT for hash ${hash.take(8)}... (hit rate: ${getHitRate()}%)")
        } else {
            Log.d(TAG, "Cache MISS for hash ${hash.take(8)}...")
        }

        return cached
    }

    /**
     * Stores an answer in the cache.
     *
     * @param hash MD5 hash of the cleaned question text
     * @param answer The AI-generated answer to cache
     */
    fun put(hash: String, answer: Answer) {
        cache.put(hash, answer)
        Log.d(TAG, "Cached answer for hash ${hash.take(8)}... (cache size: ${cache.size()})")
    }

    /**
     * Clears all cached answers.
     */
    fun clear() {
        cache.evictAll()
        lookupCount = 0
        hitCount = 0
        Log.d(TAG, "Cache cleared")
    }

    /**
     * Returns the cache hit rate as a percentage string.
     */
    fun getHitRate(): String {
        if (lookupCount == 0) return "0"
        return "%.1f".format(hitCount.toFloat() / lookupCount * 100)
    }

    /**
     * Returns the current number of cached entries.
     */
    fun size(): Int = cache.size()
}
