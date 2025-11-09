package com.homeflix.tv.presentation.screens.details

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
            
            mediaRepository.getMediaById(mediaId)
                .collect { result ->
                    result.fold(
                        onSuccess = { media ->
                            _uiState.value = DetailsUiState.Success(media)
                        },
                        onFailure = { error ->
                            _uiState.value = DetailsUiState.Error(
                                error.message ?: "Failed to load media details"
                            )
                        }
                    )
                }
        }
    }
}

sealed class DetailsUiState {
    object Loading : DetailsUiState()
    data class Error(val message: String) : DetailsUiState()
    data class Success(val media: Media) : DetailsUiState()
}