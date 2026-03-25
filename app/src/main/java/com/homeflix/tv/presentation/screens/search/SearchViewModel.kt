package com.homeflix.tv.presentation.screens.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.homeflix.tv.data.repository.MediaRepository
import com.homeflix.tv.domain.model.Genre
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
    
    private val _genres = MutableStateFlow<List<Genre>>(emptyList())
    val genres: StateFlow<List<Genre>> = _genres.asStateFlow()
    
    private val _topSearches = MutableStateFlow<List<Media>>(emptyList())
    val topSearches: StateFlow<List<Media>> = _topSearches.asStateFlow()
    
    private var searchJob: Job? = null
    
    init {
        loadInitialData()
    }
    
    private fun loadInitialData() {
        viewModelScope.launch {
            // Load genres
            mediaRepository.getAllGenres()
                .collect { result ->
                    result.fold(
                        onSuccess = { genreList ->
                            _genres.value = genreList
                        },
                        onFailure = { error ->
                            // Log error but don't show to user for genres
                            android.util.Log.e("SearchViewModel", "Failed to load genres", error)
                        }
                    )
                }
        }
        
        viewModelScope.launch {
            // Load top searches (recently added movies — dynamic content)
            mediaRepository.getMovies(limit = 20, offset = 0)
                .collect { result ->
                    result.fold(
                        onSuccess = { movies ->
                            // Sort by ID descending for recently added content
                            val recentMovies = movies
                                .sortedByDescending { it.id }
                                .take(8)
                            _topSearches.value = recentMovies
                        },
                        onFailure = { error ->
                            android.util.Log.e("SearchViewModel", "Failed to load top searches", error)
                        }
                    )
                }
        }
    }
    
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
    
    fun searchByGenre(genreName: String) {
        searchJob?.cancel()
        
        searchJob = viewModelScope.launch {
            _uiState.value = SearchUiState.Loading
            
            mediaRepository.getMediaByGenre(genreName, limit = 100)
                .collect { result ->
                    result.fold(
                        onSuccess = { searchResults ->
                            _uiState.value = SearchUiState.Success(searchResults)
                        },
                        onFailure = { error ->
                            _uiState.value = SearchUiState.Error(
                                error.message ?: "Genre search failed"
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