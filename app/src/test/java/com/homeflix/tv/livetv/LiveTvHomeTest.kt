package com.homeflix.tv.livetv

import com.homeflix.tv.presentation.ui.TileKind
import org.junit.Assert.assertEquals
import org.junit.Test

class LiveTvHomeTest {
    private val lineup = (1..30).map { LiveChannel(id = "ch$it", name = "Ch $it", number = it) }

    @Test
    fun entryThenFavoritesThenRecents() {
        val tiles = LiveTvHome.build(lineup, favorites = setOf("ch9", "ch3"), recents = listOf("ch7", "ch3", "ch1"), last = "ch7")
        assertEquals(listOf(null, "ch3", "ch9", "ch7", "ch1"), tiles.map { it.channelId })
        assertEquals(TileKind.CHANNEL, tiles[0].kind)
        assertEquals("7  Ch 7", tiles[0].subtitle)
        assertEquals(9, tiles[2].id) // channel number on the tile
    }

    @Test
    fun nothingWatchedYet_showsTheFirstChannels() {
        val tiles = LiveTvHome.build(lineup, emptySet(), emptyList(), null)
        assertEquals(11, tiles.size)
        assertEquals("ch1", tiles[1].channelId)
    }

    @Test
    fun countryNamesAndFlags() {
        assertEquals("Bangladesh", LiveCountries.name("BD"))
        assertEquals("🇧🇩", LiveCountries.flag("BD"))
        assertEquals("", LiveCountries.flag("x1"))
    }
}
