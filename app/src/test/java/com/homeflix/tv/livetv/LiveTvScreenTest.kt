package com.homeflix.tv.livetv

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The Live TV screen with real remote keys (fake server and player). */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-land-xhdpi")
class LiveTvScreenTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val lineup = (1..15).map { LiveChannel(id = "ch$it", name = "Channel $it", country = "BD", categories = listOf("news"), quality = "720p", number = it) }
    private var exited = false
    private lateinit var controller: LiveTvController

    private val source = object : LiveSource {
        override suspend fun play(id: String) = LivePlay(urls = listOf("http://s/$id.m3u8"))
        override fun prefetch(ids: List<String>) {}
    }
    private val memory = object : LiveMemory {
        val favs = MutableStateFlow<Set<String>>(emptySet())
        override val favorites = favs
        override suspend fun favoritesNow() = favs.value
        override suspend fun toggleFavorite(id: String) { favs.value = favs.value + id }
        override suspend fun lastAndPrevious() = "ch3" to null
        override suspend fun recents() = emptyList<String>()
        override suspend fun recordTune(id: String) {}
    }
    private val playback = object : LivePlayback {
        override fun play(url: String) {}
        override fun restartAtLiveEdge() {}
        override fun stop() {}
        override fun setMuted(muted: Boolean) {}
    }

    private fun press(k: Key, times: Int = 1) = repeat(times) {
        rule.onNodeWithTag("livetv").performKeyInput { pressKey(k) }
        rule.mainClock.advanceTimeBy(100)
        rule.waitForIdle()
    }

    private fun shows(tag: String) = rule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
    private fun text(t: String) = rule.onAllNodesWithText(t, substring = true).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun cableBoxFlow() {
        rule.setContent {
            val scope = rememberCoroutineScope()
            controller = androidx.compose.runtime.remember { LiveTvController(scope, source, memory, playback).also { it.start(lineup, null) } }
            val state by controller.state.collectAsState()
            LiveTvContent(state, controller, onExit = { exited = true }) { m -> Box(m.background(Color.DarkGray).testTag("video")) }
        }
        rule.waitForIdle()
        // Starts on the last channel: tuning screen + banner with number, name, LIVE.
        assertTrue(shows("live_tuning"))
        assertTrue(shows("live_banner"))
        assertTrue(text("Channel 3"))
        assertTrue(text("LIVE"))
        assertTrue(text("News  ·  720p  ·  Bangladesh"))
        rule.runOnIdle { controller.onFirstFrame() }
        rule.mainClock.advanceTimeBy(3_500); rule.waitForIdle()
        assertTrue("video only after the banner", !shows("live_banner") && !shows("live_tuning"))

        // ▼ zaps: banner shows the next channel at once.
        press(Key.DirectionDown)
        assertTrue(text("Channel 4"))
        rule.mainClock.advanceTimeBy(500); rule.waitForIdle()
        assertEquals("ch4", controller.state.value.current?.id)

        // Number entry: "1 2 _" top-right, tunes after 1.2 s.
        press(Key.One); press(Key.Two)
        assertTrue(shows("live_digits"))
        assertTrue(text("1 2 _"))
        rule.mainClock.advanceTimeBy(1_300); rule.waitForIdle()
        assertEquals("ch12", controller.state.value.current?.id)

        // OK: mini guide on the current channel with ● NOW; ▲ moves, OK tunes.
        press(Key.DirectionCenter)
        assertTrue(shows("live_mini_guide"))
        assertTrue(text("NOW"))
        press(Key.DirectionUp)
        press(Key.DirectionCenter)
        assertTrue(!shows("live_mini_guide"))
        assertEquals("ch11", controller.state.value.current?.id)

        // ◀: full guide with groups; Back closes it.
        press(Key.DirectionLeft)
        assertTrue(shows("live_guide"))
        assertTrue(text("All channels"))
        assertTrue(text("Bangladesh"))
        press(Key.Back)
        assertTrue(!shows("live_guide"))

        // ▶: info with the favorite toggle.
        press(Key.DirectionRight)
        assertTrue(text("Add to Favorites"))
        press(Key.DirectionCenter)
        assertTrue(text("In Favorites"))
        press(Key.Back)

        // Back: the last channel, then Back again quickly leaves.
        rule.mainClock.advanceTimeBy(2_000)
        press(Key.Back)
        assertEquals("ch12", controller.state.value.current?.id)
        assertTrue(text("Press Back again"))
        press(Key.Back)
        assertTrue(exited)
    }
}
