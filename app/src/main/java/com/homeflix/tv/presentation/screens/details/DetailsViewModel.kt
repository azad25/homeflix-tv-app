package com.homeflix.tv.presentation.screens.details

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.homeflix.tv.data.repository.MediaRepository
import com.homeflix.tv.domain.model.Media
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.flow.take
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
        val genre = media.genreNames.firstOrNull() ?: media.genres.firstOrNull()?.name
        viewModelScope.launch {
            try {
                // Try the genre as-is (backend genre slugs are capitalized, e.g.
                // "Action" / "Science Fiction" — lowercasing broke matching).
                var results = emptyList<Media>()
                if (!genre.isNullOrBlank()) {
                    mediaRepository.getMediaByGenre(genre, 20, 0).collect { r ->
                        if (r.isSuccess) results = r.getOrNull() ?: emptyList()
                    }
                }
                var filtered = results.filter { it.id != media.id }.take(12)

                // Fallback: if the genre lookup came back empty, show latest
                // movies so the row is never blank.
                if (filtered.isEmpty()) {
                    mediaRepository.getMovies(limit = 20, offset = 0).collect { r ->
                        if (r.isSuccess) {
                            filtered = (r.getOrNull() ?: emptyList())
                                .filter { it.id != media.id }
                                .take(12)
                        }
                    }
                }
                _similar.value = filtered
            } catch (e: Exception) {
                Log.w("DetailsViewModel", "Failed to load similar media", e)
                _similar.value = emptyList()
            }
        }
    }

    private suspend fun loadWatchProgress(media: Media) {
        try {
            // 1) Direct progress endpoint
            val result = mediaRepository.getPlaybackProgress(media.id.toString())
            val progress = result.getOrNull()
            if (progress != null && progress.duration > 0 && progress.progress > 0) {
                val watchProgress = (progress.progress.toFloat() / progress.duration.toFloat()).coerceIn(0f, 1f)
                _uiState.value = DetailsUiState.Success(media, watchProgress, progress.progress)
                return
            }

            // 2) Fallback to the SAME source as the Continue Watching row, so the
            //    detail page and the row always agree (shows Resume when playable).
            var applied = false
            mediaRepository.getRecentlyWatchedWithProgress().take(1).collect { r ->
                val item = r.getOrNull()?.firstOrNull { it.media.id == media.id || it.mediaId == media.id }
                if (item != null && item.durationSeconds > 0 && item.progressSeconds > 0) {
                    val wp = (item.progressSeconds.toFloat() / item.durationSeconds.toFloat()).coerceIn(0f, 1f)
                    _uiState.value = DetailsUiState.Success(media, wp, item.progressSeconds)
                    applied = true
                }
            }
            if (!applied) {
                _uiState.value = DetailsUiState.Success(media, null, null)
            }
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