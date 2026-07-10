package com.homeflix.tv.presentation.screens.home

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.homeflix.tv.BuildConfig
import com.homeflix.tv.data.repository.MediaRepository
import com.homeflix.tv.data.remote.dto.toDomain
import com.homeflix.tv.domain.model.Media
import com.homeflix.tv.domain.model.MediaType
import com.homeflix.tv.presentation.components.ContinueWatchingItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * HOME SCREEN - CACHED FOR INSTANT STARTUP
 * This screen uses aggressive caching to provide instant app startup.
 * Content is cached for 24 hours and refreshed in the background.
 * 
 * Browse screen does NOT use caching to always show fresh content.
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val mediaRepository: MediaRepository,
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context
) : ViewModel() {
    
    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()
    
    // Recommendation cycling system matching web frontend
    private var cycleCount = 0
    private val recommendationEndpoints = listOf(
        "mixed", "trending", "popular", "recent", 
        "personalized", "unique", "top-rated", "genre"
    )
    
    init {
        // Clear expired cache on startup (24-hour expiration)
        viewModelScope.launch {
            com.homeflix.tv.data.persistence.ContentCache.clearExpiredCache(context)
        }
        loadHomeContent()
    }
    
    fun loadHomeContent() {
        viewModelScope.launch {
            try {
                // CACHE-FIRST: paint instantly from cache, refresh silently in
                // the background. Only show the spinner on a true cold start.
                val cachedMovies = com.homeflix.tv.data.persistence.ContentCache.getMediaList(context, "movies")
                if (cachedMovies != null && cachedMovies.isNotEmpty()) {
                    displayCachedContent(cachedMovies)
                } else {
                    _uiState.value = HomeUiState.Loading
                }
                
                // Load movies content first
                mediaRepository.getMovies(limit = 100).collect { result ->
                    result.fold(
                        onSuccess = { movies ->
                            if (movies.isEmpty()) {
                                _uiState.value = HomeUiState.Error("No movies available in database")
                                return@collect
                            }
                            
                            // EXACTLY like web app: Sort by creation date for latest content
                            val sortedByDate = movies.sortedByDescending { 
                                it.createdAt?.time ?: it.id.toLong() // Use creation date or ID as fallback
                            }
                            
                            // Sort by view count for popular content
                            val sortedByViews = movies.sortedByDescending { it.viewCount }
                            
                            // Sort by rating for trending content  
                            val sortedByRating = movies.sortedByDescending { it.rating }
                            
                            // ALWAYS show latest content in hero slider (like browse screen)
                            val featuredMedia = sortedByDate.take(8) // Top 8 latest movies for hero slider
                            
                            // Fetch trending from dedicated API endpoint
                            val trendingMovies = try {
                                val trendingResponse = mediaRepository.getTrendingRecommendations(20)
                                if (trendingResponse.isSuccessful) {
                                    trendingResponse.body()?.map { it.toDomain() } ?: sortedByRating.take(20)
                                } else sortedByRating.take(20)
                            } catch (e: Exception) {
                                Log.w("HomeViewModel", "Trending API failed, using fallback", e)
                                sortedByRating.take(20)
                            }
                            
                            // TOP 10: deterministic most-watched ranking. The
                            // /recommendations/popular endpoint returns a RANDOM
                            // order on every call, which made Top 10 reshuffle on
                            // each visit — rank by view count (stable tie-breaks).
                            val popularMovies = movies
                                .sortedWith(
                                    compareByDescending<Media> { it.viewCount }
                                        .thenByDescending { it.rating }
                                        .thenBy { it.id }
                                )
                                .take(20)
                            
                            val latestMovies = sortedByDate.take(20) // Latest by creation date
                            
                            // Fetch genre rows from dedicated API endpoints
                            val actionMovies = try {
                                val r = mediaRepository.getMediaByGenre("action", 15, 0)
                                var result = emptyList<Media>()
                                r.collect { res -> if (res.isSuccess) result = res.getOrNull() ?: emptyList() }
                                result.ifEmpty { sortedByDate.filter { m -> m.genres.any { it.name.contains("Action", ignoreCase = true) } }.take(15) }
                            } catch (e: Exception) {
                                sortedByDate.filter { m -> m.genres.any { it.name.contains("Action", ignoreCase = true) } }.take(15)
                            }
                            
                            val dramaMovies = try {
                                val r = mediaRepository.getMediaByGenre("drama", 15, 0)
                                var result = emptyList<Media>()
                                r.collect { res -> if (res.isSuccess) result = res.getOrNull() ?: emptyList() }
                                result.ifEmpty { sortedByDate.filter { m -> m.genres.any { it.name.contains("Drama", ignoreCase = true) } }.take(15) }
                            } catch (e: Exception) {
                                sortedByDate.filter { m -> m.genres.any { it.name.contains("Drama", ignoreCase = true) } }.take(15)
                            }
                            
                            val sciFiMovies = try {
                                val r = mediaRepository.getMediaByGenre("sci-fi", 15, 0)
                                var result = emptyList<Media>()
                                r.collect { res -> if (res.isSuccess) result = res.getOrNull() ?: emptyList() }
                                result.ifEmpty { sortedByDate.filter { m -> m.genres.any { it.name.contains("Science Fiction", ignoreCase = true) || it.name.contains("Sci-Fi", ignoreCase = true) } }.take(15) }
                            } catch (e: Exception) {
                                sortedByDate.filter { m -> m.genres.any { it.name.contains("Science Fiction", ignoreCase = true) || it.name.contains("Sci-Fi", ignoreCase = true) } }.take(15)
                            }
                            
                            // Cache the movies for next time
                            viewModelScope.launch {
                                com.homeflix.tv.data.persistence.ContentCache.saveMediaList(context, "movies", movies)
                            }
                            
                            // Load recently watched data BEFORE updating UI
                            val continueWatchingItems = fetchContinueWatching()
                            
                            // Update UI with ALL data loaded (movies + recently watched)
                            _uiState.value = HomeUiState.Success(
                                featuredMedia = featuredMedia, // ALWAYS latest content for hero slider
                                continueWatching = continueWatchingItems, // Loaded synchronously
                                trendingMovies = trendingMovies,
                                popularMovies = popularMovies,
                                latestMovies = latestMovies,
                                actionMovies = actionMovies,
                                comedyMovies = sortedByDate.filter { media -> media.genres.any { genre -> genre.name.contains("Comedy", ignoreCase = true) } }.take(15),
                                dramaMovies = dramaMovies,
                                sciFiMovies = sciFiMovies,
                                horrorMovies = sortedByDate.filter { media -> media.genres.any { genre -> genre.name.contains("Horror", ignoreCase = true) } }.take(15),
                                romanceMovies = sortedByDate.filter { media -> media.genres.any { genre -> genre.name.contains("Romance", ignoreCase = true) } }.take(15),
                                thrillerMovies = sortedByDate.filter { media -> media.genres.any { genre -> genre.name.contains("Thriller", ignoreCase = true) } }.take(15),
                                currentHeroIndex = 0,
                                // Additional properties for NetflixHomeScreen
                                trending = trendingMovies,
                                popularTVShows = emptyList(), // No TV shows in this movie-focused app
                                recentlyAdded = latestMovies,
                                recommended = latestMovies.take(10) // Use latest movies as recommendations
                            )
                        },
                        onFailure = { error ->
                            Log.e("HomeViewModel", "Error loading movies", error)
                            // Cached content already on screen? Keep it - a failed
                            // background refresh must never blank a working UI.
                            if (_uiState.value is HomeUiState.Success) {
                                return@fold
                            }
                            val errorMessage = when {
                                error.message?.contains("ConnectException") == true -> "Cannot connect to server at ${BuildConfig.BASE_URL}. Check if server is running and TV is on same network."
                                error.message?.contains("UnknownHostException") == true -> "Server not found at ${BuildConfig.BASE_URL}. Check network connection and server IP."
                                error.message?.contains("SocketTimeoutException") == true -> "Server timeout at ${BuildConfig.BASE_URL}. Server may be down or slow."
                                error.message?.contains("404") == true -> "API endpoints not found on server at ${BuildConfig.BASE_URL}"
                                error.message?.contains("cleartext") == true -> "HTTP cleartext not allowed. Check network security config."
                                else -> "Error connecting to ${BuildConfig.BASE_URL}: ${error.message ?: "Unknown error occurred"}"
                            }
                            _uiState.value = HomeUiState.Error(errorMessage)
                        }
                    )
                }
            } catch (e: Exception) {
                Log.e("HomeViewModel", "Error loading home content", e)
                if (_uiState.value is HomeUiState.Success) {
                    return@launch // keep cached UI on background-refresh failure
                }
                val errorMessage = when {
                    e.message?.contains("ConnectException") == true -> "Cannot connect to server at ${BuildConfig.BASE_URL}. Check if server is running and TV is on same network."
                    e.message?.contains("UnknownHostException") == true -> "Server not found at ${BuildConfig.BASE_URL}. Check network connection and server IP."
                    e.message?.contains("SocketTimeoutException") == true -> "Server timeout at ${BuildConfig.BASE_URL}. Server may be down or slow."
                    e.message?.contains("404") == true -> "API endpoints not found on server at ${BuildConfig.BASE_URL}"
                    e.message?.contains("cleartext") == true -> "HTTP cleartext not allowed. Check network security config."
                    else -> "Error connecting to ${BuildConfig.BASE_URL}: ${e.message ?: "Unknown error occurred"}"
                }
                _uiState.value = HomeUiState.Error(errorMessage)
            }
        }
    }

    private suspend fun fetchRecommendedMedia(): List<Media>? {
        return try {
            // Cycle through different recommendation endpoints like web frontend
            val endpointIndex = cycleCount % recommendationEndpoints.size
            val endpoint = recommendationEndpoints[endpointIndex]
            
            val response = when (endpoint) {
                "mixed" -> mediaRepository.getMixedRecommendations(25)
                "trending" -> mediaRepository.getTrendingRecommendations(25)
                "popular" -> mediaRepository.getPopularRecommendations(25)
                "recent" -> mediaRepository.getRecentRecommendations(25)
                "personalized" -> mediaRepository.getPersonalizedRecommendations(25)
                "unique" -> mediaRepository.getUniqueRecommendations(25)
                "top-rated" -> mediaRepository.getTopRatedRecommendations(25)
                "genre" -> mediaRepository.getGenreRecommendations(25)
                else -> mediaRepository.getMixedRecommendations(25)
            }
            
            if (response.isSuccessful) {
                val mediaList = response.body()?.map { it.toDomain() } ?: emptyList()
                
                // Increment cycle count for next fetch
                cycleCount++
                
                mediaList.shuffled().take(10) // Add randomization like web frontend
            } else {
                Log.w("HomeViewModel", "Recommendation endpoint $endpoint failed: ${response.code()}")
                
                // Try fallback endpoint
                val fallbackResponse = mediaRepository.getMixedRecommendations(25)
                if (fallbackResponse.isSuccessful) {
                    fallbackResponse.body()?.map { it.toDomain() }?.shuffled()?.take(10)
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            Log.e("HomeViewModel", "Error fetching recommendations: ${e.message}")
            null
        }
    }

    private suspend fun fetchContinueWatching(): List<ContinueWatchingItem> {
        return try {
            var continueWatchingItems = emptyList<ContinueWatchingItem>()
            
            // Use a single collect to avoid Flow exceptions with timeout
            try {
                // Add timeout to prevent hanging
                kotlinx.coroutines.withTimeout(10000) { // 10 second timeout
                    mediaRepository.getRecentlyWatchedWithProgress().collect { result ->
                    result.fold(
                        onSuccess = { recentlyWatchedItems ->
                            continueWatchingItems = try {
                                // Null safety check first
                                if (recentlyWatchedItems.isNullOrEmpty()) {
                                    emptyList()
                                } else {
                                    recentlyWatchedItems
                                        .filterNotNull() // Remove any null items
                                        .filter { item ->
                                            try {
                                                // Comprehensive validation with null safety
                                                // ONLY show MOVIES on homepage (episodes shown on TV series page)
                                                val isValid = item.media != null &&
                                                             item.media.id > 0 && 
                                                             !item.media.title.isNullOrBlank() && 
                                                             item.durationSeconds > 0 &&
                                                             item.progressSeconds >= 0 && // Allow 0 progress
                                                             item.lastWatchedAt != null &&
                                                             item.media.type == MediaType.MOVIE // Only movies on homepage
                                                
                                                if (!isValid) {
                                                    Log.w("HomeViewModel", "Filtering out item: mediaId=${item.mediaId}, title='${item.media?.title}', type=${item.media?.type}")
                                                }
                                                
                                                isValid
                                            } catch (e: Exception) {
                                                Log.e("HomeViewModel", "Error validating item ${item.mediaId}: ${e.message}")
                                                false // Filter out items that cause validation errors
                                            }
                                        }
                                        .sortedByDescending { item ->
                                            try {
                                                // Safe sorting with null check
                                                item.lastWatchedAt?.time ?: 0L
                                            } catch (e: Exception) {
                                                Log.w("HomeViewModel", "Error sorting item ${item.mediaId}: ${e.message}")
                                                0L // Default to oldest if sorting fails
                                            }
                                        }
                                        .take(10) // Safely limit to 10 items
                                        .mapNotNull { item -> // Use mapNotNull to handle any mapping failures
                                            try {
                                                val progressPercent = if (item.durationSeconds > 0) {
                                                    (item.progressSeconds.toFloat() / item.durationSeconds.toFloat()).coerceIn(0f, 1f)
                                                } else {
                                                    0f
                                                }
                                                
                                                val lastWatchedText = try {
                                                    formatLastWatchedFromDate(item.lastWatchedAt)
                                                } catch (e: Exception) {
                                                    Log.w("HomeViewModel", "Error formatting date for item ${item.mediaId}: ${e.message}")
                                                    "Recently" // Fallback text
                                                }
                                                
                                                ContinueWatchingItem(
                                                    media = item.media,
                                                    progress = progressPercent,
                                                    progressSeconds = item.progressSeconds,
                                                    lastWatched = lastWatchedText
                                                )
                                            } catch (e: Exception) {
                                                Log.e("HomeViewModel", "Error processing item ${item.mediaId}: ${e.message}")
                                                null // This item will be filtered out by mapNotNull
                                            }
                                        }
                                }
                            } catch (e: Exception) {
                                Log.e("HomeViewModel", "Error processing recently watched items: ${e.message}", e)
                                emptyList() // Return empty list if processing fails
                            }
                        },
                        onFailure = { error ->
                            Log.e("HomeViewModel", "API Error fetching continue watching: ${error.message}", error)
                            continueWatchingItems = emptyList()
                        }
                    )
                }
                }
            } catch (timeoutException: kotlinx.coroutines.TimeoutCancellationException) {
                Log.e("HomeViewModel", "Timeout fetching continue watching data (10s)")
                continueWatchingItems = emptyList()
            } catch (flowException: Exception) {
                Log.e("HomeViewModel", "Flow exception in fetchContinueWatching: ${flowException.message}", flowException)
                continueWatchingItems = emptyList()
            }
            
            continueWatchingItems
        } catch (e: Exception) {
            Log.e("HomeViewModel", "General exception in fetchContinueWatching: ${e.message}", e)
            emptyList()
        }
    }
    
    /**
     * Format last watched date from API string format (e.g., "2025-11-13T07:20:52+06:00")
     */
    private fun formatLastWatched(dateString: String): String {
        return try {
            // Parse the date string (handles timezone offset)
            // Use multiple format patterns for API 23 compatibility
            val date = try {
                // Try ISO 8601 with timezone (API 24+)
                java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", java.util.Locale.getDefault())
                    .apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }
                    .parse(dateString.replace("+06:00", "+0600"))
            } catch (e: Exception) {
                // Fallback: Parse without timezone for API 23
                java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.getDefault())
                    .parse(dateString.substringBefore("+").substringBefore("Z"))
            } ?: return "Unknown"
            
            val now = java.util.Date()
            val diffMs = now.time - date.time
            val diffHours = diffMs / (1000 * 60 * 60)
            val diffDays = diffHours / 24
            
            when {
                diffHours < 1 -> "Just now"
                diffHours < 24 -> "${diffHours}h ago"
                diffDays < 7 -> "${diffDays}d ago"
                else -> java.text.SimpleDateFormat("MMM dd", java.util.Locale.getDefault()).format(date)
            }
        } catch (e: Exception) {
            Log.w("HomeViewModel", "Error parsing date: $dateString", e)
            "Recently"
        }
    }
    
    /**
     * Format last watched date from Date object (legacy method)
     */
    private fun formatLastWatchedFromDate(date: java.util.Date): String {
        val now = java.util.Date()
        val diffMs = now.time - date.time
        val diffHours = diffMs / (1000 * 60 * 60)
        val diffDays = diffHours / 24
        
        return when {
            diffHours < 1 -> "Just now"
            diffHours < 24 -> "${diffHours}h ago"
            diffDays < 7 -> "${diffDays}d ago"
            else -> java.text.SimpleDateFormat("MMM dd", java.util.Locale.getDefault()).format(date)
        }
    }

    private fun displayCachedContent(movies: List<Media>) {
        // ALWAYS show latest content in hero slider (like browse screen)
        val sortedByDate = movies.sortedByDescending { 
            it.createdAt?.time ?: it.id.toLong() // Use creation date or ID as fallback
        }
        
        // Sort by view count for popular content
        val sortedByViews = movies.sortedByDescending { it.viewCount }
        
        // Sort by rating for trending content  
        val sortedByRating = movies.sortedByDescending { it.rating }
        
        // ALWAYS show latest content in hero slider (like browse screen)
        val featuredMedia = sortedByDate.take(8) // Top 8 latest movies for hero slider
        
        val trendingMovies = sortedByRating.take(20) // Top rated as trending
        val popularMovies = sortedByViews.take(20) // Most viewed as popular
        val latestMovies = sortedByDate.take(20) // Latest by creation date
        
        _uiState.value = HomeUiState.Success(
            featuredMedia = featuredMedia,
            continueWatching = emptyList(), // Will be loaded separately for cached content
            trendingMovies = trendingMovies,
            popularMovies = popularMovies,
            latestMovies = latestMovies,
            // Genre filtering from latest content first
            actionMovies = sortedByDate.filter { media -> media.genres.any { genre -> genre.name.contains("Action", ignoreCase = true) } }.take(15),
            comedyMovies = sortedByDate.filter { media -> media.genres.any { genre -> genre.name.contains("Comedy", ignoreCase = true) } }.take(15),
            dramaMovies = sortedByDate.filter { media -> media.genres.any { genre -> genre.name.contains("Drama", ignoreCase = true) } }.take(15),
            sciFiMovies = sortedByDate.filter { media -> media.genres.any { genre -> genre.name.contains("Science Fiction", ignoreCase = true) || genre.name.contains("Sci-Fi", ignoreCase = true) } }.take(15),
            horrorMovies = sortedByDate.filter { media -> media.genres.any { genre -> genre.name.contains("Horror", ignoreCase = true) } }.take(15),
            romanceMovies = sortedByDate.filter { media -> media.genres.any { genre -> genre.name.contains("Romance", ignoreCase = true) } }.take(15),
            thrillerMovies = sortedByDate.filter { media -> media.genres.any { genre -> genre.name.contains("Thriller", ignoreCase = true) } }.take(15),
            currentHeroIndex = 0,
            trending = trendingMovies,
            popularTVShows = emptyList(),
            recentlyAdded = latestMovies,
            recommended = latestMovies.take(10)
        )
    }
    
    fun refreshFeaturedContent() {
        // Force refresh with new cycle count like web frontend
        cycleCount++
        loadHomeContent()
    }
    
    fun updateHeroIndex(index: Int) {
        val currentState = _uiState.value
        if (currentState is HomeUiState.Success) {
            _uiState.value = currentState.copy(currentHeroIndex = index)
        }
    }
    
    fun refreshRecentlyWatched() {
        viewModelScope.launch {
            try {
                val continueWatchingItems = fetchContinueWatching()
                
                val currentState = _uiState.value
                if (currentState is HomeUiState.Success) {
                    _uiState.value = currentState.copy(continueWatching = continueWatchingItems)
                }
            } catch (e: Exception) {
                Log.e("HomeViewModel", "Error during manual refresh", e)
            }
        }
    }
    
    fun testRecentlyWatchedAPI() {
        viewModelScope.launch {
            Log.d("HomeViewModel", "=== TESTING RECENTLY WATCHED API ===")
            try {
                // Direct API test with collect (avoiding first())
                mediaRepository.getRecentlyWatchedWithProgress().collect { result ->
                    result.fold(
                        onSuccess = { items ->
                            Log.d("HomeViewModel", "✅ API Test SUCCESS: ${items.size} items")
                            items.forEachIndexed { index, item ->
                                Log.d("HomeViewModel", "Item $index: ${item.media.title} - ${item.progressSeconds}s/${item.durationSeconds}s")
                                Log.d("HomeViewModel", "  Media ID: ${item.media.id}, Type: ${item.media.type}")
                                Log.d("HomeViewModel", "  Last Watched: ${item.lastWatchedAt}")
                            }
                            
                            // Force update UI with test data
                            val currentState = _uiState.value
                            if (currentState is HomeUiState.Success) {
                                val testContinueWatching = items.map { item ->
                                    val progressPercent = if (item.durationSeconds > 0) {
                                        (item.progressSeconds.toFloat() / item.durationSeconds.toFloat()).coerceIn(0f, 1f)
                                    } else {
                                        0f
                                    }
                                    
                                    ContinueWatchingItem(
                                        media = item.media,
                                        progress = progressPercent,
                                        progressSeconds = item.progressSeconds,
                                        lastWatched = formatLastWatchedFromDate(item.lastWatchedAt)
                                    )
                                }
                                
                                Log.d("HomeViewModel", "🔄 Forcing UI update with ${testContinueWatching.size} items")
                                // DISABLED: This was overwriting correct Continue Watching data with progressSeconds: 0s
                                // _uiState.value = currentState.copy(continueWatching = testContinueWatching)
                            }
                        },
                        onFailure = { error ->
                            Log.e("HomeViewModel", "❌ API Test FAILED: ${error.message}", error)
                        }
                    )
                }
            } catch (e: Exception) {
                Log.e("HomeViewModel", "❌ API Test EXCEPTION: ${e.message}", e)
            }
        }
    }
    

}

sealed class HomeUiState {
    object Loading : HomeUiState()
    data class Error(val message: String) : HomeUiState()
    data class Success(
        val featuredMedia: List<Media>,
        val continueWatching: List<ContinueWatchingItem>,
        val trendingMovies: List<Media>,
        val popularMovies: List<Media>,
        val latestMovies: List<Media>,
        val actionMovies: List<Media>,
        val comedyMovies: List<Media>,
        val dramaMovies: List<Media>,
        val sciFiMovies: List<Media>,
        val horrorMovies: List<Media>,
        val romanceMovies: List<Media>,
        val thrillerMovies: List<Media>,
        val currentHeroIndex: Int,
        // Additional properties for NetflixHomeScreen
        val trending: List<Media> = trendingMovies,
        val popularTVShows: List<Media> = emptyList(),
        val recentlyAdded: List<Media> = latestMovies,
        val recommended: List<Media> = emptyList()
    ) : HomeUiState()
}