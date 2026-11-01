package com.homeflix.tv.ui

import com.homeflix.tv.domain.model.Media
import com.homeflix.tv.domain.model.MediaType
import com.homeflix.tv.presentation.screens.feeds.Rows
import com.homeflix.tv.presentation.screens.tvshows.TvSeries
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Date

class HeroPickTest {
    private fun movie(id: Int, year: Int, addedDaysAgo: Int) = Media(
        id = id, uuid = "u$id", title = "Movie $id", type = MediaType.MOVIE, filePath = "", fileSize = 0, duration = 5400,
        year = year, createdAt = Date(System.currentTimeMillis() - addedDaysAgo * 86_400_000L)
    )
    private fun show(id: Int, year: Int, added: String) = TvSeries(
        id = id, title = "Show $id", description = null, rating = 0.0, year = year, totalSeasons = 1,
        totalEpisodes = 3, genres = emptyList(), posterPath = null, bannerPath = null, createdAt = added
    )

    @Test
    fun picksThisYearsNewestAdditions_moviesAndShows() {
        val movies = listOf(movie(1, 2026, 1), movie(2, 2025, 0), movie(3, 2026, 10), movie(4, 2026, 3))
        val shows = listOf(show(10, 2026, java.time.Instant.now().minusSeconds(2 * 86_400).toString()), show(11, 2024, "2026-01-01T00:00:00Z"))
        val heroes = Rows.heroThisYear(movies, shows, year = 2026)
        // 2026 only, newest addition first: movie 1 (1d), show 10 (2d), movie 4 (3d), movie 3 (10d)
        assertEquals(listOf("Movie 1", "Show 10", "Movie 4", "Movie 3"), heroes.map { it.title })
    }

    @Test
    fun topsUpWhenThisYearIsThin() {
        val movies = listOf(movie(1, 2026, 5), movie(2, 2025, 1), movie(3, 2019, 0))
        val heroes = Rows.heroThisYear(movies, emptyList(), year = 2026)
        assertEquals(listOf("Movie 1", "Movie 2", "Movie 3"), heroes.map { it.title })
    }
}
