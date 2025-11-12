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
            com.homeflix.tv.data.cache.ContentCache.clearExpiredCache(context)
        }
        loadHomeContent()
    }
    
    fun loadHomeContent() {
        viewModelScope.launch {
            _uiState.value = HomeUiState.Loading
            
            try {
                // Try to load from cache first for instant display
                val cachedMovies = com.homeflix.tv.data.cache.ContentCache.getMediaList(context, "movies")
                if (cachedMovies != null && cachedMovies.isNotEmpty()) {
                    Log.d("HomeViewModel", "Loading from cache: ${cachedMovies.size} movies")
                    displayCachedContent(cachedMovies)
                }
                
                // Test basic connectivity first
                Log.d("HomeViewModel", "Starting to load home content from: ${BuildConfig.BASE_URL}")
                
                // Load movies content
                mediaRepository.getMovies(limit = 50).collect { result ->
                    result.fold(
                        onSuccess = { movies ->
                            if (movies.isEmpty()) {
                                _uiState.value = HomeUiState.Error("No movies available in database")
                                return@collect
                            }
                        
                            // Try to get featured media from recommendation endpoints first
                            val featuredMedia = fetchRecommendedMedia() ?: movies.shuffled().take(10)
                            
                            val trendingMovies = movies.sortedByDescending { it.viewCount }.take(15)
                            val popularMovies = movies.sortedByDescending { it.rating }.take(15)
                            val latestMovies = movies.sortedByDescending { it.createdAt }.take(15)
                            
                            // Cache the movies for next time
                            viewModelScope.launch {
                                com.homeflix.tv.data.cache.ContentCache.saveMediaList(context, "movies", movies)
                                Log.d("HomeViewModel", "Cached ${movies.size} movies")
                            }
                            
                            _uiState.value = HomeUiState.Success(
                                featuredMedia = featuredMedia, // Use recommendation system
                                continueWatching = fetchContinueWatching(),
                                trendingMovies = trendingMovies,
                                popularMovies = popularMovies,
                                latestMovies = latestMovies,
                                actionMovies = movies.filter { media -> media.genres.any { genre -> genre.name.contains("Action", ignoreCase = true) } }.take(15),
                                comedyMovies = movies.filter { media -> media.genres.any { genre -> genre.name.contains("Comedy", ignoreCase = true) } }.take(15),
                                dramaMovies = movies.filter { media -> media.genres.any { genre -> genre.name.contains("Drama", ignoreCase = true) } }.take(15),
                                sciFiMovies = movies.filter { media -> media.genres.any { genre -> genre.name.contains("Science Fiction", ignoreCase = true) || genre.name.contains("Sci-Fi", ignoreCase = true) } }.take(15),
                                horrorMovies = movies.filter { media -> media.genres.any { genre -> genre.name.contains("Horror", ignoreCase = true) } }.take(15),
                                romanceMovies = movies.filter { media -> media.genres.any { genre -> genre.name.contains("Romance", ignoreCase = true) } }.take(15),
                                thrillerMovies = movies.filter { media -> media.genres.any { genre -> genre.name.contains("Thriller", ignoreCase = true) } }.take(15),
                                currentHeroIndex = 0,
                                // Additional properties for NetflixHomeScreen
                                trending = trendingMovies,
                                popularTVShows = emptyList(), // No TV shows in this movie-focused app
                                recentlyAdded = latestMovies,
                                recommended = popularMovies.shuffled().take(10) // Use popular movies as recommendations
                            )
                        },
                        onFailure = { error ->
                            Log.e("HomeViewModel", "Error loading movies", error)
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
            
            Log.d("HomeViewModel", "Fetching recommendations from: $endpoint (cycle: $cycleCount)")
            
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
                Log.d("HomeViewModel", "Successfully fetched ${mediaList.size} recommendations from $endpoint")
                
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

    private fun fetchContinueWatching(): List<ContinueWatchingItem> {
        // For now, create mock continue watching data
        // In a real app, this would come from user's watch history API
        return try {
            Log.d("HomeViewModel", "Creating mock continue watching data")
            
            // Create mock continue watching items
            listOf(
                ContinueWatchingItem(
                    media = Media(
                        id = 1,
                        uuid = "mock-1",
                        title = "Sample Movie 1",
                        type = MediaType.MOVIE,
                        filePath = "/mock/path1",
                        fileSize = 1000000L,
                        duration = 7200,
                        rating = 8.5,
                        genres = emptyList(),
                        genreNames = emptyList()
                    ),
                    progress = 0.25f,
                    lastWatched = "2 days ago"
                ),
                ContinueWatchingItem(
                    media = Media(
                        id = 2,
                        uuid = "mock-2", 
                        title = "Sample Movie 2",
                        type = MediaType.MOVIE,
                        filePath = "/mock/path2",
                        fileSize = 1500000L,
                        duration = 6600,
                        rating = 7.8,
                        genres = emptyList(),
                        genreNames = emptyList()
                    ),
                    progress = 0.67f,
                    lastWatched = "1 day ago"
                )
            )
        } catch (e: Exception) {
            Log.e("HomeViewModel", "Error creating continue watching: ${e.message}")
            emptyList()
        }
    }

    private fun displayCachedContent(movies: List<Media>) {
        val featuredMedia = movies.shuffled().take(10)
        val trendingMovies = movies.sortedByDescending { it.viewCount }.take(15)
        val popularMovies = movies.sortedByDescending { it.rating }.take(15)
        val latestMovies = movies.sortedByDescending { it.createdAt }.take(15)
        
        _uiState.value = HomeUiState.Success(
            featuredMedia = featuredMedia,
            continueWatching = fetchContinueWatching(),
            trendingMovies = trendingMovies,
            popularMovies = popularMovies,
            latestMovies = latestMovies,
            actionMovies = movies.filter { media -> media.genres.any { genre -> genre.name.contains("Action", ignoreCase = true) } }.take(15),
            comedyMovies = movies.filter { media -> media.genres.any { genre -> genre.name.contains("Comedy", ignoreCase = true) } }.take(15),
            dramaMovies = movies.filter { media -> media.genres.any { genre -> genre.name.contains("Drama", ignoreCase = true) } }.take(15),
            sciFiMovies = movies.filter { media -> media.genres.any { genre -> genre.name.contains("Science Fiction", ignoreCase = true) || genre.name.contains("Sci-Fi", ignoreCase = true) } }.take(15),
            horrorMovies = movies.filter { media -> media.genres.any { genre -> genre.name.contains("Horror", ignoreCase = true) } }.take(15),
            romanceMovies = movies.filter { media -> media.genres.any { genre -> genre.name.contains("Romance", ignoreCase = true) } }.take(15),
            thrillerMovies = movies.filter { media -> media.genres.any { genre -> genre.name.contains("Thriller", ignoreCase = true) } }.take(15),
            currentHeroIndex = 0,
            trending = trendingMovies,
            popularTVShows = emptyList(),
            recentlyAdded = latestMovies,
            recommended = popularMovies.shuffled().take(10)
        )
    }
    
    fun refreshFeaturedContent() {
        // Force refresh with new cycle count like web frontend
        cycleCount++
        Log.d("HomeViewModel", "Refreshing featured content with cycle: $cycleCount")
        loadHomeContent()
    }
    
    fun updateHeroIndex(index: Int) {
        val currentState = _uiState.value
        if (currentState is HomeUiState.Success) {
            _uiState.value = currentState.copy(currentHeroIndex = index)
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