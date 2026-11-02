package com.homeflix.tv.livetv

import android.content.Context
import android.view.TextureView
import androidx.annotation.OptIn
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * One Live TV session: **one** ExoPlayer for every channel, preview and zap
 * (setMediaItem + prepare, never recreated), tuned for fast live starts.
 */
@OptIn(UnstableApi::class)
@HiltViewModel
class LiveTvViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repo: LiveTvRepository,
    store: LiveTvStore
) : ViewModel(), LivePlayback {

    val player: ExoPlayer = ExoPlayer.Builder(context)
        .setLoadControl(
            DefaultLoadControl.Builder()
                // Start as soon as ~1.5 s is buffered; rebuffer a little longer.
                .setBufferDurationsMs(15_000, 30_000, 1_500, 3_000)
                .setPrioritizeTimeOverSizeThresholds(true)
                .build()
        )
        .setMediaSourceFactory(
            HlsMediaSource.Factory(
                DefaultHttpDataSource.Factory()
                    .setAllowCrossProtocolRedirects(true)
                    .setConnectTimeoutMs(8_000)
                    .setReadTimeoutMs(10_000)
            ).setAllowChunklessPreparation(true)
        )
        .build()
        .apply { playWhenReady = true }

    val controller = LiveTvController(viewModelScope, repo, store, this)
    val state get() = controller.state

    private val _loadFailed = MutableStateFlow(false)
    /** The lineup couldn't be loaded (server unreachable or Live TV turned off). */
    val loadFailed: StateFlow<Boolean> = _loadFailed.asStateFlow()

    private var started = false

    init {
        player.addListener(object : Player.Listener {
            override fun onRenderedFirstFrame() = controller.onFirstFrame()
            override fun onPlayerError(error: PlaybackException) =
                controller.onPlayerError(error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW)
        })
    }

    fun start(channelId: String?) {
        if (started) return
        started = true
        viewModelScope.launch {
            val lineup = repo.lineup.value?.takeIf { it.enabled && !it.channels.isNullOrEmpty() } ?: repo.refresh()
            val channels = lineup?.takeIf { it.enabled }?.channels.orEmpty()
            if (channels.isEmpty()) { _loadFailed.value = true; return@launch }
            controller.start(channels, channelId)
        }
    }

    fun attach(view: TextureView) = player.setVideoTextureView(view)
    fun detach(view: TextureView) = player.clearVideoTextureView(view)

    // ------------------------------------------------------------ LivePlayback

    override fun play(url: String) {
        val item = MediaItem.Builder()
            .setUri(url)
            .setMimeType(MimeTypes.APPLICATION_M3U8)
            .setLiveConfiguration(MediaItem.LiveConfiguration.Builder().setTargetOffsetMs(12_000).build())
            .build()
        player.setMediaItem(item)
        player.prepare()
        player.play()
    }

    override fun restartAtLiveEdge() {
        player.seekToDefaultPosition()
        player.prepare()
    }

    override fun stop() {
        player.stop()
        player.clearMediaItems()
    }

    override fun setMuted(muted: Boolean) {
        player.volume = if (muted) 0f else 1f
    }

    override fun onCleared() {
        player.release()
    }
}
