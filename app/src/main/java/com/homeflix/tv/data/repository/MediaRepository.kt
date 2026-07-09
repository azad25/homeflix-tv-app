package com.homeflix.tv.data.repository

import android.util.Log
import com.homeflix.tv.data.remote.api.HomeFlixApiService
import com.homeflix.tv.data.remote.dto.toDomain
import com.homeflix.tv.domain.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MediaRepository @Inject constructor(
    private val apiService: HomeFlixApiService
) : com.homeflix.tv.domain.repository.MediaRepository {
    
    override fun getAllMedia(
        limit: Int,
        offset: Int,
        genre: String?,
        type: String?
    ): Flow<Result<List<Media>>> = flow {
        try {
            val response = apiService.getAllMedia(limit, offset, genre, type)
            if (response.isSuccessful) {
                val mediaList = response.body()?.map { it.toDomain() } ?: emptyList()
                Log.d("MediaRepository", "getAllMedia success: ${mediaList.size} items")
                emit(Result.success(mediaList))
            } else {
                Log.e("MediaRepository", "getAllMedia failed: ${response.code()} - ${response.message()}")
                emit(Result.failure(Exception("Failed to fetch media: ${response.code()} ${response.message()}")))
            }
        } catch (e: Exception) {
            Log.e("MediaRepository", "getAllMedia error", e)
            emit(Result.failure(e))
        }
    }
    
    override fun getMovies(limit: Int, offset: Int): Flow<Result<List<Media>>> = flow {
        try {
            val response = apiService.getMovies(limit, offset)
            if (response.isSuccessful) {
                val movies = response.body()?.map { it.toDomain() } ?: emptyList()
                Log.d("MediaRepository", "getMovies success: ${movies.size} items")
                emit(Result.success(movies))
            } else {
                Log.e("MediaRepository", "getMovies failed: ${response.code()} - ${response.message()}")
                emit(Result.failure(Exception("Failed to fetch movies: ${response.code()} ${response.message()}")))
            }
        } catch (e: Exception) {
            Log.e("MediaRepository", "getMovies error", e)
            emit(Result.failure(e))
        }
    }
    
    override fun getTVShows(limit: Int, offset: Int): Flow<Result<List<Media>>> = flow {
        try {
            val response = apiService.getTVShows(limit, offset)
            if (response.isSuccessful) {
                val tvShows = response.body()?.map { it.toDomain() } ?: emptyList()
                Log.d("MediaRepository", "getTVShows success: ${tvShows.size} items")
                emit(Result.success(tvShows))
            } else {
                Log.e("MediaRepository", "getTVShows failed: ${response.code()} - ${response.message()}")
                emit(Result.failure(Exception("Failed to fetch TV shows: ${response.code()} ${response.message()}")))
            }
        } catch (e: Exception) {
            Log.e("MediaRepository", "getTVShows error", e)
            emit(Result.failure(e))
        }
    }
    
    override fun getMediaById(id: String): Flow<Result<Media>> = flow {
        try {
            val response = apiService.getMediaById(id)
            if (response.isSuccessful) {
                val media = response.body()?.toDomain()
                if (media != null) {
                    emit(Result.success(media))
                } else {
                    emit(Result.failure(Exception("Media not found")))
                }
            } else {
                emit(Result.failure(Exception("Failed to fetch media: ${response.message()}")))
            }
        } catch (e: Exception) {
            emit(Result.failure(e))
        }
    }
    
    override fun getMediaByGenre(
        genre: String,
        limit: Int,
        offset: Int
    ): Flow<Result<List<Media>>> = flow {
        try {
            val response = apiService.getMediaByGenre(genre, limit, offset)
            if (response.isSuccessful) {
                val mediaList = response.body()?.map { it.toDomain() } ?: emptyList()
                emit(Result.success(mediaList))
            } else {
                emit(Result.failure(Exception("Failed to fetch media by genre: ${response.message()}")))
            }
        } catch (e: Exception) {
            emit(Result.failure(e))
        }
    }
    
    override fun searchMedia(
        query: String,
        limit: Int,
        offset: Int
    ): Flow<Result<List<Media>>> = flow {
        try {
            val response = apiService.searchMedia(query, limit, offset)
            if (response.isSuccessful) {
                val searchResults = response.body()?.map { it.toDomain() } ?: emptyList()
                emit(Result.success(searchResults))
            } else {
                emit(Result.failure(Exception("Failed to search media: ${response.message()}")))
            }
        } catch (e: Exception) {
            emit(Result.failure(e))
        }
    }
    

    

    
    override fun getAllGenres(): Flow<Result<List<Genre>>> = flow {
        try {
            val response = apiService.getAllGenres()
            if (response.isSuccessful) {
                val genres = response.body()?.map { it.toDomain() } ?: emptyList()
                emit(Result.success(genres))
            } else {
                emit(Result.failure(Exception("Failed to fetch genres: ${response.message()}")))
            }
        } catch (e: Exception) {
            emit(Result.failure(e))
        }
    }

    // Recommendation methods matching web frontend ScrollXHero.tsx
    override suspend fun getMixedRecommendations(limit: Int) = apiService.getMixedRecommendations(limit)
    override suspend fun getTrendingRecommendations(limit: Int) = apiService.getTrendingRecommendations(limit)
    override suspend fun getPopularRecommendations(limit: Int) = apiService.getPopularRecommendations(limit)
    override suspend fun getRecentRecommendations(limit: Int) = apiService.getRecentRecommendations(limit)
    override suspend fun getPersonalizedRecommendations(limit: Int) = apiService.getPersonalizedRecommendations(limit)
    override suspend fun getUniqueRecommendations(limit: Int) = apiService.getUniqueRecommendations(limit)
    override suspend fun getTopRatedRecommendations(limit: Int) = apiService.getTopRatedRecommendations(limit)
    override suspend fun getGenreRecommendations(limit: Int) = apiService.getGenreRecommendations(limit)
    
    // Playback methods matching web frontend
    override suspend fun getPlaybackProgress(mediaId: String): Result<com.homeflix.tv.domain.model.PlaybackProgress?> {
        return try {
            val response = apiService.getPlaybackProgress(mediaId)
            if (response.isSuccessful) {
                val dto = response.body()
                if (dto != null) {
                    Result.success(dto.toDomain())
                } else {
                    Result.success(null)
                }
            } else if (response.code() == 404) {
                // No progress found — not an error
                Result.success(null)
            } else {
                Result.failure(Exception("Failed to get playback progress: ${response.code()}"))
            }
        } catch (e: Exception) {
            Log.w("MediaRepository", "Error fetching playback progress for $mediaId", e)
            Result.success(null) // Fallback to no progress
        }
    }
    override suspend fun getContinueWatching() = apiService.getContinueWatching()
    override suspend fun getRecentlyWatched() = apiService.getRecentlyWatched()
    
    // Recently watched with progress (matching web app)
    override fun getRecentlyWatchedWithProgress(): Flow<Result<List<RecentlyWatchedItem>>> = flow {
        try {
            Log.d("MediaRepository", "Calling getRecentlyWatchedWithProgress API...")
            Log.d("MediaRepository", "Base URL: ${com.homeflix.tv.BuildConfig.BASE_URL}")
            Log.d("MediaRepository", "Full URL should be: ${com.homeflix.tv.BuildConfig.BASE_URL}playback/recently-watched")
            
            val response = apiService.getRecentlyWatchedWithProgress()
            Log.d("MediaRepository", "API response code: ${response.code()}")
            Log.d("MediaRepository", "API response message: ${response.message()}")
            
            if (response.isSuccessful) {
                val rawItems = response.body() ?: emptyList()
                Log.d("MediaRepository", "Raw API response: ${rawItems.size} items")
                
                if (rawItems.isNotEmpty()) {
                    Log.d("MediaRepository", "First item sample: mediaId=${rawItems[0].mediaId}, title=${rawItems[0].media.title}")
                }
                
                val recentlyWatchedItems = rawItems.map { dto ->
                    Log.d("MediaRepository", "Processing item: mediaId=${dto.mediaId}, progress=${dto.progressSeconds}/${dto.durationSeconds}")
                    dto.toDomain()
                }
                
                Log.d("MediaRepository", "getRecentlyWatchedWithProgress success: ${recentlyWatchedItems.size} items")
                emit(Result.success(recentlyWatchedItems))
            } else {
                val errorBody = response.errorBody()?.string()
                Log.e("MediaRepository", "getRecentlyWatchedWithProgress failed: ${response.code()} - ${response.message()}")
                Log.e("MediaRepository", "Error body: $errorBody")
                Log.e("MediaRepository", "Request URL: ${response.raw().request.url}")
                emit(Result.failure(Exception("Failed to fetch recently watched: ${response.code()} ${response.message()}")))
            }
        } catch (e: Exception) {
            Log.e("MediaRepository", "getRecentlyWatchedWithProgress error: ${e.message}", e)
            emit(Result.failure(e))
        }
    }
    
    // Update playback progress (matching web app)
    override suspend fun updatePlaybackProgress(
        mediaId: Int,
        position: Long,
        duration: Long,
        userId: String
    ): Result<Unit> {
        return try {
            val request = com.homeflix.tv.data.remote.api.PlaybackProgressAltRequest(
                media_id = mediaId,
                position = position,
                duration = duration
            )
            
            val response = apiService.updatePlaybackProgressAlt(userId, request)
            if (response.isSuccessful) {
                Log.d("MediaRepository", "updatePlaybackProgress success: mediaId=$mediaId, position=$position, duration=$duration")
                Result.success(Unit)
            } else {
                Log.e("MediaRepository", "updatePlaybackProgress failed: ${response.code()} - ${response.message()}")
                Result.failure(Exception("Failed to update playback progress: ${response.code()} ${response.message()}"))
            }
        } catch (e: Exception) {
            Log.e("MediaRepository", "updatePlaybackProgress error", e)
            Result.failure(e)
        }
    }
    
    // Episode methods for TV shows
    override fun getEpisodesBySeriesAndSeason(seriesId: String, season: Int): Flow<Result<List<Media>>> = flow {
        try {
            // For now, return empty list as this is a movie-focused app
            emit(Result.success(emptyList()))
        } catch (e: Exception) {
            emit(Result.failure(e))
        }
    }
    
    // TV Series methods
    override suspend fun getTvSeries(): List<com.homeflix.tv.presentation.screens.tvshows.TvSeries> {
        return try {
            Log.d("MediaRepository", "Fetching TV series from API...")
            
            // First try the hierarchical series API, sorted by newest first
            val seriesResponse = apiService.getTvSeries(
                sort = "created_at",
                order = "desc"
            )
            if (seriesResponse.isSuccessful) {
                val seriesData = seriesResponse.body() ?: emptyList()
                Log.d("MediaRepository", "Found ${seriesData.size} series from /api/series")
                
                return seriesData.map { dto ->
                    val series = com.homeflix.tv.presentation.screens.tvshows.TvSeries(
                        id = dto.id,
                        title = dto.title,
                        description = dto.description ?: dto.longDesc ?: dto.shortDesc,
                        rating = dto.rating,
                        year = dto.year ?: dto.releaseDate?.substring(0, 4)?.toIntOrNull(),
                        totalSeasons = 0, // Will be calculated from episodes
                        totalEpisodes = 0, // Will be calculated from episodes
                        genres = dto.genres?.map { it.name } ?: dto.genreNames ?: emptyList(),
                        posterPath = dto.posterPath,
                        bannerPath = dto.bannerPath,
                        createdAt = dto.createdAt
                    )
                    Log.d("MediaRepository", "Series: ${series.title}, ID: ${series.id}, PosterPath: ${series.posterPath}")
                    series
                }
            }
            
            Log.d("MediaRepository", "Series API not available, building from episodes...")
            
            // Fallback: Build series from episodes
            val allMediaResponse = apiService.getAllMedia(limit = 1000, offset = 0, genre = null, type = "episode")
            if (allMediaResponse.isSuccessful) {
                val episodes = allMediaResponse.body() ?: emptyList()
                Log.d("MediaRepository", "Found ${episodes.size} episodes to group into series")
                
                // Group episodes by series
                val seriesMap = mutableMapOf<String, MutableList<com.homeflix.tv.data.remote.dto.MediaDto>>()
                episodes.forEach { episode ->
                    val seriesTitle = episode.title.replace(Regex("\\s*-\\s*S\\d+E\\d+.*$"), "")
                    seriesMap.getOrPut(seriesTitle) { mutableListOf() }.add(episode)
                }
                
                Log.d("MediaRepository", "Grouped episodes into ${seriesMap.size} series")
                
                // Convert to TvSeries objects
                return seriesMap.entries.mapIndexed { index, (title, episodeList) ->
                    val firstEpisode = episodeList.first()
                    val seasons = episodeList.mapNotNull { ep ->
                        ep.title?.let { title ->
                            val seasonMatch = Regex("[Ss](\\d+)[Ee](\\d+)|[Ss]eason\\s*(\\d+)").find(title)
                            seasonMatch?.groupValues?.get(1)?.toIntOrNull() 
                                ?: seasonMatch?.groupValues?.get(3)?.toIntOrNull()
                        }
                    }.distinct().size
                    
                    com.homeflix.tv.presentation.screens.tvshows.TvSeries(
                        id = firstEpisode.seriesId ?: (1000 + index),
                        title = title,
                        description = firstEpisode.description,
                        rating = firstEpisode.rating,
                        year = firstEpisode.releaseDate?.substring(0, 4)?.toIntOrNull(),
                        totalSeasons = seasons,
                        totalEpisodes = episodeList.size,
                        genres = firstEpisode.genres?.map { it.name } ?: firstEpisode.genreNames ?: emptyList(),
                        posterPath = firstEpisode.posterPath ?: firstEpisode.thumbnailPath,
                        bannerPath = firstEpisode.bannerPath,
                        createdAt = firstEpisode.createdAt
                    )
                }.sortedByDescending { it.createdAt }
            }
            
            Log.w("MediaRepository", "No TV series data available")
            emptyList()
        } catch (e: Exception) {
            Log.e("MediaRepository", "getTvSeries error", e)
            emptyList()
        }
    }
    
    override suspend fun getTvSeriesById(seriesId: Int): com.homeflix.tv.presentation.screens.tvshows.TvSeries {
        return try {
            val response = apiService.getTvSeriesById(seriesId)
            if (response.isSuccessful) {
                val dto = response.body()!!
                com.homeflix.tv.presentation.screens.tvshows.TvSeries(
                    id = dto.id,
                    title = dto.title,
                    description = dto.description ?: dto.longDesc ?: dto.shortDesc,
                    rating = dto.rating,
                    year = dto.year ?: dto.releaseDate?.substring(0, 4)?.toIntOrNull(),
                    totalSeasons = 0, // Will be calculated from episodes
                    totalEpisodes = 0, // Will be calculated from episodes
                    genres = dto.genres?.map { it.name } ?: dto.genreNames ?: emptyList(),
                    posterPath = dto.posterPath,
                    bannerPath = dto.bannerPath
                )
            } else {
                // Fallback to default
                com.homeflix.tv.presentation.screens.tvshows.TvSeries(
                    id = seriesId,
                    title = "Unknown Series",
                    description = "Series not found",
                    rating = 0.0,
                    year = null,
                    totalSeasons = 0,
                    totalEpisodes = 0,
                    genres = emptyList(),
                    posterPath = null,
                    bannerPath = null
                )
            }
        } catch (e: Exception) {
            Log.e("MediaRepository", "getTvSeriesById error", e)
            com.homeflix.tv.presentation.screens.tvshows.TvSeries(
                id = seriesId,
                title = "Error",
                description = "Failed to load series",
                rating = 0.0,
                year = null,
                totalSeasons = 0,
                totalEpisodes = 0,
                genres = emptyList(),
                posterPath = null,
                bannerPath = null
            )
        }
    }
    
    override suspend fun getTvSeriesSeasons(seriesId: Int): List<com.homeflix.tv.presentation.screens.tvshows.Season> {
        return try {
            val response = apiService.getTvSeriesSeasons(seriesId)
            if (response.isSuccessful) {
                val seasons = response.body() ?: emptyList()
                seasons.map { dto ->
                    com.homeflix.tv.presentation.screens.tvshows.Season(
                        id = dto.id,
                        seasonNumber = dto.season_number,
                        name = dto.name ?: "Season ${dto.season_number}",
                        description = dto.overview ?: "Season ${dto.season_number} of the series",
                        episodeCount = dto.episode_count ?: 0,
                        posterPath = dto.poster_path
                    )
                }
            } else {
                // Fallback to mock data
                (1..3).map { seasonNum ->
                    com.homeflix.tv.presentation.screens.tvshows.Season(
                        id = seasonNum,
                        seasonNumber = seasonNum,
                        name = "Season $seasonNum",
                        description = "Season $seasonNum of the series",
                        episodeCount = 10,
                        posterPath = null
                    )
                }
            }
        } catch (e: Exception) {
            Log.e("MediaRepository", "getTvSeriesSeasons error", e)
            emptyList()
        }
    }
    
    override suspend fun getTvSeriesSeason(seriesId: Int, seasonNumber: Int): com.homeflix.tv.presentation.screens.tvshows.Season {
        return try {
            val response = apiService.getTvSeriesSeason(seriesId, seasonNumber)
            if (response.isSuccessful) {
                val dto = response.body()!!
                com.homeflix.tv.presentation.screens.tvshows.Season(
                    id = dto.id,
                    seasonNumber = dto.season_number,
                    name = dto.name ?: "Season ${dto.season_number}",
                    description = dto.overview ?: "Season ${dto.season_number} of the series",
                    episodeCount = dto.episode_count ?: 0,
                    posterPath = dto.poster_path
                )
            } else {
                // Fallback
                com.homeflix.tv.presentation.screens.tvshows.Season(
                    id = seasonNumber,
                    seasonNumber = seasonNumber,
                    name = "Season $seasonNumber",
                    description = "Season $seasonNumber of the series",
                    episodeCount = 10,
                    posterPath = null
                )
            }
        } catch (e: Exception) {
            Log.e("MediaRepository", "getTvSeriesSeason error", e)
            com.homeflix.tv.presentation.screens.tvshows.Season(
                id = seasonNumber,
                seasonNumber = seasonNumber,
                name = "Season $seasonNumber",
                description = "Error loading season",
                episodeCount = 0,
                posterPath = null
            )
        }
    }
    
    override suspend fun getTvSeriesEpisodes(seriesId: Int, seasonNumber: Int): List<com.homeflix.tv.presentation.screens.tvshows.Episode> {
        return try {
            val response = apiService.getTvSeriesEpisodes(seriesId, seasonNumber)
            if (response.isSuccessful) {
                val episodes = response.body() ?: emptyList()
                episodes.mapIndexed { index, dto ->
                    com.homeflix.tv.presentation.screens.tvshows.Episode(
                        id = dto.id,
                        title = dto.episodeTitle ?: dto.title ?: "Episode ${index + 1}",
                        episodeTitle = dto.episodeTitle,
                        description = dto.description ?: "Episode ${index + 1} of Season $seasonNumber",
                        duration = dto.duration?.div(60) ?: 45, // Convert seconds to minutes
                        rating = dto.rating ?: 0.0,
                        airDate = dto.releaseDate,
                        thumbnailPath = dto.thumbnailPath,
                        episodeStillPath = dto.episodeStillPath
                    )
                }
            } else {
                // Fallback to mock data
                (1..10).map { episodeNum ->
                    com.homeflix.tv.presentation.screens.tvshows.Episode(
                        id = (seriesId * 1000) + (seasonNumber * 100) + episodeNum,
                        title = "Episode $episodeNum",
                        description = "Episode $episodeNum of Season $seasonNumber",
                        duration = 45,
                        rating = 8.0 + (episodeNum * 0.1),
                        airDate = "2023-01-${episodeNum.toString().padStart(2, '0')}",
                        thumbnailPath = null
                    )
                }
            }
        } catch (e: Exception) {
            Log.e("MediaRepository", "getTvSeriesEpisodes error", e)
            emptyList()
        }
    }

    // Notifications (display-only)
    override suspend fun getNotifications(limit: Int): List<com.homeflix.tv.domain.model.Notification> {
        return try {
            val response = apiService.getNotifications(limit)
            if (response.isSuccessful) {
                response.body()?.notifications?.map { it.toDomain() } ?: emptyList()
            } else {
                Log.e("MediaRepository", "getNotifications failed: ${response.code()}")
                emptyList()
            }
        } catch (e: Exception) {
            Log.e("MediaRepository", "getNotifications error", e)
            emptyList()
        }
    }

    // My List methods
    override suspend fun getMyList(): Result<List<com.homeflix.tv.domain.model.WatchlistItem>> {
        return try {
            val response = apiService.getMyList()
            if (response.isSuccessful) {
                val items = response.body()?.map { it.toDomain() } ?: emptyList()
                Log.d("MediaRepository", "getMyList success: ${items.size} items")
                Result.success(items)
            } else {
                Log.e("MediaRepository", "getMyList failed: ${response.code()} - ${response.message()}")
                Result.failure(Exception("Failed to fetch my list: ${response.code()}"))
            }
        } catch (e: Exception) {
            Log.e("MediaRepository", "getMyList error", e)
            Result.failure(e)
        }
    }
    
    override suspend fun checkMyList(mediaId: String): Result<Boolean> {
        return try {
            val response = apiService.checkMyList(mediaId)
            if (response.isSuccessful) {
                Result.success(response.body() ?: false)
            } else if (response.code() == 404) {
                Result.success(false)
            } else {
                Result.failure(Exception("Failed to check my list: ${response.code()}"))
            }
        } catch (e: Exception) {
            Log.e("MediaRepository", "checkMyList error", e)
            Result.failure(e)
        }
    }
    
    override suspend fun addToMyList(mediaId: String): Result<Unit> {
        return try {
            val response = apiService.addToMyList(mediaId)
            if (response.isSuccessful) {
                Log.d("MediaRepository", "addToMyList success: $mediaId")
                Result.success(Unit)
            } else {
                Log.e("MediaRepository", "addToMyList failed: ${response.code()}")
                Result.failure(Exception("Failed to add to my list: ${response.code()}"))
            }
        } catch (e: Exception) {
            Log.e("MediaRepository", "addToMyList error", e)
            Result.failure(e)
        }
    }
    
    override suspend fun removeFromMyList(mediaId: String): Result<Unit> {
        return try {
            val response = apiService.removeFromMyList(mediaId)
            if (response.isSuccessful) {
                Log.d("MediaRepository", "removeFromMyList success: $mediaId")
                Result.success(Unit)
            } else {
                Log.e("MediaRepository", "removeFromMyList failed: ${response.code()}")
                Result.failure(Exception("Failed to remove from my list: ${response.code()}"))
            }
        } catch (e: Exception) {
            Log.e("MediaRepository", "removeFromMyList error", e)
            Result.failure(e)
        }
    }
}