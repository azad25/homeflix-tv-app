package com.homeflix.tv.presentation.screens.mylist

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
class MyListViewModel @Inject constructor(
    private val mediaRepository: MediaRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<MyListUiState>(MyListUiState.Loading)
    val uiState: StateFlow<MyListUiState> = _uiState.asStateFlow()

    init {
        loadMyList()
    }

    fun loadMyList() {
        viewModelScope.launch {
            _uiState.value = MyListUiState.Loading
            
            // 1. Fetch Continue Watching
            var continueWatchingList = emptyList<com.homeflix.tv.presentation.components.ContinueWatchingItem>()
            try {
                kotlinx.coroutines.withTimeout(10000) {
                    mediaRepository.getRecentlyWatchedWithProgress().collect { recentResult ->
                        recentResult.onSuccess { recentlyWatchedItems ->
                            continueWatchingList = recentlyWatchedItems
                                .filter { item ->
                                    item.media.id > 0 &&
                                    item.media.title.isNotBlank() &&
                                    item.durationSeconds > 0 &&
                                    item.progressSeconds >= 0
                                }
                                .sortedByDescending { it.lastWatchedAt.time }
                                .take(20)
                                .mapNotNull { item ->
                                    try {
                                        val progress = if (item.durationSeconds > 0) {
                                            item.progressSeconds.toFloat() / item.durationSeconds.toFloat()
                                        } else {
                                            0f
                                        }
                                        com.homeflix.tv.presentation.components.ContinueWatchingItem(
                                            media = item.media,
                                            progress = progress.coerceIn(0f, 1f),
                                            progressSeconds = item.progressSeconds,
                                            lastWatched = item.lastWatchedAt.toString()
                                        )
                                    } catch (_: Exception) {
                                        null
                                    }
                                }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("MyListViewModel", "Error loading continue watching", e)
            }
            
            // 2. Fetch My List
            try {
                val result = mediaRepository.getMyList()
                result.fold(
                    onSuccess = { items ->
                        // Filter: only show items that have valid local media (file_path and preview_path)
                        val localItems = items.filter { item ->
                            item.media.filePath.isNotBlank() && item.media.previewPath?.isNotBlank() == true
                        }.map { it.media }
                        
                        Log.d("MyListViewModel", "Total mylist items: ${items.size}, local items: ${localItems.size}, continue watching items: ${continueWatchingList.size}")
                        _uiState.value = MyListUiState.Success(localItems, continueWatchingList)
                    },
                    onFailure = { error ->
                        Log.e("MyListViewModel", "Failed to load my list", error)
                        _uiState.value = MyListUiState.Error(error.message ?: "Failed to load My List")
                    }
                )
            } catch (e: Exception) {
                Log.e("MyListViewModel", "Error loading my list", e)
                _uiState.value = MyListUiState.Error(e.message ?: "Failed to load My List")
            }
        }
    }
}

sealed class MyListUiState {
    object Loading : MyListUiState()
    data class Success(
        val movies: List<Media>,
        val continueWatching: List<com.homeflix.tv.presentation.components.ContinueWatchingItem> = emptyList()
    ) : MyListUiState()
    data class Error(val message: String) : MyListUiState()
}
