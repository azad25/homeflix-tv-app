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
) {
    
    fun getAllMedia(
        limit: Int = 100,
        offset: Int = 0,
        genre: String? = null,
        type: String? = null
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
    
    fun getMovies(limit: Int = 100, offset: Int = 0): Flow<Result<List<Media>>> = flow {
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
    
    fun getTVShows(limit: Int = 100, offset: Int = 0): Flow<Result<List<Media>>> = flow {
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
    
    fun getMediaById(id: String): Flow<Result<Media>> = flow {
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
    
    fun getMediaByGenre(
        genre: String,
        limit: Int = 100,
        offset: Int = 0
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
    
    fun searchMedia(
        query: String,
        limit: Int = 50,
        offset: Int = 0
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
    

    

    
    fun getAllGenres(): Flow<Result<List<Genre>>> = flow {
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
    suspend fun getMixedRecommendations(limit: Int = 25) = apiService.getMixedRecommendations(limit)
    suspend fun getTrendingRecommendations(limit: Int = 25) = apiService.getTrendingRecommendations(limit)
    suspend fun getPopularRecommendations(limit: Int = 25) = apiService.getPopularRecommendations(limit)
    suspend fun getRecentRecommendations(limit: Int = 25) = apiService.getRecentRecommendations(limit)
    suspend fun getPersonalizedRecommendations(limit: Int = 25) = apiService.getPersonalizedRecommendations(limit)
    suspend fun getUniqueRecommendations(limit: Int = 25) = apiService.getUniqueRecommendations(limit)
    suspend fun getTopRatedRecommendations(limit: Int = 25) = apiService.getTopRatedRecommendations(limit)
    suspend fun getGenreRecommendations(limit: Int = 25) = apiService.getGenreRecommendations(limit)
    
    // Playback methods matching web frontend
    suspend fun getContinueWatching() = apiService.getContinueWatching()
    suspend fun getRecentlyWatched() = apiService.getRecentlyWatched()
    
    // Recently watched with progress (matching web app)
    fun getRecentlyWatchedWithProgress(): Flow<Result<List<RecentlyWatchedItem>>> = flow {
        try {
            val response = apiService.getRecentlyWatchedWithProgress()
            if (response.isSuccessful) {
                val recentlyWatchedItems = response.body()?.map { it.toDomain() } ?: emptyList()
                Log.d("MediaRepository", "getRecentlyWatchedWithProgress success: ${recentlyWatchedItems.size} items")
                emit(Result.success(recentlyWatchedItems))
            } else {
                Log.e("MediaRepository", "getRecentlyWatchedWithProgress failed: ${response.code()} - ${response.message()}")
                emit(Result.failure(Exception("Failed to fetch recently watched: ${response.code()} ${response.message()}")))
            }
        } catch (e: Exception) {
            Log.e("MediaRepository", "getRecentlyWatchedWithProgress error", e)
            emit(Result.failure(e))
        }
    }
    
    // Update playback progress (matching web app)
    suspend fun updatePlaybackProgress(
        mediaId: Int,
        position: Long,
        duration: Long,
        userId: String = "1"
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
    fun getEpisodesBySeriesAndSeason(seriesId: String, season: Int): Flow<Result<List<Media>>> = flow {
        try {
            // For now, return empty list as this is a movie-focused app
            emit(Result.success(emptyList()))
        } catch (e: Exception) {
            emit(Result.failure(e))
        }
    }
}