package com.homeflix.tv.data.remote.dto

import com.google.gson.annotations.SerializedName
import com.homeflix.tv.domain.model.*
import java.text.SimpleDateFormat
import java.util.*

data class MediaDto(
    val id: Int,
    val uuid: String,
    val title: String,
    @SerializedName("original_title")
    val originalTitle: String? = null,
    val type: String,
    @SerializedName("file_path")
    val filePath: String,
    @SerializedName("file_size")
    val fileSize: Long,
    val duration: Int,
    
    // Metadata
    val tagline: String? = null,
    @SerializedName("short_desc")
    val shortDesc: String? = null,
    @SerializedName("long_desc")
    val longDesc: String? = null,
    val description: String? = null,
    val year: Int? = null,
    @SerializedName("release_date")
    val releaseDate: String? = null,
    val rating: Double = 0.0,
    val country: String? = null,
    val language: String? = null,
    val quality: String? = null,
    
    // Cast and crew
    val stars: List<String>? = null,
    val director: List<String>? = null,
    val cast: List<String>? = null,
    val writers: List<String>? = null,
    val producers: List<String>? = null,
    
    // Box office and additional metadata
    val budget: Long? = null,
    val revenue: Long? = null,
    @SerializedName("box_office")
    val boxOffice: String? = null,
    val status: String? = null,
    @SerializedName("imdb_id")
    val imdbId: String? = null,
    val homepage: String? = null,
    val collection: String? = null,
    
    // Enhanced metadata
    val awards: List<String>? = null,
    val certification: String? = null,
    val runtime: Int? = null,
    val popularity: Double = 0.0,
    @SerializedName("vote_count")
    val voteCount: Int = 0,
    val adult: Boolean = false,
    
    // Genres
    val genres: List<GenreDto>? = null,
    @SerializedName("genre_names")
    val genreNames: List<String>? = null,
    
    // Video info
    val resolution: String? = null,
    val codec: String? = null,
    val bitrate: Int? = null,
    
    // Assets
    @SerializedName("thumbnail_path")
    val thumbnailPath: String? = null,
    @SerializedName("preview_path")
    val previewPath: String? = null,
    @SerializedName("preview_clip_path")
    val previewClipPath: String? = null,
    @SerializedName("poster_path")
    val posterPath: String? = null,
    @SerializedName("banner_path")
    val bannerPath: String? = null,
    @SerializedName("logo_path")
    val logoPath: String? = null,
    @SerializedName("trailer_path")
    val trailerPath: String? = null,
    
    // TMDB assets - exactly like web app
    @SerializedName("tmdb_backdrop_url")
    val tmdbBackdropUrl: String? = null,
    @SerializedName("tmdb_poster_url")
    val tmdbPosterUrl: String? = null,
    @SerializedName("tmdb_trailer_url")
    val tmdbTrailerUrl: String? = null,
    @SerializedName("tmdb_id")
    val tmdbId: Int? = null,
    
    // Episode-specific fields
    @SerializedName("series_id")
    val seriesId: Int? = null,
    @SerializedName("season_number")
    val seasonNumber: Int? = null,
    @SerializedName("episode_number")
    val episodeNumber: Int? = null,
    @SerializedName("episode_title")
    val episodeTitle: String? = null,
    @SerializedName("episode_still_path")
    val episodeStillPath: String? = null,
    @SerializedName("season_number_legacy")
    val seasonNumberLegacy: Int? = null,
    val episode: Int? = null,
    
    // Subtitles
    val subtitles: List<SubtitleDto>? = null,
    
    // View tracking
    @SerializedName("view_count")
    val viewCount: Int = 0,
    @SerializedName("last_viewed")
    val lastViewed: String? = null,
    
    // Metadata tracking
    @SerializedName("last_updated")
    val lastUpdated: String? = null,
    @SerializedName("created_at")
    val createdAt: String? = null,
    @SerializedName("updated_at")
    val updatedAt: String? = null
)

data class GenreDto(
    val id: Int,
    val name: String,
    val description: String? = null
)



data class SubtitleDto(
    val id: Int,
    @SerializedName("media_id")
    val mediaId: Int,
    val language: String,
    @SerializedName("file_path")
    val filePath: String,
    val format: String
)

// Extension functions to convert DTOs to domain models
fun MediaDto.toDomain(): Media {
    val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault())
    
    return Media(
        id = id,
        uuid = uuid,
        title = title,
        originalTitle = originalTitle,
        type = when (type.lowercase()) {
            "movie" -> MediaType.MOVIE
            "episode" -> MediaType.EPISODE
            "tv", "series", "tv_show" -> MediaType.TV_SHOW
            else -> MediaType.MOVIE
        },
        filePath = filePath,
        fileSize = fileSize,
        duration = duration,
        tagline = tagline,
        shortDesc = shortDesc,
        longDesc = longDesc,
        description = description,
        year = year,
        releaseDate = releaseDate?.let { 
            try { dateFormat.parse(it) } catch (e: Exception) { null }
        },
        rating = rating,
        country = country,
        language = language,
        quality = quality,
        stars = stars ?: emptyList(),
        director = director ?: emptyList(),
        cast = cast ?: emptyList(),
        writers = writers ?: emptyList(),
        producers = producers ?: emptyList(),
        budget = budget,
        revenue = revenue,
        boxOffice = boxOffice,
        status = status,
        imdbId = imdbId,
        homepage = homepage,
        collection = collection,
        awards = awards ?: emptyList(),
        certification = certification,
        runtime = runtime,
        popularity = popularity,
        voteCount = voteCount,
        adult = adult,
        genres = genres?.map { it.toDomain() } ?: emptyList(),
        genreNames = genreNames ?: emptyList(),
        resolution = resolution,
        codec = codec,
        bitrate = bitrate,
        thumbnailPath = thumbnailPath,
        previewPath = previewPath,
        previewClipPath = previewClipPath,
        posterPath = posterPath,
        bannerPath = bannerPath,
        logoPath = logoPath,
        trailerPath = trailerPath,
        
        // TMDB assets - exactly like web app
        tmdbBackdropUrl = tmdbBackdropUrl,
        tmdbPosterUrl = tmdbPosterUrl,
        tmdbTrailerUrl = tmdbTrailerUrl,
        tmdbId = tmdbId,
        
        // Episode-specific fields
        seriesId = seriesId,
        seasonNumber = seasonNumber,
        episodeNumber = episodeNumber,
        seasonNumberLegacy = seasonNumberLegacy,
        episode = episode,

        subtitles = subtitles?.map { it.toDomain() } ?: emptyList(),
        viewCount = viewCount,
        lastViewed = lastViewed?.let { 
            try { dateFormat.parse(it) } catch (e: Exception) { null }
        },
        lastUpdated = lastUpdated,
        createdAt = createdAt?.let { 
            try { dateFormat.parse(it) } catch (e: Exception) { null }
        },
        updatedAt = updatedAt?.let { 
            try { dateFormat.parse(it) } catch (e: Exception) { null }
        }
    )
}

fun GenreDto.toDomain(): Genre = Genre(
    id = id,
    name = name,
    description = description
)

fun SubtitleDto.toDomain(): Subtitle = Subtitle(
    id = id,
    mediaId = mediaId,
    language = language,
    filePath = filePath,
    format = format
)