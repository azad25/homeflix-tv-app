package com.homeflix.tv.launcher

import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.homeflix.tv.presentation.ui.ContentRow
import com.homeflix.tv.presentation.ui.Feed
import com.homeflix.tv.presentation.ui.RowStyle
import com.homeflix.tv.presentation.ui.Tile
import com.homeflix.tv.presentation.ui.TileKind
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.annotation.Config
import java.io.File

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class HomeScreenTest {

    private fun movie(id: Int, prefix: String, badge: String? = null) = Tile(
        key = "$prefix$id", kind = TileKind.MOVIE, id = id, title = "Movie $id",
        posterUrl = "p$id", backdropUrl = "b$id", badge = badge
    )

    private fun feed(): Feed {
        val cont = ContentRow("continue", "Continue Watching", listOf(
            Tile(key = "cw50", kind = TileKind.EPISODE, id = 50, seriesId = 7, title = "Show 7", posterUrl = "p", backdropUrl = "b",
                subtitle = "S1:E2 · Pilot · 20m left", progress = 0.4f, resumeMs = 600_000),
            movie(1, "cw").copy(progress = 0.5f, resumeMs = 3_000_000)
        ), RowStyle.LANDSCAPE)
        val newRow = ContentRow("new", "New", (1..12).map { movie(it, "n", badge = "Recently Added") })
        val rec = ContentRow("recommended", "Rec", (100..112).map { movie(it, "rc") })
        val shows = ContentRow("shows", "Shows", listOf(
            Tile(key = "s7", kind = TileKind.SERIES, id = 7, title = "Show 7", posterUrl = "p", backdropUrl = "b"),
            Tile(key = "s8", kind = TileKind.SERIES, id = 8, title = "Show 8", posterUrl = "p", backdropUrl = "b")
        ))
        return Feed(heroes = listOf(movie(2, "h", badge = "Recently Added")), rows = listOf(cont, newRow, rec, shows))
    }

    @Test
    fun continueWatchingFirst_thenNewAndRecommendedMixed_noDuplicates() {
        val cards = HomeCards.pick(feed())
        assertEquals(HomeCards.MAX_CARDS, cards.size)
        // In-progress titles lead and resume playback.
        assertEquals(HomeLink.Play(50, 600_000), cards[0].link)
        assertEquals("Continue Watching", cards[0].label)
        assertEquals(HomeLink.Play(1, 3_000_000), cards[1].link)
        assertEquals(1_500_000L, cards[0].durationMs)
        // Then a mix: the billboard's newest title, a new arrival, a recommendation…
        assertEquals(HomeLink.Movie(2), cards[2].link)
        assertTrue(cards.drop(2).any { it.link == HomeLink.Movie(100) })
        assertEquals("New", cards[2].label)
        // Movie 1 is already in Continue Watching, show 7 too: never twice.
        assertEquals(1, cards.count { it.link.targetId == 1 && it.link !is HomeLink.Series })
        assertTrue(cards.none { it.link == HomeLink.Series(7) })
        assertEquals(cards.size, cards.map { it.id }.toSet().size)
    }

    @Test
    fun links_roundTrip_andRejectJunk() {
        for (l in listOf(HomeLink.Play(5, 1234), HomeLink.Movie(9), HomeLink.Series(3), HomeLink.Home)) {
            assertEquals(l, HomeLink.parse(l.toUri()))
        }
        assertNull(HomeLink.parse(Uri.parse("https://example.com/movie/1")))
        assertNull(HomeLink.parse(Uri.parse("homeflix://movie/abc")))
        assertNull(HomeLink.parse(Uri.parse("homeflix://play/-4")))
        assertNull(HomeLink.parse(Uri.parse("homeflix://format/1")))
    }

    @Test
    fun cardArt_isRendered_andServedReadOnly() = runBlocking {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val src = File(ctx.cacheDir, "backdrop.png").apply {
            outputStream().use { Bitmap.createBitmap(320, 180, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLUE) }.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        val card = HomeCards.pick(feed())[0].copy(imageUrl = src.toURI().toString(), logoUrl = null)
        val art = HomeArt(ctx).apply {
            loader = { url, _, _ -> android.graphics.BitmapFactory.decodeFile(File(java.net.URI(url)).path) }
        }
        val uri = art.ensure(card)
        assertNotNull(uri)
        assertEquals("content://${ctx.packageName}.homeart/${art.fileName(card)}", uri.toString())
        // Same card, same file; different progress, new file (so the launcher redraws it).
        assertEquals(art.fileName(card), art.fileName(card.copy()))
        assertTrue(art.fileName(card) != art.fileName(card.copy(progress = 0.9f)))

        val provider = Robolectric.setupContentProvider(HomeArtProvider::class.java, "${ctx.packageName}.homeart")
        provider.openFile(uri!!, "r")!!.use { assertTrue(it.statSize > 0) }
        for (bad in listOf("../shared_prefs/x.xml", "x.png")) {
            val r = runCatching { provider.openFile(Uri.parse("content://${ctx.packageName}.homeart/$bad"), "r") }
            assertTrue("served $bad", r.isFailure || r.getOrNull() == null)
        }
        assertTrue(runCatching { provider.openFile(uri, "w") }.isFailure)

        art.prune(emptySet())
        assertTrue(File(ctx.filesDir, HomeArt.DIR).listFiles().isNullOrEmpty())
    }
}
