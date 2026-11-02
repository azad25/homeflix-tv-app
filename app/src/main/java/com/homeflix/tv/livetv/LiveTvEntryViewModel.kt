package com.homeflix.tv.livetv

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Whether to offer Live TV on the top menu (the server has it turned on and has channels). */
@HiltViewModel
class LiveTvEntryViewModel @Inject constructor(private val repo: LiveTvRepository) : ViewModel() {
    val enabled: StateFlow<Boolean> = repo.lineup
        .map { it?.enabled == true && !it.channels.isNullOrEmpty() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, repo.lineup.value?.let { it.enabled && !it.channels.isNullOrEmpty() } ?: false)

    init {
        if (repo.lineup.value == null) viewModelScope.launch { repo.refresh() }
    }
}
