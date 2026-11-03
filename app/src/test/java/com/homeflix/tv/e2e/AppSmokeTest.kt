package com.homeflix.tv.e2e

import android.os.Looper
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.onLast
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
        // With a dialog open each window keeps its own focus; the dialog's (last root) is the live one.
        val n = rule.onAllNodes(androidx.compose.ui.test.isFocused()).fetchSemanticsNodes().lastOrNull() ?: return "<none>"
        val c = n.config
        return if (c.contains(androidx.compose.ui.semantics.SemanticsProperties.Text))
            c[androidx.compose.ui.semantics.SemanticsProperties.Text].joinToString()
        else if (c.contains(androidx.compose.ui.semantics.SemanticsProperties.ContentDescription))
            c[androidx.compose.ui.semantics.SemanticsProperties.ContentDescription].joinToString()
        else "node#${n.id}"
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

            // Bell on the nav → Notifications; profile menu → About (developer credit).
            back()                                   // focus the nav (Search tab)
            // Home, Shows, Movies, My HomeFlix, (Live TV), bell: find the bell by name.
            var steps = 0
            while (focused() != "Notifications" && steps++ < 8) press(Key.DirectionRight)
            println("E2E on bell: ${focused()}")
            press(Key.Enter); pump(2_500)
            dump("notifications")
            back(); pump(1_000)
            back()                                   // nav again
            steps = 0
            while (focused() != "Profile" && steps++ < 9) press(Key.DirectionRight)
            press(Key.Enter); pump(800)              // menu: Notifications, Settings, About…
            press(Key.DirectionDown, 2)
            press(Key.Enter); pump(1_500)
            dump("about")
            check(waitForText("Ferdous Azad")) { "About screen didn't show the developer credit" }
            check(waitForText("ferdousazad12@gmail.com")) { "About screen didn't show the contact" }
            check(waitForText("buymeacoffee.com/ferdousazad")) { "About screen didn't show the donation link" }
        }
    }

    @Test
    fun moviePage_hasTheTopMenu_andTabsWork() {
        val base = server
        assumeTrue("set -Dhomeflix.e2e.server", base != null && reachable(base))
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        pump(3_000)
        ServerConfig.setLocal(ctx, base!!)
        ActivityScenario.launch(MainActivity::class.java).use {
            check(waitForText("Inception")) { "home never loaded" }
            pump(1_000)
            // Home's billboard leads with this year's newest addition (a show in the
            // test data), so open a movie from the Movies tab's billboard instead.
            check(waitForText("Shaque")) { "Home billboard should lead with this year's newest addition" }
            press(Key.DirectionUp)            // nav: Home
            press(Key.DirectionRight, 2)      // Shows, Movies (switches on rest)
            pump(2_500)
            press(Key.DirectionDown)          // Movies billboard: Play
            press(Key.DirectionRight)         // More Info
            press(Key.Enter)
            check(waitForText("More Like This")) { "movie page didn't open" }
            dump("movie-page")
            press(Key.DirectionUp)            // from Resume/Play into the top bar
            println("E2E sub-page nav focus=${focused()}")
            check(focused() == "Home") { "▲ from the movie page didn't reach the top menu (focus=${focused()})" }
            press(Key.DirectionRight, 2)      // Shows, Movies
            check(focused() == "Movies") { "focus=${focused()}" }
            press(Key.Enter)
            pump(2_500)
            dump("after-tab")
            // Movies' Continue Watching has only movies; Home's also has the show "Shaque".
            check(waitForText("54m left")) { "Movies page didn't load" }
            check(rule.onAllNodesWithText("Shaque", substring = true).fetchSemanticsNodes().isEmpty()) {
                "still on Home instead of the Movies tab"
            }
        }
    }

    @Test
    fun settings_everyRowReachable_checkForUpdates_andEditDialog() {
        val base = server
        assumeTrue("set -Dhomeflix.e2e.server", base != null && reachable(base))
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        pump(3_000)
        ServerConfig.setLocal(ctx, base!!)
        // Keys go to whatever holds focus, so they also reach dialog windows.
        fun key(k: Key, times: Int = 1) = repeat(times) {
            rule.onAllNodes(androidx.compose.ui.test.isFocused()).onLast().performKeyInput { pressKey(k) }
            pump(350)
            println("E2E key=$k -> focus=${focused()}")
        }
        ActivityScenario.launch(MainActivity::class.java).use {
            check(waitForText("Inception")) { "home never loaded" }
            pump(1_000)
            press(Key.DirectionUp)                 // nav: Home
            var guard = 0
            while (!focused().contains("Profile", ignoreCase = true) && guard++ < 8) press(Key.DirectionRight)
            press(Key.Enter)                        // profile menu: Notifications
            check(waitForText("About HomeFlix")) { "profile menu didn't open" }
            press(Key.DirectionDown)                // Settings
            check(focused() == "Settings") { "focus=${focused()}" }
            press(Key.Enter)
            check(waitForText("Check for updates")) { "settings didn't open" }
            pump(800)
            val seen = mutableListOf<String>()
            guard = 0
            while (!focused().contains("Check for updates") && guard++ < 14) { key(Key.DirectionDown); seen += focused() }
            println("E2E settings rows: $seen")
            check(focused().contains("Check for updates")) { "couldn't reach Check for updates: $seen" }
            key(Key.Enter)
            check(waitForText("up to date") || waitForText("Couldn't") || waitForText("ready to install")) { "check didn't run" }
            // Back up to the server address and edit it in the dialog.
            guard = 0
            while (!focused().contains("Server address") && guard++ < 10) key(Key.DirectionUp)
            check(focused().contains("Server address")) { "focus=${focused()}" }
            key(Key.Enter)
            pump(600)
            key(Key.DirectionDown)                  // field -> Save
            check(focused() == "Save") { "▼ in the edit dialog should reach Save (focus=${focused()})" }
            key(Key.DirectionRight)
            check(focused() == "Cancel") { "focus=${focused()}" }
            key(Key.Enter)
            pump(600)
            check(focused().contains("Server address")) { "Cancel should return to the row (focus=${focused()})" }
        }
    }

    @Test
    fun homeScreenCard_opensTheTitle() {
        val base = server
        assumeTrue("set -Dhomeflix.e2e.server", base != null && reachable(base))
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        pump(3_000)
        ServerConfig.setLocal(ctx, base!!)
        // What the launcher sends when a "HomeFlix" row card is pressed.
        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, com.homeflix.tv.launcher.HomeLink.Movie(2).toUri())
            .setClass(ctx, MainActivity::class.java)
        ActivityScenario.launch<MainActivity>(intent).use { scenario ->
            check(waitForText("More Like This")) { "card didn't open the movie page" }
            check(waitForText("Heat")) { "opened the wrong title" }
            // Esc on a keyboard works like Back: to Home, not out of the app.
            scenario.onActivity {
                it.dispatchKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_ESCAPE))
                it.dispatchKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, android.view.KeyEvent.KEYCODE_ESCAPE))
            }
            check(waitForText("Inception")) { "Esc from a home-screen card should land on Home" }
        }
    }

    private fun http(method: String, url: String, body: String? = null): String =
        (URL(url).openConnection() as HttpURLConnection).run {
            requestMethod = method
            if (body != null) { doOutput = true; setRequestProperty("Content-Type", "application/json"); outputStream.use { it.write(body.toByteArray()) } }
            (if (responseCode < 400) inputStream else errorStream).bufferedReader().readText()
        }

    @Test
    fun moviePage_myList_andRate_reachTheServer() {
        val base = server
        assumeTrue("set -Dhomeflix.e2e.server", base != null && reachable(base))
        val api = "http://$base/api"
        http("PUT", "$api/ratings/movie/2", """{"rating":0}""")
        http("DELETE", "$api/mylist/2")
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        pump(3_000)
        ServerConfig.setLocal(ctx, base!!)
        fun key(k: Key, times: Int = 1) = repeat(times) {
            rule.onAllNodes(androidx.compose.ui.test.isFocused()).onLast().performKeyInput { pressKey(k) }
            pump(400)
            println("E2E key=$k -> focus=${focused()}")
        }
        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, com.homeflix.tv.launcher.HomeLink.Movie(2).toUri())
            .setClass(ctx, MainActivity::class.java)
        try {
            ActivityScenario.launch<MainActivity>(intent).use {
                check(waitForText("More Like This")) { "movie page didn't open" }
                check(waitForText("Rate")) { "no Rate button" }
                pump(800)
                key(Key.DirectionRight)                      // My List
                check(focused() == "My List") { "focus=${focused()}" }
                key(Key.Enter)
                check(waitForText("In My List")) { "My List didn't toggle" }
                key(Key.DirectionRight)                      // Rate
                check(focused() == "Rate") { "focus=${focused()}" }
                key(Key.Enter)                               // dialog: focus on "I like this"
                pump(600)
                key(Key.DirectionRight)                      // Love this!
                key(Key.Enter)
                check(waitForText("Loved")) { "rating didn't apply" }
                pump(1_500)
                val rating = http("GET", "$api/ratings/movie/2")
                check(rating.contains("\"rating\":2")) { "server rating: $rating" }
                val listed = http("GET", "$api/mylist/check/2")
                check(listed.contains("true")) { "server My List: $listed" }
            }
        } finally {
            http("PUT", "$api/ratings/movie/2", """{"rating":0}""")
            http("DELETE", "$api/mylist/2")
        }
    }

    /** Home, Movies and Shows never show a title in two rows (Continue Watching, Top 10 and My List aside). */
    @Test
    fun pages_haveNoDuplicateTitles_andARealTop10() {
        val base = server
        assumeTrue("set -Dhomeflix.e2e.server", base != null && reachable(base))
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        pump(3_000)
        ServerConfig.setLocal(ctx, base!!)
        listOf("home", "movies", "shows").forEach { java.io.File(ctx.filesDir, "feed_v5_$it.json").delete() }
        ActivityScenario.launch(MainActivity::class.java).use {
            check(waitForText("Inception")) { "home never loaded" }
            pump(2_000)
            press(Key.DirectionUp)            // nav: Home
            press(Key.DirectionRight); pump(3_000)   // Shows
            press(Key.DirectionRight); pump(3_000)   // Movies
        }
        val gson = com.google.gson.Gson()
        val exempt = Regex("continue|top10|mylist")
        for (page in listOf("home", "movies", "shows")) {
            val f = java.io.File(ctx.filesDir, "feed_v5_$page.json")
            check(f.exists()) { "$page feed was never built" }
            val feed = gson.fromJson(f.readText(), com.homeflix.tv.presentation.ui.Feed::class.java)
            println("E2E[$page rows] " + feed.rows.joinToString { "${it.title} (${it.tiles.size})" })
            val owner = HashMap<String, String>()
            for (row in feed.rows.filter { !exempt.containsMatchIn(it.id) }) {
                for (t in row.tiles) {
                    val k = com.homeflix.tv.presentation.screens.feeds.PagePlanner.identity(t)
                    val prev = owner.put(k, row.title)
                    check(prev == null) { "$page: ${t.title} is in both \"$prev\" and \"${row.title}\"" }
                }
            }
            val ranked = feed.rows.filter { it.id.contains("top10") }.flatMap { it.tiles }
            for (row in feed.rows.filter { !exempt.containsMatchIn(it.id) }) {
                check(row.tiles.none { t -> ranked.any { r -> com.homeflix.tv.presentation.screens.feeds.PagePlanner.identity(r) == com.homeflix.tv.presentation.screens.feeds.PagePlanner.identity(t) } }) {
                    "$page: \"${row.title}\" repeats a Top 10 title"
                }
            }
            ranked.forEachIndexed { i, t -> check(t.rank == i + 1) { "$page: Top 10 ranks out of order" } }
        }
    }

    /** Live TV against the real server: menu entry, Home row, banner, zapping, Back. */
    @Test
    fun liveTv_menuRow_banner_zap_back() {
        val base = server
        assumeTrue("set -Dhomeflix.e2e.server", base != null && reachable(base))
        val lineup = runCatching { URL("http://$base/api/livetv/channels").readText() }.getOrNull()
        assumeTrue("server has no Live TV channels", lineup != null && lineup.contains("\"enabled\":true") && lineup.contains("\"number\":1"))
        val first = Regex("\"name\":\"([^\"]+)\"[^}]*\"number\":1[,}]").find(lineup!!)?.groupValues?.get(1)
            ?: error("no channel 1 in $lineup")
        val second = Regex("\"name\":\"([^\"]+)\"[^}]*\"number\":2[,}]").find(lineup)?.groupValues?.get(1)
            ?: error("no channel 2")
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        pump(3_000)
        ServerConfig.setLocal(ctx, base!!)
        java.io.File(ctx.filesDir, "feed_v5_home.json").delete()
        ActivityScenario.launch(MainActivity::class.java).use {
            check(waitForText("Inception")) { "home never loaded" }
            check(waitForText("Live TV")) { "no Live TV on the menu / Home" }
            dump("home-with-live")
            val feedFile = java.io.File(ctx.filesDir, "feed_v5_home.json")
            var waited = 0
            while ((!feedFile.exists() || !feedFile.readText().contains("\"livetv\"")) && waited++ < 40) pump(250)
            val feedJson = feedFile.readText()
            check(feedJson.contains("\"livetv\"") && feedJson.contains("live-entry") && feedJson.contains(first)) {
                "Home has no Live TV row (entry + channels)"
            }
            press(Key.DirectionUp)                      // nav: Home
            var guard = 0
            while (!focused().contains("Live TV") && guard++ < 8) press(Key.DirectionRight)
            check(focused().contains("Live TV")) { "focus=${focused()}" }
            press(Key.Enter)
            check(waitForText(first)) { "Live TV didn't open on channel 1 ($first)" }
            check(waitForText("LIVE")) { "no banner" }
            dump("live-banner")
            press(Key.DirectionDown)                    // next channel: banner at once
            check(waitForText(second, 3_000)) { "▼ didn't zap to $second" }
            pump(1_000)
            press(Key.DirectionCenter)                  // mini guide
            check(waitForText("NOW", 3_000)) { "mini guide didn't open" }
            dump("mini-guide")
            press(Key.Back)                             // closes the guide
            pump(2_000)
            press(Key.Back)                             // back to the last channel
            check(waitForText(first, 3_000)) { "Back didn't return to $first" }
            press(Key.Back)                             // twice quickly: leave
            check(waitForText("Inception")) { "didn't leave Live TV" }
        }
    }
}
