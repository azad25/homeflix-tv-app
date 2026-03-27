package com.homeflix.tv.presentation.screens.tvshows

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.homeflix.tv.domain.model.MediaType
import com.homeflix.tv.domain.repository.MediaRepository
import com.homeflix.tv.presentation.components.ContinueWatchingItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlinx.coroutines.flow.first

@HiltViewModel
class TvShowsViewModel @Inject constructor(
    private val mediaRepository: MediaRepository
) : ViewModel() {
    
    private val _uiState = MutableStateFlow<TvShowsUiState>(TvShowsUiState.Loading)
    val uiState: StateFlow<TvShowsUiState> = _uiState.asStateFlow()
    
    init {
        loadTvShows()
    }
    
    fun loadTvShows() {
        viewModelScope.launch {
            try {
                _uiState.value = TvShowsUiState.Loading
                
                // Get all TV series from the repository
                val series = mediaRepository.getTvSeries()
                
                Log.d("TvShowsViewModel", "Loaded ${series.size} TV series")
                
                // Sort by createdAt descending (latest first)
                val sortedSeries = series.sortedByDescending { it.createdAt }
                
                // Separate featured series for hero slider (first 5)
                val featuredSeries = sortedSeries.take(5)
                
                // Fetch continue watching episodes
                val continueWatchingEpisodes = fetchContinueWatchingEpisodes()
                
                _uiState.value = TvShowsUiState.Success(
                    featuredSeries = featuredSeries,
                    series = sortedSeries,
                    continueWatchingEpisodes = continueWatchingEpisodes
                )
            } catch (e: Exception) {
                _uiState.value = TvShowsUiState.Error(
                    message = e.message ?: "Failed to load TV shows"
                )
            }
        }
    }
    
    private suspend fun fetchContinueWatchingEpisodes(): List<ContinueWatchingItem> {
        return try {
            val result = mediaRepository.getRecentlyWatchedWithProgress().first() // ← first() not collect

            result.fold(
                onSuccess = { recentlyWatchedItems ->
                    recentlyWatchedItems
                        .filterNotNull()
                        .filter { item ->
                            item.media != null &&
                            item.media.id > 0 &&
                            !item.media.title.isNullOrBlank() &&
                            item.durationSeconds > 0 &&
                            item.progressSeconds >= 0 &&
                            item.lastWatchedAt != null &&
                            (item.media.type == MediaType.EPISODE ||
                            item.media.type == MediaType.TV_SHOW)
                        }
                        .sortedByDescending { it.lastWatchedAt?.time ?: 0L }
                        .take(10)
                        .mapNotNull { item ->
                            try {
                                val progressPercent = if (item.durationSeconds > 0) {
                                    (item.progressSeconds.toFloat() / item.durationSeconds.toFloat()).coerceIn(0f, 1f)
                                } else 0f
                                ContinueWatchingItem(
                                    media = item.media,
                                    progress = progressPercent,
                                    progressSeconds = item.progressSeconds,
                                    lastWatched = formatLastWatched(item.lastWatchedAt)
                                )
                            } catch (e: Exception) { null }
                        }
                },
                onFailure = { error ->
                    Log.e("TvShowsViewModel", "Error fetching continue watching: ${error.message}")
                    emptyList()
                }
            )
        } catch (e: Exception) {
            Log.e("TvShowsViewModel", "Exception fetching continue watching: ${e.message}")
            emptyList()
        }
    }
    
    private fun formatLastWatched(date: java.util.Date): String {
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
    
    fun refreshContinueWatching() {
        viewModelScope.launch {
            try {
                val continueWatchingEpisodes = fetchContinueWatchingEpisodes()
                val currentState = _uiState.value
                if (currentState is TvShowsUiState.Success) {
                    _uiState.value = currentState.copy(continueWatchingEpisodes = continueWatchingEpisodes)
                }
            } catch (e: Exception) {
                Log.e("TvShowsViewModel", "Error refreshing continue watching", e)
            }
        }
    }
}

sealed class TvShowsUiState {
    object Loading : TvShowsUiState()
    data class Success(
        val featuredSeries: List<TvSeries>,
        val series: List<TvSeries>,
        val continueWatchingEpisodes: List<ContinueWatchingItem> = emptyList()
    ) : TvShowsUiState()
    data class Error(val message: String) : TvShowsUiState()
}

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