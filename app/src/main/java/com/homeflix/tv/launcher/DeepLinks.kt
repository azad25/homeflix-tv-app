package com.homeflix.tv.launcher

import android.content.Intent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Links from home-screen cards, waiting for the navigation graph to open them. */
object DeepLinks {
    private val _pending = MutableStateFlow<HomeLink?>(null)
    val pending: StateFlow<HomeLink?> = _pending.asStateFlow()

    /** Returns true if [intent] carried a HomeFlix link. */
    fun offer(intent: Intent?): Boolean {
        if (intent?.action != Intent.ACTION_VIEW) return false
        val link = HomeLink.parse(intent.data) ?: return false
        _pending.value = link
        return true
    }

    fun consume(link: HomeLink) { _pending.compareAndSet(link, null) }
}
