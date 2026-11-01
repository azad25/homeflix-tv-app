package com.homeflix.tv.presentation.ui

import androidx.compose.runtime.Immutable
import com.homeflix.tv.domain.model.Media
import com.homeflix.tv.domain.model.MediaType
import com.homeflix.tv.domain.model.RecentlyWatchedItem
import com.homeflix.tv.presentation.screens.tvshows.TvSeries
import com.homeflix.tv.util.ApiUtils

enum class TileKind { MOVIE, SERIES, EPISODE }

/**
 * Everything a browse tile, the hero billboard and the info block under a row
 * need — precomputed once, so focus changes never do formatting or URL work.
 */
@Immutable
data class Tile(
    val key: String,
    val kind: TileKind,
    /** Movie / episode media id, or series id for [TileKind.SERIES]. */
    val id: Int,
    val seriesId: Int? = null,
    val title: String,
    val posterUrl: String,
    val backdropUrl: String,
    val logoUrl: String? = null,
    val previewUrl: String? = null,
    val meta: String = "",
    val description: String? = null,
    /** Second line, e.g. "S1:E3  Pilot" on continue-watching tiles. */
    val subtitle: String? = null,
    val progress: Float? = null,
    val resumeMs: Long = 0,
    val badge: String? = null,
    val rank: Int? = null
)

enum class RowStyle { POSTER, LANDSCAPE, TOP10 }

@Immutable
data class ContentRow(
    val id: String,
    val title: String,
    val tiles: List<Tile>,
    val style: RowStyle = RowStyle.POSTER
)

@Immutable
data class Feed(
    val hero: Tile? = null,
    val rows: List<ContentRow> = emptyList(),
    /** Rotating billboard. Nullable so feeds cached by older versions still load. */
    val heroes: List<Tile>? = null
) {
    /** Billboard titles in rotation order (falls back to the single [hero]). */
    val heroList: List<Tile> get() = heroes?.filterNotNull()?.takeIf { it.isNotEmpty() } ?: listOfNotNull(hero)
    val isEmpty: Boolean get() = heroList.isEmpty() && rows.isEmpty()
}

object Tiles {

    private const val NEW_WINDOW_MS = 14L * 24 * 3600 * 1000

    fun previewUrlFor(media: Media): String? =
        if (!media.previewClipPath.isNullOrBlank() || !media.previewPath.isNullOrBlank())
            ApiUtils.getPreviewClipUrl(media.id) else null

    fun movie(media: Media, keyPrefix: String = "m", rank: Int? = null): Tile = Tile(
        key = "$keyPrefix${media.id}",
        kind = if (media.type == MediaType.MOVIE) TileKind.MOVIE else TileKind.EPISODE,
        id = media.id,
        seriesId = media.seriesId,
        title = media.title,
        posterUrl = ApiUtils.getPosterUrl(media),
        backdropUrl = ApiUtils.getBackdropUrl(media),
        logoUrl = ApiUtils.getLogoUrl(media),
        previewUrl = previewUrlFor(media),
        meta = MediaFormat.metaLine(media),
        description = MediaFormat.description(media),
        badge = media.createdAt?.time?.let { if (System.currentTimeMillis() - it < NEW_WINDOW_MS) "Recently Added" else null },
        rank = rank
    )

    fun series(series: TvSeries, keyPrefix: String = "s", badge: String? = null): Tile = Tile(
        key = "$keyPrefix${series.id}",
        kind = TileKind.SERIES,
        id = series.id,
        seriesId = series.id,
        title = series.title,
        posterUrl = ApiUtils.getSeriesPosterUrl(series),
        backdropUrl = ApiUtils.getSeriesBackdropUrl(series),
        logoUrl = ApiUtils.getSeriesLogoUrl(series.id),
        meta = buildList {
            add("Show")
            series.genres.firstOrNull { it.isNotBlank() }?.let(::add)
            series.year?.takeIf { it > 0 }?.let { add(it.toString()) }
            when {
                series.totalSeasons > 1 -> add("${series.totalSeasons} Seasons")
                series.totalEpisodes > 0 -> add("${series.totalEpisodes} Episodes")
            }
        }.joinToString("  •  "),
        description = series.description?.takeIf { it.isNotBlank() },
        badge = badge
    )

    /**
     * Continue-watching entry. For episodes the backend replaces `media` with
     * the series' values, so the episode to resume is the top-level [RecentlyWatchedItem.mediaId].
     */
    fun continueWatching(item: RecentlyWatchedItem): Tile? {
        val media = item.media
        if (item.durationSeconds <= 0) return null
        val progress = (item.progressSeconds.toFloat() / item.durationSeconds).coerceIn(0f, 1f)
        if (progress >= 0.95f) return null
        val seriesId = media.seriesId?.takeIf { it > 0 }
        val isEpisode = seriesId != null || media.type != MediaType.MOVIE
        return if (isEpisode && seriesId != null) {
            Tile(
                key = "cw${item.mediaId}",
                kind = TileKind.EPISODE,
                id = item.mediaId,
                seriesId = seriesId,
                title = media.title,
                posterUrl = "${ApiUtils.getBaseUrl()}/series/$seriesId/poster",
                backdropUrl = ApiUtils.getEpisodeStillUrl(item.mediaId),
                logoUrl = ApiUtils.getSeriesLogoUrl(seriesId),
                meta = MediaFormat.episodeLabel(media.seasonNumber, media.episodeNumber, null),
                description = MediaFormat.description(media),
                subtitle = MediaFormat.episodeLabel(media.seasonNumber, media.episodeNumber, null).ifBlank { null },
                progress = progress,
                resumeMs = item.progressSeconds * 1000
            )
        } else {
            movie(media, keyPrefix = "cw").copy(
                id = item.mediaId,
                key = "cw${item.mediaId}",
                progress = progress,
                resumeMs = item.progressSeconds * 1000,
                badge = null,
                subtitle = remaining(item.durationSeconds - item.progressSeconds)
            )
        }
    }

    private fun remaining(seconds: Long): String? {
        val m = (seconds / 60).toInt()
        return if (m <= 0) null else "${MediaFormat.duration(m)} left"
    }
}
