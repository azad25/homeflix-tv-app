package com.homeflix.tv.comingsoon

import com.homeflix.tv.presentation.ui.Tile
import com.homeflix.tv.presentation.ui.TileKind
import javax.inject.Inject
import javax.inject.Singleton

/** Home's "Coming Soon: Movies" / "Coming Soon: Series" rows (hidden when empty or unreachable). */
@Singleton
class ComingSoonHome @Inject constructor(private val repo: ComingSoonRepository) {

    suspend fun rows(): Pair<List<Tile>, List<Tile>> {
        val l = repo.lineup()
        return l.movies.orEmpty().map(::tile) to l.series.orEmpty().map(::tile)
    }

    companion object {
        fun tile(item: ComingSoonItem): Tile = Tile(
            key = "cs${item.mediaType}${item.tmdbId}",
            kind = TileKind.COMING,
            id = item.tmdbId,
            title = item.title,
            posterUrl = item.posterUrl.orEmpty(),
            backdropUrl = item.backdropUrl ?: item.posterUrl.orEmpty(),
            // Muted trailer on focus: only once the server has the MP4.
            previewUrl = ComingSoonRepository.trailerUrl(item),
            meta = listOfNotNull(
                ComingSoonDates.fullDate(item).takeIf { it.isNotBlank() },
                item.studio?.takeIf { it.isNotBlank() },
                item.genres?.take(2)?.joinToString(", ")?.takeIf { it.isNotBlank() }
            ).joinToString("  •  "),
            description = item.overview,
            subtitle = ComingSoonDates.cardLine(item),
            badge = item.releaseLabel?.takeIf { item.isSeries && it.isNotBlank() },
            comingKey = item.key,
            bell = item.notifyOn
        )
    }
}
