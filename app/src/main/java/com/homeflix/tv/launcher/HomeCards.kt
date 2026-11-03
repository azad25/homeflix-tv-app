package com.homeflix.tv.launcher

import android.net.Uri
import com.homeflix.tv.presentation.ui.Feed
import com.homeflix.tv.presentation.ui.Tile
import com.homeflix.tv.presentation.ui.TileKind

/** One card in HomeFlix's row on the TV's home screen. */
data class HomeCard(
    /** Stable id for the launcher (same title → same program, so updates don't flicker). */
    val id: String,
    val kind: TileKind,
    val title: String,
    val description: String?,
    val imageUrl: String,
    val logoUrl: String?,
    /** Small label on the card: "Continue Watching", "New", "New Episode"… */
    val label: String?,
    val progress: Float?,
    val resumeMs: Long,
    val link: HomeLink
) {
    val isContinue: Boolean get() = progress != null
    val durationMs: Long get() = progress?.takeIf { it > 0.01f }?.let { (resumeMs / it).toLong() } ?: 0L
}

/**
 * Picks what HomeFlix shows on the TV home screen, Netflix-style: what you're
 * in the middle of first, then a mix of new arrivals and recommendations.
 */
object HomeCards {
    const val MAX_CONTINUE = 8
    const val MAX_CARDS = 20

    fun pick(feed: Feed): List<HomeCard> {
        val rows = feed.rows.associateBy { it.id }
        val seen = HashSet<String>()
        val out = ArrayList<HomeCard>()
        fun identity(t: Tile) = when (t.kind) {
            TileKind.SERIES -> "series:${t.id}"
            TileKind.EPISODE -> "series:${t.seriesId ?: "e${t.id}"}"
            TileKind.MOVIE -> "movie:${t.id}"
            TileKind.CHANNEL -> "channel:${t.channelId}"
            TileKind.COMING -> "coming:${t.comingKey}"
        }
        fun add(t: Tile, card: HomeCard) { if (out.size < MAX_CARDS && seen.add(identity(t))) out += card }

        rows["continue"]?.tiles.orEmpty().filter { it.progress != null }.take(MAX_CONTINUE).forEach { t ->
            add(t, card(t, label = "Continue Watching", continueWatching = true))
        }
        // Then interleave new arrivals and recommendations so the row feels fresh.
        val sources = listOf(
            feed.heroList.map { it to labelFor(it) },
            rows["new"]?.tiles.orEmpty().map { it to labelFor(it) },
            rows["recommended"]?.tiles.orEmpty().map { it to null },
            rows["trending"]?.tiles.orEmpty().map { it to null },
            rows["shows"]?.tiles.orEmpty().map { it to labelFor(it) }
        ).map { it.iterator() }
        var progress = true
        while (out.size < MAX_CARDS && progress) {
            progress = false
            for (src in sources) {
                if (src.hasNext()) {
                    progress = true
                    val (t, label) = src.next()
                    if (t.backdropUrl.isNotBlank() || t.posterUrl.isNotBlank()) add(t, card(t, label, continueWatching = false))
                }
            }
        }
        return out
    }

    private fun labelFor(t: Tile): String? = when {
        t.badge.isNullOrBlank() -> null
        t.badge.contains("episode", ignoreCase = true) -> "New Episode"
        else -> "New"
    }

    private fun card(t: Tile, label: String?, continueWatching: Boolean): HomeCard {
        val link = when {
            continueWatching && t.kind != TileKind.SERIES -> HomeLink.Play(t.id, t.resumeMs)
            t.kind == TileKind.SERIES -> HomeLink.Series(t.id)
            t.kind == TileKind.EPISODE -> t.seriesId?.let { HomeLink.Series(it) } ?: HomeLink.Movie(t.id)
            else -> HomeLink.Movie(t.id)
        }
        return HomeCard(
            id = "${link.kindName}:${link.targetId}",
            kind = t.kind,
            title = t.title,
            description = if (continueWatching) t.subtitle ?: t.description else t.description,
            imageUrl = t.backdropUrl.ifBlank { t.posterUrl },
            logoUrl = t.logoUrl,
            label = label,
            progress = if (continueWatching) t.progress?.coerceIn(0f, 1f) else null,
            resumeMs = if (continueWatching) t.resumeMs else 0L,
            link = link
        )
    }
}

/** Where a home-screen card leads inside the app. */
sealed class HomeLink {
    data class Play(val mediaId: Int, val positionMs: Long) : HomeLink()
    data class Movie(val mediaId: Int) : HomeLink()
    data class Series(val seriesId: Int) : HomeLink()
    data object Home : HomeLink()

    val kindName: String get() = when (this) { is Play -> "play"; is Movie -> "movie"; is Series -> "series"; Home -> "home" }
    val targetId: Int get() = when (this) { is Play -> mediaId; is Movie -> mediaId; is Series -> seriesId; Home -> 0 }

    fun toUri(): Uri = when (this) {
        is Play -> Uri.parse("$SCHEME://play/$mediaId?t=$positionMs")
        is Movie -> Uri.parse("$SCHEME://movie/$mediaId")
        is Series -> Uri.parse("$SCHEME://series/$seriesId")
        Home -> Uri.parse("$SCHEME://home")
    }

    companion object {
        const val SCHEME = "homeflix"

        fun parse(uri: Uri?): HomeLink? {
            if (uri == null || uri.scheme != SCHEME) return null
            val id = uri.pathSegments.firstOrNull()?.toIntOrNull()
            return when (uri.host) {
                "play" -> id?.takeIf { it > 0 }?.let { Play(it, uri.getQueryParameter("t")?.toLongOrNull()?.coerceAtLeast(0) ?: 0L) }
                "movie" -> id?.takeIf { it > 0 }?.let(::Movie)
                "series" -> id?.takeIf { it > 0 }?.let(::Series)
                "home" -> Home
                else -> null
            }
        }
    }
}
