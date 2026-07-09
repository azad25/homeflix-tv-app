package com.homeflix.tv.presentation.screens.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.homeflix.tv.data.repository.MediaRepository
import com.homeflix.tv.domain.model.Notification
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class NotificationsViewModel @Inject constructor(
    private val mediaRepository: MediaRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<NotificationsUiState>(NotificationsUiState.Loading)
    val uiState: StateFlow<NotificationsUiState> = _uiState.asStateFlow()

    init {
        loadNotifications()
    }

    fun loadNotifications() {
        viewModelScope.launch {
            if (_uiState.value !is NotificationsUiState.Success) {
                _uiState.value = NotificationsUiState.Loading
            }
            try {
                // Only local-library notifications, newest first.
                val items = mediaRepository.getNotifications(50)
                    .filter { it.isLocal }
                    .sortedByDescending { it.timestampSeconds }
                _uiState.value = NotificationsUiState.Success(items)
            } catch (e: Exception) {
                _uiState.value = NotificationsUiState.Error(e.message ?: "Failed to load notifications")
            }
        }
    }
}

sealed class NotificationsUiState {
    object Loading : NotificationsUiState()
    data class Error(val message: String) : NotificationsUiState()
    data class Success(val notifications: List<Notification>) : NotificationsUiState()
}
