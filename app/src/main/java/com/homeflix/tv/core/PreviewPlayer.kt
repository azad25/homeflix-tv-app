package com.homeflix.tv.core

import android.content.Context
import android.net.Uri
import android.util.Log
import android.view.TextureView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheWriter
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.homeflix.tv.util.DeviceCapabilities
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * One app-wide, muted preview player shared by every hero and focused tile
 * (Netflix "focus plays the trailer").
 *
 * Why one player: creating an ExoPlayer + video decoder per title costs
 * 300-800 ms on TV SoCs and many TV chips only allow one or two decoder
 * instances. Switching titles on a warm player is just setMediaItem/prepare.
 *
 * Flow for a focused title:
 *  1. [request] — after a short dwell (fast D-pad scrolling never loads
 *     anything), the clip is prepared paused while the still image shows.
 *  2. At the reveal time playback starts; once the first frame is on screen
 *     [visibleKey] flips and the UI crossfades image → video.
 *  3. Focus leaves → [cancel]; the player stops but stays warm.
 *
 * Clips are read through an on-disk LRU cache and neighbours are prefetched,
 * so moving back and forth along a row starts almost instantly.
 */
@UnstableApi
@Singleton
class PreviewPlayer @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var player: ExoPlayer? = null
    private var requestJob: Job? = null
    private var prefetchJob: Job? = null
    private var firstFrameRendered = false
    private var started = false
    private var attachedView: TextureView? = null
    /** Bounded memory of clips that 404'd so we never retry them (LRU, 256 entries). */
    private val failedUrls: MutableSet<String> = java.util.Collections.newSetFromMap(
        object : LinkedHashMap<String, Boolean>(64, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Boolean>?) = size > 256
        }
    )

    /** Key whose clip is loaded (UI should host the video surface for it). */
    private val _activeKey = MutableStateFlow<String?>(null)
    val activeKey: StateFlow<String?> = _activeKey.asStateFlow()

    /** Key whose video is actually playing with a frame on screen. */
    private val _visibleKey = MutableStateFlow<String?>(null)
    val visibleKey: StateFlow<String?> = _visibleKey.asStateFlow()

    /** Display aspect ratio (w/h) of the current clip, for cover-scaling the surface. */
    private val _videoAspect = MutableStateFlow(16f / 9f)
    val videoAspect: StateFlow<Float> = _videoAspect.asStateFlow()

    /** Key whose clip just played to the end (the billboard uses it to rotate). */
    private val _endedKey = MutableStateFlow<String?>(null)
    val endedKey: StateFlow<String?> = _endedKey.asStateFlow()

    /** Bumped every time the app comes back to the foreground so focused UI can re-request. */
    private val _epoch = MutableStateFlow(0)
    val epoch: StateFlow<Int> = _epoch.asStateFlow()

    val tilePreviewsEnabled: Boolean by lazy { !DeviceCapabilities.isVeryLowRam(context) }

    private val cache: SimpleCache by lazy { sharedCache(context) }

    private val upstream by lazy {
        DefaultHttpDataSource.Factory()
            .setConnectTimeoutMs(4_000)
            .setReadTimeoutMs(8_000)
            .setAllowCrossProtocolRedirects(true)
    }

    private val cacheFactory by lazy {
        CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(upstream)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    }

    private fun ensurePlayer(): ExoPlayer {
        player?.let { return it }
        val loadControl = DefaultLoadControl.Builder()
            // Start after ~0.5 s of data; keep the buffer small (it's a 15 s clip).
            .setBufferDurationsMs(2_000, 8_000, 500, 1_000)
            .setTargetBufferBytes(8 * 1024 * 1024)
            .setPrioritizeTimeOverSizeThresholds(false)
            .build()
        val exo = ExoPlayer.Builder(context)
            .setLoadControl(loadControl)
            .setMediaSourceFactory(DefaultMediaSourceFactory(cacheFactory))
            .build()
        exo.trackSelectionParameters = exo.trackSelectionParameters.buildUpon()
            // Muted preview: skip the audio decoder entirely, cap video size.
            .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true)
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
            .setMaxVideoSize(1920, 1080)
            .build()
        exo.volume = 0f
        exo.repeatMode = Player.REPEAT_MODE_OFF
        exo.addListener(object : Player.Listener {
            override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) {
                if (videoSize.width > 0 && videoSize.height > 0) {
                    _videoAspect.value = videoSize.width * videoSize.pixelWidthHeightRatio / videoSize.height
                }
            }

            override fun onRenderedFirstFrame() {
                firstFrameRendered = true
                updateVisible()
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) = updateVisible()

            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED) {
                    // Trailer done: fade back to the still image (Netflix behaviour).
                    _visibleKey.value = null
                    _endedKey.value = _activeKey.value
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                currentUrl?.let { failedUrls += it }
                Log.d(TAG, "preview unavailable: ${error.errorCodeName}")
                stopInternal()
            }
        })
        attachedView?.let { exo.setVideoTextureView(it) }
        player = exo
        return exo
    }

    private var currentUrl: String? = null

    private fun updateVisible() {
        val p = player ?: return
        _visibleKey.value = if (firstFrameRendered && p.isPlaying) _activeKey.value else null
    }

    /**
     * Ask for [url] to play for [key] once focus has rested.
     * @param revealDelayMs time from the request until video may appear.
     */
    fun request(key: String, url: String?, revealDelayMs: Long = 1_200, dwellMs: Long = 400) {
        // Same title, same clip: nothing to do. Same title with a different clip
        // (e.g. a show's preview resolved late) restarts, so a title never keeps
        // showing another title's video.
        if ((key == _activeKey.value && url == currentUrl) || (key == pendingKey && url == pendingUrl)) return
        requestJob?.cancel()
        stopInternal()
        if (!started || url.isNullOrBlank() || url in failedUrls) return
        pendingKey = key
        pendingUrl = url
        _endedKey.value = null
        requestJob = scope.launch {
            delay(dwellMs)
            val p = ensurePlayer()
            currentUrl = url
            firstFrameRendered = false
            _activeKey.value = key
            p.setMediaItem(MediaItem.fromUri(url))
            p.playWhenReady = false
            p.prepare()
            delay((revealDelayMs - dwellMs).coerceAtLeast(0))
            p.playWhenReady = true
        }
    }

    private var pendingKey: String? = null
    private var pendingUrl: String? = null

    /** Focus left [key]: stop its preview (player stays warm for the next title). */
    fun cancel(key: String) {
        if (pendingKey == key || _activeKey.value == key) {
            requestJob?.cancel()
            stopInternal()
        }
    }

    fun cancelAll() {
        requestJob?.cancel()
        stopInternal()
    }

    private fun stopInternal() {
        pendingKey = null
        pendingUrl = null
        _visibleKey.value = null
        _activeKey.value = null
        firstFrameRendered = false
        currentUrl = null
        player?.let {
            it.playWhenReady = false
            it.stop()
            it.clearMediaItems()
        }
    }

    /** Pre-cache the first ~2 MB (≈2-3 s) of neighbouring clips. */
    fun prefetch(urls: List<String>) {
        prefetchJob?.cancel()
        val targets = urls.filter { it.isNotBlank() && it !in failedUrls }.take(3)
        if (targets.isEmpty() || !started) return
        prefetchJob = scope.launch(Dispatchers.IO) {
            delay(600)
            for (u in targets) {
                runCatching {
                    val writer = CacheWriter(
                        cacheFactory.createDataSource(),
                        DataSpec.Builder().setUri(Uri.parse(u)).setPosition(0).setLength(PREFETCH_BYTES).build(),
                        null, null
                    )
                    runInterruptible { writer.cache() }
                }
            }
        }
    }

    fun attachSurface(view: TextureView) {
        attachedView = view
        player?.setVideoTextureView(view)
    }

    fun detachSurface(view: TextureView) {
        if (attachedView === view) {
            attachedView = null
            player?.clearVideoTextureView(view)
        }
    }

    private var users = 0

    /** A screen showing previews became visible (ON_START). */
    fun acquire() {
        users++
        if (users == 1) {
            started = true
            _epoch.value = _epoch.value + 1
        }
    }

    /** That screen went away (ON_STOP): when nobody is left, free the decoder. */
    fun release() {
        users = (users - 1).coerceAtLeast(0)
        if (users == 0) {
            started = false
            hardStop()
        }
    }

    /** System is short on memory: drop the decoder and buffers now; they come back on demand. */
    fun onTrimMemory() {
        hardStop()
    }

    /** The full-screen player is opening: give it the hardware decoder right now. */
    fun hardStop() {
        requestJob?.cancel()
        prefetchJob?.cancel()
        stopInternal()
        player?.release()
        player = null
    }

    companion object {
        private const val TAG = "PreviewPlayer"
        private const val PREFETCH_BYTES = 2L * 1024 * 1024

        @Volatile private var cacheInstance: SimpleCache? = null

        /** SimpleCache allows a single instance per directory. */
        fun sharedCache(context: Context): SimpleCache = cacheInstance ?: synchronized(this) {
            cacheInstance ?: SimpleCache(
                File(context.cacheDir, "preview-clips"),
                LeastRecentlyUsedCacheEvictor(200L * 1024 * 1024),
                StandaloneDatabaseProvider(context)
            ).also { cacheInstance = it }
        }
    }
}
