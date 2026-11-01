package com.homeflix.tv.presentation.screens.player

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import com.homeflix.tv.core.ApplicationScope
import com.homeflix.tv.core.PlaybackProgressStore
import com.homeflix.tv.core.PreviewPlayer
import com.homeflix.tv.data.remote.api.HomeFlixApiService
import com.homeflix.tv.data.repository.StreamingRepository
import com.homeflix.tv.domain.model.Media
import com.homeflix.tv.domain.model.MediaType
import com.homeflix.tv.domain.model.SubtitleTrack
import com.homeflix.tv.domain.repository.MediaRepository
import com.homeflix.tv.presentation.screens.tvshows.Episode
import com.homeflix.tv.presentation.ui.MediaFormat
import com.homeflix.tv.util.ApiUtils
import com.homeflix.tv.util.DeviceCapabilities
import com.homeflix.tv.util.ServerConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID
import javax.inject.Inject

/**
 * Owns the ExoPlayer for the full-screen player.
 *
 * The player lives here — not in a composable — so recomposition, focus
 * changes or overlay animations can never tear it down. Its life follows the
 * screen's lifecycle explicitly:
 *  - [onStop]  (Home button, screen off, another app): save position, remember
 *    whether it was playing, release the decoders.
 *  - [onStart] (back in the app): re-prepare at the saved position and restore
 *    the play/pause state, with a loading state until the first frame.
 */
@UnstableApi
@HiltViewModel
class VideoPlayerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val mediaRepository: MediaRepository,
    private val streamingRepository: StreamingRepository,
    private val api: HomeFlixApiService,
    private val progressStore: PlaybackProgressStore,
    private val previewPlayer: PreviewPlayer,
    @ApplicationScope private val appScope: CoroutineScope
) : ViewModel() {

    private val _ui = MutableStateFlow(PlayerUiState())
    val ui: StateFlow<PlayerUiState> = _ui.asStateFlow()

    private val _events = MutableSharedFlow<PlayerEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<PlayerEvent> = _events.asSharedFlow()

    private val trackSelector = DefaultTrackSelector(context).apply {
        parameters = buildUponParameters()
            .setPreferredTextLanguage("en")
            .setSelectUndeterminedTextLanguage(true)
            .setPreferredAudioLanguage(null)
            .setTunnelingEnabled(false)
            .build()
    }

    private val listener = object : Player.Listener {
        override fun onPlaybackStateChanged(state: Int) {
            when (state) {
                Player.STATE_BUFFERING -> _ui.update { it.copy(isBuffering = true) }
                Player.STATE_READY -> {
                    retryCount = 0
                    // Audio-only media never renders a frame: don't sit on the loading curtain.
                    if (!player.currentTracks.containsType(C.TRACK_TYPE_VIDEO) && !player.currentTracks.isEmpty) {
                        _ui.update { it.copy(phase = PlayerPhase.Ready) }
                    }
                    _ui.update {
                        it.copy(
                            isBuffering = false,
                            durationMs = player.duration.takeIf { d -> d > 0 } ?: it.durationMs
                        )
                    }
                }
                Player.STATE_ENDED -> onEnded()
                Player.STATE_IDLE -> Unit
            }
        }

        override fun onRenderedFirstFrame() {
            _ui.update { it.copy(phase = PlayerPhase.Ready, isBuffering = false) }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _ui.update { it.copy(isPlaying = isPlaying) }
            if (!isPlaying) saveProgress(sync = true)
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            _ui.update { it.copy(playWhenReady = playWhenReady) }
        }

        override fun onTracksChanged(tracks: Tracks) {
            _ui.update { it.copy(audioTracks = audioOptions(tracks), textTracks = textOptions(tracks)) }
        }

        override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
            _ui.update { it.copy(speed = playbackParameters.speed) }
        }

        override fun onPlayerError(error: PlaybackException) = handleError(error)
    }

    val player: ExoPlayer = buildPlayer()

    private val sessionId = UUID.randomUUID().toString().replace("-", "").take(16)
    private var mediaId: Int = 0
    private var loadedFor: Int = -1
    private var streamUrl: String? = null
    private var isHls = false
    private var triedTranscodeFallback = false
    private var subtitleConfigs: List<MediaItem.SubtitleConfiguration> = emptyList()
    private var retryCount = 0
    private var stoppedPositionMs = -1L
    private var resumePlayingOnStart = true
    private var endedHandled = false
    private var tickerJob: Job? = null

    private fun buildPlayer(): ExoPlayer {
        // Buffer budget follows Android TV memory guidance: ~40-60 MB on 1-2 GB
        // devices, more only where the heap class allows it.
        val lowRam = DeviceCapabilities.isLowRam(context)
        val veryLow = DeviceCapabilities.isVeryLowRam(context)
        val targetBytes = when { veryLow -> 40; lowRam -> 64; else -> 128 } * 1024 * 1024
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                /* minBufferMs = */ 15_000,
                /* maxBufferMs = */ if (lowRam) 40_000 else 90_000,
                /* bufferForPlaybackMs = */ 1_000,
                /* bufferForPlaybackAfterRebufferMs = */ 2_500
            )
            .setTargetBufferBytes(targetBytes)
            .setPrioritizeTimeOverSizeThresholds(false)
            .build()
        val renderers = DefaultRenderersFactory(context).setEnableDecoderFallback(true)
        return ExoPlayer.Builder(context)
            .setTrackSelector(trackSelector)
            .setRenderersFactory(renderers)
            .setLoadControl(loadControl)
            .setSeekBackIncrementMs(10_000)
            .setSeekForwardIncrementMs(10_000)
            .build().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(C.USAGE_MEDIA)
                        .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                        .build(),
                    /* handleAudioFocus = */ true
                )
                setHandleAudioBecomingNoisy(true)
                addListener(listener)
            }
    }

    // ---------------------------------------------------------------- loading

    fun load(id: Int, startMs: Long, forceFromStart: Boolean) {
        if (loadedFor == id) return
        loadedFor = id
        mediaId = id
        // The full player needs the hardware decoder: make sure no preview holds it.
        previewPlayer.hardStop()
        _ui.value = PlayerUiState(phase = PlayerPhase.Loading)
        viewModelScope.launch {
            val media = runCatching {
                withTimeoutOrNull(8_000) { mediaRepository.getMediaById(id.toString()).first().getOrNull() }
            }.getOrNull()
            if (media == null) {
                _ui.update { it.copy(phase = PlayerPhase.Error("Couldn't load this title. Check the connection to your HomeFlix server.")) }
                return@launch
            }
            _ui.update { it.withMedia(media) }

            // Resolve everything that playback needs in parallel.
            val resumeDeferred = async {
                if (forceFromStart) 0L else progressStore.resolveResumeMs(id, startMs)
            }
            val subsDeferred = async { fetchSubtitles(id) }
            val resumeMs = resumeDeferred.await()
            val decisionDeferred = async { resolveStream(id, resumeMs / 1000, transcode = false) }
            val (url, hls) = decisionDeferred.await()
            subtitleConfigs = subsDeferred.await()
            streamUrl = url
            isHls = hls
            startPlayback(resumeMs, playWhenReady = true)

            if (media.type == MediaType.EPISODE && media.seriesId != null) enrichEpisode(media)
            refreshMyList(media.id)
        }
        startTicker()
    }

    private suspend fun resolveStream(id: Int, startSec: Long, transcode: Boolean): Pair<String, Boolean> {
        val root = ServerConfig.activeApiBase.removeSuffix("/").removeSuffix("/api")
        val fallback = "$root/api/stream/$id" to false
        val dto = runCatching {
            withTimeoutOrNull(4_000) {
                api.getStreamDecision(
                    id = id,
                    sid = sessionId,
                    startSeconds = startSec,
                    videoCodecs = if (transcode) "h264" else null,
                    audioCodecs = if (transcode) "aac" else null
                ).body()
            }
        }.getOrNull() ?: return fallback
        val url = dto.url?.takeIf { it.isNotBlank() } ?: return fallback
        val absolute = if (url.startsWith("http")) url else root + (if (url.startsWith("/")) url else "/$url")
        return absolute to (dto.strategy.equals("hls", ignoreCase = true) || absolute.contains(".m3u8"))
    }

    private suspend fun fetchSubtitles(id: Int): List<MediaItem.SubtitleConfiguration> {
        val tracks: List<SubtitleTrack> = runCatching {
            withTimeoutOrNull(2_000) { streamingRepository.getSubtitleTracks(id.toString()).first().getOrNull() }
        }.getOrNull().orEmpty()
        if (tracks.isEmpty()) return emptyList()
        // One sidecar track (preferring English) keeps start-up fast and avoids
        // a failing subtitle download stalling the video.
        val pick = tracks.firstOrNull { it.language.startsWith("en", ignoreCase = true) } ?: tracks.first()
        val mime = when (pick.format.lowercase()) {
            "ass", "ssa" -> MimeTypes.TEXT_SSA
            "vtt", "webvtt" -> MimeTypes.TEXT_VTT
            else -> MimeTypes.APPLICATION_SUBRIP
        }
        return listOf(
            MediaItem.SubtitleConfiguration.Builder(android.net.Uri.parse(ApiUtils.getSubtitleUrl(id, pick.id)))
                .setMimeType(mime)
                .setLanguage(pick.language)
                .setLabel(pick.title?.takeIf { it.isNotBlank() } ?: MediaFormat.languageName(pick.language))
                .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                .build()
        )
    }

    private fun buildItem(): MediaItem {
        val builder = MediaItem.Builder().setUri(streamUrl).setSubtitleConfigurations(subtitleConfigs)
        if (isHls) builder.setMimeType(MimeTypes.APPLICATION_M3U8)
        return builder.build()
    }

    private fun startPlayback(positionMs: Long, playWhenReady: Boolean) {
        endedHandled = false
        _ui.update { it.copy(phase = PlayerPhase.Loading) }
        player.setMediaItem(buildItem(), positionMs.coerceAtLeast(0))
        player.playWhenReady = playWhenReady
        player.prepare()
    }

    private suspend fun enrichEpisode(media: Media) {
        runCatching {
            val seriesId = media.seriesId ?: return
            val series = mediaRepository.getTvSeriesById(seriesId)
            val season = media.seasonNumber ?: 1
            val episodes = mediaRepository.getTvSeriesEpisodes(seriesId, season)
            val index = episodes.indexOfFirst { it.id == media.id }
            val current = if (index >= 0) episodes[index] else null
            val next = if (index >= 0) episodes.getOrNull(index + 1) else null
            val epTitle = current?.episodeTitle?.takeIf { it.isNotBlank() } ?: current?.title
            _ui.update {
                it.copy(
                    title = series.title,
                    subtitle = MediaFormat.episodeLabel(media.seasonNumber, media.episodeNumber, epTitle),
                    nextEpisode = next,
                    nextEpisodeLabel = next?.let { n ->
                        MediaFormat.episodeLabel(season, (media.episodeNumber ?: index + 1) + 1, n.episodeTitle ?: n.title)
                    }
                )
            }
        }.onFailure { Log.w(TAG, "episode context failed: ${it.message}") }
    }

    private suspend fun refreshMyList(id: Int) {
        val inList = runCatching { mediaRepository.checkMyList(id.toString()).getOrNull() }.getOrNull()
        _ui.update { it.copy(inMyList = inList) }
    }

    // ---------------------------------------------------------------- player events

    private fun handleError(error: PlaybackException) {
        Log.w(TAG, "playback error ${error.errorCodeName}: ${error.message}")
        val position = player.currentPosition.coerceAtLeast(0)
        val code = error.errorCode
        val isIo = code in 2000..2999
        val isFormatOrDecoder = code in 3000..4999

        // 1. A broken sidecar subtitle must never stop the movie.
        if (isIo && subtitleConfigs.isNotEmpty()) {
            subtitleConfigs = emptyList()
            startPlayback(position, playWhenReady = true)
            return
        }
        // 2. Direct play the TV can't decode → ask the server for an H.264/AAC transcode.
        if (isFormatOrDecoder && !isHls && !triedTranscodeFallback) {
            triedTranscodeFallback = true
            viewModelScope.launch {
                val (url, hls) = resolveStream(mediaId, position / 1000, transcode = true)
                if (hls) {
                    streamUrl = url; isHls = true
                    startPlayback(position, playWhenReady = true)
                } else {
                    _ui.update { it.copy(phase = PlayerPhase.Error("This video format isn't supported on this TV.")) }
                }
            }
            return
        }
        // 3. Network hiccup / server closed an idle stream → quiet retries with backoff.
        if (retryCount < 3) {
            retryCount++
            viewModelScope.launch {
                _ui.update { it.copy(phase = PlayerPhase.Loading) }
                delay(800L * retryCount)
                startPlayback(position, playWhenReady = true)
            }
            return
        }
        _ui.update {
            it.copy(phase = PlayerPhase.Error(
                if (isIo) "Can't reach your HomeFlix server. Check the network and try again."
                else "Something went wrong while playing this title."
            ))
        }
    }

    private fun onEnded() {
        if (endedHandled) return
        endedHandled = true
        val dur = player.duration.takeIf { it > 0 } ?: _ui.value.durationMs
        progressStore.save(mediaId, dur, dur)
        val next = _ui.value.nextEpisode
        viewModelScope.launch { _events.emit(if (next != null) PlayerEvent.PlayNext(next.id) else PlayerEvent.Close) }
    }

    // ---------------------------------------------------------------- lifecycle

    fun onStart() {
        if (stoppedPositionMs < 0 || streamUrl == null) return
        val pos = stoppedPositionMs
        stoppedPositionMs = -1
        retryCount = 0
        startPlayback(pos, playWhenReady = resumePlayingOnStart)
        startTicker()
    }

    fun onStop() {
        if (streamUrl == null) return
        stoppedPositionMs = player.currentPosition.coerceAtLeast(0)
        resumePlayingOnStart = player.playWhenReady
        saveProgress(sync = true)
        tickerJob?.cancel()
        player.stop() // frees decoders + network; media item and position are kept
    }

    private fun startTicker() {
        tickerJob?.cancel()
        tickerJob = viewModelScope.launch {
            var n = 0
            while (isActive) {
                delay(5_000)
                if (player.isPlaying) {
                    n++
                    saveProgress(sync = n % 6 == 0) // local every 5 s, server every 30 s
                }
            }
        }
    }

    private var lastSyncedPos = -1L

    private fun saveProgress(sync: Boolean) {
        val dur = player.duration
        val pos = player.currentPosition
        if (mediaId <= 0 || dur <= 0 || pos <= 0 || endedHandled) return
        // Several triggers fire on exit (pause, stop, close): sync once per position.
        val doSync = sync && kotlin.math.abs(pos - lastSyncedPos) > 2_000
        if (doSync) lastSyncedPos = pos
        progressStore.save(mediaId, pos, dur, doSync)
    }

    // ---------------------------------------------------------------- controls

    fun togglePlayPause() {
        if (player.playbackState == Player.STATE_IDLE && streamUrl != null) {
            startPlayback(player.currentPosition, playWhenReady = true); return
        }
        if (player.playbackState == Player.STATE_ENDED) { player.seekTo(0); player.play(); return }
        if (player.playWhenReady) player.pause() else player.play()
    }

    fun play() { if (!player.playWhenReady) togglePlayPause() }
    fun pause() { player.pause() }

    fun seekTo(positionMs: Long) {
        val dur = player.duration.takeIf { it > 0 } ?: Long.MAX_VALUE
        player.seekTo(positionMs.coerceIn(0, (dur - 1_000).coerceAtLeast(0)))
    }

    fun restart() { seekTo(0); player.play() }

    fun retry() {
        retryCount = 0
        val url = streamUrl
        if (url == null) { val id = mediaId; loadedFor = -1; load(id, 0, false); return }
        startPlayback(player.currentPosition.coerceAtLeast(0), playWhenReady = true)
    }

    fun setSpeed(speed: Float) { player.setPlaybackSpeed(speed) }

    fun selectAudio(option: TrackOption) {
        val group = player.currentTracks.groups.getOrNull(option.groupIndex) ?: return
        trackSelector.parameters = trackSelector.buildUponParameters()
            .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, option.trackIndex))
            .build()
    }

    fun selectText(option: TrackOption?) {
        val builder = trackSelector.buildUponParameters()
        if (option == null) {
            builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true).clearOverridesOfType(C.TRACK_TYPE_TEXT)
        } else {
            val group = player.currentTracks.groups.getOrNull(option.groupIndex) ?: return
            builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, option.trackIndex))
        }
        trackSelector.parameters = builder.build()
    }

    fun toggleMyList() {
        val inList = _ui.value.inMyList ?: return
        _ui.update { it.copy(inMyList = !inList) }
        viewModelScope.launch {
            val r = if (inList) mediaRepository.removeFromMyList(mediaId.toString())
            else mediaRepository.addToMyList(mediaId.toString())
            if (r.isFailure) _ui.update { it.copy(inMyList = inList) }
        }
    }

    /** User is leaving the player (Back / Close). */
    fun close() {
        saveProgress(sync = true)
        player.pause()
    }

    override fun onCleared() {
        saveProgress(sync = true)
        tickerJob?.cancel()
        player.removeListener(listener)
        player.release()
        if (isHls) {
            val id = mediaId; val sid = sessionId
            appScope.launch { runCatching { api.releaseHlsSession(id, sid) } }
        }
        super.onCleared()
    }

    // ---------------------------------------------------------------- tracks

    private fun audioOptions(tracks: Tracks): List<TrackOption> {
        val out = mutableListOf<TrackOption>()
        tracks.groups.forEachIndexed { gi, group ->
            if (group.type != C.TRACK_TYPE_AUDIO) return@forEachIndexed
            for (ti in 0 until group.length) {
                if (!group.isTrackSupported(ti)) continue
                val f = group.getTrackFormat(ti)
                val name = f.label?.takeIf { it.isNotBlank() } ?: MediaFormat.languageName(f.language)
                val ch = when (f.channelCount) { 1 -> " (Mono)"; 2 -> ""; 6 -> " (5.1)"; 8 -> " (7.1)"; else -> "" }
                out += TrackOption("a$gi.$ti", name + ch, group.isTrackSelected(ti), gi, ti)
            }
        }
        return out
    }

    private fun textOptions(tracks: Tracks): List<TrackOption> {
        val out = mutableListOf<TrackOption>()
        tracks.groups.forEachIndexed { gi, group ->
            if (group.type != C.TRACK_TYPE_TEXT) return@forEachIndexed
            for (ti in 0 until group.length) {
                val f = group.getTrackFormat(ti)
                val name = f.label?.takeIf { it.isNotBlank() } ?: MediaFormat.languageName(f.language)
                out += TrackOption("t$gi.$ti", name, group.isTrackSelected(ti), gi, ti)
            }
        }
        return out
    }

    private fun PlayerUiState.withMedia(media: Media) = copy(
        media = media,
        title = media.title,
        subtitle = if (media.type == MediaType.EPISODE)
            MediaFormat.episodeLabel(media.seasonNumber, media.episodeNumber, null) else null,
        logoUrl = if (media.type == MediaType.EPISODE) media.seriesId?.let { ApiUtils.getSeriesLogoUrl(it) } else ApiUtils.getLogoUrl(media),
        backdropUrl = ApiUtils.getBackdropUrl(media),
        meta = MediaFormat.metaLine(media),
        description = MediaFormat.description(media),
        durationMs = media.duration * 1000L
    )

    companion object { private const val TAG = "PlayerVM" }
}

sealed interface PlayerPhase {
    data object Loading : PlayerPhase
    data object Ready : PlayerPhase
    data class Error(val message: String) : PlayerPhase
}

sealed interface PlayerEvent {
    data object Close : PlayerEvent
    data class PlayNext(val mediaId: Int) : PlayerEvent
}

data class TrackOption(
    val id: String,
    val label: String,
    val selected: Boolean,
    val groupIndex: Int,
    val trackIndex: Int
)

data class PlayerUiState(
    val phase: PlayerPhase = PlayerPhase.Loading,
    val media: Media? = null,
    val title: String = "",
    val subtitle: String? = null,
    val logoUrl: String? = null,
    val backdropUrl: String? = null,
    val meta: String = "",
    val description: String? = null,
    val durationMs: Long = 0,
    val isPlaying: Boolean = false,
    val playWhenReady: Boolean = true,
    val isBuffering: Boolean = false,
    val speed: Float = 1f,
    val audioTracks: List<TrackOption> = emptyList(),
    val textTracks: List<TrackOption> = emptyList(),
    val nextEpisode: Episode? = null,
    val nextEpisodeLabel: String? = null,
    val inMyList: Boolean? = null
) {
    val textSummary: String
        get() {
            val audio = audioTracks.firstOrNull { it.selected }?.label
            val sub = textTracks.firstOrNull { it.selected }?.label
            return when {
                audio != null && sub != null -> "$audio with $sub Subtitles"
                audio != null -> audio
                sub != null -> "$sub Subtitles"
                else -> "Audio & Subtitles"
            }
        }
}
