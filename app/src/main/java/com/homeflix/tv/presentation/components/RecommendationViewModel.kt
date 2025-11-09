package com.homeflix.tv.presentation.components

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.homeflix.tv.data.repository.MediaRepository
import com.homeflix.tv.domain.model.Media
import com.homeflix.tv.domain.model.MediaType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class RecommendationUiState {
    object Loading : RecommendationUiState()
    data class Success(
        val personalizedRecommendations: List<Media> = emptyList(),
        val similarRecommendations: List<Media> = emptyList(),
        val genreRecommendations: List<Media> = emptyList(),
        val trendingRecommendations: List<Media> = emptyList(),
        val topRatedRecommendations: List<Media> = emptyList(),
        val mixedRecommendations: List<Media> = emptyList()
    ) : RecommendationUiState()
    data class Error(val message: String) : RecommendationUiState()
}

@HiltViewModel
class RecommendationViewModel @Inject constructor(
    private val mediaRepository: MediaRepository
) : ViewModel() {
    
    private val _uiState = MutableStateFlow<RecommendationUiState>(RecommendationUiState.Loading)
    val uiState: StateFlow<RecommendationUiState> = _uiState.asStateFlow()
    
    fun loadRecommendations(currentMedia: Media) {
        viewModelScope.launch {
            try {
                _uiState.value = RecommendationUiState.Loading
                
                // Fetch all media and generate recommendations
                mediaRepository.getAllMedia().collect { result ->
                    val allMedia = result.getOrElse { emptyList() }
                    val filteredMedia = allMedia.filter { it.id != currentMedia.id && it.type == MediaType.MOVIE }
                
                    if (filteredMedia.isEmpty()) {
                        _uiState.value = RecommendationUiState.Success()
                        return@collect
                    }
                    
                    // Generate different types of recommendations
                    val personalizedRecommendations = generatePersonalizedRecommendations(filteredMedia, currentMedia)
                    val similarRecommendations = generateSimilarRecommendations(filteredMedia, currentMedia)
                    val genreRecommendations = generateGenreRecommendations(filteredMedia, currentMedia)
                    val trendingRecommendations = generateTrendingRecommendations(filteredMedia)
                    val topRatedRecommendations = generateTopRatedRecommendations(filteredMedia)
                    val mixedRecommendations = generateMixedRecommendations(filteredMedia, currentMedia)
                    
                    _uiState.value = RecommendationUiState.Success(
                        personalizedRecommendations = personalizedRecommendations,
                        similarRecommendations = similarRecommendations,
                        genreRecommendations = genreRecommendations,
                        trendingRecommendations = trendingRecommendations,
                        topRatedRecommendations = topRatedRecommendations,
                        mixedRecommendations = mixedRecommendations
                    )
                }
                
            } catch (e: Exception) {
                _uiState.value = RecommendationUiState.Error(
                    message = e.message ?: "Failed to load recommendations"
                )
            }
        }
    }
    
    private fun generatePersonalizedRecommendations(allMedia: List<Media>, currentMedia: Media): List<Media> {
        // Prioritize same genre, similar rating, recent content
        return allMedia
            .filter { media ->
                // Same genre or similar rating
                media.genres.any { genre -> 
                    currentMedia.genres.any { currentGenre -> currentGenre.name == genre.name }
                } || Math.abs((media.rating ?: 0.0) - (currentMedia.rating ?: 0.0)) < 1.0
            }
            .sortedByDescending { it.id } // Latest first
            .take(20)
    }
    
    private fun generateSimilarRecommendations(allMedia: List<Media>, currentMedia: Media): List<Media> {
        // Similar by genre and year
        return allMedia
            .filter { media ->
                media.genres.any { genre -> 
                    currentMedia.genres.any { currentGenre -> currentGenre.name == genre.name }
                } && Math.abs((media.year ?: 0) - (currentMedia.year ?: 0)) <= 5
            }
            .sortedByDescending { it.rating ?: 0.0 }
            .take(20)
    }
    
    private fun generateGenreRecommendations(allMedia: List<Media>, currentMedia: Media): List<Media> {
        if (currentMedia.genres.isEmpty()) return emptyList()
        
        val primaryGenre = currentMedia.genres.first().name
        return allMedia
            .filter { media ->
                media.genres.any { it.name == primaryGenre }
            }
            .sortedByDescending { it.id } // Latest first
            .take(20)
    }
    
    private fun generateTrendingRecommendations(allMedia: List<Media>): List<Media> {
        // Sort by view count and recent additions
        return allMedia
            .sortedWith(compareByDescending<Media> { it.viewCount ?: 0 }.thenByDescending { it.id })
            .take(20)
    }
    
    private fun generateTopRatedRecommendations(allMedia: List<Media>): List<Media> {
        return allMedia
            .filter { (it.rating ?: 0.0) >= 7.0 }
            .sortedByDescending { it.rating ?: 0.0 }
            .take(20)
    }
    
    private fun generateMixedRecommendations(allMedia: List<Media>, currentMedia: Media): List<Media> {
        // Mix of different criteria
        val highRated = allMedia.filter { (it.rating ?: 0.0) >= 6.5 }.shuffled().take(7)
        val sameGenre = allMedia.filter { media ->
            media.genres.any { genre -> 
                currentMedia.genres.any { currentGenre -> currentGenre.name == genre.name }
            }
        }.shuffled().take(7)
        val latest = allMedia.sortedByDescending { it.id }.take(6)
        
        return (highRated + sameGenre + latest).distinctBy { it.id }.shuffled().take(20)
    }
}