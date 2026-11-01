package com.homeflix.tv.presentation.ui

import com.homeflix.tv.domain.model.Media
import com.homeflix.tv.domain.model.MediaType
import java.util.Locale

/** Text formatting shared by tiles, details and the player (Netflix-style meta lines). */
object MediaFormat {

    /** "Movie • Drama • 2025 • 2h 32m • 18+" */
    fun metaLine(media: Media): String = buildList {
        add(if (media.type == MediaType.MOVIE) "Movie" else "Show")
        genreOf(media)?.let(::add)
        media.year?.takeIf { it > 0 }?.let { add(it.toString()) }
        runtime(media)?.let(::add)
        media.certification?.takeIf { it.isNotBlank() }?.let(::add)
    }.joinToString("  •  ")

    fun genreOf(media: Media): String? =
        media.genreNames.firstOrNull { it.isNotBlank() } ?: media.genres.firstOrNull()?.name

    fun runtime(media: Media): String? {
        val minutes = media.runtime?.takeIf { it > 0 } ?: (media.duration / 60).takeIf { it > 0 } ?: return null
        return duration(minutes)
    }

    fun duration(minutes: Int): String = when {
        minutes >= 60 -> "${minutes / 60}h ${minutes % 60}m"
        else -> "${minutes}m"
    }

    fun description(media: Media): String? =
        listOf(media.description, media.shortDesc, media.longDesc, media.tagline)
            .firstOrNull { !it.isNullOrBlank() }?.trim()

    /** "S1:E3 Pilot" */
    fun episodeLabel(season: Int?, episode: Int?, title: String?): String = buildString {
        if (season != null && episode != null) append("S$season:E$episode")
        if (!title.isNullOrBlank()) {
            if (isNotEmpty()) append("  ")
            append(title)
        }
    }

    /** 5_853_000 ms → "1:37:33"; 790_000 → "13:10" */
    fun clock(ms: Long): String {
        val total = (ms.coerceAtLeast(0) / 1000)
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s)
        else String.format(Locale.US, "%d:%02d", m, s)
    }

    fun languageName(code: String?): String {
        if (code.isNullOrBlank() || code == "und") return "Unknown"
        val name = runCatching { Locale(code).getDisplayLanguage(Locale.ENGLISH) }.getOrNull()
        return if (name.isNullOrBlank() || name.equals(code, ignoreCase = true)) code.uppercase(Locale.US) else name
    }
}
