package com.homeflix.tv.core

import android.content.Context
import android.util.Log
import com.homeflix.tv.domain.repository.MediaRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single source of truth for "where was I?".
 *
 * Every save is written to local storage synchronously (so it survives the
 * player being torn down, the app being backgrounded or the process being
 * killed) and then synced to the server on an application-scoped coroutine,
 * which — unlike a composable's scope — is never cancelled by navigation.
 */
@Singleton
class PlaybackProgressStore @Inject constructor(
    @ApplicationContext context: Context,
    private val repository: MediaRepository,
    @ApplicationScope private val appScope: CoroutineScope
) {
    data class Entry(val positionMs: Long, val durationMs: Long, val updatedAt: Long) {
        val fraction: Float get() = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
        val isFinished: Boolean get() = durationMs > 0 && positionMs >= durationMs * 0.95f
    }

    private val prefs = context.getSharedPreferences("playback_progress_v5", Context.MODE_PRIVATE)

    fun get(mediaId: Int): Entry? {
        val raw = prefs.getString(key(mediaId), null) ?: return null
        val parts = raw.split('|')
        if (parts.size != 3) return null
        return runCatching { Entry(parts[0].toLong(), parts[1].toLong(), parts[2].toLong()) }.getOrNull()
    }

    /** Persist locally now; push to the server in the background. */
    fun save(mediaId: Int, positionMs: Long, durationMs: Long, sync: Boolean = true) {
        if (mediaId <= 0 || durationMs <= 0 || positionMs < 0) return
        val pos = positionMs.coerceAtMost(durationMs)
        prefs.edit().putString(key(mediaId), "$pos|$durationMs|${System.currentTimeMillis()}").apply()
        pruneIfLarge()
        if (!sync) return
        // Backend rejects position=0 (binding:"required"); nothing worth syncing anyway.
        val posSec = pos / 1000
        val durSec = durationMs / 1000
        if (posSec < 1 || durSec < 1) return
        appScope.launch {
            val result = runCatching { repository.updatePlaybackProgress(mediaId, posSec, durSec) }
            if (result.isFailure || result.getOrNull()?.isFailure == true) {
                Log.w("ProgressStore", "sync failed for $mediaId (kept locally)")
            }
        }
    }

    /**
     * Best resume position in ms, or 0 to start from the beginning.
     * Prefers the local entry (most recent on this device), then the server.
     */
    suspend fun resolveResumeMs(mediaId: Int, hintMs: Long): Long {
        val local = get(mediaId)
        val candidate = when {
            local != null -> if (local.isFinished) 0L else local.positionMs
            hintMs > 0 -> hintMs
            else -> runCatching {
                kotlinx.coroutines.withTimeoutOrNull(1_500) {
                    repository.getPlaybackProgress(mediaId.toString()).getOrNull()
                }
            }.getOrNull()?.let { p -> if (p.completed) 0L else p.progress * 1000 } ?: 0L
        }
        return if (candidate < 10_000) 0L else candidate
    }

    /** Keep the store bounded: forget the oldest entries beyond 600 titles. */
    private fun pruneIfLarge() {
        val all = prefs.all
        if (all.size <= 600) return
        val oldest = all.entries
            .sortedBy { (it.value as? String)?.substringAfterLast('|')?.toLongOrNull() ?: 0L }
            .take(all.size - 500)
        prefs.edit().apply { oldest.forEach { remove(it.key) } }.apply()
    }

    private fun key(mediaId: Int) = "m$mediaId"
}
