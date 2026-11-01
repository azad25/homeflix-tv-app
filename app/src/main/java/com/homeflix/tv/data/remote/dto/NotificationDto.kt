package com.homeflix.tv.data.remote.dto

import com.google.gson.annotations.SerializedName
import com.homeflix.tv.domain.model.Notification

/**
 * Backend GET /api/notifications response:
 * { "notifications": [ ... ], "count": N }
 */
data class NotificationsResponseDto(
    @SerializedName("notifications") val notifications: List<NotificationDto>?,
    @SerializedName("count") val count: Int?
)

data class NotificationDto(
    @SerializedName("id") val id: String?,
    @SerializedName("type") val type: String?,
    @SerializedName("title") val title: String?,
    @SerializedName("message") val message: String?,
    @SerializedName("backdrop_url") val backdropUrl: String?,
    @SerializedName("poster_url") val posterUrl: String?,
    @SerializedName("logo_url") val logoUrl: String?,
    @SerializedName("rating") val rating: Double?,
    @SerializedName("year") val year: Int?,
    @SerializedName("overview") val overview: String?,
    @SerializedName("category") val category: String?,
    @SerializedName("priority") val priority: String?,
    @SerializedName("timestamp") val timestamp: Long?,
    @SerializedName("read") val read: Boolean?,
    // Local library references (used to filter to local-only + navigate)
    @SerializedName("movie_ids") val movieIds: List<Int>?,
    @SerializedName("series_id") val seriesId: Int?,
    @SerializedName("episode_ids") val episodeIds: List<Int>?,
    @SerializedName("genres") val genres: List<String>? = null,
    @SerializedName("runtime") val runtime: Int? = null,
    @SerializedName("series_name") val seriesName: String? = null,
    @SerializedName("media_details") val mediaDetails: List<NotificationMediaDto>? = null
) {
    fun toDomain(): Notification {
        val first = mediaDetails?.firstOrNull()
        val movieId = movieIds?.firstOrNull() ?: episodeIds?.firstOrNull()
        val sid = seriesId?.takeIf { it > 0 }
        val base = com.homeflix.tv.util.ApiUtils.getBaseUrl()
        // Artwork candidates, best first. The server sends relative paths
        // ("/api/posters/12"), TMDB URLs, or nothing at top level (the art is
        // then only in media_details) — resolve all of them, then fall back to
        // the library's own artwork endpoints.
        val backdrops = listOfNotNull(
            resolveImage(backdropUrl), resolveImage(first?.backdropUrl),
            sid?.let { "$base/series/$it/backdrop" }, movieId?.let { "$base/backdrops/$it" }
        )
        val posters = listOfNotNull(
            resolveImage(posterUrl), resolveImage(first?.posterUrl),
            sid?.let { "$base/series/$it/poster" }, movieId?.let { "$base/posters/$it" }
        )
        return Notification(
        id = id ?: "",
        type = type ?: "",
        title = title ?: "",
        message = message ?: "",
        backdropUrl = backdrops.firstOrNull(),
        posterUrl = posters.firstOrNull(),
        logoUrl = resolveImage(logoUrl) ?: sid?.let { com.homeflix.tv.util.ApiUtils.getSeriesLogoUrl(it) },
        rating = rating ?: first?.rating ?: 0.0,
        year = year ?: first?.year,
        overview = overview?.takeIf { it.isNotBlank() } ?: first?.overview,
        imageCandidates = (backdrops + posters).distinct(),
        genres = genres?.takeIf { it.isNotEmpty() } ?: first?.genres.orEmpty(),
        runtimeMinutes = runtime ?: first?.runtime,
        itemTitle = first?.title?.takeIf { it.isNotBlank() } ?: seriesName,
        category = category,
        timestampSeconds = timestamp ?: 0L,
        read = read ?: false,
        movieIds = movieIds ?: emptyList(),
        seriesId = seriesId ?: 0,
        episodeIds = episodeIds ?: emptyList()
        )
    }
}

data class NotificationMediaDto(
    @SerializedName("id") val id: Int? = null,
    @SerializedName("title") val title: String? = null,
    @SerializedName("poster_url") val posterUrl: String? = null,
    @SerializedName("backdrop_url") val backdropUrl: String? = null,
    @SerializedName("rating") val rating: Double? = null,
    @SerializedName("year") val year: Int? = null,
    @SerializedName("runtime") val runtime: Int? = null,
    @SerializedName("genres") val genres: List<String>? = null,
    @SerializedName("overview") val overview: String? = null
)

/** Absolute URL for a server image path ("/api/x", "api/x", "posters/1") or a full URL. */
internal fun resolveImage(path: String?): String? {
    val p = path?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    if (p.startsWith("http://") || p.startsWith("https://")) return p
    val base = com.homeflix.tv.util.ApiUtils.getBaseUrl().trimEnd('/')  // …/api
    val root = base.removeSuffix("/api")
    return when {
        p.startsWith("/api/") -> root + p
        p.startsWith("api/") -> "$root/$p"
        p.startsWith("/") -> base + p
        else -> "$base/$p"
    }
}
