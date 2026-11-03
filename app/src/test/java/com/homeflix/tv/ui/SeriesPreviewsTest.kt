package com.homeflix.tv.ui

import com.homeflix.tv.core.SeriesPreviews
import com.homeflix.tv.data.remote.dto.MediaDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SeriesPreviewsTest {
    private fun ep(id: Int, created: String, s: Int, e: Int, clip: String? = null) =
        MediaDto(id = id, uuid = "u$id", title = "Ep $id", type = "episode", filePath = "", fileSize = 0, duration = 0, createdAt = created, seasonNumber = s, episodeNumber = e, previewClipPath = clip)

    @Test
    fun newestUploadWithAPreviewWins_elseNewestUpload() {
        val eps = listOf(
            ep(1, "2026-01-01T00:00:00Z", 1, 1, clip = "/c/1.mp4"),
            ep(7, "2026-10-01T00:00:00Z", 2, 3, clip = "/c/7.mp4"),
            ep(8, "2026-11-01T00:00:00Z", 2, 4) // newest, but no preview yet
        )
        assertEquals(7, SeriesPreviews.pick(eps))
        assertEquals("no previews at all: the newest upload", 8, SeriesPreviews.pick(eps.map { it.copy(previewClipPath = null) }))
        assertNull(SeriesPreviews.pick(emptyList()))
    }

    @Test
    fun anotherShowsEpisodeIsNeverPicked() {
        // Older servers return the episode whose media id equals the series id, even from another show.
        val stray = ep(10, "2026-12-01T00:00:00Z", 1, 1, clip = "/c/10.mp4").copy(seriesId = 99)
        val own = ep(20, "2026-01-01T00:00:00Z", 1, 1, clip = "/c/20.mp4").copy(seriesId = 10)
        assertEquals(20, SeriesPreviews.pick(listOf(stray, own).filter { it.seriesId == null || it.seriesId == 10 }))
    }
}
