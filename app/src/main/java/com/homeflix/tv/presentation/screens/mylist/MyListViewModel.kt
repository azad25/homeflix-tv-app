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
            try {
                val result = mediaRepository.getMyList()
                result.fold(
                    onSuccess = { items ->
                        // Filter: only show items that have valid local media (file_path and preview_path)
                        val localItems = items.filter { item ->
                            item.media.filePath.isNotBlank() && item.media.previewPath?.isNotBlank() == true
                        }.map { it.media }
                        
                        Log.d("MyListViewModel", "Total items: ${items.size}, local items: ${localItems.size}")
                        _uiState.value = MyListUiState.Success(localItems)
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
    data class Success(val movies: List<Media>) : MyListUiState()
    data class Error(val message: String) : MyListUiState()
}
