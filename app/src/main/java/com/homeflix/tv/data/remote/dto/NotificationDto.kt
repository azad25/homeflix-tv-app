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
    @SerializedName("episode_ids") val episodeIds: List<Int>?
) {
    fun toDomain(): Notification = Notification(
        id = id ?: "",
        type = type ?: "",
        title = title ?: "",
        message = message ?: "",
        backdropUrl = backdropUrl,
        posterUrl = posterUrl,
        logoUrl = logoUrl,
        rating = rating ?: 0.0,
        year = year,
        overview = overview,
        category = category,
        timestampSeconds = timestamp ?: 0L,
        read = read ?: false,
        movieIds = movieIds ?: emptyList(),
        seriesId = seriesId ?: 0,
        episodeIds = episodeIds ?: emptyList()
    )
}
