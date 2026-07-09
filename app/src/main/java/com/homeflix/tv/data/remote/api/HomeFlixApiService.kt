package com.homeflix.tv.data.remote.api

import com.homeflix.tv.data.remote.dto.*
import retrofit2.Response
import retrofit2.http.*

interface HomeFlixApiService {
    
    // Media endpoints
    @GET("media")
    suspend fun getAllMedia(
        @Query("limit") limit: Int = 100,
        @Query("offset") offset: Int = 0,
        @Query("genre") genre: String? = null,
        @Query("type") type: String? = null
    ): Response<List<MediaDto>>
    
    @GET("media/movies")
    suspend fun getMovies(
        @Query("limit") limit: Int = 100,
        @Query("offset") offset: Int = 0
    ): Response<List<MediaDto>>
    
    @GET("media/tv-shows")
    suspend fun getTVShows(
        @Query("limit") limit: Int = 100,
        @Query("offset") offset: Int = 0
    ): Response<List<MediaDto>>
    
    @GET("media/{id}")
    suspend fun getMediaById(@Path("id") id: String): Response<MediaDto>
    
    @GET("media/genre/{genre}")
    suspend fun getMediaByGenre(
        @Path("genre") genre: String,
        @Query("limit") limit: Int = 100,
        @Query("offset") offset: Int = 0
    ): Response<List<MediaDto>>
    
    @GET("media/search")
    suspend fun searchMedia(
        @Query("q") query: String,
        @Query("limit") limit: Int = 50,
        @Query("offset") offset: Int = 0
    ): Response<List<MediaDto>>
    

    

    
    // Streaming endpoints
    @GET("stream/{id}")
    suspend fun getStreamUrl(@Path("id") id: String): Response<StreamInfoDto>
    
    @GET("preview-clips/{id}")
    suspend fun getPreviewClip(@Path("id") id: String): Response<StreamInfoDto>
    
    // Asset endpoints
    @GET("thumbnails/{id}")
    suspend fun getThumbnail(@Path("id") id: String): Response<ByteArray>
    
    @GET("posters/{id}")
    suspend fun getPoster(@Path("id") id: String): Response<ByteArray>
    
    @GET("previews/{id}")
    suspend fun getPreview(@Path("id") id: String): Response<ByteArray>
    
    // Subtitle endpoints
    @GET("media/{id}/subtitles")
    suspend fun getSubtitleTracks(@Path("id") id: String): Response<List<SubtitleTrackDto>>
    
    @GET("media/{id}/audio")
    suspend fun getAudioTracks(@Path("id") id: String): Response<List<AudioTrackDto>>
    
    @GET("media/{mediaId}/subtitles/{trackId}/file")
    suspend fun getSubtitleFile(
        @Path("mediaId") mediaId: String,
        @Path("trackId") trackId: String
    ): Response<String>
    
    // Playback endpoints
    @POST("track-view/{id}")
    suspend fun trackView(@Path("id") id: String): Response<Unit>
    
    @POST("playback/progress")
    suspend fun updatePlaybackProgress(@Body request: PlaybackProgressRequest): Response<Unit>
    
    // Alternative endpoint matching web app
    @POST("playback/progress")
    suspend fun updatePlaybackProgressAlt(
        @Header("X-User-ID") userId: String = "1",
        @Body request: PlaybackProgressAltRequest
    ): Response<Unit>
    
    @GET("playback/progress/{id}")
    suspend fun getPlaybackProgress(@Path("id") id: String): Response<PlaybackProgressDto>
    
    @POST("playback/initialize/{id}")
    suspend fun initializePlaybackProgress(@Path("id") id: String): Response<PlaybackProgressDto>
    
    @GET("playback/recent")
    suspend fun getRecentlyWatched(): Response<List<MediaDto>>
    
    @GET("playback/continue")
    suspend fun getContinueWatching(): Response<List<MediaDto>>
    
    // Recently watched with progress (matching web app)
    @GET("playback/recently-watched")
    suspend fun getRecentlyWatchedWithProgress(
        @Header("X-User-ID") userId: String = "1"
    ): Response<List<RecentlyWatchedItemDto>>
    
    @GET("playback/history")
    suspend fun getWatchHistory(): Response<List<ViewHistoryDto>>
    
    // My List endpoints
    @POST("mylist/{id}")
    suspend fun addToMyList(@Path("id") id: String): Response<Unit>
    
    @DELETE("mylist/{id}")
    suspend fun removeFromMyList(@Path("id") id: String): Response<Unit>
    
    @GET("mylist")
    suspend fun getMyList(): Response<List<WatchlistItemDto>>
    
    @GET("mylist/check/{id}")
    suspend fun checkMyList(@Path("id") id: String): Response<Boolean>
    
    // Recommendation endpoints - matching web frontend ScrollXHero.tsx
    @GET("recommendations")
    suspend fun getRecommendations(
        @Query("category") category: String? = null,
        @Query("limit") limit: Int = 20
    ): Response<List<MediaDto>>
    
    @GET("recommendations/mixed")
    suspend fun getMixedRecommendations(@Query("limit") limit: Int = 25): Response<List<MediaDto>>

    @GET("recommendations/trending")
    suspend fun getTrendingRecommendations(@Query("limit") limit: Int = 25): Response<List<MediaDto>>

    @GET("recommendations/popular")
    suspend fun getPopularRecommendations(@Query("limit") limit: Int = 25): Response<List<MediaDto>>

    @GET("recommendations/recent")
    suspend fun getRecentRecommendations(@Query("limit") limit: Int = 25): Response<List<MediaDto>>

    @GET("recommendations/personalized")
    suspend fun getPersonalizedRecommendations(@Query("limit") limit: Int = 25): Response<List<MediaDto>>

    @GET("recommendations/unique")
    suspend fun getUniqueRecommendations(@Query("limit") limit: Int = 25): Response<List<MediaDto>>

    @GET("recommendations/top-rated")
    suspend fun getTopRatedRecommendations(@Query("limit") limit: Int = 25): Response<List<MediaDto>>

    @GET("recommendations/genre")
    suspend fun getGenreRecommendations(@Query("limit") limit: Int = 25): Response<List<MediaDto>>
    
    @GET("recommendations/similar/{id}")
    suspend fun getSimilarMedia(@Path("id") id: String): Response<List<MediaDto>>
    
    @POST("recommendations/track-click/{id}")
    suspend fun trackRecommendationClick(@Path("id") id: String): Response<Unit>
    
    // Notification endpoints (display-only)
    @GET("notifications")
    suspend fun getNotifications(@Query("limit") limit: Int = 50): Response<NotificationsResponseDto>

    // Genre endpoints
    @GET("genres")
    suspend fun getAllGenres(): Response<List<GenreDto>>
    
    @GET("genres/{id}")
    suspend fun getGenreById(@Path("id") id: Int): Response<GenreDto>
    
    // TV Series endpoints (hierarchical API matching web app)
    @GET("series")
    suspend fun getTvSeries(
        @Query("sort") sort: String? = null,
        @Query("order") order: String? = null,
        @Query("limit") limit: Int? = null,
        @Query("offset") offset: Int? = null
    ): Response<List<MediaDto>>
    
    @GET("series/{id}")
    suspend fun getTvSeriesById(@Path("id") id: Int): Response<MediaDto>
    
    @GET("series/{id}/seasons")
    suspend fun getTvSeriesSeasons(@Path("id") id: Int): Response<List<SeasonDto>>
    
    @GET("series/{id}/seasons/{season}")
    suspend fun getTvSeriesSeason(
        @Path("id") id: Int,
        @Path("season") seasonNumber: Int
    ): Response<SeasonDto>
    
    @GET("series/{id}/seasons/{season}/episodes")
    suspend fun getTvSeriesEpisodes(
        @Path("id") id: Int,
        @Path("season") seasonNumber: Int
    ): Response<List<MediaDto>>
}

// Response wrapper classes
data class MediaResponse(
    val data: List<MediaDto>,
    val total: Int,
    val limit: Int,
    val offset: Int
)

data class PlaybackProgressRequest(
    val mediaId: String,
    val progress: Long,
    val duration: Long,
    val userId: String = "default"
)

data class PlaybackProgressAltRequest(
    val media_id: Int,
    val position: Long,
    val duration: Long
)