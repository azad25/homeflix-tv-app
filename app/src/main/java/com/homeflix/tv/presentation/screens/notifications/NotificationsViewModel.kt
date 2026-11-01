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

    /** The user opened the Notifications screen: mark everything as read (clears the bell's dot). */
    fun markAllRead() {
        val current = (_uiState.value as? NotificationsUiState.Success)?.notifications ?: return
        val unread = current.filter { !it.read }
        if (unread.isEmpty()) return
        viewModelScope.launch {
            unread.forEach { runCatching { mediaRepository.markNotificationRead(it.id) } }
        }
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

/** Unread count for the bell in the top nav. */
val NotificationsUiState.unreadCount: Int
    get() = (this as? NotificationsUiState.Success)?.notifications?.count { !it.read } ?: 0

sealed class NotificationsUiState {
    object Loading : NotificationsUiState()
    data class Error(val message: String) : NotificationsUiState()
    data class Success(val notifications: List<Notification>) : NotificationsUiState()
}
