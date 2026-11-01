package com.homeflix.tv.cast

import com.google.gson.annotations.SerializedName

/** Wire format of the HomeFlix cast protocol (see homeflix-wifi docs/TV_CAST_PROTOCOL.md). */

data class CastMedia(
    val id: Int = 0,
    val type: String? = null,
    val title: String? = null,
    @SerializedName("series_id") val seriesId: Int? = null,
    val season: Int? = null,
    val episode: Int? = null,
    val poster: String? = null
)

/** Any server → TV message. Fields not used by a given `type` stay null. */
data class CastInbound(
    val type: String? = null,
    @SerializedName("device_id") val deviceId: String? = null,
    @SerializedName("command_id") val commandId: String? = null,
    val action: String? = null,
    val media: CastMedia? = null,
    /** Seconds. */
    val position: Double? = null,
    val volume: Double? = null,
    val muted: Boolean? = null,
    val subtitle: String? = null,
    val from: String? = null
)

data class CastAck(
    @SerializedName("command_id") val commandId: String,
    val ok: Boolean,
    val error: String? = null,
    val type: String = "ack"
)

enum class CastState(val wire: String) {
    IDLE("idle"), LOADING("loading"), PLAYING("playing"), PAUSED("paused"),
    BUFFERING("buffering"), ENDED("ended"), ERROR("error")
}

data class CastStatus(
    val state: String,
    @SerializedName("media_id") val mediaId: Int? = null,
    @SerializedName("media_type") val mediaType: String? = null,
    val title: String? = null,
    /** Seconds. */
    val position: Double = 0.0,
    val duration: Double = 0.0,
    val volume: Double = 1.0,
    val muted: Boolean = false,
    val error: String = "",
    val type: String = "status"
) {
    companion object {
        val Idle = CastStatus(state = CastState.IDLE.wire)
    }
}

data class CastRename(val name: String, val type: String = "rename")
data class CastPong(val type: String = "pong")
