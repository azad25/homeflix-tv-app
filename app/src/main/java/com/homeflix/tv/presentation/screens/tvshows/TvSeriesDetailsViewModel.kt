package com.homeflix.tv.presentation.screens.tvshows

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.homeflix.tv.domain.model.MediaType
import com.homeflix.tv.domain.repository.MediaRepository
import com.homeflix.tv.presentation.components.ContinueWatchingItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.take
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

    // In-progress episodes for THIS series (Continue Watching on the page)
    private val _continueWatching = MutableStateFlow<List<ContinueWatchingItem>>(emptyList())
    val continueWatching: StateFlow<List<ContinueWatchingItem>> = _continueWatching.asStateFlow()

    fun loadContinueWatching(seriesId: Int) {
        viewModelScope.launch {
            try {
                mediaRepository.getRecentlyWatchedWithProgress().take(1).collect { result ->
                    result.fold(
                        onSuccess = { items ->
                            _continueWatching.value = items
                                .filter {
                                    it.media.seriesId == seriesId &&
                                        it.media.type == MediaType.EPISODE &&
                                        it.durationSeconds > 0 && it.progressSeconds > 0
                                }
                                .sortedByDescending { it.lastWatchedAt?.time ?: 0L }
                                .map {
                                    ContinueWatchingItem(
                                        media = it.media,
                                        progress = (it.progressSeconds.toFloat() / it.durationSeconds).coerceIn(0f, 1f),
                                        progressSeconds = it.progressSeconds,
                                        lastWatched = null
                                    )
                                }
                        },
                        onFailure = { _continueWatching.value = emptyList() }
                    )
                }
            } catch (e: Exception) {
                _continueWatching.value = emptyList()
            }
        }
    }

    // Tracks the series currently loaded so returning from the player (which
    // re-enters the screen and re-fires LaunchedEffect) does NOT reload and
    // reset the selected season back to 1.
    private var loadedSeriesId: String? = null

    fun loadSeriesDetails(seriesId: String) {
        // Already loaded this series? Keep selectedSeason + episodes intact.
        if (loadedSeriesId == seriesId && _uiState.value is TvSeriesDetailsUiState.Success) {
            return
        }
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
                loadedSeriesId = seriesId
                loadContinueWatching(seriesId.toInt())

                // Keep the previously-selected season if it's valid for this
                // series; otherwise default to the first season.
                val target = if (seasons.any { it.seasonNumber == _selectedSeason.value })
                    _selectedSeason.value
                else
                    (seasons.firstOrNull()?.seasonNumber ?: 1)
                selectSeason(seriesId, target)
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

/**
 * Season model shared across the TV-series screens and the repository.
 * (Defined here as the canonical location in the tvshows package.)
 */
data class Season(
    val id: Int,
    val seasonNumber: Int,
    val name: String,
    val description: String?,
    val episodeCount: Int,
    val posterPath: String?
)
