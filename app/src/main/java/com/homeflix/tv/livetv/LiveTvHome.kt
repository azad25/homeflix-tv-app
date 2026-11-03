package com.homeflix.tv.livetv

import com.homeflix.tv.presentation.ui.Tile
import com.homeflix.tv.presentation.ui.TileKind
import javax.inject.Inject
import javax.inject.Singleton

/** The "Live TV" row on Home: the Live TV entry, then favorites, then recently watched channels. */
@Singleton
class LiveTvHome @Inject constructor(private val repo: LiveTvRepository, private val store: LiveTvStore) {

    /** Null when the server has no Live TV (the row is hidden). */
    suspend fun tiles(): List<Tile>? {
        val lineup = repo.lineupFresh()
        val channels = lineup?.takeIf { it.enabled }?.channels.orEmpty()
        if (channels.isEmpty()) return null
        return build(channels, store.favoritesNow(), store.recents(), store.lastAndPrevious().first)
    }

    companion object {
        const val MAX = 16

        fun build(channels: List<LiveChannel>, favorites: Set<String>, recents: List<String>, last: String?): List<Tile> {
            val byId = channels.associateBy { it.id }
            val lastName = last?.let { byId[it] }?.let { "${it.number}  ${it.name}" }
            val entry = Tile(key = "live-entry", kind = TileKind.CHANNEL, id = 0, title = "Live TV",
                posterUrl = "", backdropUrl = "", subtitle = lastName ?: "${channels.size} channels",
                meta = "Live TV  •  ${channels.size} channels", description = "Pick up where you left off, or press ◀ for the guide.")
            val picked = LinkedHashMap<String, LiveChannel>()
            channels.filter { it.id in favorites }.forEach { picked[it.id] = it }
            recents.mapNotNull { byId[it] }.forEach { picked.putIfAbsent(it.id, it) }
            // Nothing watched yet: start with the first channels of the lineup.
            if (picked.isEmpty()) channels.sortedBy { it.number }.take(10).forEach { picked[it.id] = it }
            return listOf(entry) + picked.values.take(MAX - 1).map { ch ->
                Tile(
                    key = "lv${ch.id}", kind = TileKind.CHANNEL, id = ch.number, channelId = ch.id, title = ch.name,
                    posterUrl = ch.logo.orEmpty(), backdropUrl = "",
                    meta = listOf("Channel ${ch.number}", ch.details).filter { it.isNotBlank() }.joinToString("  •  "),
                    description = if (ch.id in favorites) "★ Favorite  •  Live now" else "Live now"
                )
            }
        }
    }
}
