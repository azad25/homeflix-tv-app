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
    
    private val _isInMyList = MutableStateFlow(false)
    val isInMyList: StateFlow<Boolean> = _isInMyList.asStateFlow()
    
    private val _myListLoading = MutableStateFlow(false)
    val myListLoading: StateFlow<Boolean> = _myListLoading.asStateFlow()

    private val _similar = MutableStateFlow<List<Media>>(emptyList())
    val similar: StateFlow<List<Media>> = _similar.asStateFlow()
    
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
                                // Check if media is in My List
                                checkMyList(mediaId)
                                // Fetch "More like this" by first genre
                                loadSimilar(media)
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
    
    private suspend fun checkMyList(mediaId: String) {
        try {
            val result = mediaRepository.checkMyList(mediaId)
            result.fold(
                onSuccess = { inList ->
                    _isInMyList.value = inList
                    Log.d("DetailsViewModel", "Media $mediaId in my list: $inList")
                },
                onFailure = { error ->
                    Log.w("DetailsViewModel", "Failed to check my list: ${error.message}")
                    _isInMyList.value = false
                }
            )
        } catch (e: Exception) {
            Log.w("DetailsViewModel", "Error checking my list", e)
            _isInMyList.value = false
        }
    }
    
    fun addToMyList(mediaId: String) {
        viewModelScope.launch {
            _myListLoading.value = true
            try {
                val result = mediaRepository.addToMyList(mediaId)
                result.fold(
                    onSuccess = {
                        _isInMyList.value = true
                        Log.d("DetailsViewModel", "Added to my list: $mediaId")
                    },
                    onFailure = { error ->
                        Log.e("DetailsViewModel", "Failed to add to my list: ${error.message}")
                    }
                )
            } catch (e: Exception) {
                Log.e("DetailsViewModel", "Error adding to my list", e)
            } finally {
                _myListLoading.value = false
            }
        }
    }
    
    private fun loadSimilar(media: Media) {
        val genre = media.genreNames.firstOrNull() ?: media.genres.firstOrNull()?.name ?: return
        viewModelScope.launch {
            try {
                mediaRepository.getMediaByGenre(genre.lowercase(), 15, 0).collect { result ->
                    result.fold(
                        onSuccess = { list ->
                            _similar.value = list.filter { it.id != media.id }.take(12)
                        },
                        onFailure = { _similar.value = emptyList() }
                    )
                }
            } catch (e: Exception) {
                Log.w("DetailsViewModel", "Failed to load similar media", e)
            }
        }
    }

    private suspend fun loadWatchProgress(media: Media) {
        try {
            // Use direct playback progress API (matches web frontend: GET /api/playback/progress/{id})
            val result = mediaRepository.getPlaybackProgress(media.id.toString())
            
            result.fold(
                onSuccess = { progress ->
                    if (progress != null && progress.duration > 0) {
                        val watchProgress = (progress.progress.toFloat() / progress.duration.toFloat()).coerceIn(0f, 1f)
                        Log.d("DetailsViewModel", "Watch progress for media ${media.id}: ${(watchProgress * 100).toInt()}% (${progress.progress}s / ${progress.duration}s)")
                        _uiState.value = DetailsUiState.Success(media, watchProgress, progress.progress)
                    } else {
                        Log.d("DetailsViewModel", "No watch progress found for media ${media.id}")
                        _uiState.value = DetailsUiState.Success(media, null, null)
                    }
                },
                onFailure = { error ->
                    Log.w("DetailsViewModel", "Failed to load watch progress: ${error.message}")
                    _uiState.value = DetailsUiState.Success(media, null, null)
                }
            )
        } catch (e: Exception) {
            Log.w("DetailsViewModel", "Error loading watch progress", e)
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