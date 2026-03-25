package com.homeflix.tv.data.persistence

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.homeflix.tv.domain.model.Media
import java.util.*

/**
 * Simple content cache using SharedPreferences and GSON.
 * Caches media lists for offline/instant startup.
 */
object ContentCache {
    private const val PREFS_NAME = "homeflix_content_cache"
    private const val CACHE_EXPIRATION_MS = 24 * 60 * 60 * 1000L // 24 hours

    fun saveMediaList(context: Context, key: String, mediaList: List<Media>) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val gson = Gson()
            val json = gson.toJson(mediaList)
            prefs.edit()
                .putString(key, json)
                .putLong("${key}_timestamp", System.currentTimeMillis())
                .apply()
        } catch (e: Exception) {
            android.util.Log.e("ContentCache", "Error saving media list to cache", e)
        }
    }

    fun getMediaList(context: Context, key: String): List<Media>? {
        return try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val timestamp = prefs.getLong("${key}_timestamp", 0L)
            
            // Check if cache has expired
            if (System.currentTimeMillis() - timestamp > CACHE_EXPIRATION_MS) {
                return null
            }

            val json = prefs.getString(key, null) ?: return null
            val gson = Gson()
            val type = object : TypeToken<List<Media>>() {}.type
            gson.fromJson<List<Media>>(json, type)
        } catch (e: Exception) {
            android.util.Log.e("ContentCache", "Error reading media list from cache", e)
            null
        }
    }

    fun clearExpiredCache(context: Context) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val allEntries = prefs.all
            val editor = prefs.edit()
            val now = System.currentTimeMillis()

            allEntries.keys.filter { it.endsWith("_timestamp") }.forEach { timestampKey ->
                val timestamp = allEntries[timestampKey] as? Long ?: 0L
                if (now - timestamp > CACHE_EXPIRATION_MS) {
                    val baseKey = timestampKey.removeSuffix("_timestamp")
                    editor.remove(baseKey)
                    editor.remove(timestampKey)
                }
            }
            editor.apply()
        } catch (e: Exception) {
            android.util.Log.e("ContentCache", "Error clearing expired cache", e)
        }
    }
}
