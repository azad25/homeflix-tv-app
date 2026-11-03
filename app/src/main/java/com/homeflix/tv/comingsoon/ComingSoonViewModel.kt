package com.homeflix.tv.comingsoon

import android.content.Context
import android.view.SurfaceView
import androidx.annotation.OptIn
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** One Coming Soon session: one ExoPlayer for every trailer (setMediaItem + prepare, never recreated). */
@OptIn(UnstableApi::class)
@HiltViewModel
class ComingSoonViewModel @Inject constructor(
    @ApplicationContext context: Context,
    private val repo: ComingSoonRepository
) : ViewModel(), TrailerPlayback, ComingSoonSource {

    private val lowRam = com.homeflix.tv.util.DeviceCapabilities.isLowRam(context)

    val player: ExoPlayer = ExoPlayer.Builder(context)
        .setLoadControl(
            DefaultLoadControl.Builder()
                // Local faststart MP4s: start after ~1 s, keep a modest read-ahead.
                .setBufferDurationsMs(10_000, if (lowRam) 25_000 else 40_000, 1_000, 2_500)
                .setTargetBufferBytes(if (lowRam) 16 * 1024 * 1024 else 32 * 1024 * 1024)
                .setPrioritizeTimeOverSizeThresholds(false)
                .build()
        )
        .build()
        .apply { playWhenReady = true }

    val controller = ComingSoonController(viewModelScope, this, this)

    init {
        player.addListener(object : Player.Listener {
            override fun onRenderedFirstFrame() = controller.onFirstFrame()
            override fun onPlaybackStateChanged(state: Int) { if (state == Player.STATE_ENDED) controller.onEnded() }
            override fun onPlayerError(error: PlaybackException) = controller.onError()
        })
    }

    private var started = false
    fun start(startKey: String?) { if (!started) { started = true; controller.start(startKey) } }

    fun attach(view: SurfaceView) = player.setVideoSurfaceView(view)
    fun detach(view: SurfaceView) = player.clearVideoSurfaceView(view)

    // ComingSoonSource
    override suspend fun items(): List<ComingSoonItem> = repo.lineup().let { it.movies.orEmpty() + it.series.orEmpty() }
    override suspend fun title(mediaType: String, tmdbId: Int) = repo.title(mediaType, tmdbId)
    override suspend fun setNotify(item: ComingSoonItem, on: Boolean) = repo.setNotify(item, on)
    override fun trailerUrl(item: ComingSoonItem) = ComingSoonRepository.trailerUrl(item)

    // TrailerPlayback
    override fun play(url: String) {
        player.setMediaItem(MediaItem.fromUri(url))
        player.prepare()
        player.play()
    }
    override fun stop() { player.stop(); player.clearMediaItems() }
    override fun pause() = player.pause()
    override fun resume() = player.play()

    override fun onCleared() = player.release()
}
