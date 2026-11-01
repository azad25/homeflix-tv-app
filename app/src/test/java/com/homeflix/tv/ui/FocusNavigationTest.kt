package com.homeflix.tv.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.media3.common.util.UnstableApi
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.homeflix.tv.core.PreviewPlayer
import com.homeflix.tv.presentation.screens.feeds.FeedState
import com.homeflix.tv.presentation.shell.AppShell
import com.homeflix.tv.presentation.shell.FeedPage
import com.homeflix.tv.presentation.shell.ShellTab
import com.homeflix.tv.presentation.ui.BrowseFeed
import com.homeflix.tv.presentation.ui.ContentRow
import com.homeflix.tv.presentation.ui.Feed
import com.homeflix.tv.presentation.ui.Tile
import com.homeflix.tv.presentation.ui.TileActions
import com.homeflix.tv.presentation.ui.TileKind
import com.homeflix.tv.presentation.ui.rememberFocusRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Drives the real browse UI with D-pad key presses on a 1080p TV-sized screen
 * and checks that focus always lands where Netflix would put it.
 */
@OptIn(ExperimentalTestApi::class)
@UnstableApi
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-land-xhdpi")
class FocusNavigationTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private fun tile(i: Int, prefix: String = "") = Tile(
        key = "$prefix$i", kind = TileKind.MOVIE, id = i, title = "$prefix Title $i",
        posterUrl = "", backdropUrl = "", meta = "Movie  •  Drama", description = "Synopsis $i"
    )

    private fun feed(heroTitle: String, rows: Int = 4, perRow: Int = 40, prefix: String = "") = Feed(
        hero = tile(999, prefix).copy(title = heroTitle, key = "hero"),
        rows = (0 until rows).map { r -> ContentRow("r$r", "Row $r", (0 until perRow).map { tile(it, prefix) }) }
    )

    private val noActions = object : TileActions {
        override fun open(tile: Tile) {}
        override fun play(tile: Tile) {}
    }

    private fun press(key: Key, times: Int = 1) {
        repeat(times) {
            rule.onRoot().performKeyInput { pressKey(key) }
            rule.waitForIdle()
        }
    }

    @Test
    fun rowsNeverTrapFocus_andRememberColumn() {
        val focused = mutableListOf<String>()
        val heroPlay = FocusRequester()
        var section = -1
        lateinit var list: LazyListState
        rule.setContent {
            list = rememberLazyListState()
            val registry = rememberFocusRegistry()
            var active by remember { mutableIntStateOf(0) }
            BrowseFeed(
                feed = feed("Hero"),
                listState = list,
                registry = registry,
                heroPlayRequester = heroPlay,
                activeSection = active,
                onSectionFocused = { active = it; section = it },
                onTileFocused = { focused += it },
                actions = noActions
            )
        }
        rule.runOnIdle { heroPlay.requestFocus() }
        rule.waitForIdle()
        assertEquals(0, section)

        press(Key.DirectionDown)
        assertEquals(1, section)
        assertEquals("r0|0", focused.last())

        // The old MediaRow lost focus after item 20; walk well past it.
        press(Key.DirectionRight, 30)
        assertEquals("r0|30", focused.last())

        press(Key.DirectionDown)
        assertEquals(2, section)
        assertEquals("entering a row lands on its remembered (pinned) tile", "r1|0", focused.last())
        // Section 2 glides to the top keyline.
        assertEquals(2, list.firstVisibleItemIndex)

        press(Key.DirectionUp)
        assertEquals(1, section)
        assertEquals("row 0 restores its column", "r0|30", focused.last())

        press(Key.DirectionRight, 15)                 // run off the end of the row
        assertEquals("r0|39", focused.last())        // last tile keeps focus, no wrap
        press(Key.DirectionLeft, 9)
        assertEquals("r0|30", focused.last())
        press(Key.DirectionLeft, 30)
        assertEquals("r0|0", focused.last())
        press(Key.DirectionLeft) // first tile: no wrap, focus stays
        assertEquals("r0|0", focused.last())

        press(Key.DirectionUp)
        assertEquals(0, section)
        assertEquals(0, list.firstVisibleItemIndex)

        // Deep dive and back up through every row.
        press(Key.DirectionDown, 4)
        assertEquals(4, section)
        press(Key.DirectionUp, 4)
        assertEquals(0, section)
    }

    @Test
    fun topNav_backChain_andTabSwitch() {
        val preview = PreviewPlayer(rule.activity.applicationContext)
        val home = FeedState(feed("Home Hero", prefix = "h"), loading = false)
        val shows = FeedState(feed("Shows Hero", prefix = "s"), loading = false)
        rule.setContent {
            AppShell(previewPlayer = preview, actions = noActions, onOpenNotifications = {}) { tab, shell ->
                when (tab) {
                    ShellTab.SHOWS -> FeedPage(shows, shell, noActions, onRetry = {}, onResumed = {})
                    else -> FeedPage(home, shell, noActions, onRetry = {}, onResumed = {})
                }
            }
        }
        rule.waitForIdle()
        // Launch: focus starts on the billboard's Play button.
        rule.onNodeWithText("Play").assertIsFocused()

        // Up from the billboard reaches the top nav on the selected tab.
        press(Key.DirectionUp)
        rule.onNodeWithText("Home").assertIsFocused()

        // Resting on "Shows" switches the page (Netflix-style, debounced).
        press(Key.DirectionRight)
        rule.onNodeWithText("Shows").assertIsFocused()
        rule.mainClock.advanceTimeBy(600)
        rule.waitForIdle()
        rule.onNodeWithText("Shows Hero").assertExists()

        // Into the page, deep down, then Back returns to the nav.
        press(Key.DirectionDown)
        rule.onNodeWithText("Play").assertIsFocused()
        press(Key.DirectionDown, 3)
        back()
        rule.onNodeWithText("Shows").assertIsFocused()

        // Back again: Home tab; once more: exit.
        back()
        rule.onNodeWithText("Home").assertIsFocused()
        rule.onNodeWithText("Home Hero").assertExists()
        back()
        assertTrue(rule.activity.isFinishing)
    }

    @Test
    fun returningFromAScreen_restoresTheExactTile() {
        val preview = PreviewPlayer(rule.activity.applicationContext)
        val home = FeedState(feed("Home Hero", prefix = "h"), loading = false)
        var away by androidx.compose.runtime.mutableStateOf(false)
        val opened = mutableListOf<String>()
        val actions = object : TileActions {
            override fun open(tile: Tile) { opened += tile.key; away = true }
            override fun play(tile: Tile) {}
        }
        rule.setContent {
            // Same save/restore mechanics as a NavHost back stack entry.
            val holder = androidx.compose.runtime.saveable.rememberSaveableStateHolder()
            if (away) {
                androidx.compose.material3.Text("Details")
            } else {
                holder.SaveableStateProvider("browse") {
                    AppShell(previewPlayer = preview, actions = actions, onOpenNotifications = {}) { _, shell ->
                        FeedPage(home, shell, actions, onRetry = {}, onResumed = {})
                    }
                }
            }
        }
        rule.waitForIdle()
        press(Key.DirectionDown, 2)          // row 1
        press(Key.DirectionRight, 7)         // tile 7
        press(Key.Enter)
        assertEquals(listOf("h7"), opened)
        rule.onNodeWithText("Details").assertExists()

        rule.runOnIdle { away = false }      // "Back" from details
        rule.waitForIdle()
        rule.mainClock.advanceTimeBy(500)
        rule.waitForIdle()
        press(Key.Enter)                     // OK opens whatever is focused now
        assertEquals("focus must return to the same tile", listOf("h7", "h7"), opened)
    }

    private fun back() {
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.waitForIdle()
        rule.mainClock.advanceTimeBy(100)
        rule.waitForIdle()
    }
}
