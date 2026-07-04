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

    // Episodes of the currently selected season (Prime-style inline list)
    private val _episodes = MutableStateFlow<List<Episode>>(emptyList())
    val episodes: StateFlow<List<Episode>> = _episodes.asStateFlow()

    private val _selectedSeason = MutableStateFlow(1)
    val selectedSeason: StateFlow<Int> = _selectedSeason.asStateFlow()

    private val _episodesLoading = MutableStateFlow(false)
    val episodesLoading: StateFlow<Boolean> = _episodesLoading.asStateFlow()

    fun loadSeriesDetails(seriesId: String) {
        viewModelScope.launch {
            try {
                _uiState.value = TvSeriesDetailsUiState.Loading

                // Get series details and seasons
                val series = mediaRepository.getTvSeriesById(seriesId.toInt())
                val seasons = mediaRepository.getTvSeriesSeasons(seriesId.toInt())
                    .sortedBy { it.seasonNumber }

                _uiState.value = TvSeriesDetailsUiState.Success(
                    series = series,
                    seasons = seasons
                )

                // Auto-load episodes for the first season
                seasons.firstOrNull()?.let { selectSeason(seriesId, it.seasonNumber) }
            } catch (e: Exception) {
                _uiState.value = TvSeriesDetailsUiState.Error(
                    message = e.message ?: "Failed to load series details"
                )
            }
        }
    }

    fun selectSeason(seriesId: String, seasonNumber: Int) {
        _selectedSeason.value = seasonNumber
        viewModelScope.launch {
            _episodesLoading.value = true
            try {
                _episodes.value = mediaRepository.getTvSeriesEpisodes(seriesId.toInt(), seasonNumber)
            } catch (e: Exception) {
                _episodes.value = emptyList()
            } finally {
                _episodesLoading.value = false
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
