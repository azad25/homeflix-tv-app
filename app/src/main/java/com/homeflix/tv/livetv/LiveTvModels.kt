package com.homeflix.tv.livetv

import java.util.Locale

/** A Live TV channel from GET /api/livetv/channels. */
data class LiveChannel(
    val id: String = "",
    val name: String = "",
    val country: String? = null,
    val categories: List<String>? = null,
    val logo: String? = null,
    /** online | offline | unknown */
    val status: String? = null,
    val quality: String? = null,
    /** Stable channel number for the remote (position in the full lineup). */
    val number: Int = 0
) {
    /** Channels the server can only offer as a YouTube live stream: they don't play on the TV. */
    val isYouTubeOnly: Boolean get() = quality.equals("YouTube", ignoreCase = true)

    val category: String? get() = categories?.firstOrNull()?.takeIf { it.isNotBlank() }

    /** "News · 720p · Bangladesh" */
    val details: String
        get() = listOfNotNull(
            category?.replaceFirstChar { it.uppercase() },
            quality?.takeIf { it.isNotBlank() },
            country?.let { LiveCountries.name(it) }
        ).joinToString("  ·  ")
}

data class LiveCount(val code: String = "", val count: Int = 0)

data class LiveLineup(
    val enabled: Boolean = false,
    val channels: List<LiveChannel>? = null,
    val countries: List<LiveCount>? = null,
    val categories: List<LiveCount>? = null
)

/** GET /api/livetv/channels/{id}/play */
data class LivePlay(
    val channel: LiveChannel? = null,
    /** HLS playlists, best first; relative to the server. (YouTube-only sources are not used on the TV.) */
    val urls: List<String>? = null
)

object LiveCountries {
    /** "BD" → "Bangladesh". */
    fun name(code: String): String =
        runCatching { Locale("", code).getDisplayCountry(Locale.ENGLISH) }.getOrNull()?.takeIf { it.isNotBlank() && it != code } ?: code

    /** "BD" → 🇧🇩 */
    fun flag(code: String): String {
        if (code.length != 2 || !code.all { it.isLetter() }) return ""
        val base = 0x1F1E6 - 'A'.code
        return code.uppercase().map { String(Character.toChars(base + it.code)) }.joinToString("")
    }
}
