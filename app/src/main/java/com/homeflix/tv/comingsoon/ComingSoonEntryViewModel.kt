package com.homeflix.tv.comingsoon

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Whether to offer "Coming Soon" in the profile menu (the server has upcoming titles). */
@HiltViewModel
class ComingSoonEntryViewModel @Inject constructor(repo: ComingSoonRepository) : ViewModel() {
    private val _available = MutableStateFlow(false)
    val available: StateFlow<Boolean> = _available.asStateFlow()

    init {
        viewModelScope.launch {
            val l = repo.lineup()
            _available.value = l.movies.orEmpty().isNotEmpty() || l.series.orEmpty().isNotEmpty()
        }
    }
}
