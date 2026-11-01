package com.homeflix.tv.presentation.screens.tvshows

/** A TV series as returned by the server (also cached by the data layer). */
data class TvSeries(
    val id: Int,
    val title: String,
    val description: String?,
    val rating: Double,
    val year: Int?,
    val totalSeasons: Int,
    val totalEpisodes: Int,
    val genres: List<String>,
    val posterPath: String?,
    val bannerPath: String?,
    val tmdbPosterUrl: String? = null,
    val tmdbBackdropUrl: String? = null,
    val createdAt: String? = null
)

/** One episode of a season. */
data class Episode(
    val id: Int,
    val title: String,
    val episodeTitle: String? = null, // episode_title from API, more specific than title
    val description: String?,
    val duration: Int?, // in minutes
    val rating: Double,
    val airDate: String?,
    val thumbnailPath: String?,
    val episodeStillPath: String? = null // episode_still_path from API for still images
)
