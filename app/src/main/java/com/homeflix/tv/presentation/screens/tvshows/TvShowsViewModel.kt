package com.homeflix.tv.presentation.screens.tvshows

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.homeflix.tv.domain.repository.MediaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TvShowsViewModel @Inject constructor(
    private val mediaRepository: MediaRepository
) : ViewModel() {
    
    private val _uiState = MutableStateFlow<TvShowsUiState>(TvShowsUiState.Loading)
    val uiState: StateFlow<TvShowsUiState> = _uiState.asStateFlow()
    
    fun loadTvShows() {
        viewModelScope.launch {
            try {
                _uiState.value = TvShowsUiState.Loading
                
                // Get all TV series from the repository
                val series = mediaRepository.getTvSeries()
                
                android.util.Log.d("TvShowsViewModel", "Loaded ${series.size} TV series")
                series.forEach { tvSeries ->
                    android.util.Log.d("TvShowsViewModel", "Series: ${tvSeries.title}, Poster: ${tvSeries.posterPath}")
                }
                
                _uiState.value = TvShowsUiState.Success(series = series)
            } catch (e: Exception) {
                _uiState.value = TvShowsUiState.Error(
                    message = e.message ?: "Failed to load TV shows"
                )
            }
        }
    }
}

sealed class TvShowsUiState {
    object Loading : TvShowsUiState()
    data class Success(val series: List<TvSeries>) : TvShowsUiState()
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
    val bannerPath: String?
)