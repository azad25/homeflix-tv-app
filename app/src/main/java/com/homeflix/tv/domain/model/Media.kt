package com.homeflix.tv.domain.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import java.util.Date

@Parcelize
data class Media(
    val id: Int,
    val uuid: String,
    val title: String,
    val originalTitle: String? = null,
    val type: MediaType,
    val filePath: String,
    val fileSize: Long,
    val duration: Int, // in seconds
    
    // Metadata
    val tagline: String? = null,
    val shortDesc: String? = null,
    val longDesc: String? = null,
    val description: String? = null,
    val year: Int? = null,
    val releaseDate: Date? = null,
    val rating: Double = 0.0,
    val country: String? = null,
    val language: String? = null,
    val quality: String? = null,
    
    // Cast and crew
    val stars: List<String> = emptyList(),
    val director: List<String> = emptyList(),
    val cast: List<String> = emptyList(),
    val writers: List<String> = emptyList(),
    val producers: List<String> = emptyList(),
    
    // Box office and additional metadata
    val budget: Long? = null,
    val revenue: Long? = null,
    val boxOffice: String? = null,
    val status: String? = null,
    val imdbId: String? = null,
    val homepage: String? = null,
    val collection: String? = null,
    
    // Enhanced metadata
    val awards: List<String> = emptyList(),
    val certification: String? = null,
    val runtime: Int? = null,
    val popularity: Double = 0.0,
    val voteCount: Int = 0,
    val adult: Boolean = false,
    
    // Genres
    val genres: List<Genre> = emptyList(),
    val genreNames: List<String> = emptyList(),
    
    // Video info
    val resolution: String? = null,
    val codec: String? = null,
    val bitrate: Int? = null,
    
    // Assets
    val thumbnailPath: String? = null,
    val previewPath: String? = null,
    val previewClipPath: String? = null,
    val posterPath: String? = null,
    val bannerPath: String? = null,
    val logoPath: String? = null,
    val trailerPath: String? = null,
    
    // TMDB assets - exactly like web app
    val tmdbBackdropUrl: String? = null,
    val tmdbPosterUrl: String? = null,
    val tmdbTrailerUrl: String? = null,
    val tmdbId: Int? = null,
    
    // Episode-specific fields
    val seriesId: Int? = null,
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,
    val seasonNumberLegacy: Int? = null, // For backward compatibility
    val episode: Int? = null, // For backward compatibility
    
    // Subtitles
    val subtitles: List<Subtitle> = emptyList(),
    
    // View tracking
    val viewCount: Int = 0,
    val lastViewed: Date? = null,
    
    // Metadata tracking
    val lastUpdated: String? = null,
    val createdAt: Date? = null,
    val updatedAt: Date? = null
) : Parcelable

enum class MediaType {
    MOVIE,
    EPISODE,
    TV_SHOW
}

@Parcelize
data class Genre(
    val id: Int,
    val name: String,
    val description: String? = null
) : Parcelable



@Parcelize
data class Subtitle(
    val id: Int,
    val mediaId: Int,
    val language: String,
    val filePath: String,
    val format: String
) : Parcelable