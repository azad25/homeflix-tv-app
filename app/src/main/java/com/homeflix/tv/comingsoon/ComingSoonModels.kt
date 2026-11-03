package com.homeflix.tv.comingsoon

import com.google.gson.annotations.SerializedName
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/** One upcoming title from GET /api/comingsoon (homeflix-wifi docs/COMING_SOON.md). */
data class ComingSoonItem(
    @SerializedName("tmdb_id") val tmdbId: Int = 0,
    /** movie | tv */
    @SerializedName("media_type") val mediaType: String = "movie",
    val title: String = "",
    val overview: String? = null,
    val tagline: String? = null,
    /** YYYY-MM-DD */
    @SerializedName("release_date") val releaseDate: String? = null,
    /** tv: "New Series" or "Season 3" */
    @SerializedName("release_label") val releaseLabel: String? = null,
    val season: Int = 0,
    @SerializedName("days_until") val daysUntil: Int = 0,
    @SerializedName("poster_url") val posterUrl: String? = null,
    @SerializedName("backdrop_url") val backdropUrl: String? = null,
    val genres: List<String>? = null,
    val cast: List<String>? = null,
    val studio: String? = null,
    val rank: Int = 0,
    val trailer: ComingSoonTrailer? = null,
    val notify: ComingSoonNotify? = null
) {
    val key: String get() = "$mediaType:$tmdbId"
    val isSeries: Boolean get() = mediaType == "tv"
    val notifyOn: Boolean get() = notify?.on == true
}

data class ComingSoonTrailer(
    /** ready | pending | downloading | encoding | failed | no_downloader | over_budget */
    val state: String? = null,
    /** Server-relative MP4 (H.264, faststart), only when ready. */
    @SerializedName("video_url") val videoUrl: String? = null,
    val width: Int = 0,
    val height: Int = 0,
    val duration: Double = 0.0
)

data class ComingSoonNotify(val on: Boolean = false, val status: String? = null)

data class ComingSoonList(
    val movies: List<ComingSoonItem>? = null,
    val series: List<ComingSoonItem>? = null
)

data class NotifyBody(@SerializedName("media_type") val mediaType: String, @SerializedName("tmdb_id") val tmdbId: Int)

data class NotifyResult(val on: Boolean = false, val status: String? = null, val message: String? = null)

object ComingSoonDates {
    private fun parse(date: String?): Calendar? {
        if (date.isNullOrBlank()) return null
        return runCatching {
            val f = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = TimeZone.getDefault() }
            Calendar.getInstance().apply { time = f.parse(date)!! }
        }.getOrNull()
    }

    /** Card line: "In 12 days" within 45 days, else "Dec 18" this year / "Feb 2027" later. */
    fun cardLine(item: ComingSoonItem, now: Calendar = Calendar.getInstance()): String {
        val d = item.daysUntil
        if (d in 0..45) return countdown(d)
        val c = parse(item.releaseDate) ?: return ""
        val sameYear = c.get(Calendar.YEAR) == now.get(Calendar.YEAR)
        return SimpleDateFormat(if (sameYear) "MMM d" else "MMM yyyy", Locale.ENGLISH).format(c.time)
    }

    /** "Friday, February 12, 2027" */
    fun fullDate(item: ComingSoonItem): String =
        parse(item.releaseDate)?.let { SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.ENGLISH).format(it.time) } ?: ""

    fun countdown(days: Int): String = when {
        days <= 0 -> "Out today"
        days == 1 -> "Tomorrow"
        else -> "In $days days"
    }
}
