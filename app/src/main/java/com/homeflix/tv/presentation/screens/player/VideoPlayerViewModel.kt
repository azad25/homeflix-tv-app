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
    
    fun loadMedia(mediaId: Int) {
        viewModelScope.launch {
            _uiState.value = VideoPlayerUiState.Loading
            
            try {
                mediaRepository.getMediaById(mediaId.toString()).collect { result ->
                    if (result.isSuccess) {
                        val media = result.getOrNull()
                        if (media != null) {
                            _uiState.value = VideoPlayerUiState.Success(media)
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
    data class Success(val media: Media) : VideoPlayerUiState()
}