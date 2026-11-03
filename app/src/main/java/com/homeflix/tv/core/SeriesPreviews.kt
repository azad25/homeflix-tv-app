package com.homeflix.tv.core

import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.runtime.staticCompositionLocalOf
import com.homeflix.tv.data.remote.api.HomeFlixApiService
import com.homeflix.tv.data.remote.dto.MediaDto
import com.homeflix.tv.presentation.ui.Tile
import com.homeflix.tv.presentation.ui.TileKind
import com.homeflix.tv.util.ApiUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeout
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Series have no trailer of their own, so their preview is the newest
 * **uploaded** episode's preview clip from the server (`/api/preview-clips/{episodeId}`),
 * the same source the web app uses. Resolved on demand (only when a show is
 * focused or opened), then cached, so browsing never fetches episode lists.
 */
@Singleton
class SeriesPreviews @Inject constructor(private val api: HomeFlixApiService) {
    private data class Entry(val at: Long, val episodeId: Int?)
    private val cache = ConcurrentHashMap<Int, Entry>()

    /** Preview URL for [seriesId], or null if it has no episodes. */
    suspend fun previewUrl(seriesId: Int): String? = latestEpisodeId(seriesId)?.let(ApiUtils::getPreviewClipUrl)

    suspend fun latestEpisodeId(seriesId: Int): Int? {
        if (seriesId <= 0) return null
        cache[seriesId]?.takeIf { SystemClock.elapsedRealtime() - it.at < TTL_MS }?.let { return it.episodeId }
        val id = try {
            withTimeout(8_000) { resolve(seriesId) }
        } catch (e: Exception) {
            if (e is CancellationException && e !is kotlinx.coroutines.TimeoutCancellationException) throw e
            return null // don't cache failures
        }
        cache[seriesId] = Entry(SystemClock.elapsedRealtime(), id)
        return id
    }

    private suspend fun resolve(seriesId: Int): Int? = coroutineScope {
        val seasons = api.getTvSeriesSeasons(seriesId).body().orEmpty()
            .map { it.season_number }.sortedDescending()
            .ifEmpty { listOf(1) }
        // New uploads are almost always in the latest seasons: look at the newest three.
        val episodes = seasons.take(MAX_SEASONS).map { season ->
            async { runCatching { api.getTvSeriesEpisodes(seriesId, season).body().orEmpty() }.getOrDefault(emptyList()) }
        }.awaitAll().flatten()
        // Older servers also return an unrelated episode whose media id equals the series id.
        pick(episodes.filter { it.seriesId == null || it.seriesId == seriesId })
    }

    companion object {
        const val TTL_MS = 30 * 60_000L
        const val MAX_SEASONS = 3

        /** The most recently uploaded episode that has a preview; else the most recently uploaded one. */
        fun pick(episodes: List<MediaDto>): Int? {
            if (episodes.isEmpty()) return null
            val newestFirst = episodes.sortedWith(
                compareByDescending<MediaDto> { it.createdAt.orEmpty() }
                    .thenByDescending { it.seasonNumber ?: 0 }
                    .thenByDescending { it.episodeNumber ?: 0 }
            )
            val withPreview = newestFirst.firstOrNull { !it.previewClipPath.isNullOrBlank() || !it.previewPath.isNullOrBlank() }
            return (withPreview ?: newestFirst.first()).id
        }
    }
}

val LocalSeriesPreviews = staticCompositionLocalOf<SeriesPreviews?> { null }

/** A tile's preview URL: its own, or for a show the newest uploaded episode's (resolved lazily). */
@Composable
fun rememberPreviewUrl(tile: Tile, active: Boolean): State<String?> {
    val resolver = LocalSeriesPreviews.current
    return produceState(initialValue = tile.previewUrl, tile.key, active) {
        if (value != null || !active) return@produceState
        if (tile.kind == TileKind.SERIES && resolver != null) value = resolver.previewUrl(tile.seriesId ?: tile.id)
    }
}
