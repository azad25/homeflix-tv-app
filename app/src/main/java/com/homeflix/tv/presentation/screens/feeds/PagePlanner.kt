package com.homeflix.tv.presentation.screens.feeds

import com.homeflix.tv.data.remote.dto.ChartDto
import com.homeflix.tv.data.remote.dto.TasteResultDto
import com.homeflix.tv.data.remote.dto.TasteRowDto
import com.homeflix.tv.domain.model.Media
import com.homeflix.tv.domain.model.MediaType
import com.homeflix.tv.presentation.screens.tvshows.TvSeries
import com.homeflix.tv.presentation.ui.ContentRow
import com.homeflix.tv.presentation.ui.RowStyle
import com.homeflix.tv.presentation.ui.Tile
import com.homeflix.tv.presentation.ui.TileKind
import com.homeflix.tv.presentation.ui.Tiles

/**
 * Lays out a browse page so **no title appears in two rows**.
 *
 * Rows *claim* titles in priority order (most important first), then are
 * *shown* in display order. A row only gets titles nobody claimed before it;
 * rows left too short are dropped. Some rows keep their full list even if a
 * title was claimed earlier ([exclusive] = false), e.g. Top 10, where a gap
 * would break the ranking. Episodes count as their show.
 *
 * Row minimums shrink for small libraries (about a tenth of the titles), so a
 * library of a dozen titles still gets several short rows instead of an empty page.
 */
class PagePlanner(private val librarySize: Int = Int.MAX_VALUE) {
    private class Planned(val order: Int, val row: ContentRow)

    private val claimed = HashSet<String>()
    private val planned = mutableListOf<Planned>()

    /** True if this title is already on the page. */
    fun isClaimed(t: Tile) = identity(t) in claimed

    /**
     * Adds a row of [candidates] (best first). Returns the row, or null if
     * fewer than [min] unclaimed titles were left.
     */
    fun add(
        order: Int,
        id: String,
        title: String,
        candidates: List<Tile>,
        style: RowStyle = RowStyle.POSTER,
        min: Int = 4,
        max: Int = 20,
        exclusive: Boolean = true
    ): ContentRow? {
        val seenHere = HashSet<String>()
        val picked = candidates.filter { t ->
            val k = identity(t)
            seenHere.add(k) && (!exclusive || k !in claimed)
        }.take(max)
        val need = if (exclusive) min.coerceAtMost(maxOf(1, librarySize / 10)) else min
        if (picked.size < need) return null
        picked.forEach { claimed += identity(it) }
        val row = ContentRow(id, title, picked, style)
        planned += Planned(order, row)
        return row
    }

    /** Everything not yet on the page, e.g. "More Movies" at the bottom, so every title stays reachable. */
    fun leftovers(order: Int, id: String, title: String, all: List<Tile>, min: Int = 1, max: Int = 200) =
        add(order, id, title, all, min = min, max = max)

    val rows: List<ContentRow> get() = planned.sortedBy { it.order }.map { it.row }

    companion object {
        fun identity(t: Tile): String = when (t.kind) {
            TileKind.MOVIE -> "m:${t.id}"
            TileKind.SERIES -> "s:${t.id}"
            TileKind.EPISODE -> t.seriesId?.takeIf { it > 0 }?.let { "s:$it" } ?: "m:${t.id}"
        }
    }
}

/** Server recommendations and charts → tiles from the loaded library (full artwork + trailers). */
internal class Library(movies: List<Media>, series: List<TvSeries>) {
    private val movieById = movies.filter { it.type == MediaType.MOVIE }.associateBy { it.id }
    private val seriesById = series.associateBy { it.id }

    fun tile(type: String?, id: Int, prefix: String, rank: Int? = null): Tile? =
        if (type == "series") seriesById[id]?.let { Tiles.series(it, "${prefix}s").copy(rank = rank) }
        else movieById[id]?.let { Tiles.movie(it, prefix, rank) }

    fun tiles(row: TasteRowDto, prefix: String): List<Tile> =
        row.items.orEmpty().mapNotNull { tile(it.type, it.id, prefix) }

    /** Top 10 tiles, ranked 1…10 as the server ranked them; null if it can't fill at least 5. */
    fun top10(chart: ChartDto?, prefix: String): List<Tile>? =
        chart?.items.orEmpty().sortedBy { it.rank }
            .mapNotNull { tile(it.type, it.id, prefix) }
            .take(10)
            .mapIndexed { i, t -> t.copy(rank = i + 1) }
            .takeIf { it.size >= 5 }
}

internal object Charts {
    /** "Top 10 Movies on HomeFlix This Week", or without "This Week" when the chart isn't from this week's plays. */
    fun title(what: String, chart: ChartDto?): String {
        val thisWeek = chart != null && chart.windowDays in 1..7 && chart.source != "catalog"
        return "Top 10 $what on HomeFlix" + if (thisWeek) " This Week" else ""
    }
}

internal object Taste {
    /** The server's themed rows, in the order it ranked them (it already avoids repeats). */
    fun themed(result: TasteResultDto?): Map<String, List<TasteRowDto>> =
        result?.takeIf { it.personalized }?.rows.orEmpty().groupBy { it.kind ?: "" }

    /**
     * Genre rows ordered by the viewer's taste (their strongest genres first),
     * then the usual order. [topFeatures] look like "gen:Action".
     */
    fun <T> byTaste(genres: List<Pair<String, List<String>>>, topFeatures: List<String>?, build: (String, List<String>) -> T?): List<T> {
        val liked = topFeatures.orEmpty().filter { it.startsWith("gen:") }.map { it.removePrefix("gen:").lowercase() }
        fun rank(keys: List<String>): Int {
            val i = liked.indexOfFirst { g -> keys.any { k -> g.contains(k) } }
            return if (i < 0) Int.MAX_VALUE else i
        }
        return genres.sortedBy { (_, keys) -> rank(keys) }.mapNotNull { (title, keys) -> build(title, keys) }
    }
}
