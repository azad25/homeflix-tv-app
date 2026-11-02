package com.homeflix.tv.presentation.ui

import androidx.compose.runtime.Immutable
import com.homeflix.tv.domain.model.Media
import com.homeflix.tv.domain.model.MediaType
import com.homeflix.tv.domain.model.RecentlyWatchedItem
import com.homeflix.tv.presentation.screens.tvshows.TvSeries
import com.homeflix.tv.util.ApiUtils

/** CHANNEL = a Live TV channel ([Tile.channelId]); id 0 = "Live TV" (last channel). */
enum class TileKind { MOVIE, SERIES, EPISODE, CHANNEL }

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
    /** Live TV channel id for [TileKind.CHANNEL]. */
    val channelId: String? = null,
    val rank: Int? = null
)

enum class RowStyle { POSTER, LANDSCAPE, TOP10, CHANNEL }

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
     * Continue-watching entry. For episodes the backend replaces `media` with the
     * series' values (id = series id, title = "Show S01E02"); the episode to resume
     * is the top-level [RecentlyWatchedItem.mediaId]. Episode cards show the
     * series' artwork and name with "S1:E2 · Episode title · 30m left".
     */
    fun continueWatching(item: RecentlyWatchedItem, seriesById: Map<Int, TvSeries> = emptyMap()): Tile? {
        val media = item.media
        if (item.durationSeconds <= 0) return null
        val progress = (item.progressSeconds.toFloat() / item.durationSeconds).coerceIn(0f, 1f)
        if (progress >= 0.95f) return null
        val left = remaining(item.durationSeconds - item.progressSeconds)
        val seriesId = media.seriesId?.takeIf { it > 0 }
        val isEpisode = seriesId != null || media.type != MediaType.MOVIE
        return if (isEpisode && seriesId != null) {
            val series = seriesById[seriesId]
            val name = series?.title ?: media.title
                .replace(Regex("""\s+S\d+E\d+$""", RegexOption.IGNORE_CASE), "")
                .substringBefore(" - ")
                .ifBlank { media.title }
            val code = MediaFormat.episodeLabel(media.seasonNumber, media.episodeNumber, null)
            val epTitle = media.episodeTitle?.takeIf { it.isNotBlank() }
            Tile(
                key = "cw${item.mediaId}",
                kind = TileKind.EPISODE,
                id = item.mediaId,
                seriesId = seriesId,
                title = name,
                posterUrl = series?.let { ApiUtils.getSeriesPosterUrl(it) } ?: "${ApiUtils.getBaseUrl()}/series/$seriesId/poster",
                backdropUrl = series?.let { ApiUtils.getSeriesBackdropUrl(it) } ?: "${ApiUtils.getBaseUrl()}/series/$seriesId/backdrop",
                logoUrl = ApiUtils.getSeriesLogoUrl(seriesId),
                meta = listOfNotNull(code.ifBlank { null }, epTitle).joinToString("  •  "),
                description = MediaFormat.description(media),
                subtitle = listOfNotNull(code.ifBlank { null }, epTitle, left).joinToString("  ·  ").ifBlank { null },
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
                subtitle = left
            )
        }
    }

    private fun remaining(seconds: Long): String? {
        val m = (seconds / 60).toInt()
        return if (m <= 0) null else "${MediaFormat.duration(m)} left"
    }
}
