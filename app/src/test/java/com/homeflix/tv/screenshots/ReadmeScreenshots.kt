package com.homeflix.tv.screenshots

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.media3.common.util.UnstableApi
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.homeflix.tv.comingsoon.ComingSoonContent
import com.homeflix.tv.comingsoon.ComingSoonController
import com.homeflix.tv.comingsoon.ComingSoonItem
import com.homeflix.tv.comingsoon.ComingSoonNotify
import com.homeflix.tv.comingsoon.ComingSoonSource
import com.homeflix.tv.comingsoon.ComingSoonTrailer
import com.homeflix.tv.comingsoon.NotifyResult
import com.homeflix.tv.comingsoon.TrailerPlayback
import com.homeflix.tv.core.PreviewPlayer
import com.homeflix.tv.domain.model.Notification
import com.homeflix.tv.livetv.LiveChannel
import com.homeflix.tv.livetv.LiveMemory
import com.homeflix.tv.livetv.LivePlay
import com.homeflix.tv.livetv.LivePlayback
import com.homeflix.tv.livetv.LiveSource
import com.homeflix.tv.livetv.LiveTvContent
import com.homeflix.tv.livetv.LiveTvController
import com.homeflix.tv.presentation.navigation.AppNavigator
import com.homeflix.tv.presentation.screens.feeds.FeedState
import com.homeflix.tv.presentation.screens.notifications.NotificationsContent
import com.homeflix.tv.presentation.screens.search.SearchContent
import com.homeflix.tv.presentation.screens.search.SearchState
import com.homeflix.tv.presentation.shell.AppShell
import com.homeflix.tv.presentation.shell.FeedPage
import com.homeflix.tv.presentation.shell.ShellController
import com.homeflix.tv.presentation.shell.ShellTab
import com.homeflix.tv.presentation.ui.ContentRow
import com.homeflix.tv.presentation.ui.DetailHeader
import com.homeflix.tv.presentation.ui.Feed
import com.homeflix.tv.presentation.ui.LocalPreviewPlayer
import com.homeflix.tv.presentation.ui.PillButton
import com.homeflix.tv.presentation.ui.RateButton
import com.homeflix.tv.presentation.ui.RowStyle
import com.homeflix.tv.presentation.ui.Tile
import com.homeflix.tv.presentation.ui.TileActions
import com.homeflix.tv.presentation.ui.TileKind
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * README screenshots of the real UI (`-Phomeflix.screenshots=<dir>`; skipped
 * otherwise). Every title, channel and image is made up (see [MockArt]).
 */
@OptIn(ExperimentalTestApi::class)
@UnstableApi
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-land-xhdpi")
class ReadmeScreenshots {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val out: String? = System.getProperty("homeflix.screenshots")

    @Before fun setUp() {
        assumeTrue("set -Phomeflix.screenshots=<dir>", out != null)
        File(out!!).mkdirs()
        coil.Coil.setImageLoader(MockArt.loader(rule.activity))
        // A TV is never in touch mode (focus requests are ignored in touch mode).
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().setInTouchMode(false)
    }

    private val titles = listOf(
        "Kingdom of Rain", "The Quiet Harbor", "Northbound", "Glass Orchard", "Ember Line", "Paper Moons",
        "The Last Lantern", "Salt & Static", "Hollow Pines", "Afterglow City", "Driftwood", "Silver Coast",
        "The Long Winter", "Neon Tide", "Quiet Thunder", "Atlas Falls", "Saffron Road", "Low Tide"
    )
    private val shows = listOf("Midnight Ferry", "The Lighthouse Keepers", "Static Hearts", "Red Meridian", "Cold Open", "Wildflower")

    private fun movie(i: Int, prefix: String = "m") = Tile(
        key = "$prefix$i", kind = TileKind.MOVIE, id = i, title = titles[i % titles.size],
        posterUrl = "http://mock/poster/${titles[i % titles.size]}", backdropUrl = "http://mock/backdrop/${titles[i % titles.size]}",
        meta = listOf("Movie  •  Drama  •  2025  •  2h 4m", "Movie  •  Thriller  •  2024  •  1h 52m", "Movie  •  Sci-Fi  •  2026  •  2h 11m")[i % 3],
        description = "A sweeping story about people pushed to the edge, and what they find there."
    )
    private fun show(i: Int) = Tile(
        key = "s$i", kind = TileKind.SERIES, id = 100 + i, seriesId = 100 + i, title = shows[i % shows.size],
        posterUrl = "http://mock/poster/${shows[i % shows.size]}", backdropUrl = "http://mock/backdrop/${shows[i % shows.size]}",
        meta = "Show  •  Mystery  •  2026  •  2 Seasons", description = "Every night the last ferry leaves without one passenger."
    )

    private val actions = object : TileActions { override fun open(tile: Tile) {}; override fun play(tile: Tile) {} }

    /** Native graphics mode doesn't give the Compose host view focus by itself; a TV would. */
    private fun focusHost() {
        fun find(v: android.view.View): android.view.View? =
            if (v.javaClass.name.contains("AndroidComposeView")) v
            else (v as? android.view.ViewGroup)?.let { g -> (0 until g.childCount).firstNotNullOfOrNull { find(g.getChildAt(it)) } }
        rule.runOnIdle {
            find(rule.activity.window.decorView)?.let { it.isFocusable = true; it.isFocusableInTouchMode = true; it.requestFocus() }
        }
    }

    private fun frames(n: Int = 30) { rule.waitForIdle(); rule.mainClock.advanceTimeBy(n * 16L); rule.waitForIdle() }
    private fun press(k: Key, times: Int = 1) = repeat(times) { rule.onRoot().performKeyInput { pressKey(k) }; frames(12) }

    private fun shot(name: String) {
        frames(40)
        val root = rule.activity.window.decorView
        val bmp = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        root.draw(Canvas(bmp))
        File(out!!).mkdirs()
        File(out, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun homeFeed(): Feed {
        val cw = (0..3).map { i ->
            if (i % 2 == 0) movie(i + 6, "cw").copy(progress = 0.35f + i * 0.12f, subtitle = "${42 - i * 9}m left", resumeMs = 1)
            else show(i).copy(kind = TileKind.EPISODE, key = "cwe$i", subtitle = "S1:E${i + 2}  ·  Chapter ${i + 2}  ·  ${20 + i}m left", progress = 0.5f)
        }
        val live = listOf(
            Tile(key = "live-entry", kind = TileKind.CHANNEL, id = 0, title = "Live TV", posterUrl = "", backdropUrl = "", subtitle = "12  Northline Sports"),
        ) + listOf("Daybreak News", "Northline Sports", "Cinema One", "Nature Today", "Music Box", "Harbor Weather").mapIndexed { i, n ->
            Tile(key = "lv$i", kind = TileKind.CHANNEL, id = 10 + i * 3, channelId = "c$i", title = n, posterUrl = "", backdropUrl = "")
        }
        return Feed(
            hero = movie(0, "hero").copy(key = "hero"),
            heroes = listOf(movie(0, "hero").copy(key = "hero")),
            rows = listOf(
                ContentRow("continue", "Continue Watching", cw, RowStyle.LANDSCAPE),
                ContentRow("recommended", "Top Picks for You", (1..12).map { movie(it, "fy") }),
                ContentRow("livetv", "Live TV", live, RowStyle.CHANNEL),
                ContentRow("top10", "Top 10 on HomeFlix This Week", (0..9).map { movie(it + 3, "t").copy(rank = it + 1) }, RowStyle.TOP10),
                ContentRow("shows", "TV Shows", (0..5).map { show(it) })
            )
        )
    }

    @Test fun home() {
        val preview = PreviewPlayer(rule.activity.applicationContext)
        val state = FeedState(homeFeed(), loading = false)
        rule.setContent {
            AppShell(previewPlayer = preview, actions = actions, onOpenNotifications = {}, unreadNotifications = 3,
                onOpenLiveTv = {}) { _, shell -> FeedPage(state, shell, actions, onRetry = {}, onResumed = {}) }
        }
        focusHost()
        frames(60)
        // ▼ from the top menu enters the page at the billboard's Play, as with a remote.
        press(Key.DirectionDown)
        shot("01-home")
        // Into "Top Picks": the focused tile widens and its details show under the row.
        press(Key.DirectionDown, 2)
        press(Key.DirectionRight, 2)
        rule.mainClock.advanceTimeBy(1_500)
        shot("02-rows")
        press(Key.DirectionDown, 1)
        press(Key.DirectionRight, 2)
        rule.mainClock.advanceTimeBy(800)
        shot("03-live-row")
    }

    @Test fun movie() {
        val preview = PreviewPlayer(rule.activity.applicationContext)
        rule.setContent {
            CompositionLocalProvider(LocalPreviewPlayer provides preview) {
                val play = remember { FocusRequester() }
                Box(Modifier.fillMaxSize()) {
                    DetailHeader(
                        previewKey = "d", previewUrl = null, previewActive = false,
                        backdropUrl = "http://mock/backdrop/Glass Orchard", logoUrl = null, title = "Glass Orchard",
                        meta = "Movie  •  Drama  •  2025  •  2h 4m  •  PG-13",
                        description = "When the last orchard on the coast is sold, three siblings come home for one final harvest, and the secrets buried under the trees.",
                        progress = 0.42f, progressLabel = "1h 12m left",
                        credits = listOf("Starring: Ana Reyes, Tom Hale, Mira Okafor", "Director: J. Lindqvist"),
                        peek = androidx.compose.ui.unit.Dp(0f), belowHint = "More Like This"
                    ) {
                        PillButton("Resume", {}, icon = Icons.Filled.PlayArrow, focusRequester = play)
                        PillButton("Play from beginning", {}, icon = Icons.Filled.Replay)
                        PillButton("My List", {}, icon = Icons.Filled.Add)
                        RateButton(1) {}
                    }
                }
                androidx.compose.runtime.LaunchedEffect(Unit) {
                    androidx.compose.runtime.withFrameNanos { }
                    repeat(3) { androidx.compose.runtime.withFrameNanos { } }
                    runCatching { play.requestFocus() }
                }
            }
        }
        focusHost()
        frames(60)
        shot("04-movie")
    }

    private val channels = listOf(
        "Daybreak News" to "news", "Northline Sports" to "sports", "Cinema One" to "movies", "Nature Today" to "documentary",
        "Music Box" to "music", "Harbor Weather" to "weather", "Kids Planet" to "kids", "World Report" to "news",
        "Retro Classics" to "classic", "Travel Now" to "travel", "City Council Live" to "legislative", "Comedy Plus" to "comedy"
    ).mapIndexed { i, (n, cat) ->
        LiveChannel(id = "c$i", name = n, country = if (i % 3 == 0) "GB" else "US", categories = listOf(cat),
            quality = if (i % 2 == 0) "1080p" else "720p", number = i + 1, status = "online")
    }

    @Composable
    private fun liveTv(controller: LiveTvController) {
        val st by controller.state.collectAsState()
        val art = remember { MockArt.paint("live-${st.playing?.id}", 1920, 1080).asImageBitmap() }
        LiveTvContent(st, controller, onExit = {}) { m ->
            Image(art, null, contentScale = ContentScale.Crop, modifier = m)
        }
    }

    private fun liveController(scope: kotlinx.coroutines.CoroutineScope) = LiveTvController(scope, object : LiveSource {
        override suspend fun play(id: String) = LivePlay(urls = listOf("http://mock/$id.m3u8"))
        override fun prefetch(ids: List<String>) {}
    }, object : LiveMemory {
        override val favorites = MutableStateFlow(setOf("c1", "c2"))
        override suspend fun favoritesNow() = setOf("c1", "c2")
        override suspend fun toggleFavorite(id: String) {}
        override suspend fun lastAndPrevious() = "c1" to "c0"
        override suspend fun recents() = emptyList<String>()
        override suspend fun recordTune(id: String) {}
    }, object : LivePlayback {
        override fun play(url: String) {}
        override fun restartAtLiveEdge() {}
        override fun stop() {}
        override fun setMuted(muted: Boolean) {}
    })

    @Test fun liveTv() {
        lateinit var c: LiveTvController
        rule.setContent {
            val scope = rememberCoroutineScope()
            c = remember { liveController(scope).also { it.start(channels, null) } }
            liveTv(c)
        }
        focusHost()
        frames(20)
        rule.runOnIdle { c.onFirstFrame(); c.showBanner(60_000) }
        shot("05-livetv-banner")
        rule.runOnIdle { c.ok(); c.down(); c.down() }
        shot("06-livetv-mini-guide")
        rule.runOnIdle { c.back(); c.left() }
        frames(10)
        rule.runOnIdle { c.down() }
        rule.mainClock.advanceTimeBy(700)
        rule.runOnIdle { c.onFirstFrame() }
        shot("07-livetv-guide")
    }

    @Test fun comingSoon() {
        val items = listOf(
            ComingSoonItem(tmdbId = 1, mediaType = "movie", title = "Atlas Falls", releaseDate = "2027-02-12", daysUntil = 101,
                studio = "Northwind Pictures", genres = listOf("Adventure", "Drama"), cast = listOf("Ana Reyes", "Tom Hale", "Mira Okafor"),
                overview = "A mapmaker follows her missing brother's last expedition into a valley that no map agrees on.",
                backdropUrl = "http://mock/backdrop/Atlas Falls", rank = 1, trailer = ComingSoonTrailer("ready", "/v/1.mp4"),
                notify = ComingSoonNotify(true, "scheduled")),
            ComingSoonItem(tmdbId = 2, mediaType = "tv", title = "Red Meridian", releaseLabel = "Season 2", releaseDate = "2026-12-04",
                daysUntil = 31, backdropUrl = "http://mock/backdrop/Red Meridian", rank = 1),
        ) + (3..8).map { ComingSoonItem(tmdbId = it, title = titles[it + 5], backdropUrl = "http://mock/backdrop/${titles[it + 5]}", rank = it) }
        val fake = object : ComingSoonSource, TrailerPlayback {
            override suspend fun items() = items
            override suspend fun title(mediaType: String, tmdbId: Int) = items.first { it.tmdbId == tmdbId }
            override suspend fun setNotify(item: ComingSoonItem, on: Boolean) = Result.success(NotifyResult(on))
            override fun trailerUrl(item: ComingSoonItem) = null
            override fun play(url: String) {}
            override fun stop() {}
            override fun pause() {}
            override fun resume() {}
        }
        rule.setContent {
            val scope = rememberCoroutineScope()
            val c = remember { ComingSoonController(scope, fake, fake).also { it.start(null) } }
            val st by c.state.collectAsState()
            ComingSoonContent(st.copy(noTrailer = false), c, onExit = {}) { }
        }
        focusHost()
        frames(40)
        shot("08-coming-soon")
    }

    @Test fun search() {
        rule.setContent {
            val shell = remember { ShellController(ShellTab.SEARCH) }
            SearchContent(
                state = SearchState(query = "nor", title = "Results for “nor”", results = (0..14).map { movie(it + 2, "sr") }),
                shell = shell, actions = actions, onType = {}, onBackspace = {}, onClear = {}, onGenre = {}
            )
        }
        focusHost()
        frames(60)
        shot("09-search")
    }

    @Test fun notifications() {
        val now = System.currentTimeMillis() / 1000
        val items = listOf(
            Notification(id = "1", type = "new", title = "New on HomeFlix", message = "Glass Orchard is ready to watch", itemTitle = "Glass Orchard",
                category = "new_movie", timestampSeconds = now - 1800, movieIds = listOf(1), imageCandidates = listOf("http://mock/backdrop/Glass Orchard"),
                year = 2025, runtimeMinutes = 124, genres = listOf("Drama"), rating = 7.8,
                overview = "When the last orchard on the coast is sold, three siblings come home for one final harvest."),
            Notification(id = "2", type = "ep", title = "New episode", message = "Midnight Ferry · S2:E4 \"Low Water\"", itemTitle = "Midnight Ferry",
                category = "new_episode", timestampSeconds = now - 4 * 3600, seriesId = 101, imageCandidates = listOf("http://mock/backdrop/Midnight Ferry")),
            Notification(id = "3", type = "new", title = "New on HomeFlix", message = "Northbound was added", itemTitle = "Northbound", read = true,
                category = "new_movie", timestampSeconds = now - 30 * 3600, movieIds = listOf(2), imageCandidates = listOf("http://mock/backdrop/Northbound")),
            Notification(id = "4", type = "dl", title = "Downloaded", message = "Static Hearts · Season 1 is ready", itemTitle = "Static Hearts", read = true,
                category = "download", timestampSeconds = now - 3 * 86_400, seriesId = 102, imageCandidates = listOf("http://mock/backdrop/Static Hearts")),
            Notification(id = "5", type = "new", title = "New on HomeFlix", message = "Ember Line was added", itemTitle = "Ember Line", read = true,
                category = "new_movie", timestampSeconds = now - 9 * 86_400, movieIds = listOf(3), imageCandidates = listOf("http://mock/backdrop/Ember Line"))
        )
        val preview = PreviewPlayer(rule.activity.applicationContext)
        rule.setContent {
            val nav = rememberNavController()
            val navigator = remember { AppNavigator(nav, preview) }
            NotificationsContent(items, navigator)
        }
        focusHost()
        frames(60)
        shot("10-notifications")
    }
}
