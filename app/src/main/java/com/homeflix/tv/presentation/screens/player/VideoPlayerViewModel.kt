package com.homeflix.tv.presentation.screens.player

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.homeflix.tv.data.repository.MediaRepository
import com.homeflix.tv.domain.model.Media
import com.homeflix.tv.domain.model.MediaType
import com.homeflix.tv.presentation.screens.tvshows.Episode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class VideoPlayerViewModel @Inject constructor(
    private val mediaRepository: MediaRepository
) : ViewModel() {
    
    private val _uiState = MutableStateFlow<VideoPlayerUiState>(VideoPlayerUiState.Loading)
    val uiState: StateFlow<VideoPlayerUiState> = _uiState.asStateFlow()
    
    fun loadMedia(mediaId: Int, startTimeSeconds: Long = 0L) {
        viewModelScope.launch {
            _uiState.value = VideoPlayerUiState.Loading

            try {
                mediaRepository.getMediaById(mediaId.toString()).collect { result ->
                    if (result.isSuccess) {
                        val media = result.getOrNull()
                        if (media != null) {
                            // Emit basic Success immediately so playback starts
                            // without waiting on the lookups below.
                            _uiState.value = VideoPlayerUiState.Success(media, startTimeSeconds.takeIf { it > 0 })

                            // If launched without an explicit resume time (e.g.
                            // episodes from the series page), load saved progress
                            // so "continue watching" works for TV too.
                            if (startTimeSeconds <= 0) {
                                loadSavedProgressFor(media)
                            }

                            // For episodes, enrich with series title + season list
                            // (drives the two-line title and reliable auto-advance)
                            if (media.type == MediaType.EPISODE && media.seriesId != null) {
                                enrichWithSeriesContext(media, startTimeSeconds.takeIf { it > 0 })
                            }
                        } else {
                            _uiState.value = VideoPlayerUiState.Error("Media not found")
                        }
                    } else {
                        _uiState.value = VideoPlayerUiState.Error(
                            result.exceptionOrNull()?.message ?: "Failed to load media"
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.value = VideoPlayerUiState.Error(e.message ?: "Unknown error")
            }
        }
    }

    /** Load stored resume position for this media and apply it if meaningful. */
    private suspend fun loadSavedProgressFor(media: Media) {
        try {
            val result = mediaRepository.getPlaybackProgress(media.id.toString())
            val progress = result.getOrNull() ?: return
            // progress.progress is seconds; skip if finished or negligible
            val secs = progress.progress
            if (!progress.completed && secs > 10) {
                val current = _uiState.value
                if (current is VideoPlayerUiState.Success && current.media.id == media.id) {
                    _uiState.value = current.copy(savedProgressSeconds = secs)
                }
            }
        } catch (e: Exception) {
            Log.w("VideoPlayerVM", "loadSavedProgress failed: ${e.message}")
        }
    }

    /**
     * Fetches the series title and the ordered episode list for the current
     * episode's season, so the player can show "Series · Episode" and know the
     * next episode deterministically. Degrades silently on any failure.
     */
    private suspend fun enrichWithSeriesContext(media: Media, savedProgress: Long?) {
        try {
            val seriesId = media.seriesId ?: return
            val series = mediaRepository.getTvSeriesById(seriesId)
            val season = media.seasonNumber ?: 1
            val episodes = mediaRepository.getTvSeriesEpisodes(seriesId, season)
            val index = episodes.indexOfFirst { it.id == media.id }
            val current = _uiState.value
            if (current is VideoPlayerUiState.Success && current.media.id == media.id) {
                _uiState.value = current.copy(
                    seriesTitle = series.title,
                    seasonEpisodes = episodes,
                    currentIndex = index
                )
            }
        } catch (e: Exception) {
            Log.w("VideoPlayerVM", "Series enrichment failed: ${e.message}")
        }
    }
    
    private suspend fun loadSavedProgress(media: Media) {
        try {
            // Use take(1) to get only first emission and complete
            mediaRepository.getRecentlyWatchedWithProgress()
                .take(1)
                .collect { result ->
                    result.fold(
                        onSuccess = { recentlyWatchedItems ->
                            val matchingItem = recentlyWatchedItems.find { it.mediaId == media.id }
                            val savedProgress = matchingItem?.progressSeconds
                            
                            _uiState.value = VideoPlayerUiState.Success(media, savedProgress?.takeIf { it > 0 })
                        },
                        onFailure = { error ->
                            _uiState.value = VideoPlayerUiState.Success(media, null)
                        }
                    )
                }
        } catch (e: Exception) {
            _uiState.value = VideoPlayerUiState.Success(media, null)
        }
    }
    
    fun updateProgress(currentTime: Long, duration: Long) {
        // Progress updates are now handled only on player close for performance
        // No frequent API calls during playback
    }
    
    fun getMediaRepository(): MediaRepository = mediaRepository
}

sealed class VideoPlayerUiState {
    object Loading : VideoPlayerUiState()
    data class Error(val message: String) : VideoPlayerUiState()
    data class Success(
        val media: Media,
        val savedProgressSeconds: Long? = null, // Saved progress in seconds
        // Episode context (null/empty for movies)
        val seriesTitle: String? = null,
        val seasonEpisodes: List<Episode> = emptyList(),
        val currentIndex: Int = -1
    ) : VideoPlayerUiState()
}