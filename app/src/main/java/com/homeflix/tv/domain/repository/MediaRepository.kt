package com.homeflix.tv.domain.repository

import com.homeflix.tv.domain.model.*
import com.homeflix.tv.presentation.screens.tvshows.*
import kotlinx.coroutines.flow.Flow

interface MediaRepository {
    
    // Existing media methods
    fun getAllMedia(
        limit: Int = 100,
        offset: Int = 0,
        genre: String? = null,
        type: String? = null
    ): Flow<Result<List<Media>>>
    
    fun getMovies(limit: Int = 100, offset: Int = 0): Flow<Result<List<Media>>>
    
    fun getTVShows(limit: Int = 100, offset: Int = 0): Flow<Result<List<Media>>>
    
    fun getMediaById(id: String): Flow<Result<Media>>
    
    fun getMediaByGenre(
        genre: String,
        limit: Int = 100,
        offset: Int = 0
    ): Flow<Result<List<Media>>>
    
    fun searchMedia(
        query: String,
        limit: Int = 50,
        offset: Int = 0
    ): Flow<Result<List<Media>>>
    
    fun getAllGenres(): Flow<Result<List<Genre>>>
    
    fun getRecentlyWatchedWithProgress(): Flow<Result<List<RecentlyWatchedItem>>>
    
    suspend fun updatePlaybackProgress(
        mediaId: Int,
        position: Long,
        duration: Long,
        userId: String = "1"
    ): Result<Unit>
    
    fun getEpisodesBySeriesAndSeason(seriesId: String, season: Int): Flow<Result<List<Media>>>
    
    // TV Series methods
    suspend fun getTvSeries(): List<TvSeries>
    suspend fun getTvSeriesById(seriesId: Int): TvSeries
    suspend fun getTvSeriesSeasons(seriesId: Int): List<Season>
    suspend fun getTvSeriesSeason(seriesId: Int, seasonNumber: Int): Season
    suspend fun getTvSeriesEpisodes(seriesId: Int, seasonNumber: Int): List<Episode>

    // Notifications (display-only)
    suspend fun getNotifications(limit: Int = 50): List<Notification>

    // Recommendation methods
    suspend fun getMixedRecommendations(limit: Int = 25): retrofit2.Response<List<com.homeflix.tv.data.remote.dto.MediaDto>>
    suspend fun getTrendingRecommendations(limit: Int = 25): retrofit2.Response<List<com.homeflix.tv.data.remote.dto.MediaDto>>
    suspend fun getPopularRecommendations(limit: Int = 25): retrofit2.Response<List<com.homeflix.tv.data.remote.dto.MediaDto>>
    suspend fun getRecentRecommendations(limit: Int = 25): retrofit2.Response<List<com.homeflix.tv.data.remote.dto.MediaDto>>
    suspend fun getPersonalizedRecommendations(limit: Int = 25): retrofit2.Response<List<com.homeflix.tv.data.remote.dto.MediaDto>>
    suspend fun getUniqueRecommendations(limit: Int = 25): retrofit2.Response<List<com.homeflix.tv.data.remote.dto.MediaDto>>
    suspend fun getTopRatedRecommendations(limit: Int = 25): retrofit2.Response<List<com.homeflix.tv.data.remote.dto.MediaDto>>
    suspend fun getGenreRecommendations(limit: Int = 25): retrofit2.Response<List<com.homeflix.tv.data.remote.dto.MediaDto>>
    
    // Playback methods
    suspend fun getPlaybackProgress(mediaId: String): Result<PlaybackProgress?>
    suspend fun getContinueWatching(): retrofit2.Response<List<com.homeflix.tv.data.remote.dto.MediaDto>>
    suspend fun getRecentlyWatched(): retrofit2.Response<List<com.homeflix.tv.data.remote.dto.MediaDto>>
    
    // My List methods
    suspend fun getMyList(): Result<List<WatchlistItem>>
    suspend fun checkMyList(mediaId: String): Result<Boolean>
    suspend fun addToMyList(mediaId: String): Result<Unit>
    suspend fun removeFromMyList(mediaId: String): Result<Unit>
}