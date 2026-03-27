package com.homeflix.tv.domain.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import java.util.Date

@Parcelize
data class PlaybackProgress(
    val id: Int,
    val mediaId: Int,
    val userId: String,
    val progress: Long, // in milliseconds
    val duration: Long, // in milliseconds
    val completed: Boolean = false,
    val lastWatched: Date,
    val createdAt: Date,
    val updatedAt: Date
) : Parcelable

@Parcelize
data class WatchlistItem(
    val id: Int,
    val mediaId: Int,
    val userId: Int,
    val media: Media,
    val addedAt: Date
) : Parcelable

@Parcelize
data class ViewHistory(
    val id: Int,
    val mediaId: Int,
    val userId: String,
    val media: Media,
    val progress: Long, // in milliseconds
    val completed: Boolean,
    val watchedAt: Date
) : Parcelable

@Parcelize
data class RecentlyWatchedItem(
    val id: Int,
    val mediaId: Int,
    val userId: String,
    val lastWatchedAt: Date,
    val progressSeconds: Long,
    val durationSeconds: Long,
    val media: Media
) : Parcelable

@Parcelize
data class Recommendation(
    val id: Int,
    val mediaId: Int,
    val userId: String,
    val media: Media,
    val score: Float,
    val reason: String,
    val category: RecommendationCategory,
    val clicked: Boolean = false,
    val clickedAt: Date? = null,
    val createdAt: Date
) : Parcelable

enum class RecommendationCategory {
    TRENDING,
    SIMILAR,
    GENRE_MATCH,
    CONTINUE_WATCHING,
    POPULAR,
    RECENT,
    TOP_RATED,
    MIXED,
    PERSONALIZED
}