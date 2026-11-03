package com.homeflix.tv.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.homeflix.tv.core.rememberPreviewUrl
import com.homeflix.tv.presentation.ui.Tile
import com.homeflix.tv.presentation.ui.TileKind
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The billboard reuses one slot for every title: each title must get its own clip. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class PreviewUrlTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private fun movie(id: Int, clip: String?) = Tile(key = "m$id", kind = TileKind.MOVIE, id = id, title = "M$id",
        posterUrl = "", backdropUrl = "", previewUrl = clip)

    @Test
    fun switchingTitlesInOneSlotNeverKeepsTheOldClip() {
        var tile by mutableStateOf(movie(1, "http://s/1.mp4"))
        var seen: String? = "unset"
        rule.setContent { seen = rememberPreviewUrl(tile, active = true).value }
        rule.waitForIdle()
        assertEquals("http://s/1.mp4", seen)
        tile = movie(2, "http://s/2.mp4"); rule.waitForIdle()
        assertEquals("http://s/2.mp4", seen)
        tile = movie(3, null); rule.waitForIdle()
        assertEquals("a title without a clip shows none, not the previous one", null, seen)
    }
}
