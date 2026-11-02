package com.homeflix.tv.livetv

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LiveTvControllerTest {

    private val lineup = (1..20).map { n ->
        LiveChannel(id = "ch$n", name = "Channel $n", country = if (n <= 10) "BD" else "US",
            categories = listOf(if (n % 2 == 0) "news" else "sports"), number = n)
    }

    private class FakeSource : LiveSource {
        val plays = mutableListOf<String>()
        val prefetched = mutableListOf<List<String>>()
        var answer: (String) -> LivePlay? = { id -> LivePlay(urls = listOf("http://s/$id/a.m3u8", "http://s/$id/b.m3u8")) }
        override suspend fun play(id: String): LivePlay? { plays += id; return answer(id) }
        override fun prefetch(ids: List<String>) { prefetched += ids }
    }

    private class FakeMemory(var last: String? = null) : LiveMemory {
        val favs = MutableStateFlow<Set<String>>(emptySet())
        val tuned = mutableListOf<String>()
        override val favorites = favs
        override suspend fun favoritesNow() = favs.value
        override suspend fun toggleFavorite(id: String) { favs.value = if (id in favs.value) favs.value - id else favs.value + id }
        override suspend fun lastAndPrevious() = last to null
        override suspend fun recents() = tuned.reversed()
        override suspend fun recordTune(id: String) { tuned += id; last = id }
    }

    private class FakePlayer : LivePlayback {
        val played = mutableListOf<String>()
        var restarts = 0
        var stops = 0
        var mutedNow = false
        override fun play(url: String) { played += url }
        override fun restartAtLiveEdge() { restarts++ }
        override fun stop() { stops++ }
        override fun setMuted(muted: Boolean) { mutedNow = muted }
    }

    private class Rig(scope: TestScope, last: String? = null) {
        val source = FakeSource()
        val memory = FakeMemory(last)
        val player = FakePlayer()
        val c = LiveTvController(scope, source, memory, player, now = { scope.testScheduler.currentTime })
    }

    private fun TestScope.rig(last: String? = null, start: String? = null): Rig {
        val r = Rig(this, last)
        r.c.start(lineup, start)
        runCurrent()
        return r
    }

    @Test
    fun startsOnTheLastChannel_playsBestUrl_bannerThenVideoOnly() = runTest {
        val r = rig(last = "ch5")
        val s = r.c.state.value
        assertEquals("ch5", s.current?.id)
        assertEquals(LivePhase.TUNING, s.phase)
        assertEquals(listOf("http://s/ch5/a.m3u8"), r.player.played)
        assertTrue(s.bannerVisible)
        // Neighbours prefetched for the next zap.
        assertEquals(listOf(listOf("ch6", "ch4")), r.source.prefetched)
        r.c.onFirstFrame()
        assertEquals(LivePhase.PLAYING, r.c.state.value.phase)
        advanceTimeBy(3_100)
        assertFalse("banner hides after ~3 s", r.c.state.value.bannerVisible)
        // Nothing watched before: channel 1.
        val fresh = rig()
        assertEquals("ch1", fresh.c.state.value.current?.id)
    }

    @Test
    fun zapping_movesTheBannerAtOnce_butTunesOnceAfterKeysSettle() = runTest {
        val r = rig(last = "ch5")
        r.c.onFirstFrame()
        r.source.plays.clear(); r.player.played.clear()
        repeat(3) { r.c.down(); advanceTimeBy(150) }
        assertEquals("banner follows every press", "ch8", r.c.state.value.shown?.id)
        assertEquals(LivePhase.TUNING, r.c.state.value.phase)
        assertTrue("no tune while keys are still coming", r.source.plays.isEmpty())
        advanceTimeBy(500)
        assertEquals(listOf("ch8"), r.source.plays)
        assertEquals("ch8", r.c.state.value.current?.id)
        // ▲ is the previous channel, and the list wraps at both ends.
        r.c.onFirstFrame()
        repeat(8) { r.c.up() }
        advanceTimeBy(500)
        assertEquals("ch20", r.c.state.value.current?.id)
    }

    @Test
    fun urlFallback_thenUnavailable_andBehindLiveWindowIsNotDead() = runTest {
        val r = rig(last = "ch3")
        r.c.onPlayerError(behindLiveWindow = true)
        assertEquals(1, r.player.restarts)
        assertEquals(listOf("http://s/ch3/a.m3u8"), r.player.played)
        r.c.onPlayerError(behindLiveWindow = false)
        assertEquals("http://s/ch3/b.m3u8", r.player.played.last())
        r.c.onPlayerError(behindLiveWindow = false)
        assertEquals(LivePhase.UNAVAILABLE, r.c.state.value.phase)
        assertEquals("never skips to another channel by itself", "ch3", r.c.state.value.current?.id)
    }

    @Test
    fun youtubeFallback_andTenSecondsWithoutVideo() = runTest {
        val r = rig(last = "ch3")
        r.source.answer = { LivePlay(urls = emptyList(), youtubeId = "abcdefghijk") }
        r.c.down(); advanceTimeBy(450)
        assertEquals(LivePhase.YOUTUBE, r.c.state.value.phase)
        assertEquals("abcdefghijk", r.c.state.value.youtubeId)

        r.source.answer = { LivePlay(urls = listOf("http://s/x.m3u8")) }
        r.c.down(); advanceTimeBy(450)
        assertEquals(LivePhase.TUNING, r.c.state.value.phase)
        advanceTimeBy(10_100)
        assertEquals(LivePhase.UNAVAILABLE, r.c.state.value.phase)
        assertEquals("ch5", r.c.state.value.current?.id)
    }

    @Test
    fun numberEntry_tunesAfterAPause_orOnOk() = runTest {
        val r = rig(last = "ch1")
        r.c.digit(1); r.c.digit(2)
        assertEquals("12", r.c.state.value.digits)
        advanceTimeBy(1_000)
        assertEquals("ch1", r.c.state.value.current?.id)
        advanceTimeBy(300)
        assertEquals("ch12", r.c.state.value.current?.id)
        assertEquals("", r.c.state.value.digits)

        r.c.digit(7); r.c.ok()
        assertEquals("OK tunes at once", "ch7", r.c.state.value.current?.id)

        r.c.digit(9); r.c.digit(9); r.c.ok()
        assertEquals("No channel 99", r.c.state.value.message)
        assertEquals("ch7", r.c.state.value.current?.id)
    }

    @Test
    fun back_returnsToTheLastChannel_twiceQuicklyLeaves() = runTest {
        val r = rig(last = "ch2")
        r.c.digit(9); r.c.ok()
        assertEquals("ch9", r.c.state.value.current?.id)
        advanceTimeBy(2_000)
        assertFalse(r.c.back())
        assertEquals("ch2", r.c.state.value.current?.id)
        assertTrue(r.c.state.value.exitHint)
        advanceTimeBy(500)
        assertTrue("second Back within 1.5 s leaves", r.c.back())
        advanceTimeBy(3_000)
        assertFalse(r.c.back())
        assertEquals("Back flips between the two channels", "ch9", r.c.state.value.current?.id)
    }

    @Test
    fun miniGuide_opensOnCurrent_wraps_okTunes_backCloses() = runTest {
        val r = rig(last = "ch1")
        r.c.ok()
        assertEquals(LiveOverlay.MINI_GUIDE, r.c.state.value.overlay)
        assertEquals(0, r.c.state.value.miniIndex)
        r.c.up()
        assertEquals("wraps to the end", 19, r.c.state.value.miniIndex)
        assertEquals("the player didn't move", "ch1", r.c.state.value.current?.id)
        r.c.ok()
        assertEquals(LiveOverlay.NONE, r.c.state.value.overlay)
        assertEquals("ch20", r.c.state.value.current?.id)
        r.c.ok(); assertFalse(r.c.back())
        assertEquals(LiveOverlay.NONE, r.c.state.value.overlay)
        assertEquals("Back only closed the guide", "ch20", r.c.state.value.current?.id)
    }

    @Test
    fun fullGuide_groups_previewMuted_okWatches_backRestores() = runTest {
        val r = rig(last = "ch1")
        r.c.onFirstFrame()
        r.c.left()
        val s = r.c.state.value
        assertEquals(LiveOverlay.GUIDE, s.overlay)
        assertEquals(listOf("All channels", "🇧🇩  Bangladesh", "🇺🇸  United States", "News", "Sports"), s.groups.map { it.title })
        r.c.down(); advanceTimeBy(300)
        assertTrue("no preview before ~600 ms", r.player.played.size == 1)
        advanceTimeBy(400)
        assertEquals("http://s/ch2/a.m3u8", r.player.played.last())
        assertTrue(r.player.mutedNow)
        assertEquals("ch1", r.c.state.value.current?.id)
        // Back: the previewed channel goes away, the current one comes back with sound.
        r.c.back(); runCurrent()
        assertFalse(r.player.mutedNow)
        assertEquals("http://s/ch1/a.m3u8", r.player.played.last())

        // Pick a group, then a channel: ▲▼ then walk that group.
        r.c.left(); r.c.left()
        assertEquals(GuideColumn.GROUPS, r.c.state.value.guideColumn)
        r.c.down(); r.c.down()   // United States
        r.c.right(); r.c.ok()
        assertEquals("ch11", r.c.state.value.current?.id)
        r.c.down(); advanceTimeBy(450)
        assertEquals("ch12", r.c.state.value.current?.id)
        r.c.up(); r.c.up(); advanceTimeBy(450)
        assertEquals("wraps inside the group", "ch20", r.c.state.value.current?.id)
    }

    @Test
    fun infoTogglesFavorite_andFavoritesGroupLeadsTheGuide() = runTest {
        val r = rig(last = "ch4")
        r.c.right()
        assertEquals(LiveOverlay.INFO, r.c.state.value.overlay)
        r.c.ok(); runCurrent()
        assertTrue(r.c.state.value.isFavorite)
        assertEquals(setOf("ch4"), r.memory.favs.value)
        r.c.back()
        r.c.left()
        assertEquals("★  Favorites", r.c.state.value.groups.first().title)
        assertNull(r.c.state.value.message)
    }
}
