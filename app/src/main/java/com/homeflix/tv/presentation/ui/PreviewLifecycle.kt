package com.homeflix.tv.presentation.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.util.UnstableApi
import com.homeflix.tv.core.PreviewPlayer

/**
 * Ties the shared preview player to this screen's lifecycle: previews may run
 * while the screen is started, and the decoder is freed as soon as no
 * started screen needs it (Home button, player opening, screen off).
 */
@UnstableApi
@Composable
fun PreviewLifecycle(preview: PreviewPlayer) {
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner, preview) {
        var acquired = false
        fun acquire() { if (!acquired) { acquired = true; preview.acquire() } }
        fun release() { if (acquired) { acquired = false; preview.release() } }
        val obs = LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_START -> acquire()
                Lifecycle.Event.ON_STOP -> release()
                else -> Unit
            }
        }
        owner.lifecycle.addObserver(obs)
        onDispose {
            owner.lifecycle.removeObserver(obs)
            release()
        }
    }
}
