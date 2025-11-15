package com.homeflix.tv.data.remote.dto

import com.google.gson.annotations.SerializedName

data class SeasonDto(
    @SerializedName("id")
    val id: Int,
    
    @SerializedName("season_number")
    val season_number: Int,
    
    @SerializedName("name")
    val name: String?,
    
    @SerializedName("overview")
    val overview: String?,
    
    @SerializedName("air_date")
    val air_date: String?,
    
    @SerializedName("episode_count")
    val episode_count: Int?,
    
    @SerializedName("poster_path")
    val poster_path: String?,
    
    @SerializedName("episodes")
    val episodes: List<MediaDto>?
)