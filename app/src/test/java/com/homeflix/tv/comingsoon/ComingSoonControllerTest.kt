package com.homeflix.tv.comingsoon

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

@OptIn(ExperimentalCoroutinesApi::class)
class ComingSoonControllerTest {

    private fun item(id: Int, type: String = "movie", ready: Boolean = true, days: Int = 20) = ComingSoonItem(
        tmdbId = id, mediaType = type, title = "Title $id", releaseDate = "2027-02-12", daysUntil = days, rank = id,
        trailer = ComingSoonTrailer(state = if (ready) "ready" else "pending", videoUrl = if (ready) "/api/comingsoon/video/$type-$id.mp4" else null)
    )

    private class Fake(val list: List<ComingSoonItem>) : ComingSoonSource, TrailerPlayback {
        val played = mutableListOf<String>()
        var stops = 0; var paused = false
        var notifyFails = false
        val serverNotify = HashMap<String, ComingSoonNotify>()
        override suspend fun items() = list
        override suspend fun title(mediaType: String, tmdbId: Int) =
            list.first { it.tmdbId == tmdbId }.let { it.copy(notify = serverNotify[it.key] ?: it.notify) }
        override suspend fun setNotify(item: ComingSoonItem, on: Boolean): Result<NotifyResult> {
            if (notifyFails) return Result.failure(RuntimeException("down"))
            serverNotify[item.key] = ComingSoonNotify(on, if (on) "scheduled" else "")
            return Result.success(NotifyResult(on, if (on) "scheduled" else ""))
        }
        override fun trailerUrl(item: ComingSoonItem) = item.trailer?.videoUrl?.let { "http://s$it" }
        override fun play(url: String) { played += url }
        override fun stop() { stops++ }
        override fun pause() { paused = true }
        override fun resume() { paused = false }
    }

    private fun TestScope.rig(list: List<ComingSoonItem>, start: String? = null): Pair<ComingSoonController, Fake> {
        val f = Fake(list)
        val c = ComingSoonController(this, f, f)
        c.start(start); runCurrent()
        return c to f
    }

    @Test
    fun startsOnTheChosenTitle_trailerEndsThenNext_backdropUntilFirstFrame() = runTest {
        val (c, f) = rig(listOf(item(1), item(2), item(3, "tv")), start = "movie:2")
        assertEquals(1, c.state.value.index)
        assertEquals(listOf("http://s/api/comingsoon/video/movie-2.mp4"), f.played)
        assertFalse("backdrop until the first frame", c.state.value.videoVisible)
        c.onFirstFrame()
        assertTrue(c.state.value.videoVisible)
        c.onEnded()
        assertEquals("like changing channel", 2, c.state.value.index)
        assertEquals("http://s/api/comingsoon/video/tv-3.mp4", f.played.last())
        c.onEnded()
        assertEquals("wraps around", 0, c.state.value.index)
    }

    @Test
    fun leftRightChangeTitle_overlayFadesAndComesBackOnAnyKey() = runTest {
        val (c, _) = rig(listOf(item(1), item(2), item(3)))
        assertTrue(c.state.value.overlayVisible)
        advanceTimeBy(4_100)
        assertFalse("overlay fades after ~4 s", c.state.value.overlayVisible)
        c.down()
        assertTrue("any key brings it back", c.state.value.overlayVisible)
        assertEquals("the first key only wakes the overlay", CsZone.NOTIFY, c.state.value.zone)
        c.right(); assertEquals(1, c.state.value.index)
        c.left(); c.left(); assertEquals(2, c.state.value.index)
    }

    @Test
    fun titlesWithoutATrailer_holdTheBackdrop_thenMoveOn() = runTest {
        val (c, f) = rig(listOf(item(1, ready = false), item(2)))
        assertTrue(c.state.value.noTrailer)
        assertTrue(f.played.isEmpty())
        advanceTimeBy(10_100)
        assertEquals(1, c.state.value.index)
        assertEquals(1, f.played.size)
        // A playback error is treated the same way.
        c.onError()
        assertTrue(c.state.value.noTrailer)
    }

    @Test
    fun okTogglesNotify_optimistic_thenServerStatus_rollsBackOnFailure() = runTest {
        val (c, f) = rig(listOf(item(1), item(2)))
        c.ok()
        assertTrue("optimistic", c.state.value.current!!.notifyOn)
        runCurrent()
        assertEquals("scheduled", c.state.value.current!!.notify?.status)
        f.notifyFails = true
        c.ok(); assertFalse(c.state.value.current!!.notifyOn)
        runCurrent()
        assertTrue("rolled back", c.state.value.current!!.notifyOn)
        assertEquals("Couldn't reach the server", c.state.value.message)
    }

    @Test
    fun zones_details_playPause_andBackClosesOverlayThenScreen() = runTest {
        val (c, f) = rig(listOf(item(1), item(2)))
        c.down(); assertEquals(CsZone.DETAILS, c.state.value.zone)
        c.ok(); assertTrue(c.state.value.details)
        advanceTimeBy(6_000)
        assertTrue("stays while Details is open", c.state.value.overlayVisible)
        c.down(); assertEquals(CsZone.STRIP, c.state.value.zone)
        c.down(); assertEquals(CsZone.STRIP, c.state.value.zone)
        c.up(); c.up(); assertEquals(CsZone.NOTIFY, c.state.value.zone)
        c.onFirstFrame(); c.playPause(); assertTrue(f.paused); c.playPause(); assertFalse(f.paused)
        assertFalse(c.back()); assertFalse("Details closed first", c.state.value.details)
        assertFalse(c.back()); assertFalse("then the overlay", c.state.value.overlayVisible)
        assertTrue("then the screen", c.back())
        assertNull(c.state.value.message)
    }

    @Test
    fun dateLines() {
        val now = Calendar.getInstance().apply { set(2026, Calendar.NOVEMBER, 3) }
        assertEquals("In 12 days", ComingSoonDates.cardLine(item(1, days = 12).copy(releaseDate = "2026-11-15"), now))
        assertEquals("Dec 18", ComingSoonDates.cardLine(item(1, days = 45 + 1).copy(releaseDate = "2026-12-18"), now))
        assertEquals("Feb 2027", ComingSoonDates.cardLine(item(1, days = 101), now))
        assertEquals("Friday, February 12, 2027", ComingSoonDates.fullDate(item(1)))
        assertEquals("Tomorrow", ComingSoonDates.countdown(1))
    }

    @Test
    fun homeTiles_haveTrailerOnlyWhenReady_andShowLabelsAndBell() {
        val ready = ComingSoonHome.tile(item(1).copy(notify = ComingSoonNotify(true, "queued")))
        assertTrue(ready.previewUrl!!.endsWith("/api/comingsoon/video/movie-1.mp4"))
        assertTrue(ready.bell)
        assertEquals("movie:1", ready.comingKey)
        val pending = ComingSoonHome.tile(item(2, type = "tv", ready = false).copy(releaseLabel = "Season 3"))
        assertNull(pending.previewUrl)
        assertEquals("Season 3", pending.badge)
    }
}
