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

/**
 * BROWSE SCREEN - PAGINATED CONTENT FOR PERFORMANCE
 * Implements pagination to prevent memory issues with large libraries.
 * Shows latest content first with load more functionality.
 */
@HiltViewModel
class BrowseViewModel @Inject constructor(
    private val mediaRepository: MediaRepository
) : ViewModel() {
    
    private val _uiState = MutableStateFlow<BrowseUiState>(BrowseUiState.Loading)
    val uiState: StateFlow<BrowseUiState> = _uiState.asStateFlow()
    
    // Pagination state with memory management
    private var currentPage = 0
    private val pageSize = 24 // Netflix-style page size for optimal performance
    private var isLoadingMore = false
    private var hasMoreContent = true
    private val allLoadedMovies = mutableListOf<Media>()
    
    // Memory management - limit total loaded items to prevent OOM
    private val maxLoadedItems = 200 // Maximum items to keep in memory
    
    init {
        loadBrowseContent()
    }
    
    fun loadBrowseContent() {
        viewModelScope.launch {
            _uiState.value = BrowseUiState.Loading
            try {
                // Load one batch (capped for low-RAM) so the screen can group
                // it into genre rows — no scroll pagination needed.
                mediaRepository.getMovies(limit = maxLoadedItems, offset = 0).collect { result ->
                    val movies = result.getOrNull()?.sortedByDescending { it.id } ?: emptyList()
                    if (movies.isEmpty()) {
                        _uiState.value = BrowseUiState.Error("No movies available")
                        return@collect
                    }
                    _uiState.value = BrowseUiState.Success(
                        movies = movies,
                        hasMore = false,
                        isLoadingMore = false,
                        totalCount = movies.size
                    )
                }
            } catch (e: Exception) {
                _uiState.value = BrowseUiState.Error(e.message ?: "Unknown error occurred")
            }
        }
    }
    
    private suspend fun loadMoviesPage() {
        try {
            val offset = currentPage * pageSize
            
            // Load movies with pagination - latest first
            mediaRepository.getMovies(limit = pageSize, offset = offset)
                .collect { moviesResult ->
                    val newMovies = moviesResult.getOrNull()?.sortedByDescending { it.id } ?: emptyList()
                    
                    if (currentPage == 0) {
                        // First page - replace all movies
                        allLoadedMovies.clear()
                        allLoadedMovies.addAll(newMovies)
                        
                        if (allLoadedMovies.isEmpty()) {
                            _uiState.value = BrowseUiState.Error("No movies available")
                            return@collect
                        }
                    } else {
                        // Subsequent pages - append movies with memory management
                        allLoadedMovies.addAll(newMovies)
                        
                        // Memory management: Remove oldest items if we exceed limit
                        if (allLoadedMovies.size > maxLoadedItems) {
                            val itemsToRemove = allLoadedMovies.size - maxLoadedItems
                            repeat(itemsToRemove) {
                                allLoadedMovies.removeFirstOrNull()
                            }
                        }
                    }
                    
                    // Check if we have more content
                    hasMoreContent = newMovies.size == pageSize
                    
                    _uiState.value = BrowseUiState.Success(
                        movies = allLoadedMovies.toList(), // Create immutable copy
                        hasMore = hasMoreContent,
                        isLoadingMore = false,
                        totalCount = allLoadedMovies.size
                    )
                    
                    isLoadingMore = false
                }
                
        } catch (e: Exception) {
            _uiState.value = BrowseUiState.Error(e.message ?: "Failed to load movies")
            isLoadingMore = false
        }
    }
    
    fun loadMoreMovies() {
        if (isLoadingMore || !hasMoreContent) return
        
        viewModelScope.launch {
            isLoadingMore = true
            
            // Update UI to show loading more state
            val currentState = _uiState.value
            if (currentState is BrowseUiState.Success) {
                _uiState.value = currentState.copy(isLoadingMore = true)
            }
            
            currentPage++
            loadMoviesPage()
        }
    }
    
    override fun onCleared() {
        super.onCleared()
        // Clean up memory when ViewModel is destroyed
        allLoadedMovies.clear()
    }
}

sealed class BrowseUiState {
    object Loading : BrowseUiState()
    data class Error(val message: String) : BrowseUiState()
    data class Success(
        val movies: List<Media>,
        val hasMore: Boolean = false,
        val isLoadingMore: Boolean = false,
        val totalCount: Int = 0
    ) : BrowseUiState()
}