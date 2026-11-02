package com.homeflix.tv.ui

import com.homeflix.tv.data.remote.dto.ChartDto
import com.homeflix.tv.data.remote.dto.ChartItemDto
import com.homeflix.tv.presentation.screens.feeds.PagePlanner
import com.homeflix.tv.presentation.ui.RowStyle
import com.homeflix.tv.presentation.ui.Tile
import com.homeflix.tv.presentation.ui.TileKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PagePlannerTest {
    private fun m(id: Int, prefix: String = "m") = Tile(key = "$prefix$id", kind = TileKind.MOVIE, id = id, title = "M$id", posterUrl = "", backdropUrl = "")
    private fun s(id: Int) = Tile(key = "s$id", kind = TileKind.SERIES, id = id, seriesId = id, title = "S$id", posterUrl = "", backdropUrl = "")
    private fun ep(id: Int, series: Int) = Tile(key = "cw$id", kind = TileKind.EPISODE, id = id, seriesId = series, title = "S$series", posterUrl = "", backdropUrl = "", progress = 0.3f)

    @Test
    fun noTitleInTwoRows_exceptRankedAndProgressRows() {
        val p = PagePlanner()
        p.add(0, "continue", "Continue Watching", listOf(ep(100, 7), m(1)), RowStyle.LANDSCAPE, min = 1, exclusive = false)
        p.add(3, "top10", "Top 10", (1..10).map { m(it, "t") }, RowStyle.TOP10, min = 5, max = 10, exclusive = false)
        p.add(2, "picks", "Top Picks", (5..20).map { m(it, "p") } + s(7))
        p.add(4, "new", "New", (1..30).map { m(it, "n") })
        val rows = p.rows
        assertEquals(listOf("continue", "picks", "top10", "new"), rows.map { it.id })
        // Top 10 keeps its full ranking even though movie 1 is in Continue Watching.
        assertEquals((1..10).toList(), rows[2].tiles.map { it.id })
        // Picks skip the Top 10 and the show already in Continue Watching (episode = its show).
        assertEquals((11..20).toList(), rows[1].tiles.map { it.id })
        // New gets only what's left.
        assertEquals((21..30).toList(), rows[3].tiles.map { it.id })
    }

    @Test
    fun shortRowsAreDropped_andLeftoversKeepEverythingReachable() {
        val p = PagePlanner()
        p.add(1, "a", "A", (1..6).map { m(it) })
        assertNull("only 2 unclaimed titles left: dropped", p.add(2, "b", "B", (5..8).map { m(it) }))
        p.leftovers(99, "more", "More", (1..10).map { m(it) })
        val rows = p.rows
        assertEquals(listOf("a", "more"), rows.map { it.id })
        assertEquals((7..10).toList(), rows[1].tiles.map { it.id })
        val all = rows.flatMap { r -> r.tiles.map { PagePlanner.identity(it) } }
        assertEquals(all.size, all.toSet().size)
    }

    @Test
    fun duplicatesInsideOneCandidateListCollapse() {
        val p = PagePlanner()
        val row = p.add(1, "a", "A", listOf(m(1), m(1, "x"), s(2), ep(9, 2), m(3), m(4)), min = 1)!!
        assertEquals(listOf("m:1", "s:2", "m:3", "m:4"), row.tiles.map { PagePlanner.identity(it) })
        assertTrue(p.isClaimed(ep(55, 2)))
    }

    @Test
    fun smallLibrariesKeepShortRows() {
        val p = PagePlanner(librarySize = 6)
        assertTrue("min 4 becomes 1 for a 6-title library", p.add(1, "a", "A", listOf(m(1), m(2)), min = 4) != null)
        assertNull("Top 10 never shrinks", p.add(2, "top10", "Top 10", listOf(m(3, "t"), m(4, "t")), min = 5, exclusive = false))
    }

    @Test
    fun chartTitles() {
        val week = ChartDto(windowDays = 7, source = "plays", items = listOf(ChartItemDto(1, "movie", "A", 1)))
        assertEquals("Top 10 Movies on HomeFlix This Week",
            com.homeflix.tv.presentation.screens.feeds.Charts.title("Movies", week))
        assertEquals("Top 10 Shows on HomeFlix",
            com.homeflix.tv.presentation.screens.feeds.Charts.title("Shows", week.copy(source = "catalog")))
    }

    @Test
    fun favouriteGenresFirst() {
        val genres = listOf("Action" to listOf("action"), "Comedies" to listOf("comedy"), "Horror" to listOf("horror"))
        val ordered = com.homeflix.tv.presentation.screens.feeds.Taste.byTaste(genres, listOf("cast:X", "gen:Horror", "gen:Comedy")) { t, _ -> t }
        assertEquals(listOf("Horror", "Comedies", "Action"), ordered)
    }
}
