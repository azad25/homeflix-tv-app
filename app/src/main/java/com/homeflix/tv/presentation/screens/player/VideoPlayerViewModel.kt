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
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class VideoPlayerViewModel @Inject constructor(
    private val mediaRepository: MediaRepository
) : ViewModel() {
    
    private val _uiState = MutableStateFlow<VideoPlayerUiState>(VideoPlayerUiState.Loading)
    val uiState: StateFlow<VideoPlayerUiState> = _uiState.asStateFlow()
    
    fun loadMedia(mediaId: Int, resumeFromProgress: Boolean = false) {
        viewModelScope.launch {
            _uiState.value = VideoPlayerUiState.Loading
            
            try {
                mediaRepository.getMediaById(mediaId.toString()).collect { result ->
                    if (result.isSuccess) {
                        val media = result.getOrNull()
                        if (media != null) {
                            // If resumeFromProgress is true, load the saved progress
                            if (resumeFromProgress) {
                                loadSavedProgress(media)
                            } else {
                                _uiState.value = VideoPlayerUiState.Success(media, null)
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
                Log.e("VideoPlayerViewModel", "Error loading media", e)
                _uiState.value = VideoPlayerUiState.Error(e.message ?: "Unknown error")
            }
        }
    }
    
    private suspend fun loadSavedProgress(media: Media) {
        try {
            // Get recently watched items to find saved progress
            mediaRepository.getRecentlyWatchedWithProgress().collect { result ->
                result.fold(
                    onSuccess = { recentlyWatchedItems ->
                        val savedProgress = recentlyWatchedItems
                            .find { it.mediaId == media.id }
                            ?.progressSeconds
                        
                        Log.d("VideoPlayerViewModel", "Found saved progress for media ${media.id}: ${savedProgress}s")
                        _uiState.value = VideoPlayerUiState.Success(media, savedProgress)
                    },
                    onFailure = { error ->
                        Log.w("VideoPlayerViewModel", "Failed to load saved progress: ${error.message}")
                        _uiState.value = VideoPlayerUiState.Success(media, null)
                    }
                )
            }
        } catch (e: Exception) {
            Log.w("VideoPlayerViewModel", "Error loading saved progress", e)
            _uiState.value = VideoPlayerUiState.Success(media, null)
        }
    }
    
    fun updateProgress(currentTime: Long, duration: Long) {
        // Progress updates are now handled only on player close for performance
        // No frequent API calls during playback
        Log.d("VideoPlayerViewModel", "Progress: ${currentTime}ms / ${duration}ms")
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