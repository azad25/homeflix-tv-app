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
class TvSeriesDetailsViewModel @Inject constructor(
    private val mediaRepository: MediaRepository
) : ViewModel() {
    
    private val _uiState = MutableStateFlow<TvSeriesDetailsUiState>(TvSeriesDetailsUiState.Loading)
    val uiState: StateFlow<TvSeriesDetailsUiState> = _uiState.asStateFlow()
    
    fun loadSeriesDetails(seriesId: String) {
        viewModelScope.launch {
            try {
                _uiState.value = TvSeriesDetailsUiState.Loading
                
                // Get series details and seasons
                val series = mediaRepository.getTvSeriesById(seriesId.toInt())
                val seasons = mediaRepository.getTvSeriesSeasons(seriesId.toInt())
                
                _uiState.value = TvSeriesDetailsUiState.Success(
                    series = series,
                    seasons = seasons
                )
            } catch (e: Exception) {
                _uiState.value = TvSeriesDetailsUiState.Error(
                    message = e.message ?: "Failed to load series details"
                )
            }
        }
    }
}

sealed class TvSeriesDetailsUiState {
    object Loading : TvSeriesDetailsUiState()
    data class Success(
        val series: TvSeries,
        val seasons: List<Season>
    ) : TvSeriesDetailsUiState()
    data class Error(val message: String) : TvSeriesDetailsUiState()
}

data class Season(
    val id: Int,
    val seasonNumber: Int,
    val name: String,
    val description: String?,
    val episodeCount: Int,
    val posterPath: String?
)