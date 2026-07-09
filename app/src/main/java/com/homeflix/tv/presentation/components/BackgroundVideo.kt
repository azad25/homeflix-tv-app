package com.homeflix.tv.presentation.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import kotlinx.coroutines.delay

/**
 * Prime/Netflix-style ambient background video.
 *
 * Shows [backdropUrl] instantly, then after [startDelayMs] starts a muted,
 * looping preview clip and crossfades it in once the first frame renders.
 *
 * Lifecycle-safe by construction:
 *  - pauses on ON_PAUSE / releases on ON_STOP (no background audio/battery drain)
 *  - releases on dispose (navigation away)
 *  - restarts cleanly when [videoUrl] changes (hero slide change)
 */
@UnstableApi
@Composable
fun BackgroundVideo(
    backdropUrl: String,
    videoUrl: String?,
    modifier: Modifier = Modifier,
    startDelayMs: Long = 2500,
    playbackEnabled: Boolean = true,
    contentDescription: String? = null
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var player by remember { mutableStateOf<ExoPlayer?>(null) }
    var firstFrameRendered by remember { mutableStateOf(false) }

    val videoAlpha by animateFloatAsState(
        targetValue = if (firstFrameRendered) 1f else 0f,
        animationSpec = tween(durationMillis = 700),
        label = "bg_video_alpha"
    )

    // (Re)start playback when the target video changes. Background preview is
    // a HomeFlix signature — always kept; we just keep buffers small (below)
    // so it's light on low-RAM TVs.
    LaunchedEffect(videoUrl, playbackEnabled) {
        firstFrameRendered = false
        player?.release()
        player = null
        if (videoUrl.isNullOrBlank() || !playbackEnabled) return@LaunchedEffect

        delay(startDelayMs)

        // Small buffers keep the ambient preview light on low-RAM TVs
        val loadControl = androidx.media3.exoplayer.DefaultLoadControl.Builder()
            .setBufferDurationsMs(2_000, 8_000, 1_000, 1_500)
            .build()
        val exo = ExoPlayer.Builder(context)
            .setLoadControl(loadControl)
            .build().apply {
            setMediaItem(MediaItem.fromUri(videoUrl))
            repeatMode = Player.REPEAT_MODE_ONE
            volume = 0f
            playWhenReady = true
            addListener(object : Player.Listener {
                override fun onRenderedFirstFrame() {
                    firstFrameRendered = true
                }

                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                    // Preview unavailable - stay on the backdrop image
                    firstFrameRendered = false
                }
            })
            prepare()
        }
        player = exo
    }

    // Hard lifecycle guarantees: never play while not visible
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> player?.pause()
                Lifecycle.Event.ON_STOP -> {
                    player?.release()
                    player = null
                    firstFrameRendered = false
                }
                Lifecycle.Event.ON_RESUME -> player?.play()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            player?.release()
            player = null
        }
    }

    // Backdrop image - always present underneath
    AsyncImage(
        model = coil.request.ImageRequest.Builder(context)
            .data(backdropUrl)
            .crossfade(true)
            .build(),
        contentDescription = contentDescription,
        modifier = modifier.fillMaxSize(),
        contentScale = ContentScale.Crop
    )

    // Video surface, faded in over the backdrop
    if (player != null) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                }
            },
            update = { view -> view.player = player },
            modifier = modifier
                .fillMaxSize()
                .graphicsLayer { alpha = videoAlpha }
        )
    }
}
