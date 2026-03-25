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
class TvSeriesSeasonViewModel @Inject constructor(
    private val mediaRepository: MediaRepository
) : ViewModel() {
    
    private val _uiState = MutableStateFlow<TvSeriesSeasonUiState>(TvSeriesSeasonUiState.Loading)
    val uiState: StateFlow<TvSeriesSeasonUiState> = _uiState.asStateFlow()
    
    fun loadSeasonDetails(seriesId: String, seasonNumber: Int) {
        viewModelScope.launch {
            try {
                _uiState.value = TvSeriesSeasonUiState.Loading
                
                // Get series, season, and episodes
                val series = mediaRepository.getTvSeriesById(seriesId.toInt())
                val season = mediaRepository.getTvSeriesSeason(seriesId.toInt(), seasonNumber)
                val episodes = mediaRepository.getTvSeriesEpisodes(seriesId.toInt(), seasonNumber)
                
                _uiState.value = TvSeriesSeasonUiState.Success(
                    series = series,
                    season = season,
                    episodes = episodes
                )
            } catch (e: Exception) {
                _uiState.value = TvSeriesSeasonUiState.Error(
                    message = e.message ?: "Failed to load season details"
                )
            }
        }
    }
}

sealed class TvSeriesSeasonUiState {
    object Loading : TvSeriesSeasonUiState()
    data class Success(
        val series: TvSeries,
        val season: Season,
        val episodes: List<Episode>
    ) : TvSeriesSeasonUiState()
    data class Error(val message: String) : TvSeriesSeasonUiState()
}

data class Episode(
    val id: Int,
    val title: String,
    val episodeTitle: String? = null, // episode_title from API, more specific than title
    val description: String?,
    val duration: Int?, // in minutes
    val rating: Double,
    val airDate: String?,
    val thumbnailPath: String?,
    val episodeStillPath: String? = null // episode_still_path from API for still images
)