package com.homeflix.tv.presentation.screens.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.homeflix.tv.data.repository.MediaRepository
import com.homeflix.tv.domain.model.Media
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val mediaRepository: MediaRepository
) : ViewModel() {
    
    private val _uiState = MutableStateFlow<SearchUiState>(SearchUiState.Initial)
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()
    
    private var searchJob: Job? = null
    
    fun searchMedia(query: String) {
        searchJob?.cancel()
        
        if (query.isBlank()) {
            _uiState.value = SearchUiState.Initial
            return
        }
        
        searchJob = viewModelScope.launch {
            // Debounce search to avoid too many API calls
            delay(300)
            
            _uiState.value = SearchUiState.Loading
            
            mediaRepository.searchMedia(query, limit = 100)
                .collect { result ->
                    result.fold(
                        onSuccess = { searchResults ->
                            _uiState.value = SearchUiState.Success(searchResults)
                        },
                        onFailure = { error ->
                            _uiState.value = SearchUiState.Error(
                                error.message ?: "Search failed"
                            )
                        }
                    )
                }
        }
    }
    
    fun clearSearch() {
        searchJob?.cancel()
        _uiState.value = SearchUiState.Initial
    }
}

sealed class SearchUiState {
    object Initial : SearchUiState()
    object Loading : SearchUiState()
    data class Error(val message: String) : SearchUiState()
    data class Success(val results: List<Media>) : SearchUiState()
}