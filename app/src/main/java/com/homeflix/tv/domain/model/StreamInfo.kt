package com.homeflix.tv.domain.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class StreamInfo(
    val streamUrl: String,
    val mediaType: String,
    val duration: Long,
    val subtitles: List<SubtitleTrack> = emptyList(),
    val audioTracks: List<AudioTrack> = emptyList(),
    val quality: StreamQuality = StreamQuality.AUTO
) : Parcelable

@Parcelize
data class SubtitleTrack(
    val id: Int,
    val mediaId: Int,
    val streamIndex: Int,
    val language: String,
    val title: String? = null,
    val codecName: String,
    val filePath: String? = null,
    val format: String,
    val trackType: String,
    val isDefault: Boolean = false,
    val isForced: Boolean = false,
    val isHearingImpaired: Boolean = false
) : Parcelable

@Parcelize
data class AudioTrack(
    val id: Int,
    val mediaId: Int,
    val streamIndex: Int,
    val language: String,
    val title: String? = null,
    val codecName: String,
    val channels: Int,
    val sampleRate: Int,
    val bitrate: Int,
    val trackType: String,
    val isDefault: Boolean = false
) : Parcelable

enum class StreamQuality {
    AUTO,
    SD_480P,
    HD_720P,
    HD_1080P,
    UHD_4K
}