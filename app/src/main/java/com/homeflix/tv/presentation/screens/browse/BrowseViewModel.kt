package com.homeflix.tv.presentation.screens.browse

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.homeflix.tv.data.repository.MediaRepository
import com.homeflix.tv.domain.model.Genre
import com.homeflix.tv.domain.model.Media
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BrowseViewModel @Inject constructor(
    private val mediaRepository: MediaRepository
) : ViewModel() {
    
    private val _uiState = MutableStateFlow<BrowseUiState>(BrowseUiState.Loading)
    val uiState: StateFlow<BrowseUiState> = _uiState.asStateFlow()
    
    init {
        loadBrowseContent()
    }
    
    fun loadBrowseContent() {
        viewModelScope.launch {
            _uiState.value = BrowseUiState.Loading
            
            try {
                // Load content in parallel
                val moviesFlow = mediaRepository.getMovies(limit = 50)
                val tvShowsFlow = mediaRepository.getTVShows(limit = 50)
                val genresFlow = mediaRepository.getAllGenres()
                
                combine(
                    moviesFlow,
                    tvShowsFlow,
                    genresFlow
                ) { moviesResult, tvShowsResult, genresResult ->
                    Triple(moviesResult, tvShowsResult, genresResult)
                }.collect { (moviesResult, tvShowsResult, genresResult) ->
                    
                    val movies = moviesResult.getOrNull() ?: emptyList()
                    val tvShows = tvShowsResult.getOrNull() ?: emptyList()
                    val genres = genresResult.getOrNull() ?: emptyList()
                    
                    if (movies.isEmpty() && tvShows.isEmpty()) {
                        _uiState.value = BrowseUiState.Error("No content available")
                        return@collect
                    }
                    
                    // Load content by genre
                    val genreContent = mutableMapOf<Genre, List<Media>>()
                    
                    genres.take(5).forEach { genre ->
                        mediaRepository.getMediaByGenre(genre.name, limit = 20)
                            .collect { result ->
                                result.getOrNull()?.let { mediaList ->
                                    if (mediaList.isNotEmpty()) {
                                        genreContent[genre] = mediaList
                                    }
                                }
                            }
                    }
                    
                    _uiState.value = BrowseUiState.Success(
                        movies = movies,
                        tvShows = tvShows,
                        genreContent = genreContent
                    )
                }
                
            } catch (e: Exception) {
                _uiState.value = BrowseUiState.Error(e.message ?: "Unknown error occurred")
            }
        }
    }
}

sealed class BrowseUiState {
    object Loading : BrowseUiState()
    data class Error(val message: String) : BrowseUiState()
    data class Success(
        val movies: List<Media>,
        val tvShows: List<Media>,
        val genreContent: Map<Genre, List<Media>>
    ) : BrowseUiState()
}