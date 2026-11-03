package com.homeflix.tv.comingsoon

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-land-xhdpi")
class ComingSoonScreenTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private var exited = false
    private lateinit var c: ComingSoonController

    private val list = listOf(
        ComingSoonItem(tmdbId = 1, mediaType = "movie", title = "Big Movie", releaseDate = "2027-02-12", daysUntil = 101,
            studio = "Studio A", genres = listOf("Action"), cast = listOf("Star One", "Star Two"), overview = "An overview.", rank = 1,
            trailer = ComingSoonTrailer("ready", "/v/1.mp4")),
        ComingSoonItem(tmdbId = 2, mediaType = "tv", title = "New Show", releaseLabel = "Season 3", releaseDate = "2026-12-01", daysUntil = 28, rank = 1,
            trailer = ComingSoonTrailer("pending"))
    )
    private val fake = object : ComingSoonSource, TrailerPlayback {
        override suspend fun items() = list
        override suspend fun title(mediaType: String, tmdbId: Int) = list.first { it.tmdbId == tmdbId }.copy(notify = ComingSoonNotify(true, "scheduled"))
        override suspend fun setNotify(item: ComingSoonItem, on: Boolean) = Result.success(NotifyResult(on, "scheduled"))
        override fun trailerUrl(item: ComingSoonItem) = item.trailer?.videoUrl
        override fun play(url: String) {}
        override fun stop() {}
        override fun pause() {}
        override fun resume() {}
    }

    private fun press(k: Key) { rule.onNodeWithTag("comingsoon").performKeyInput { pressKey(k) }; rule.mainClock.advanceTimeBy(150); rule.waitForIdle() }
    private fun text(t: String) = rule.onAllNodesWithText(t, substring = true).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun overlayButtonsAndRemote() {
        rule.setContent {
            val scope = rememberCoroutineScope()
            c = remember { ComingSoonController(scope, fake, fake).also { it.start(null) } }
            val st by c.state.collectAsState()
            ComingSoonContent(st, c, onExit = { exited = true }) { m -> Box(m.background(Color.DarkGray)) }
        }
        rule.waitForIdle()
        assertTrue(text("Big Movie"))
        assertTrue(text("Friday, February 12, 2027"))
        assertTrue(text("In 101 days"))
        assertTrue(text("Studio A  ·  Action"))
        assertTrue(text("Starring Star One, Star Two"))
        assertTrue(text("Notify Me"))
        press(Key.DirectionCenter)
        rule.mainClock.advanceTimeBy(300); rule.waitForIdle()
        assertTrue(text("We'll Notify You"))
        assertTrue(text("scheduled"))
        press(Key.DirectionRight)
        assertTrue(text("New Show"))
        assertTrue(text("Season 3"))
        assertTrue(text("Trailer coming soon"))
        press(Key.Back)
        assertTrue(rule.onAllNodesWithTag("cs_overlay").fetchSemanticsNodes().isEmpty())
        press(Key.Back)
        assertTrue(exited)
        assertEquals(1, c.state.value.index)
    }
}
