package com.homeflix.tv.e2e

import android.os.Looper
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.homeflix.tv.presentation.MainActivity
import com.homeflix.tv.util.ServerConfig
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import java.net.HttpURLConnection
import java.net.URL
import java.time.Duration

/**
 * Drives the REAL app (Hilt, navigation, ViewModels, repositories, player)
 * against a real HomeFlix backend given by -Dhomeflix.e2e.server=host:port.
 * Skipped when no server is configured.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@LooperMode(LooperMode.Mode.PAUSED)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-land-xhdpi")
class AppSmokeTest {
    @get:Rule val rule = createEmptyComposeRule()

    private val server: String? = System.getProperty("homeflix.e2e.server")

    private fun reachable(base: String) = runCatching {
        (URL("http://$base/api/genres").openConnection() as HttpURLConnection).run { connectTimeout = 1500; responseCode }
    }.isSuccess

    private fun pump(ms: Long) {
        val end = System.currentTimeMillis() + ms
        while (System.currentTimeMillis() < end) {
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(50))
            rule.mainClock.advanceTimeBy(50)
            Thread.sleep(20)
        }
    }

    private fun waitForText(text: String, timeoutMs: Long = 15_000): Boolean {
        val end = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < end) {
            pump(150)
            if (rule.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()) return true
        }
        return false
    }

    private fun focused(): String {
        val n = rule.onAllNodes(androidx.compose.ui.test.isFocused()).fetchSemanticsNodes().firstOrNull() ?: return "<none>"
        val c = n.config
        return if (c.contains(androidx.compose.ui.semantics.SemanticsProperties.Text))
            c[androidx.compose.ui.semantics.SemanticsProperties.Text].joinToString() else "node#${n.id}"
    }

    private fun press(key: Key, times: Int = 1) = repeat(times) {
        rule.onRoot().performKeyInput { pressKey(key) }
        pump(350)
        println("E2E key=$key -> focus=${focused()}")
    }

    private fun dump(tag: String) {
        val texts = rule.onAllNodesWithText("", substring = true).fetchSemanticsNodes()
            .mapNotNull { n ->
                if (n.config.contains(androidx.compose.ui.semantics.SemanticsProperties.Text))
                    n.config[androidx.compose.ui.semantics.SemanticsProperties.Text].joinToString() else null
            }
        println("E2E[$tag] ${texts.take(25)}")
    }

    @Test
    fun browseTabs_openMovie_play_back_search() {
        val base = server
        assumeTrue("set -Dhomeflix.e2e.server", base != null && reachable(base))
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        pump(3_000) // let the app's boot-time server probe finish first
        ServerConfig.setLocal(ctx, base!!)

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            fun back() { scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }; pump(1_200); println("E2E back -> focus=${focused()}") }
            check(waitForText("Inception")) { "home never loaded" }
            pump(1_000)
            dump("home")
            println("E2E initial focus=${focused()}")

            // Menu: up into the nav, across every tab, letting each page load.
            press(Key.DirectionUp)
            for (i in 0 until 3) { press(Key.DirectionRight); pump(2_500); dump("tab+$i") }
            press(Key.DirectionLeft, 3) // back to Home
            pump(1_500)
            press(Key.DirectionDown)    // into the billboard
            dump("home-again")

            // Continue Watching plays directly (fake bytes → a clean error, never a crash).
            press(Key.DirectionDown)
            press(Key.Enter)
            pump(4_000)
            dump("player-from-cw")
            back()
            pump(1_500)

            // A movie page: More Info on the billboard.
            press(Key.DirectionUp)        // back to the billboard
            press(Key.DirectionRight)     // More Info
            press(Key.Enter)
            check(waitForText("More Like This") || waitForText("Resume") || waitForText("Play")) { "details didn't show" }
            dump("details")
            press(Key.Enter)              // Resume / Play
            pump(4_000)
            dump("player")
            back()               // leave player
            pump(1_500)
            dump("details-again")
            back()               // leave details
            pump(1_500)
            dump("home-after-back")

            // Shows tab → open the series → episodes.
            back() // focus nav
            press(Key.DirectionRight); pump(1_500)
            press(Key.DirectionDown); press(Key.Enter); pump(3_000)
            dump("series")

            // Search tab, type.
            back(); back(); pump(1_000)
            press(Key.DirectionLeft, 3); pump(1_500)
            press(Key.DirectionDown); press(Key.H); press(Key.E); pump(1_500)
            dump("search")
        }
    }
}
