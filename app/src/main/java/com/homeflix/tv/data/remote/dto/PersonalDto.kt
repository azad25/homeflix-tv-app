package com.homeflix.tv.data.remote.dto

import com.google.gson.annotations.SerializedName

data class MyListCheckDto(@SerializedName("in_list") val inList: Boolean = false)

data class MyListEntryDto(
    @SerializedName("media_id") val mediaId: Int = 0,
    @SerializedName("media_type") val mediaType: String? = null,
    @SerializedName("added_at") val addedAt: String? = null
)

data class RatingDto(val kind: String? = null, val id: Int = 0, val rating: Int = 0)

data class RatingBody(val rating: Int)

data class TasteItemDto(
    val id: Int = 0,
    /** "movie" | "series" */
    val type: String? = null,
    val title: String? = null,
    val reason: String? = null
)

data class TasteRowDto(
    val id: String? = null,
    val title: String? = null,
    /** top-picks, because, actor, director, collection, genre, fallback */
    val kind: String? = null,
    val items: List<TasteItemDto>? = null
)

data class TasteResultDto(
    val personalized: Boolean = false,
    val rows: List<TasteRowDto>? = null,
    /** Strongest taste features, e.g. "gen:Action", "cast:Michael Caine". */
    @SerializedName("top_features") val topFeatures: List<String>? = null
)

data class ChartItemDto(
    val id: Int = 0,
    val type: String? = null,
    val title: String? = null,
    val rank: Int = 0,
    val plays: Int = 0
)

data class ChartDto(
    val kind: String? = null,
    @SerializedName("window_days") val windowDays: Int = 0,
    /** plays | mixed | catalog */
    val source: String? = null,
    val items: List<ChartItemDto>? = null
)
