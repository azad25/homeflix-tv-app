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
    // Backend semantics: position = seconds played, progress = percentage
    // (0-100), duration = seconds. All floats.
    val position: Double = 0.0,
    val progress: Double = 0.0,
    val duration: Double = 0.0,
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
    val userId: Int,
    @SerializedName("media_type")
    val mediaType: String? = null,
    val notes: String? = null,
    val priority: Int = 0,
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
    // Try multiple date formats — API may return different formats
    fun parseDate(dateStr: String): Date {
        val formats = listOf(
            "yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'",
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd'T'HH:mm:ssXXX",
            "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
            "yyyy-MM-dd HH:mm:ss"
        )
        for (fmt in formats) {
            try {
                return SimpleDateFormat(fmt, Locale.getDefault()).parse(dateStr) ?: continue
            } catch (_: Exception) { }
        }
        // Fallback: strip timezone and try basic format
        try {
            val cleaned = dateStr.replace(Regex("[+-]\\d{2}:\\d{2}$"), "Z")
                .replace(Regex("\\.\\d+"), "")
            return SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault()).parse(cleaned) ?: Date()
        } catch (_: Exception) {
            return Date()
        }
    }
    
    return PlaybackProgress(
        id = id,
        mediaId = mediaId,
        userId = userId,
        // Domain contract: progress = seconds played. The backend keeps the
        // seconds in `position` (its `progress` field is a 0-100 percentage).
        progress = position.toLong(),
        duration = duration.toLong(),
        completed = completed,
        lastWatched = parseDate(lastWatched),
        createdAt = parseDate(createdAt),
        updatedAt = parseDate(updatedAt)
    )
}

fun WatchlistItemDto.toDomain(): WatchlistItem {
    val parsedDate = try {
        val dateStr = addedAt.replace(Regex("[+-]\\d{2}:\\d{2}$"), "Z")
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault()).parse(dateStr)
    } catch (e: Exception) {
        try {
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
                .parse(addedAt.substringBefore("+").substringBefore("Z"))
        } catch (e2: Exception) {
            Date()
        }
    }
    
    return WatchlistItem(
        id = id,
        mediaId = mediaId,
        userId = userId,
        media = media.toDomain(),
        addedAt = parsedDate ?: Date()
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
        // Use Z pattern for API 23 compatibility, manually handle timezone offset
        val dateStr = lastWatchedAt.replace(Regex("[+-]\\d{2}:\\d{2}$"), "Z")
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault()).parse(dateStr)
    } catch (e: Exception) {
        try {
            // Fallback to parsing without timezone for API 23
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
                .parse(lastWatchedAt.substringBefore("+").substringBefore("Z"))
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