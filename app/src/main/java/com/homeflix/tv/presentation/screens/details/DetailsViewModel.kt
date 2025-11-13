package com.homeflix.tv.presentation.screens.details

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.homeflix.tv.data.repository.MediaRepository
import com.homeflix.tv.domain.model.Media
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DetailsViewModel @Inject constructor(
    private val mediaRepository: MediaRepository
) : ViewModel() {
    
    private val _uiState = MutableStateFlow<DetailsUiState>(DetailsUiState.Loading)
    val uiState: StateFlow<DetailsUiState> = _uiState.asStateFlow()
    
    fun loadMediaDetails(mediaId: String) {
        viewModelScope.launch {
            _uiState.value = DetailsUiState.Loading
            
            try {
                // Load media details and watch progress concurrently
                mediaRepository.getMediaById(mediaId)
                    .collect { result ->
                        result.fold(
                            onSuccess = { media ->
                                // Load watch progress for this media
                                loadWatchProgress(media)
                            },
                            onFailure = { error ->
                                _uiState.value = DetailsUiState.Error(
                                    error.message ?: "Failed to load media details"
                                )
                            }
                        )
                    }
            } catch (e: Exception) {
                Log.e("DetailsViewModel", "Error loading media details", e)
                _uiState.value = DetailsUiState.Error(
                    e.message ?: "Failed to load media details"
                )
            }
        }
    }
    
    private suspend fun loadWatchProgress(media: Media) {
        try {
            Log.d("DetailsViewModel", "Loading watch progress for media: ${media.id} - ${media.title}")
            
            // Get recently watched items to find progress for this media
            val result = mediaRepository.getRecentlyWatchedWithProgress().first()
            
            result.fold(
                onSuccess = { recentlyWatchedItems ->
                    Log.d("DetailsViewModel", "Found ${recentlyWatchedItems.size} recently watched items")
                    
                    // Find progress for current media
                    val recentlyWatchedItem = recentlyWatchedItems.find { it.mediaId == media.id }
                    
                    val watchProgress = recentlyWatchedItem?.let { item ->
                        Log.d("DetailsViewModel", "Found progress for media ${media.id}: ${item.progressSeconds}/${item.durationSeconds}")
                        
                        if (item.durationSeconds > 0) {
                            (item.progressSeconds.toFloat() / item.durationSeconds.toFloat()).coerceIn(0f, 1f)
                        } else {
                            0f
                        }
                    }
                    
                    if (recentlyWatchedItem == null) {
                        Log.d("DetailsViewModel", "No watch progress found for media ${media.id}")
                    } else {
                        Log.d("DetailsViewModel", "Watch progress for media ${media.id}: ${(watchProgress!! * 100).toInt()}%")
                    }
                    
                    _uiState.value = DetailsUiState.Success(media, watchProgress, recentlyWatchedItem?.progressSeconds)
                },
                onFailure = { error ->
                    Log.w("DetailsViewModel", "Failed to load watch progress: ${error.message}")
                    // Still show media details even if progress loading fails
                    _uiState.value = DetailsUiState.Success(media, null, null)
                }
            )
        } catch (e: Exception) {
            Log.w("DetailsViewModel", "Error loading watch progress", e)
            // Still show media details even if progress loading fails
            _uiState.value = DetailsUiState.Success(media, null, null)
        }
    }
}

sealed class DetailsUiState {
    object Loading : DetailsUiState()
    data class Error(val message: String) : DetailsUiState()
    data class Success(
        val media: Media,
        val watchProgress: Float? = null, // 0.0 to 1.0, null if no progress
        val progressSeconds: Long? = null // Progress in seconds for resume
    ) : DetailsUiState()
}