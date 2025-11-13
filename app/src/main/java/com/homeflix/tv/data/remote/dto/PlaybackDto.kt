package com.homeflix.tv.data.remote.dto

import com.google.gson.annotations.SerializedName
import com.homeflix.tv.domain.model.*
import java.text.SimpleDateFormat
import java.util.*

data class PlaybackProgressDto(
    val id: Int,
    @SerializedName("media_id")
    val mediaId: Int,
    @SerializedName("user_id")
    val userId: String,
    val progress: Long,
    val duration: Long,
    val completed: Boolean = false,
    @SerializedName("last_watched")
    val lastWatched: String,
    @SerializedName("created_at")
    val createdAt: String,
    @SerializedName("updated_at")
    val updatedAt: String
)

data class WatchlistItemDto(
    val id: Int,
    @SerializedName("media_id")
    val mediaId: Int,
    @SerializedName("user_id")
    val userId: String,
    val media: MediaDto,
    @SerializedName("added_at")
    val addedAt: String
)

data class ViewHistoryDto(
    val id: Int,
    @SerializedName("media_id")
    val mediaId: Int,
    @SerializedName("user_id")
    val userId: String,
    val media: MediaDto,
    val progress: Long,
    val completed: Boolean,
    @SerializedName("watched_at")
    val watchedAt: String
)

data class RecentlyWatchedItemDto(
    val id: Int,
    @SerializedName("media_id")
    val mediaId: Int,
    @SerializedName("user_id")
    val userId: String,
    @SerializedName("last_watched_at")
    val lastWatchedAt: String,
    @SerializedName("progress_seconds")
    val progressSeconds: Long,
    @SerializedName("duration_seconds")
    val durationSeconds: Long,
    val media: MediaDto
)

data class RecommendationDto(
    val id: Int,
    @SerializedName("media_id")
    val mediaId: Int,
    @SerializedName("user_id")
    val userId: String,
    val media: MediaDto,
    val score: Float,
    val reason: String,
    val category: String,
    val clicked: Boolean = false,
    @SerializedName("clicked_at")
    val clickedAt: String? = null,
    @SerializedName("created_at")
    val createdAt: String
)

data class StreamInfoDto(
    @SerializedName("stream_url")
    val streamUrl: String,
    @SerializedName("media_type")
    val mediaType: String,
    val duration: Long,
    val subtitles: List<SubtitleTrackDto> = emptyList(),
    @SerializedName("audio_tracks")
    val audioTracks: List<AudioTrackDto> = emptyList(),
    val quality: String = "auto"
)

data class SubtitleTrackDto(
    val id: Int,
    @SerializedName("media_id")
    val mediaId: Int,
    @SerializedName("stream_index")
    val streamIndex: Int,
    val language: String,
    val title: String? = null,
    @SerializedName("codec_name")
    val codecName: String,
    @SerializedName("file_path")
    val filePath: String? = null,
    val format: String,
    @SerializedName("track_type")
    val trackType: String,
    @SerializedName("is_default")
    val isDefault: Boolean = false,
    @SerializedName("is_forced")
    val isForced: Boolean = false,
    @SerializedName("is_hearing_impaired")
    val isHearingImpaired: Boolean = false
)

data class AudioTrackDto(
    val id: Int,
    @SerializedName("media_id")
    val mediaId: Int,
    @SerializedName("stream_index")
    val streamIndex: Int,
    val language: String,
    val title: String? = null,
    @SerializedName("codec_name")
    val codecName: String,
    val channels: Int,
    @SerializedName("sample_rate")
    val sampleRate: Int,
    val bitrate: Int,
    @SerializedName("track_type")
    val trackType: String,
    @SerializedName("is_default")
    val isDefault: Boolean = false
)

// Extension functions to convert DTOs to domain models
fun PlaybackProgressDto.toDomain(): PlaybackProgress {
    val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault())
    
    return PlaybackProgress(
        id = id,
        mediaId = mediaId,
        userId = userId,
        progress = progress,
        duration = duration,
        completed = completed,
        lastWatched = dateFormat.parse(lastWatched) ?: Date(),
        createdAt = dateFormat.parse(createdAt) ?: Date(),
        updatedAt = dateFormat.parse(updatedAt) ?: Date()
    )
}

fun WatchlistItemDto.toDomain(): WatchlistItem {
    val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault())
    
    return WatchlistItem(
        id = id,
        mediaId = mediaId,
        userId = userId,
        media = media.toDomain(),
        addedAt = dateFormat.parse(addedAt) ?: Date()
    )
}

fun ViewHistoryDto.toDomain(): ViewHistory {
    val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault())
    
    return ViewHistory(
        id = id,
        mediaId = mediaId,
        userId = userId,
        media = media.toDomain(),
        progress = progress,
        completed = completed,
        watchedAt = dateFormat.parse(watchedAt) ?: Date()
    )
}

fun RecentlyWatchedItemDto.toDomain(): RecentlyWatchedItem {
    // Handle multiple date formats from API
    val parsedDate = try {
        // Try with timezone offset first (e.g., "2025-11-13T07:20:52+06:00")
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.getDefault()).parse(lastWatchedAt)
    } catch (e: Exception) {
        try {
            // Fallback to Z format
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault()).parse(lastWatchedAt)
        } catch (e2: Exception) {
            // Final fallback to current date
            Date()
        }
    }
    
    return RecentlyWatchedItem(
        id = id,
        mediaId = mediaId,
        userId = userId,
        lastWatchedAt = parsedDate ?: Date(),
        progressSeconds = progressSeconds,
        durationSeconds = durationSeconds,
        media = media.toDomain()
    )
}

fun RecommendationDto.toDomain(): Recommendation {
    val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault())
    
    return Recommendation(
        id = id,
        mediaId = mediaId,
        userId = userId,
        media = media.toDomain(),
        score = score,
        reason = reason,
        category = when (category.lowercase()) {
            "trending" -> RecommendationCategory.TRENDING
            "similar" -> RecommendationCategory.SIMILAR
            "genre_match" -> RecommendationCategory.GENRE_MATCH
            "continue_watching" -> RecommendationCategory.CONTINUE_WATCHING
            "popular" -> RecommendationCategory.POPULAR
            "recent" -> RecommendationCategory.RECENT
            "top_rated" -> RecommendationCategory.TOP_RATED
            "mixed" -> RecommendationCategory.MIXED
            "personalized" -> RecommendationCategory.PERSONALIZED
            else -> RecommendationCategory.MIXED
        },
        clicked = clicked,
        clickedAt = clickedAt?.let { dateFormat.parse(it) },
        createdAt = dateFormat.parse(createdAt) ?: Date()
    )
}

fun StreamInfoDto.toDomain(): StreamInfo = StreamInfo(
    streamUrl = streamUrl,
    mediaType = mediaType,
    duration = duration,
    subtitles = subtitles.map { it.toDomain() },
    audioTracks = audioTracks.map { it.toDomain() },
    quality = when (quality.lowercase()) {
        "sd", "480p" -> StreamQuality.SD_480P
        "hd", "720p" -> StreamQuality.HD_720P
        "1080p" -> StreamQuality.HD_1080P
        "4k", "uhd" -> StreamQuality.UHD_4K
        else -> StreamQuality.AUTO
    }
)

fun SubtitleTrackDto.toDomain(): com.homeflix.tv.domain.model.SubtitleTrack = 
    com.homeflix.tv.domain.model.SubtitleTrack(
        id = id,
        mediaId = mediaId,
        streamIndex = streamIndex,
        language = language,
        title = title,
        codecName = codecName,
        filePath = filePath,
        format = format,
        trackType = trackType,
        isDefault = isDefault,
        isForced = isForced,
        isHearingImpaired = isHearingImpaired
    )

fun AudioTrackDto.toDomain(): com.homeflix.tv.domain.model.AudioTrack = 
    com.homeflix.tv.domain.model.AudioTrack(
        id = id,
        mediaId = mediaId,
        streamIndex = streamIndex,
        language = language,
        title = title,
        codecName = codecName,
        channels = channels,
        sampleRate = sampleRate,
        bitrate = bitrate,
        trackType = trackType,
        isDefault = isDefault
    )