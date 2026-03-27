package com.homeflix.tv.presentation.screens.player

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.homeflix.tv.data.repository.MediaRepository
import com.homeflix.tv.domain.model.Media
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
                            // Use provided startTime directly - no complex loading
                            _uiState.value = VideoPlayerUiState.Success(media, startTimeSeconds.takeIf { it > 0 })
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
        val savedProgressSeconds: Long? = null // Saved progress in seconds
    ) : VideoPlayerUiState()
}