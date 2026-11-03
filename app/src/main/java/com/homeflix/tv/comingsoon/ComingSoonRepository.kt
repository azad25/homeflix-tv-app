package com.homeflix.tv.comingsoon

import com.homeflix.tv.data.remote.api.HomeFlixApiService
import com.homeflix.tv.util.ApiUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeout
import javax.inject.Inject
import javax.inject.Singleton

/** Coming Soon from the server: the lineup, one title, and Notify Me. Always fresh (responses are small). */
@Singleton
class ComingSoonRepository @Inject constructor(private val api: HomeFlixApiService) {

    private suspend fun <T> call(block: suspend () -> T): Result<T> = try {
        Result.success(withTimeout(8_000) { block() })
    } catch (e: Exception) {
        if (e is CancellationException && e !is kotlinx.coroutines.TimeoutCancellationException) throw e
        Result.failure(e)
    }

    /** Movies then series, each in rank order. Empty on any failure (the UI just hides Coming Soon). */
    suspend fun lineup(): ComingSoonList = call {
        val r = api.getComingSoon()
        if (!r.isSuccessful) error("HTTP ${r.code()}")
        r.body() ?: ComingSoonList()
    }.getOrDefault(ComingSoonList()).let { l ->
        ComingSoonList(movies = l.movies.orEmpty().sortedBy { it.rank }, series = l.series.orEmpty().sortedBy { it.rank })
    }

    /** One title, or null when it isn't coming soon (404) or the server can't be reached. */
    suspend fun title(mediaType: String, tmdbId: Int): ComingSoonItem? = call {
        val r = api.getComingSoonTitle(mediaType, tmdbId)
        if (r.isSuccessful) r.body() else null
    }.getOrNull()

    suspend fun setNotify(item: ComingSoonItem, on: Boolean): Result<NotifyResult> = call {
        val r = if (on) api.comingSoonNotify(NotifyBody(item.mediaType, item.tmdbId))
        else api.comingSoonUnnotify(item.mediaType, item.tmdbId)
        if (!r.isSuccessful) error("HTTP ${r.code()}")
        r.body() ?: NotifyResult(on = on)
    }

    companion object {
        /** Absolute trailer URL, or null when the MP4 isn't ready yet. */
        fun trailerUrl(item: ComingSoonItem): String? =
            item.trailer?.takeIf { it.state == "ready" }?.videoUrl?.takeIf { it.isNotBlank() }?.let { u ->
                if (u.startsWith("http")) u else ApiUtils.getRootUrl().trimEnd('/') + "/" + u.trimStart('/')
            }
    }
}
