package com.homeflix.tv.livetv

import android.os.SystemClock
import com.homeflix.tv.core.ApplicationScope
import com.homeflix.tv.data.remote.api.HomeFlixApiService
import com.homeflix.tv.util.ApiUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/** Where the controller gets streams; an interface so tests can fake the server. */
interface LiveSource {
    /** Fresh stream URLs (absolute) for a channel, or null if the server can't be reached. */
    suspend fun play(id: String): LivePlay?
    /** Warm /play for the channels around the one just tuned. */
    fun prefetch(ids: List<String>)
}

/**
 * Live TV lineup and stream URLs from the HomeFlix server
 * (docs/LIVE_TV.md in homeflix-wifi). The server proxies every stream, so
 * the player needs no headers.
 */
@Singleton
class LiveTvRepository @Inject constructor(
    private val api: HomeFlixApiService,
    @ApplicationScope private val scope: CoroutineScope
) : LiveSource {
    private val _lineup = MutableStateFlow<LiveLineup?>(null)
    /** Last loaded lineup; null until loaded. `enabled = false` hides Live TV. */
    val lineup: StateFlow<LiveLineup?> = _lineup.asStateFlow()

    /** Prefetched /play answers, used once and only while fresh. */
    private val prefetched = ConcurrentHashMap<String, Pair<Long, LivePlay>>()

    private var loadedAt = 0L

    /**
     * The lineup, from memory if it's younger than [maxAgeMs]. Home, the top
     * menu and the player share one copy instead of each downloading the
     * full list (thousands of channels on some servers).
     */
    suspend fun lineupFresh(maxAgeMs: Long = LINEUP_TTL_MS): LiveLineup? {
        val cached = _lineup.value
        if (cached != null && SystemClock.elapsedRealtime() - loadedAt < maxAgeMs) return cached
        return refresh() ?: cached
    }

    suspend fun refresh(): LiveLineup? = try {
        val r = withTimeout(8_000) { api.liveChannels() }
        val body = if (r.isSuccessful) r.body() else if (r.code() == 404) LiveLineup(enabled = false) else null
        body?.let { l ->
            // Only channels that can actually play here: no YouTube-only sources,
            // nothing the server's health check found offline. Sorted by number
            // so ▲▼ walk the lineup like a cable box (numbers stay the server's).
            l.copy(channels = playable(l.channels.orEmpty()))
        }?.also { _lineup.value = it; loadedAt = SystemClock.elapsedRealtime() }
    } catch (e: Exception) {
        if (e is CancellationException && e !is kotlinx.coroutines.TimeoutCancellationException) throw e
        null
    }

    override suspend fun play(id: String): LivePlay? {
        prefetched.remove(id)?.let { (at, play) ->
            if (SystemClock.elapsedRealtime() - at < PREFETCH_TTL_MS) return play
        }
        return fetch(id)
    }

    override fun prefetch(ids: List<String>) {
        ids.distinct().forEach { id ->
            scope.launch { fetch(id)?.let { prefetched[id] = SystemClock.elapsedRealtime() to it } }
        }
    }

    private suspend fun fetch(id: String): LivePlay? = try {
        val r = withTimeout(6_000) { api.livePlay(id) }
        r.body()?.takeIf { r.isSuccessful }?.let { p ->
            p.copy(urls = p.urls.orEmpty().filter { it.isNotBlank() }.map(::absolute))
        }
    } catch (e: Exception) {
        if (e is CancellationException && e !is kotlinx.coroutines.TimeoutCancellationException) throw e
        null
    }

    private fun absolute(url: String): String =
        if (url.startsWith("http://") || url.startsWith("https://")) url
        else ApiUtils.getRootUrl().trimEnd('/') + "/" + url.trimStart('/')

    companion object {
        /** A prefetched URL set is only trusted briefly: the order follows the latest health check. */
        const val PREFETCH_TTL_MS = 45_000L
        const val LINEUP_TTL_MS = 10 * 60_000L

        /** Channels the TV can play: no YouTube-only sources, nothing offline; by number. */
        fun playable(channels: List<LiveChannel>): List<LiveChannel> = channels
            .filter { it.id.isNotBlank() && !it.isYouTubeOnly && !it.status.equals("offline", ignoreCase = true) }
            .sortedBy { it.number }
    }
}
