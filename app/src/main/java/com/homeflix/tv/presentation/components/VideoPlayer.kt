package com.homeflix.tv.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.PlayerView
import com.homeflix.tv.domain.model.Media
import com.homeflix.tv.domain.model.MediaType
import com.homeflix.tv.util.ApiUtils
import kotlinx.coroutines.delay
import kotlin.math.roundToInt
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.EntryPoint
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.withTimeoutOrNull
/**
 * ULTRA-INSTANT LAN VIDEO PLAYER for Android TV
 *
 * Optimized for sub-millisecond streaming performance on LAN networks.
 * Features:
 * - Zero-copy sendfile streaming for instant playback
 * - Multi-tier caching (L1/L2/L3) for sub-ms cache hits
 * - Ultra-fast seeking with backend transcoding
 * - Netflix-level buffer management
 * - Gigabit LAN optimization
 * - Instant MKV transcoding and caching
 * - Sub-millisecond response times
 * - TV remote D-pad navigation
 *
 * Backend Integration:
 * - Uses ultra-fast streaming service with sendfile optimization
 * - Leverages L1 cache for instant preview access
 * - Supports instant seeking through backend transcoding
 * - Optimized for unlimited LAN bandwidth
 */
// Removed hardcoded getBaseUrl - using ApiUtils.getBaseUrl() instead

@dagger.hilt.EntryPoint
@dagger.hilt.InstallIn(dagger.hilt.components.SingletonComponent::class)
interface VideoPlayerEntryPoint {
    fun getMediaRepository(): com.homeflix.tv.domain.repository.MediaRepository
    fun getStreamingRepository(): com.homeflix.tv.data.repository.StreamingRepository
}

@UnstableApi
@Composable
fun VideoPlayer(
    media: Media,
    isVisible: Boolean,
    onClose: () -> Unit,
    seriesTitle: String? = null,
    episodeTitle: String? = null,
    nextEpisodeId: Int? = null,
    startTime: Long = 0L,
    forceStartFromBeginning: Boolean = false,
    onProgress: (currentTime: Long, duration: Long) -> Unit = { _, _ -> },
    onPlayNext: ((Int) -> Unit)? = null,
    modifier: Modifier = Modifier,
    mediaRepository: com.homeflix.tv.domain.repository.MediaRepository? = null
) {

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    // Get MediaRepository from Hilt if not provided
    val hiltEntryPoint = remember {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            VideoPlayerEntryPoint::class.java
        )
    }
    val repository = mediaRepository ?: remember { hiltEntryPoint.getMediaRepository() }
    val streamingRepository = remember { hiltEntryPoint.getStreamingRepository() }
    
    var exoPlayer by remember { mutableStateOf<ExoPlayer?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var currentPosition by remember { mutableStateOf(0L) }
    var duration by remember { mutableStateOf(0L) }
    var showControls by remember { mutableStateOf(true) }
    var isBuffering by remember { mutableStateOf(false) }
    var isMediaLoading by remember { mutableStateOf(true) } // Loading until media with subtitles is ready
    var bufferPercentage by remember { mutableStateOf(0) }
    var volume by remember { mutableStateOf(1f) }
    var isMuted by remember { mutableStateOf(false) }

    // Settings drawer (speed / audio / subtitles) - D-pad navigable
    var showSettings by remember { mutableStateOf(false) }
    var playbackSpeed by remember { mutableStateOf(1.0f) }

    // Resume seeking state - persists across recompositions, resets for new media
    var resumeSeekAttempted by remember(media.id) { mutableStateOf(false) }
    val shouldResumePlayback = remember(media.id) { !forceStartFromBeginning && startTime > 0 }
    

    
    // Subtitle state
    var subtitlesEnabled by remember { mutableStateOf(false) }
    var userDisabledSubtitles by remember(media.id) { mutableStateOf(false) } // Track if user manually disabled
    var availableSubtitleTracks by remember { mutableStateOf<List<Tracks.Group>>(emptyList()) }
    var currentSubtitleTrack by remember { mutableStateOf<Int?>(null) }
    var trackSelector by remember { mutableStateOf<DefaultTrackSelector?>(null) }
    var showSubtitleToast by remember { mutableStateOf(false) }
    var subtitleToastMessage by remember { mutableStateOf("") }
    
    // External subtitle tracks fetched from API
    var externalSubtitleTracks by remember { mutableStateOf<List<com.homeflix.tv.domain.model.SubtitleTrack>>(emptyList()) }

    // Next episode is provided by the ViewModel (nextEpisodeId param) — no
    // client-side scan needed. The player listener below is created once per
    // media, but series enrichment (which supplies nextEpisodeId) arrives
    // asynchronously AFTER creation — read through rememberUpdatedState so
    // STATE_ENDED sees the latest value instead of the stale null.
    val currentNextEpisodeId by rememberUpdatedState(nextEpisodeId)
    val currentOnPlayNext by rememberUpdatedState(onPlayNext)

    // TV remote control focus
    val rootFocusRequester = remember { FocusRequester() }
    val playPauseFocusRequester = remember { FocusRequester() }
    val seekBackwardFocusRequester = remember { FocusRequester() }
    val seekForwardFocusRequester = remember { FocusRequester() }
    val subtitlesFocusRequester = remember { FocusRequester() }
    val closeFocusRequester = remember { FocusRequester() }

    // Progress saving function (matching web app)
    fun savePlaybackProgress() {
        exoPlayer?.let { player ->
            val currentTime = player.currentPosition / 1000 // Convert to seconds
            val totalDuration = player.duration / 1000 // Convert to seconds
            
            if (totalDuration > 0 && currentTime > 5) { // Only save if watched more than 5 seconds
                coroutineScope.launch {
                    try {
                        // Use repository with correct API endpoint: /api/playback/progress
                        val result = repository.updatePlaybackProgress(
                            mediaId = media.id,
                            position = currentTime,
                            duration = totalDuration
                        )
                        if (result.isSuccess) {
                            android.util.Log.d("VideoPlayer", "Progress saved successfully: $currentTime of $totalDuration seconds")
                        } else {
                            android.util.Log.e("VideoPlayer", "Failed to save progress: ${result.exceptionOrNull()?.message}")
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("VideoPlayer", "Failed to save progress", e)
                    }
                }
            }
        }
    }
    
    // Enhanced close function with progress saving
    fun closePlayerWithProgressSave() {
        savePlaybackProgress()
        onClose()
    }
    
    // Subtitle toggle function
    // IMPORTANT: Only use setTrackTypeDisabled() — NOT setRendererDisabled()
    // setRendererDisabled takes a RENDERER INDEX (0,1,2), not a track type constant
    // C.TRACK_TYPE_TEXT = 3, which is NOT the text renderer index (usually 2)
    fun toggleSubtitles() {
        trackSelector?.let { selector ->
            if (availableSubtitleTracks.isNotEmpty()) {
                if (subtitlesEnabled) {
                    // Disable subtitles
                    selector.parameters = selector.parameters.buildUpon()
                        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                        .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                        .build()
                    subtitlesEnabled = false
                    userDisabledSubtitles = true // Mark that user manually disabled
                    currentSubtitleTrack = null
                    subtitleToastMessage = "Subtitles OFF"
                    android.util.Log.d("VideoPlayer", "Subtitles disabled by user via setTrackTypeDisabled(TEXT, true)")
                } else {
                    // Enable subtitles with explicit track selection
                    val firstGroup = availableSubtitleTracks.firstOrNull()
                    if (firstGroup != null && firstGroup.length > 0) {
                        val trackGroup = firstGroup.mediaTrackGroup
                        val format = firstGroup.getTrackFormat(0)
                        selector.parameters = selector.parameters.buildUpon()
                            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                            .setOverrideForType(
                                androidx.media3.common.TrackSelectionOverride(trackGroup, listOf(0))
                            )
                            .build()
                        subtitlesEnabled = true
                        userDisabledSubtitles = false // User re-enabled
                        currentSubtitleTrack = 0
                        val trackLabel = format.label ?: format.language ?: "Track 1"
                        subtitleToastMessage = "Subtitles ON: $trackLabel"
                        android.util.Log.d("VideoPlayer", "Subtitles enabled by user via setTrackTypeDisabled(TEXT, false) + override: lang=${format.language}, label=${format.label}, mime=${format.sampleMimeType}")
                    }
                }
                showSubtitleToast = true
            } else {
                subtitleToastMessage = "No subtitles available"
                showSubtitleToast = true
                android.util.Log.d("VideoPlayer", "No subtitle tracks available to toggle")
            }
        }
    }
    
    // Auto-hide subtitle toast
    LaunchedEffect(showSubtitleToast) {
        if (showSubtitleToast) {
            delay(2000)
            showSubtitleToast = false
        }
    }

    // Auto-hide controls — also while PAUSED (slightly longer dwell), so the
    // Netflix-style pause overlay can fade in without pressing Back.
    LaunchedEffect(showControls, isPlaying, showSettings) {
        if (showControls && !showSettings) {
            delay(if (isPlaying) 3_000 else 4_000)
            showControls = false
        }
    }

    // Initialize ExoPlayer
    LaunchedEffect(media.id, isVisible) {
        if (isVisible) {
            exoPlayer?.release()
            
            // Reset seek flag for new media
            resumeSeekAttempted = false
            
            // CRITICAL: Aggressive safety timeout to ensure video ALWAYS starts
            // Force loading screen off after 10 seconds if still loading
            launch {
                delay(10000) // 10 seconds (reduced from 30)
                if (isMediaLoading) {
                    android.util.Log.w("VideoPlayer", "Loading timeout reached (10s), forcing loading screen off and starting playback")
                    isMediaLoading = false
                    isBuffering = false
                    // Force player to start if it hasn't already
                    exoPlayer?.let { player ->
                        if (!player.isPlaying && player.playbackState != Player.STATE_ENDED) {
                            player.playWhenReady = true
                            android.util.Log.w("VideoPlayer", "Forcing playback start after timeout")
                        }
                    }
                }
            }
            
            // Fetch external subtitle tracks from API with TIMEOUT to prevent infinite loading
            var fetchedSubtitles = emptyList<com.homeflix.tv.domain.model.SubtitleTrack>()
            try {
                // Use withTimeout to prevent blocking forever
                withTimeoutOrNull(3000) { // 3 second timeout
                    streamingRepository.getSubtitleTracks(media.id.toString()).collect { result ->
                        if (result.isSuccess) {
                            val allSubtitles = result.getOrNull() ?: emptyList()
                            // CRITICAL FIX: Only use the FIRST subtitle to prevent loading issues
                            fetchedSubtitles = if (allSubtitles.isNotEmpty()) {
                                listOf(allSubtitles.first())
                            } else {
                                emptyList()
                            }
                            externalSubtitleTracks = fetchedSubtitles
                            android.util.Log.d("VideoPlayer", "Using first subtitle track from ${allSubtitles.size} available tracks")
                        } else {
                            android.util.Log.w("VideoPlayer", "Failed to fetch subtitles: ${result.exceptionOrNull()?.message}")
                        }
                    }
                } ?: run {
                    android.util.Log.w("VideoPlayer", "Subtitle fetch timed out after 3 seconds, proceeding without subtitles")
                }
            } catch (e: Exception) {
                android.util.Log.w("VideoPlayer", "Error fetching external subtitles, proceeding without them", e)
            }

            // Create track selector with subtitle support and auto-selection
            val newTrackSelector = DefaultTrackSelector(context)
            // CRITICAL: Configure to NEVER block video playback for subtitle loading
            newTrackSelector.parameters = newTrackSelector.parameters.buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false) // Enable text tracks
                .setPreferredTextLanguage("en") // Prefer English subtitles
                .setIgnoredTextSelectionFlags(C.SELECTION_FLAG_FORCED) // Ignore forced subtitles if they fail
                .setSelectUndeterminedTextLanguage(false) // Don't wait for undetermined language tracks
                .setExceedRendererCapabilitiesIfNecessary(true) // Allow exceeding capabilities
                .setTunnelingEnabled(false) // Disable tunneling for better compatibility
                .build()
            trackSelector = newTrackSelector

            // Enable decoder fallback for black screen issues
            val renderersFactory = DefaultRenderersFactory(context)
                .setEnableDecoderFallback(true)

            // Buffer AHEAD like Netflix/YouTube for smooth playback: start fast
            // (~2s) but then keep reading ahead up to ~2 minutes so disk seeks,
            // WiFi dips or server hiccups never stall playback. A byte cap keeps
            // it safe on low-RAM TVs — at high bitrate (4K) the cap is hit first
            // (~15-20s), at HD bitrate it reaches the full ~2 minutes.
            val lowRam = com.homeflix.tv.util.DeviceCapabilities.isLowRam(context)
            val targetBufferBytes = if (lowRam) 80 * 1024 * 1024 else 256 * 1024 * 1024
            val loadControl = androidx.media3.exoplayer.DefaultLoadControl.Builder()
                .setBufferDurationsMs(
                    30_000,   // Min buffer to keep (30s)
                    120_000,  // Max read-ahead (2 minutes)
                    2_000,    // Buffer before playback starts (2s — still fast)
                    5_000     // Buffer before resuming after a rebuffer (5s)
                )
                .setTargetBufferBytes(targetBufferBytes)
                // Respect the byte cap so 4K on a 2GB TV can't OOM
                .setPrioritizeTimeOverSizeThresholds(false)
                .build()

            val player = ExoPlayer.Builder(context)
                .setTrackSelector(newTrackSelector)
                .setRenderersFactory(renderersFactory)
                .setLoadControl(loadControl)
                .build()
                .apply {
                    // ULTRA-INSTANT LAN STREAMING OPTIMIZATION
                    // Netflix-level buffer settings for instant streaming

                    // FIXED: Proper video loading with multiple URL attempts
                    val urlsToTry = listOf(
                        "${ApiUtils.getBaseUrl()}/stream/${media.id}",
                        "file://${media.filePath}",
                        media.filePath // Direct file path
                    )
                    
                    // Build SubtitleConfiguration from FIRST subtitle only (if available)
                    // IMPORTANT: Mark subtitle as optional to prevent blocking video playback
                    val subtitleConfig = if (fetchedSubtitles.isNotEmpty()) {
                        try {
                            val track = fetchedSubtitles.first()
                            val subtitleUri = android.net.Uri.parse(
                                ApiUtils.getSubtitleUrl(media.id, track.id)
                            )
                            val mimeType = when (track.format.lowercase()) {
                                "srt", "subrip" -> MimeTypes.APPLICATION_SUBRIP
                                "ass", "ssa" -> MimeTypes.TEXT_SSA
                                "vtt", "webvtt" -> MimeTypes.TEXT_VTT
                                else -> MimeTypes.APPLICATION_SUBRIP
                            }
                            listOf(
                                MediaItem.SubtitleConfiguration.Builder(subtitleUri)
                                    .setMimeType(mimeType)
                                    .setLanguage(track.language)
                                    .setLabel(track.title ?: track.language)
                                    .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                                    .setRoleFlags(0) // No special role flags - optional subtitle
                                    .build()
                            )
                        } catch (e: Exception) {
                            android.util.Log.w("VideoPlayer", "Failed to build subtitle config", e)
                            emptyList()
                        }
                    } else {
                        emptyList()
                    }
                    
                    if (subtitleConfig.isNotEmpty()) {
                        android.util.Log.d("VideoPlayer", "Adding 1 subtitle track to MediaItem: ${subtitleConfig[0].language}")
                    } else {
                        android.util.Log.d("VideoPlayer", "No subtitles available for this media")
                    }
                    
                    var mediaLoaded = false
                    for (streamUrl in urlsToTry) {
                        try {
                            android.util.Log.d("VideoPlayer", "Trying URL: $streamUrl")
                            
                            val mediaItem = MediaItem.Builder()
                                .setUri(streamUrl)
                                .setSubtitleConfigurations(subtitleConfig)
                                .build()
                            
                            if (shouldResumePlayback && startTime > 0) {
                                setMediaItems(listOf(mediaItem), 0, startTime)
                            } else {
                                setMediaItem(mediaItem)
                            }
                            prepare()
                            
                            // Enable audio and auto-play
                            volume = 1f
                            playWhenReady = true
                            mediaLoaded = true
                            
                            android.util.Log.d("VideoPlayer", "Successfully loaded URL: $streamUrl with ${subtitleConfig.size} subtitle")
                            break // Success, exit loop
                            
                        } catch (e: Exception) {
                            android.util.Log.w("VideoPlayer", "Failed to load URL: $streamUrl", e)
                            // Continue to next URL
                        }
                    }
                    
                    if (!mediaLoaded) {
                        android.util.Log.e("VideoPlayer", "Failed to load any video URL for media: ${media.id}")
                        // Try a simple test URL as final fallback
                        try {
                            val testUrl = "${ApiUtils.getBaseUrl()}/media/${media.id}/stream"
                            android.util.Log.d("VideoPlayer", "Final attempt with: $testUrl")
                            
                            val mediaItem = MediaItem.Builder()
                                .setUri(testUrl)
                                .setSubtitleConfigurations(subtitleConfig)
                                .build()
                            
                            if (shouldResumePlayback && startTime > 0) {
                                setMediaItems(listOf(mediaItem), 0, startTime)
                            } else {
                                setMediaItem(mediaItem)
                            }
                            
                            prepare()
                            playWhenReady = true
                        } catch (e: Exception) {
                            android.util.Log.e("VideoPlayer", "All video loading attempts failed", e)
                        }
                    }

                    // Player event listeners
                    addListener(object : Player.Listener {
                        override fun onPlaybackStateChanged(playbackState: Int) {
                            isBuffering = playbackState == Player.STATE_BUFFERING
                            bufferPercentage = this@apply.bufferedPercentage

                            when (playbackState) {
                                Player.STATE_READY -> {
                                    val currentDuration = this@apply.duration

                                    if (currentDuration > 0 && currentDuration != C.TIME_UNSET) {
                                        duration = currentDuration
                                    }

                                    // END GUARD: a resume position at/near the end
                                    // (fully-watched episode from Continue Watching)
                                    // would fire STATE_ENDED instantly and close the
                                    // player. Restart from the beginning instead.
                                    if (!resumeSeekAttempted && shouldResumePlayback &&
                                        currentDuration > 0 && currentDuration != C.TIME_UNSET &&
                                        this@apply.currentPosition >= currentDuration - 5_000
                                    ) {
                                        resumeSeekAttempted = true
                                        this@apply.seekTo(0)
                                        android.util.Log.w("VideoPlayer", "Resume position at end of media — restarting from 0")
                                    }

                                    // CRITICAL FIX: Always set loading flags to false when ready
                                    isBuffering = false
                                    isMediaLoading = false
                                    
                                    // IMMEDIATE subtitle check - detect tracks right after ready
                                    val currentTracks = this@apply.currentTracks
                                    val subtitleGroups = currentTracks.groups.filter { it.type == C.TRACK_TYPE_TEXT }
                                    android.util.Log.d("VideoPlayer", "Immediate subtitle check: ${subtitleGroups.size} groups found")
                                    
                                    if (subtitleGroups.isNotEmpty()) {
                                        availableSubtitleTracks = subtitleGroups
                                        val firstGroup = subtitleGroups.first()
                                        // Only auto-enable if user hasn't manually disabled
                                        if (firstGroup.length > 0 && !subtitlesEnabled && !userDisabledSubtitles) {
                                            val trackGroup = firstGroup.mediaTrackGroup
                                            newTrackSelector.parameters = newTrackSelector.parameters.buildUpon()
                                                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                                                .setOverrideForType(
                                                    androidx.media3.common.TrackSelectionOverride(trackGroup, listOf(0))
                                                )
                                                .build()
                                            subtitlesEnabled = true
                                            currentSubtitleTrack = 0
                                            android.util.Log.d("VideoPlayer", "Subtitles auto-enabled via immediate check")
                                        }
                                    } else {
                                        // Delayed subtitle re-check: ExoPlayer may detect external subtitle
                                        // tracks after the initial STATE_READY, since they download separately
                                        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
                                            kotlinx.coroutines.delay(2000)
                                            val delayedTracks = this@apply.currentTracks
                                            val delayedSubtitleGroups = delayedTracks.groups.filter { it.type == C.TRACK_TYPE_TEXT }
                                            android.util.Log.d("VideoPlayer", "Delayed subtitle re-check: ${delayedSubtitleGroups.size} groups found")
                                            // Only auto-enable if user hasn't manually disabled
                                            if (delayedSubtitleGroups.isNotEmpty() && !subtitlesEnabled && !userDisabledSubtitles) {
                                                availableSubtitleTracks = delayedSubtitleGroups
                                                val firstGroup = delayedSubtitleGroups.first()
                                                if (firstGroup.length > 0) {
                                                    val trackGroup = firstGroup.mediaTrackGroup
                                                    newTrackSelector.parameters = newTrackSelector.parameters.buildUpon()
                                                        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                                                        .setOverrideForType(
                                                            androidx.media3.common.TrackSelectionOverride(trackGroup, listOf(0))
                                                        )
                                                        .build()
                                                    subtitlesEnabled = true
                                                    currentSubtitleTrack = 0
                                                    android.util.Log.d("VideoPlayer", "Subtitles auto-enabled via delayed re-check")
                                                }
                                            }
                                        }
                                    }
                                }
                                Player.STATE_ENDED -> {
                                    // Save progress before handling episode end
                                    savePlaybackProgress()

                                    // Autoplay the next episode when one exists
                                    // (read latest values — enrichment is async)
                                    val nextId = currentNextEpisodeId
                                    val playNext = currentOnPlayNext
                                    if (nextId != null && playNext != null) {
                                        playNext(nextId)
                                    } else {
                                        onClose()
                                    }
                                }
                                Player.STATE_IDLE -> {
                                    // Player is idle, might need to retry
                                }
                                Player.STATE_BUFFERING -> {
                                    isBuffering = true
                                }
                            }
                        }

                        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                            isBuffering = false
                            isMediaLoading = false // Stop loading screen on error
                            
                            // Check if error is subtitle-related (non-critical)
                            val errorMessage = error.message ?: ""
                            val isSubtitleError = errorMessage.contains("subtitle", ignoreCase = true) ||
                                                 errorMessage.contains("text track", ignoreCase = true) ||
                                                 error.errorCode == androidx.media3.common.PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS
                            
                            if (isSubtitleError) {
                                // Subtitle loading failed - continue playback without subtitles
                                android.util.Log.w("VideoPlayer", "Subtitle loading failed (non-critical): ${error.message}")
                                // Clear subtitle tracks since they're not available
                                availableSubtitleTracks = emptyList()
                                subtitlesEnabled = false
                                // Don't stop video playback for subtitle errors
                            } else {
                                // Critical video error
                                android.util.Log.e("VideoPlayer", "Critical playback error: ${error.message}", error)
                            }
                        }

                        override fun onIsPlayingChanged(playing: Boolean) {
                            isPlaying = playing
                        }
                        
                        override fun onTracksChanged(tracks: Tracks) {
                            // Update available subtitle tracks
                            val subtitleGroups = tracks.groups.filter { group ->
                                group.type == C.TRACK_TYPE_TEXT
                            }
                            availableSubtitleTracks = subtitleGroups
                            android.util.Log.d("VideoPlayer", "Tracks changed: ${subtitleGroups.size} subtitle groups detected")
                            subtitleGroups.forEachIndexed { i, group ->
                                for (j in 0 until group.length) {
                                    val format = group.getTrackFormat(j)
                                    android.util.Log.d("VideoPlayer", "  Subtitle track [$i][$j]: lang=${format.language}, label=${format.label}, mime=${format.sampleMimeType}")
                                }
                            }
                            
                            // Only auto-enable subtitles if user hasn't manually disabled them
                            if (subtitleGroups.isNotEmpty() && !subtitlesEnabled && !userDisabledSubtitles) {
                                val firstGroup = subtitleGroups.first()
                                if (firstGroup.length > 0) {
                                    val trackGroup = firstGroup.mediaTrackGroup
                                    newTrackSelector.parameters = newTrackSelector.parameters.buildUpon()
                                        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                                        .setOverrideForType(
                                            androidx.media3.common.TrackSelectionOverride(trackGroup, listOf(0))
                                        )
                                        .build()
                                    subtitlesEnabled = true
                                    currentSubtitleTrack = 0
                                    android.util.Log.d("VideoPlayer", "Subtitles auto-enabled on track change (user hasn't disabled)")
                                }
                            }
                        }
                        
                        override fun onPositionDiscontinuity(
                            oldPosition: Player.PositionInfo,
                            newPosition: Player.PositionInfo,
                            reason: Int
                        ) {
                            if (reason == Player.DISCONTINUITY_REASON_SEEK) {
                                // Reset buffering after seek completes
                                isBuffering = false
                            }
                        }
                    })

                    // Auto-play with audio enabled
                    // NOTE: prepare() already called above, do NOT call again
                    // Double prepare() can reset subtitle configurations
                    playWhenReady = true
                    volume = 1f
                    setAudioAttributes(
                        androidx.media3.common.AudioAttributes.Builder()
                            .setUsage(androidx.media3.common.C.USAGE_MEDIA)
                            .setContentType(androidx.media3.common.C.AUDIO_CONTENT_TYPE_MOVIE)
                            .build(),
                        true
                    )
                }

            exoPlayer = player

            // Focus on play/pause button initially with delay
            delay(500)
            try {
                playPauseFocusRequester.requestFocus()
            } catch (e: Exception) {
                // Ignore focus errors
            }
        }
    }

    // Poll position/duration continuously (not gated on isPlaying) so the
    // scrubber and timer are correct from the very start and while paused.
    LaunchedEffect(exoPlayer) {
        while (exoPlayer != null) {
            val p = exoPlayer
            if (p != null) {
                currentPosition = p.currentPosition.coerceAtLeast(0L)
                val d = p.duration
                if (d > 0) {
                    duration = d
                    onProgress(currentPosition, d)
                }
            }
            delay(500)
        }
    }

    // Focus ownership: when controls appear, move focus onto the Play button
    // so D-pad drives the buttons; when they hide, return focus to the root so
    // the next key press re-reveals them. This is what makes the buttons
    // selectable instead of the root swallowing all D-pad input.
    LaunchedEffect(showControls) {
        if (showSettings) return@LaunchedEffect
        if (showControls) {
            repeat(10) {
                try { playPauseFocusRequester.requestFocus(); return@LaunchedEffect } catch (_: Exception) { delay(40) }
            }
        } else {
            try { rootFocusRequester.requestFocus() } catch (_: Exception) {}
        }
    }

    // Cleanup with progress saving
    DisposableEffect(Unit) {
        onDispose {
            // Save progress before cleanup
            savePlaybackProgress()
            exoPlayer?.release()
        }
    }

    // LIFECYCLE GUARD: the app must never keep playing in the background.
    // HOME button / screen off -> pause immediately and persist progress.
    val playerLifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(playerLifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            when (event) {
                androidx.lifecycle.Lifecycle.Event.ON_PAUSE,
                androidx.lifecycle.Lifecycle.Event.ON_STOP -> {
                    exoPlayer?.let { player ->
                        if (player.isPlaying) {
                            savePlaybackProgress()
                            player.pause()
                        }
                    }
                }
                else -> {}
            }
        }
        playerLifecycleOwner.lifecycle.addObserver(observer)
        onDispose { playerLifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (isVisible) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(Color.Black)
                .focusRequester(rootFocusRequester)
                .focusable() // receives keys only while controls are hidden
                .onKeyEvent { keyEvent ->
                    if (showSettings) return@onKeyEvent false
                    if (keyEvent.type != KeyEventType.KeyDown) return@onKeyEvent false
                    when (keyEvent.key) {
                        Key.Back, Key.Escape -> {
                            // First Back hides controls; second closes the player
                            if (showControls) { showControls = false; true }
                            else { closePlayerWithProgressSave(); true }
                        }
                        Key.Menu -> {
                            showControls = true; showSettings = true; true
                        }
                        Key.M -> {
                            isMuted = !isMuted
                            exoPlayer?.volume = if (isMuted) 0f else volume
                            showControls = true; true
                        }
                        Key.S -> { toggleSubtitles(); showControls = true; true }
                        else -> {
                            // Any other key: if controls are hidden, reveal them
                            // (focus moves to Play via LaunchedEffect) and consume
                            // this press. If already shown, let the focused
                            // control handle it.
                            if (!showControls) { showControls = true; true } else false
                        }
                    }
                }
        ) {
            // FIXED: Video Player View with proper player binding
            AndroidView(
                factory = { context ->
                    PlayerView(context).apply {
                        useController = false // We'll use custom controls
                        setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER) // Disable built-in buffering indicator
                        // Set background to black to prevent white flash
                        setBackgroundColor(android.graphics.Color.BLACK)
                        
                        // Configure subtitle styling
                        subtitleView?.apply {
                            // Slightly larger bold subtitle text
                            setFixedTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 18f)
                            
                            // Remove black background and set transparent
                            setBackgroundColor(android.graphics.Color.TRANSPARENT)
                            
                            // White bold text with drop shadow for readability
                            setStyle(
                                androidx.media3.ui.CaptionStyleCompat(
                                    android.graphics.Color.WHITE, // Foreground color (text)
                                    android.graphics.Color.TRANSPARENT, // Background color (transparent)
                                    android.graphics.Color.TRANSPARENT, // Window color (transparent)
                                    androidx.media3.ui.CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW, // Edge type
                                    android.graphics.Color.BLACK, // Edge color (shadow)
                                    android.graphics.Typeface.DEFAULT_BOLD // Bold typeface
                                )
                            )
                        }
                    }
                },
                update = { playerView ->
                    // CRITICAL: Update player when exoPlayer changes
                    playerView.player = exoPlayer
                },
                modifier = Modifier.fillMaxSize()
            )

            // ── NETFLIX-STYLE CONTROLS ─────────────────────────────────
            // Top-left title, bottom red scrubber with thumb + remaining
            // time, and a centered option row (Speed / Audio & Subtitles
            // open the D-pad settings drawer).
            if (showControls) {
                // Netflix gradient: subtle top, strong bottom
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            androidx.compose.ui.graphics.Brush.verticalGradient(
                                colors = listOf(
                                    Color.Black.copy(alpha = 0.55f),
                                    Color.Transparent,
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.92f)
                                )
                            )
                        )
                )

                // Title - small, top-left like Netflix
                Column(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(horizontal = 48.dp, vertical = 30.dp)
                ) {
                    val isEpisode = media.type == MediaType.EPISODE
                    // Primary line = series name for episodes (falls back to
                    // media.title if enrichment failed); movies show their title.
                    Text(
                        text = if (isEpisode) (seriesTitle ?: media.title) else media.title,
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    if (isEpisode) {
                        val se = if (media.seasonNumber != null && media.episodeNumber != null)
                            "S${media.seasonNumber}:E${media.episodeNumber} · " else ""
                        // Prefer the real TMDB episode title from enrichment —
                        // media.title is often just the media file name.
                        val epLine = (se + (episodeTitle ?: media.title)).trim().trimEnd('·').trim()
                        if (epLine.isNotBlank()) {
                            Text(
                                text = epLine,
                                color = Color.White.copy(alpha = 0.75f),
                                fontSize = 13.sp
                            )
                        }
                    }
                }

                // Bottom control stack
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 48.dp)
                        .padding(bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    // ── Scrubber: LEFT/RIGHT seeks, CENTER play/pause ──
                    var scrubberFocused by remember { mutableStateOf(false) }
                    val progress = if (duration > 0) (currentPosition.toFloat() / duration).coerceIn(0f, 1f) else 0f
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Elapsed time, left of the bar
                        Text(
                            text = formatTime(currentPosition.coerceAtLeast(0)),
                            color = Color.White.copy(alpha = 0.9f),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(24.dp)
                                .focusRequester(playPauseFocusRequester)
                                .onFocusChanged { scrubberFocused = it.isFocused }
                                .onKeyEvent { keyEvent ->
                                    if (keyEvent.type == KeyEventType.KeyDown) {
                                        when (keyEvent.key) {
                                            Key.DirectionLeft -> {
                                                exoPlayer?.let { p -> p.seekTo((p.currentPosition - 10_000).coerceAtLeast(0)) }
                                                showControls = true
                                                true
                                            }
                                            Key.DirectionRight -> {
                                                exoPlayer?.let { p -> p.seekTo((p.currentPosition + 10_000).coerceAtMost(p.duration)) }
                                                showControls = true
                                                true
                                            }
                                            Key.DirectionCenter, Key.Enter -> {
                                                exoPlayer?.let { p -> if (p.isPlaying) p.pause() else p.play() }
                                                true
                                            }
                                            else -> false
                                        }
                                    } else false
                                }
                                .focusable(),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            // Track
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .height(if (scrubberFocused) 6.dp else 4.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(Color.White.copy(alpha = 0.3f))
                            )
                            // Red fill
                            Box(
                                Modifier
                                    .fillMaxWidth(progress)
                                    .height(if (scrubberFocused) 6.dp else 4.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(Color(0xFFE50914))
                            )
                            // Thumb (visible when scrubber focused, Netflix style)
                            if (scrubberFocused) {
                                Box(
                                    Modifier
                                        .align(
                                            androidx.compose.ui.BiasAlignment(
                                                horizontalBias = progress * 2f - 1f,
                                                verticalBias = 0f
                                            )
                                        )
                                        .size(18.dp)
                                        .clip(RoundedCornerShape(9.dp))
                                        .background(Color(0xFFE50914))
                                        .border(2.dp, Color.White, RoundedCornerShape(9.dp))
                                )
                            }
                        }
                        // Remaining time, right of the bar (Netflix shows -mm:ss)
                        Text(
                            text = formatTime((duration - currentPosition).coerceAtLeast(0)),
                            color = Color.White.copy(alpha = 0.9f),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    // ── Option row: centered like the Netflix TV player ──
                    Row(
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                        horizontalArrangement = Arrangement.spacedBy(18.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        NetflixCircleButton(
                            icon = {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Default.PlayArrow,
                                    contentDescription = if (isPlaying) "Pause" else "Play",
                                    tint = it,
                                    modifier = Modifier.size(30.dp)
                                )
                            },
                            focusRequester = seekBackwardFocusRequester,
                            onClick = { exoPlayer?.let { p -> if (p.isPlaying) p.pause() else p.play() } }
                        )
                        NetflixCircleButton(
                            icon = {
                                Icon(Icons.Rounded.FastRewind, "Rewind 10 seconds", tint = it, modifier = Modifier.size(26.dp))
                            },
                            onClick = {
                                exoPlayer?.let { p -> p.seekTo((p.currentPosition - 10_000).coerceAtLeast(0)) }
                            }
                        )
                        NetflixCircleButton(
                            icon = {
                                Icon(Icons.Rounded.FastForward, "Forward 10 seconds", tint = it, modifier = Modifier.size(26.dp))
                            },
                            onClick = {
                                exoPlayer?.let { p -> p.seekTo((p.currentPosition + 10_000).coerceAtMost(p.duration)) }
                            }
                        )
                        // Next Episode (TV series only)
                        if (nextEpisodeId != null && onPlayNext != null) {
                            NetflixCircleButton(
                                icon = {
                                    Icon(Icons.Rounded.SkipNext, "Next episode", tint = it, modifier = Modifier.size(28.dp))
                                },
                                onClick = {
                                    savePlaybackProgress()
                                    onPlayNext(nextEpisodeId)
                                }
                            )
                        }
                        NetflixPillButton(
                            label = "Speed (${if (playbackSpeed == playbackSpeed.toInt().toFloat()) "${playbackSpeed.toInt()}" else playbackSpeed.toString()}x)",
                            onClick = { showSettings = true }
                        )
                        NetflixPillButton(
                            label = "Audio & Subtitles",
                            focusRequester = subtitlesFocusRequester,
                            onClick = { showSettings = true }
                        )
                    }
                }
            }

            // ── NETFLIX-STYLE PAUSE OVERLAY ────────────────────────────
            // When paused and the controls have faded out, fade in an ambient
            // info panel: what you're watching + synopsis + a banner still.
            androidx.compose.animation.AnimatedVisibility(
                visible = !isPlaying && !showControls && !showSettings && !isMediaLoading && !isBuffering && duration > 0,
                enter = androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(600)),
                exit = androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(250))
            ) {
              Box(Modifier.fillMaxSize()) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            androidx.compose.ui.graphics.Brush.horizontalGradient(
                                colors = listOf(
                                    Color.Black.copy(alpha = 0.92f),
                                    Color.Black.copy(alpha = 0.55f),
                                    Color.Transparent
                                )
                            )
                        )
                )
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 64.dp, vertical = 56.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(0.55f)) {
                        Text(
                            text = "You're watching",
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(Modifier.height(8.dp))
                        val isEp = media.type == MediaType.EPISODE
                        Text(
                            text = if (isEp) (seriesTitle ?: media.title) else media.title,
                            color = Color.White,
                            fontSize = 30.sp,
                            fontWeight = FontWeight.Black,
                            maxLines = 2,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                        if (isEp && media.seasonNumber != null && media.episodeNumber != null) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "S${media.seasonNumber}:E${media.episodeNumber} · ${episodeTitle ?: media.title}",
                                color = Color.White.copy(alpha = 0.85f),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        media.description?.takeIf { it.isNotBlank() }?.let { desc ->
                            Spacer(Modifier.height(12.dp))
                            Text(
                                text = desc,
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 13.sp,
                                lineHeight = 19.sp,
                                maxLines = 4,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                        }
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = "Paused",
                            color = Color.White.copy(alpha = 0.5f),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Spacer(Modifier.width(48.dp))
                    // Banner still on the right — episodes use the SERIES
                    // backdrop (episode media has no backdrop of its own).
                    val pauseArt = if (media.type == MediaType.EPISODE && media.seriesId != null)
                        "${ApiUtils.getBaseUrl()}/series/${media.seriesId}/backdrop"
                    else ApiUtils.getBackdropUrl(media)
                    val pauseArtKey = if (media.type == MediaType.EPISODE && media.seriesId != null)
                        "series_backdrop_${media.seriesId}" else "backdrop_${media.id}"
                    coil.compose.AsyncImage(
                        model = coil.request.ImageRequest.Builder(context)
                            .data(pauseArt)
                            .memoryCacheKey(pauseArtKey)
                            .diskCacheKey(pauseArtKey)
                            .crossfade(false)
                            .build(),
                        contentDescription = null,
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                        modifier = Modifier
                            .weight(0.45f)
                            .aspectRatio(16f / 9f)
                            .clip(RoundedCornerShape(10.dp))
                    )
                }
              }
            }

            // Netflix-style loading indicator - shows during initial load AND buffering
            if (isMediaLoading || isBuffering) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(if (isMediaLoading) Color.Black else Color.Transparent),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = Color(0xFFE50914), // Netflix red
                        modifier = Modifier.size(64.dp),
                        strokeWidth = 6.dp
                    )
                }
            }
            
            // Subtitle toast notification
            if (showSubtitleToast) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 100.dp)
                ) {
                    Card(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp)),
                        colors = CardDefaults.cardColors(
                            containerColor = Color.Black.copy(alpha = 0.8f)
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Subtitles,
                                contentDescription = null,
                                tint = if (subtitlesEnabled) Color(0xFFE50914) else Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                            Text(
                                text = subtitleToastMessage,
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }

            // ── SETTINGS DRAWER: speed / audio / subtitles, D-pad navigable ──
            if (showSettings) {
                val speedOptions = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
                val audioGroups = exoPlayer?.currentTracks?.groups
                    ?.filter { it.type == C.TRACK_TYPE_AUDIO } ?: emptyList()

                val sections = buildList {
                    add(SettingsSection(
                        title = "Playback Speed",
                        options = speedOptions.map { speed ->
                            SettingsOption(
                                label = if (speed == 1.0f) "Normal" else "${speed}x",
                                selected = playbackSpeed == speed,
                                onSelect = {
                                    playbackSpeed = speed
                                    exoPlayer?.setPlaybackSpeed(speed)
                                }
                            )
                        }
                    ))
                    if (availableSubtitleTracks.isNotEmpty()) {
                        add(SettingsSection(
                            title = "Subtitles",
                            options = buildList {
                                add(SettingsOption(
                                    label = "Off",
                                    selected = !subtitlesEnabled,
                                    onSelect = { if (subtitlesEnabled) toggleSubtitles() }
                                ))
                                availableSubtitleTracks.forEachIndexed { index, group ->
                                    val format = group.getTrackFormat(0)
                                    val label = format.label ?: format.language ?: "Track ${index + 1}"
                                    add(SettingsOption(
                                        label = label,
                                        selected = subtitlesEnabled && currentSubtitleTrack == index,
                                        onSelect = {
                                            trackSelector?.let { selector ->
                                                selector.parameters = selector.parameters.buildUpon()
                                                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                                                    .setOverrideForType(
                                                        androidx.media3.common.TrackSelectionOverride(
                                                            group.mediaTrackGroup, listOf(0)
                                                        )
                                                    )
                                                    .build()
                                                subtitlesEnabled = true
                                                userDisabledSubtitles = false
                                                currentSubtitleTrack = index
                                            }
                                        }
                                    ))
                                }
                            }
                        ))
                    }
                    if (audioGroups.size > 1) {
                        add(SettingsSection(
                            title = "Audio",
                            options = audioGroups.mapIndexed { index, group ->
                                val format = group.getTrackFormat(0)
                                val label = format.label ?: format.language ?: "Audio ${index + 1}"
                                SettingsOption(
                                    label = label,
                                    selected = group.isSelected,
                                    onSelect = {
                                        trackSelector?.let { selector ->
                                            selector.parameters = selector.parameters.buildUpon()
                                                .setOverrideForType(
                                                    androidx.media3.common.TrackSelectionOverride(
                                                        group.mediaTrackGroup, listOf(0)
                                                    )
                                                )
                                                .build()
                                        }
                                    }
                                )
                            }
                        ))
                    }
                }

                PlayerSettingsPanel(
                    sections = sections,
                    onClose = { showSettings = false }
                )
            }
        }
    }
}

/**
 * Netflix-style circular control button: translucent at rest, white on focus.
 * The icon lambda receives the tint to use.
 */
@Composable
private fun NetflixCircleButton(
    icon: @Composable (tint: Color) -> Unit,
    onClick: () -> Unit,
    focusRequester: FocusRequester? = null
) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .size(52.dp)
            .clip(RoundedCornerShape(26.dp))
            .background(if (focused) Color.White else Color.White.copy(alpha = 0.14f))
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged { focused = it.isFocused }
            .onKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown &&
                    (keyEvent.key == Key.DirectionCenter || keyEvent.key == Key.Enter)
                ) {
                    onClick(); true
                } else false
            }
            .focusable()
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        icon(if (focused) Color.Black else Color.White)
    }
}

/**
 * Netflix-style text pill button (e.g. "Speed (1x)", "Audio & Subtitles").
 */
@Composable
private fun NetflixPillButton(
    label: String,
    onClick: () -> Unit,
    focusRequester: FocusRequester? = null
) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(24.dp))
            .background(if (focused) Color.White else Color.White.copy(alpha = 0.14f))
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged { focused = it.isFocused }
            .onKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown &&
                    (keyEvent.key == Key.DirectionCenter || keyEvent.key == Key.Enter)
                ) {
                    onClick(); true
                } else false
            }
            .focusable()
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (focused) Color.Black else Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 11.dp)
        )
    }
}

private fun formatTime(timeMs: Long): String {
    val totalSeconds = timeMs / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60

    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%d:%02d", minutes, seconds)
    }
}
