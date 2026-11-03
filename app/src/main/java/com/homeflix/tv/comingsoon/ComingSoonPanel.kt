package com.homeflix.tv.comingsoon

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.homeflix.tv.presentation.ui.PillButton
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Is this title coming soon? (e.g. a show in the library with a new season on the way). */
@HiltViewModel
class ComingSoonPanelViewModel @Inject constructor(private val repo: ComingSoonRepository) : ViewModel() {
    private val _item = MutableStateFlow<ComingSoonItem?>(null)
    val item: StateFlow<ComingSoonItem?> = _item.asStateFlow()
    private var loaded: String? = null

    fun load(mediaType: String, tmdbId: Int?) {
        if (tmdbId == null || tmdbId <= 0) return
        val key = "$mediaType:$tmdbId"
        if (loaded == key) return
        loaded = key
        viewModelScope.launch { _item.value = repo.title(mediaType, tmdbId) }
    }

    /** Optimistic, rolled back on failure, then refreshed for the server's status. */
    fun toggleNotify() {
        val item = _item.value ?: return
        val on = !item.notifyOn
        _item.value = item.copy(notify = ComingSoonNotify(on, if (on) "queued" else ""))
        viewModelScope.launch {
            if (repo.setNotify(item, on).isFailure) { _item.value = item; return@launch }
            repo.title(item.mediaType, item.tmdbId)?.let { _item.value = it }
        }
    }
}

/** Buttons for a title page's action row: "Season 3 · In 20 days" (plays the trailer) and Notify Me. */
@Composable
fun RowScope.ComingSoonActions(item: ComingSoonItem, onTrailer: () -> Unit, onNotify: () -> Unit) {
    val label = listOfNotNull(item.releaseLabel?.takeIf { it.isNotBlank() } ?: "Coming Soon", ComingSoonDates.countdown(item.daysUntil))
        .joinToString("  ·  ")
    PillButton("$label  Trailer", onTrailer, icon = Icons.Filled.PlayCircle)
    PillButton(if (item.notifyOn) "We'll Notify You" else "Notify Me", onNotify,
        icon = if (item.notifyOn) Icons.Filled.Check else Icons.Filled.NotificationsNone)
}
