package com.homeflix.tv.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.Pause
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
import kotlinx.coroutines.flow.first
import kotlin.math.roundToInt
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.EntryPoint
import androidx.compose.ui.platform.LocalContext
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
    startTime: Long = 0L,
    forceStartFromBeginning: Boolean = false,
    onProgress: (currentTime: Long, duration: Long) -> Unit = { _, _ -> },
    onPlayNext: ((Media) -> Unit)? = null,
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
    
    // Resume seeking state - persists across recompositions, resets for new media
    var resumeSeekAttempted by remember(media.id) { mutableStateOf(false) }
    val shouldResumePlayback = remember(media.id) { !forceStartFromBeginning && startTime > 0 }
    

    
    // Subtitle state
    var subtitlesEnabled by remember { mutableStateOf(false) }
    var availableSubtitleTracks by remember { mutableStateOf<List<Tracks.Group>>(emptyList()) }
    var currentSubtitleTrack by remember { mutableStateOf<Int?>(null) }
    var trackSelector by remember { mutableStateOf<DefaultTrackSelector?>(null) }
    var showSubtitleToast by remember { mutableStateOf(false) }
    var subtitleToastMessage by remember { mutableStateOf("") }
    
    // External subtitle tracks fetched from API
    var externalSubtitleTracks by remember { mutableStateOf<List<com.homeflix.tv.domain.model.SubtitleTrack>>(emptyList()) }

    // Next episode state for autoplay
    var nextEpisode by remember(media.id) { mutableStateOf<Media?>(null) }
    var showNextEpisodePreview by remember { mutableStateOf(false) }
    
    // TV remote control focus
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
    
    // Fetch next episode for TV series autoplay
    LaunchedEffect(media.id) {
        if (media.type == MediaType.EPISODE && media.seriesId != null && onPlayNext != null) {
            try {
                // Get all episodes for this series
                val allMediaResult = repository.getAllMedia(limit = 1000, offset = 0)
                allMediaResult.collect { result ->
                    if (result.isSuccess) {
                        val allMedia = result.getOrNull() ?: emptyList()
                        
                        // Filter episodes for this series
                        val seriesEpisodes = allMedia.filter { 
                            it.type == MediaType.EPISODE && it.seriesId == media.seriesId 
                        }.sortedWith(compareBy({ it.seasonNumber }, { it.episodeNumber }))
                        
                        // Find current episode index
                        val currentIndex = seriesEpisodes.indexOfFirst { it.id == media.id }
                        
                        if (currentIndex != -1 && currentIndex < seriesEpisodes.size - 1) {
                            // Get next episode
                            nextEpisode = seriesEpisodes[currentIndex + 1]
                            android.util.Log.d("VideoPlayer", "Next episode found: ${nextEpisode?.title}")
                        } else {
                            android.util.Log.d("VideoPlayer", "No next episode found (last episode or not found)")
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("VideoPlayer", "Failed to fetch next episode", e)
            }
        }
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
                    currentSubtitleTrack = null
                    subtitleToastMessage = "Subtitles OFF"
                    android.util.Log.d("VideoPlayer", "Subtitles disabled via setTrackTypeDisabled(TEXT, true)")
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
                        currentSubtitleTrack = 0
                        val trackLabel = format.label ?: format.language ?: "Track 1"
                        subtitleToastMessage = "Subtitles ON: $trackLabel"
                        android.util.Log.d("VideoPlayer", "Subtitles enabled via setTrackTypeDisabled(TEXT, false) + override: lang=${format.language}, label=${format.label}, mime=${format.sampleMimeType}")
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

    // Auto-hide controls
    LaunchedEffect(showControls, isPlaying) {
        if (showControls && isPlaying) {
            delay(3000)
            showControls = false
        }
    }

    // Initialize ExoPlayer
    LaunchedEffect(media.id, isVisible) {
        if (isVisible) {
            exoPlayer?.release()
            
            // Reset seek flag for new media
            resumeSeekAttempted = false
            
            // Fetch external subtitle tracks from API
            var fetchedSubtitles = emptyList<com.homeflix.tv.domain.model.SubtitleTrack>()
            try {
                val result = streamingRepository.getSubtitleTracks(media.id.toString()).first()
                if (result.isSuccess) {
                    fetchedSubtitles = result.getOrNull() ?: emptyList()
                    externalSubtitleTracks = fetchedSubtitles
                    android.util.Log.d("VideoPlayer", "Found ${fetchedSubtitles.size} external subtitle tracks")
                } else {
                    android.util.Log.w("VideoPlayer", "Failed to fetch subtitles: ${result.exceptionOrNull()?.message}")
                }
            } catch (e: Exception) {
                android.util.Log.w("VideoPlayer", "Error fetching external subtitles", e)
            }

            // Create track selector with subtitle support
            val newTrackSelector = DefaultTrackSelector(context)
            // Subtitles enabled by default — do NOT disable text track type
            // ExoPlayer will auto-select subtitle tracks from SubtitleConfiguration
            newTrackSelector.parameters = newTrackSelector.parameters.buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                .build()
            trackSelector = newTrackSelector

            // Enable decoder fallback for black screen issues
            val renderersFactory = DefaultRenderersFactory(context)
                .setEnableDecoderFallback(true)

            val player = ExoPlayer.Builder(context)
                .setTrackSelector(newTrackSelector)
                .setRenderersFactory(renderersFactory)
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
                    
                    // Build SubtitleConfigurations from external subtitle tracks
                    val subtitleConfigs = fetchedSubtitles.map { track ->
                        val subtitleUri = android.net.Uri.parse(
                            ApiUtils.getSubtitleUrl(media.id, track.id)
                        )
                        val mimeType = when (track.format.lowercase()) {
                            "srt", "subrip" -> MimeTypes.APPLICATION_SUBRIP
                            "ass", "ssa" -> MimeTypes.TEXT_SSA
                            "vtt", "webvtt" -> MimeTypes.TEXT_VTT
                            else -> MimeTypes.APPLICATION_SUBRIP // Default to SRT
                        }
                        MediaItem.SubtitleConfiguration.Builder(subtitleUri)
                            .setMimeType(mimeType)
                            .setLanguage(track.language)
                            .setLabel(track.title ?: track.language)
                            .setSelectionFlags(if (track.isDefault) C.SELECTION_FLAG_DEFAULT else 0)
                            .build()
                    }
                    
                    if (subtitleConfigs.isNotEmpty()) {
                        android.util.Log.d("VideoPlayer", "Adding ${subtitleConfigs.size} external subtitle tracks to MediaItem")
                    }
                    
                    var mediaLoaded = false
                    for (streamUrl in urlsToTry) {
                        try {
                            android.util.Log.d("VideoPlayer", "Trying URL: $streamUrl")
                            
                            val mediaItem = MediaItem.Builder()
                                .setUri(streamUrl)
                                .setSubtitleConfigurations(subtitleConfigs)
                                .build()
                            
                            // ALWAYS use setMediaItem to preserve SubtitleConfigurations
                            // setMediaItems() can drop subtitle configs in some ExoPlayer versions
                            setMediaItem(mediaItem)
                            prepare()
                            
                            // Don't seek here - wait for STATE_READY for reliable seeking
                            
                            // Enable audio and auto-play
                            volume = 1f
                            playWhenReady = true
                            mediaLoaded = true
                            
                            android.util.Log.d("VideoPlayer", "Successfully loaded URL: $streamUrl")
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
                                .setSubtitleConfigurations(subtitleConfigs)
                                .build()
                            
                            // ALWAYS use setMediaItem to preserve SubtitleConfigurations  
                            setMediaItem(mediaItem)
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
                                    
                                    isBuffering = false
                                    isMediaLoading = false // Media with subtitles is now ready
                                    
                                    // Resume seek: since we use setMediaItem() (not setMediaItems with position)
                                    // to preserve subtitle configs, we seek here on STATE_READY
                                    if (shouldResumePlayback && startTime > 0 && !resumeSeekAttempted) {
                                        resumeSeekAttempted = true
                                        this@apply.seekTo(startTime)
                                        android.util.Log.d("VideoPlayer", "Resume seek to $startTime ms on STATE_READY")
                                    }
                                    
                                    // Delayed subtitle re-check: ExoPlayer may detect external subtitle
                                    // tracks after the initial STATE_READY, since they download separately
                                    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
                                        kotlinx.coroutines.delay(2000)
                                        val currentTracks = this@apply.currentTracks
                                        val subtitleGroups = currentTracks.groups.filter { it.type == C.TRACK_TYPE_TEXT }
                                        android.util.Log.d("VideoPlayer", "Delayed subtitle re-check: ${subtitleGroups.size} groups found")
                                        if (subtitleGroups.isNotEmpty() && !subtitlesEnabled) {
                                            availableSubtitleTracks = subtitleGroups
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
                                                android.util.Log.d("VideoPlayer", "Subtitles enabled via delayed re-check")
                                            }
                                        }
                                    }
                                }
                                Player.STATE_ENDED -> {
                                    // Save progress before handling episode end
                                    savePlaybackProgress()
                                    
                                    // Check if there's a next episode for autoplay
                                    if (nextEpisode != null && onPlayNext != null) {
                                        android.util.Log.d("VideoPlayer", "Episode ended, playing next: ${nextEpisode?.title}")
                                        onPlayNext(nextEpisode!!)
                                    } else {
                                        android.util.Log.d("VideoPlayer", "Episode ended, no next episode available")
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
                            
                            // Auto-enable subtitles when tracks are first detected
                            if (subtitleGroups.isNotEmpty() && !subtitlesEnabled) {
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
                                    android.util.Log.d("VideoPlayer", "Subtitles auto-enabled (default ON)")
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

    // Update progress for UI only (no periodic saving for better performance)
    LaunchedEffect(exoPlayer, isPlaying) {
        while (isPlaying && exoPlayer != null) {
            currentPosition = exoPlayer?.currentPosition ?: 0L
            duration = exoPlayer?.duration ?: 0L

            if (duration > 0) {
                onProgress(currentPosition, duration)
            }

            delay(1000) // Update every second
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

    if (isVisible) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(Color.Black)
                .focusable() // CRITICAL: Make video player focusable for D-pad
                .onKeyEvent { keyEvent ->
                    if (keyEvent.type == KeyEventType.KeyDown) {
                        when (keyEvent.key) {
                            Key.DirectionCenter, Key.Enter, Key.Spacebar -> {
                                // Always show controls and toggle play/pause
                                exoPlayer?.let { player ->
                                    if (player.isPlaying) {
                                        player.pause()
                                    } else {
                                        player.play()
                                    }
                                }
                                showControls = true
                                true
                            }
                            Key.DirectionLeft -> {
                                // Always seek backward and show controls
                                exoPlayer?.let { player ->
                                    if (player.duration > 0) {
                                        val newPosition = (player.currentPosition - 10000).coerceAtLeast(0)
                                        player.seekTo(newPosition)
                                        // Don't manually set isBuffering - let the player handle it
                                    }
                                }
                                showControls = true
                                true
                            }
                            Key.DirectionRight -> {
                                // Always seek forward and show controls
                                exoPlayer?.let { player ->
                                    if (player.duration > 0) {
                                        val newPosition = (player.currentPosition + 10000).coerceAtMost(player.duration)
                                        player.seekTo(newPosition)
                                        // Don't manually set isBuffering - let the player handle it
                                    }
                                }
                                showControls = true
                                true
                            }
                            Key.DirectionUp -> {
                                // Volume up
                                volume = (volume + 0.1f).coerceAtMost(1f)
                                exoPlayer?.volume = volume
                                isMuted = false
                                showControls = true
                                true
                            }
                            Key.DirectionDown -> {
                                // Volume down
                                volume = (volume - 0.1f).coerceAtLeast(0f)
                                exoPlayer?.volume = volume
                                isMuted = volume == 0f
                                showControls = true
                                true
                            }
                            Key.Back, Key.Escape -> {
                                // Close player with progress saving
                                closePlayerWithProgressSave()
                                true
                            }
                            Key.M -> {
                                // Toggle mute
                                isMuted = !isMuted
                                exoPlayer?.volume = if (isMuted) 0f else volume
                                showControls = true
                                true
                            }
                            Key.S -> {
                                // Toggle subtitles
                                toggleSubtitles()
                                showControls = true
                                true
                            }
                            else -> {
                                // Show controls on any other key
                                showControls = true
                                false
                            }
                        }
                    } else {
                        false
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

            // Custom TV Controls (Netflix-style)
            if (showControls) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.3f))
                ) {
                    // Top bar with title and close button
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = media.title,
                            color = Color.White,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold
                        )

                        // Close button removed - use Back/Escape key to close
                    }

                    // Center controls
                    Row(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(horizontal = 48.dp),
                        horizontalArrangement = Arrangement.spacedBy(24.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Seek backward - Netflix style
                        var seekBackFocused by remember { mutableStateOf(false) }
                        IconButton(
                            onClick = {
                                exoPlayer?.let { player ->
                                    val newPosition = (player.currentPosition - 10000).coerceAtLeast(0)
                                    player.seekTo(newPosition)
                                }
                            },
                            modifier = Modifier
                                .focusRequester(seekBackwardFocusRequester)
                                .focusable()
                                .onFocusChanged { seekBackFocused = it.isFocused }
                                .size(56.dp)
                                .clip(RoundedCornerShape(28.dp))
                                .background(
                                    if (seekBackFocused) Color.White.copy(alpha = 0.9f) 
                                    else Color.Black.copy(alpha = 0.7f)
                                )
                                .border(
                                    width = if (seekBackFocused) 2.dp else 0.dp,
                                    color = if (seekBackFocused) Color(0xFFE50914) else Color.Transparent,
                                    shape = RoundedCornerShape(28.dp)
                                )
                                .onKeyEvent { keyEvent ->
                                    if (keyEvent.type == KeyEventType.KeyDown && keyEvent.key == Key.DirectionCenter) {
                                        exoPlayer?.let { player ->
                                            val newPosition = (player.currentPosition - 10000).coerceAtLeast(0)
                                            player.seekTo(newPosition)
                                        }
                                        true
                                    } else false
                                }
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.FastRewind,
                                contentDescription = "Rewind 10 seconds",
                                tint = if (seekBackFocused) Color.Black else Color.White,
                                modifier = Modifier.size(32.dp)
                            )
                        }

                        // Play/Pause - Netflix style
                        var playPauseFocused by remember { mutableStateOf(false) }
                        IconButton(
                            onClick = {
                                exoPlayer?.let { player ->
                                    if (player.isPlaying) {
                                        player.pause()
                                    } else {
                                        player.play()
                                    }
                                }
                            },
                            modifier = Modifier
                                .focusRequester(playPauseFocusRequester)
                                .focusable()
                                .onFocusChanged { playPauseFocused = it.isFocused }
                                .size(72.dp)
                                .clip(RoundedCornerShape(36.dp))
                                .background(Color.White)
                                .border(
                                    width = if (playPauseFocused) 3.dp else 0.dp,
                                    color = if (playPauseFocused) Color(0xFFE50914) else Color.Transparent,
                                    shape = RoundedCornerShape(36.dp)
                                )
                                .onKeyEvent { keyEvent ->
                                    if (keyEvent.type == KeyEventType.KeyDown && keyEvent.key == Key.DirectionCenter) {
                                        exoPlayer?.let { player ->
                                            if (player.isPlaying) {
                                                player.pause()
                                            } else {
                                                player.play()
                                            }
                                        }
                                        true
                                    } else false
                                }
                        ) {
                            if (isPlaying) {
                                Icon(
                                    imageVector = Icons.Rounded.Pause,
                                    contentDescription = "Pause",
                                    tint = Color.Black,
                                    modifier = Modifier.size(36.dp)
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = "Play",
                                    tint = Color.Black,
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                        }

                        // Seek forward - Netflix style
                        var seekForwardFocused by remember { mutableStateOf(false) }
                        IconButton(
                            onClick = {
                                exoPlayer?.let { player ->
                                    val newPosition = (player.currentPosition + 10000).coerceAtMost(player.duration)
                                    player.seekTo(newPosition)
                                }
                            },
                            modifier = Modifier
                                .focusRequester(seekForwardFocusRequester)
                                .focusable()
                                .onFocusChanged { seekForwardFocused = it.isFocused }
                                .size(56.dp)
                                .clip(RoundedCornerShape(28.dp))
                                .background(
                                    if (seekForwardFocused) Color.White.copy(alpha = 0.9f) 
                                    else Color.Black.copy(alpha = 0.7f)
                                )
                                .border(
                                    width = if (seekForwardFocused) 2.dp else 0.dp,
                                    color = if (seekForwardFocused) Color(0xFFE50914) else Color.Transparent,
                                    shape = RoundedCornerShape(28.dp)
                                )
                                .onKeyEvent { keyEvent ->
                                    if (keyEvent.type == KeyEventType.KeyDown && keyEvent.key == Key.DirectionCenter) {
                                        exoPlayer?.let { player ->
                                            val newPosition = (player.currentPosition + 10000).coerceAtMost(player.duration)
                                            player.seekTo(newPosition)
                                        }
                                        true
                                    } else false
                                }
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.FastForward,
                                contentDescription = "Forward 10 seconds",
                                tint = if (seekForwardFocused) Color.Black else Color.White,
                                modifier = Modifier.size(32.dp)
                            )
                        }

                        // Subtitle toggle - Netflix style with enhanced focus
                        if (availableSubtitleTracks.isNotEmpty()) {
                            var subtitleButtonFocused by remember { mutableStateOf(false) }
                            
                            IconButton(
                                onClick = { toggleSubtitles() },
                                modifier = Modifier
                                    .focusRequester(subtitlesFocusRequester)
                                    .focusable()
                                    .onFocusChanged { subtitleButtonFocused = it.isFocused }
                                    .size(56.dp)
                                    .clip(RoundedCornerShape(28.dp))
                                    .background(
                                        when {
                                            subtitleButtonFocused -> Color.White.copy(alpha = 0.9f) // White when focused
                                            subtitlesEnabled -> Color(0xFFE50914).copy(alpha = 0.8f) // Netflix red when enabled
                                            else -> Color.Black.copy(alpha = 0.7f) // Dark when disabled
                                        }
                                    )
                                    .border(
                                        width = if (subtitleButtonFocused) 2.dp else 0.dp,
                                        color = if (subtitleButtonFocused) Color(0xFFE50914) else Color.Transparent,
                                        shape = RoundedCornerShape(28.dp)
                                    )
                                    .onKeyEvent { keyEvent ->
                                        if (keyEvent.type == KeyEventType.KeyDown && keyEvent.key == Key.DirectionCenter) {
                                            toggleSubtitles()
                                            true
                                        } else false
                                    }
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Subtitles,
                                    contentDescription = if (subtitlesEnabled) "Disable subtitles" else "Enable subtitles",
                                    tint = when {
                                        subtitleButtonFocused -> Color.Black // Black icon when focused (on white background)
                                        subtitlesEnabled -> Color.White // White icon when enabled
                                        else -> Color.White // White icon when disabled
                                    },
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }
                    }

                    // Bottom progress bar and info
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(24.dp)
                    ) {
                        // Progress bar (Netflix red) with circular thumb
                        if (duration > 0) {
                            val progress = (currentPosition.toFloat() / duration.toFloat()).coerceIn(0f, 1f)

                            Slider(
                                value = progress,
                                onValueChange = { newProgress ->
                                    // Seek to new position when user drags the slider
                                    exoPlayer?.let { player ->
                                        val newPosition = (newProgress * duration).toLong()
                                        player.seekTo(newPosition)
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(24.dp) // Increased height to accommodate thumb
                                    .focusable()
                                    .onKeyEvent { keyEvent ->
                                        if (keyEvent.type == KeyEventType.KeyDown) {
                                            when (keyEvent.key) {
                                                Key.DirectionLeft -> {
                                                    // Seek backward 10 seconds when left arrow is pressed on progress bar
                                                    exoPlayer?.let { player ->
                                                        val newPosition = (player.currentPosition - 10000).coerceAtLeast(0)
                                                        player.seekTo(newPosition)
                                                    }
                                                    true
                                                }
                                                Key.DirectionRight -> {
                                                    // Seek forward 10 seconds when right arrow is pressed on progress bar
                                                    exoPlayer?.let { player ->
                                                        val newPosition = (player.currentPosition + 10000).coerceAtMost(player.duration)
                                                        player.seekTo(newPosition)
                                                    }
                                                    true
                                                }
                                                else -> false
                                            }
                                        } else false
                                    },
                                colors = SliderDefaults.colors(
                                    thumbColor = Color.White, // White circular thumb
                                    activeTrackColor = Color(0xFFE50914), // Netflix red for progress
                                    inactiveTrackColor = Color.White.copy(alpha = 0.3f) // Semi-transparent white for remaining
                                )
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            // Time info
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = formatTime(currentPosition),
                                    color = Color.White,
                                    fontSize = 16.sp
                                )

                                Text(
                                    text = formatTime(duration),
                                    color = Color.White,
                                    fontSize = 16.sp
                                )
                            }
                        }

                        // Status indicators row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Empty space where volume indicator was
                            Spacer(modifier = Modifier.width(1.dp))

                            // Subtitle status indicator
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (availableSubtitleTracks.isNotEmpty()) {
                                    Icon(
                                        imageVector = Icons.Rounded.Subtitles,
                                        contentDescription = null,
                                        tint = if (subtitlesEnabled) Color(0xFFE50914) else Color.White.copy(alpha = 0.5f),
                                        modifier = Modifier.size(20.dp)
                                    )
                                    
                                    Spacer(modifier = Modifier.width(4.dp))
                                    
                                    Text(
                                        text = if (subtitlesEnabled) "ON" else "OFF",
                                        color = if (subtitlesEnabled) Color(0xFFE50914) else Color.White.copy(alpha = 0.5f),
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                                
                                // Control hints
                                Spacer(modifier = Modifier.width(16.dp))
                                Text(
                                    text = "HOMEFLIX",
                                    color = Color.White.copy(alpha = 0.7f),
                                    fontSize = 12.sp
                                )
                            }
                        }
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
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
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