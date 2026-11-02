package com.homeflix.tv.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.Dp
import androidx.media3.common.util.UnstableApi
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.homeflix.tv.presentation.ui.ContentRow
import com.homeflix.tv.presentation.ui.FocusRegistry
import com.homeflix.tv.presentation.ui.Tile
import com.homeflix.tv.presentation.ui.TileActions
import com.homeflix.tv.presentation.ui.TileKind
import com.homeflix.tv.presentation.ui.TileRow
import com.homeflix.tv.presentation.ui.Tv
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Left/Right along a row must be one clean slide: the next tile moves straight
 * to the keyline — never past it and back — and nothing moves once it's there.
 */
@OptIn(ExperimentalTestApi::class)
@UnstableApi
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-land-xhdpi")
class RowMotionTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val registry = FocusRegistry()
    private val row = ContentRow("r0", "Row", (0 until 20).map {
        Tile(key = "$it", kind = TileKind.MOVIE, id = it, title = "Title $it", posterUrl = "", backdropUrl = "", meta = "Movie")
    })

    private fun left(key: String): Dp = rule.onAllNodesWithTag("tile:r0|$key")[0].getBoundsInRoot().left

    /** Press [key] and record [watch]'s left edge every frame for [ms]. */
    private fun pressAndTrack(key: Key, watch: String, ms: Long = 600): List<Float> {
        rule.onRoot().performKeyInput { pressKey(key) }
        val xs = mutableListOf<Float>()
        var t = 0L
        while (t < ms) { rule.mainClock.advanceTimeByFrame(); t += 16; xs += left(watch).value }
        return xs
    }

    private fun assertOneWay(xs: List<Float>, decreasing: Boolean) {
        for (i in 1 until xs.size) {
            val d = xs[i] - xs[i - 1]
            assertTrue("row swung back at frame $i: $xs", if (decreasing) d <= 0.6f else d >= -0.6f)
        }
    }

    @Test
    fun rightAndLeft_slideOnce_withoutSwingingBack() {
        rule.setContent {
            TileRow(row = row, active = true, registry = registry, actions = object : TileActions {
                override fun open(tile: Tile) {}
                override fun play(tile: Tile) {}
            }, onTileFocused = {})
        }
        rule.mainClock.autoAdvance = false
        rule.runOnIdle { registry.tryFocus("r0|0") }
        rule.mainClock.advanceTimeBy(2_000) // rest: tile 0 expands
        val keyline = Tv.Keyline.value

        // Leaving an expanded tile: the next one moves left, once, to the keyline.
        val right = pressAndTrack(Key.DirectionRight, "1")
        assertOneWay(right, decreasing = true)
        assertEquals("tile 1 should end on the keyline: $right", keyline, right.last(), 1f)
        // Resting on it: it widens to the right; its left edge never moves.
        rule.mainClock.advanceTimeBy(2_000)
        assertEquals(keyline, left("1").value, 1f)

        // Quick presses: each tile arrives at the keyline in one motion.
        for (i in 2..5) {
            val xs = pressAndTrack(Key.DirectionRight, "$i", 320)
            assertOneWay(xs, decreasing = true)
        }
        rule.mainClock.advanceTimeBy(2_000)
        assertEquals(keyline, left("5").value, 1f)

        val back = pressAndTrack(Key.DirectionLeft, "4")
        assertOneWay(back, decreasing = false)
        assertEquals(keyline, back.last(), 1f)
    }
}
