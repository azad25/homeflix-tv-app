package com.homeflix.tv.presentation.screens.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import com.homeflix.tv.presentation.components.VideoPlayer
import kotlinx.coroutines.delay


@UnstableApi
@Composable
fun VideoPlayerScreen(
    mediaId: Int,
    startTime: Long = 0L,
    forceStartFromBeginning: Boolean = false,
    onNavigateBack: () -> Unit,
    onNavigateToEpisode: ((Int) -> Unit)? = null,
    viewModel: VideoPlayerViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    
    LaunchedEffect(mediaId, startTime) {
        val startTimeSeconds = startTime / 1000 // Convert ms to seconds for ViewModel
        viewModel.loadMedia(mediaId, startTimeSeconds)
    }
    
    when (val state = uiState) {
        is VideoPlayerUiState.Loading -> {
            // Show loading indicator
        }
        
        is VideoPlayerUiState.Error -> {
            // Show the error briefly, then navigate back — an instant close
            // looks like the player "randomly quit".
            Box(
                modifier = Modifier.fillMaxSize().background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "Couldn't play this title", color = Color.White)
            }
            LaunchedEffect(state.message) {
                delay(1500)
                onNavigateBack()
            }
        }
        
        is VideoPlayerUiState.Success -> {
            // CRITICAL FIX: Use the provided startTime parameter directly
            // Don't override with savedProgressSeconds from ViewModel
            // The startTime from navigation already contains the correct resume position
            val actualStartTime = if (startTime > 0) {
                startTime // Use provided startTime (already in milliseconds)
            } else if (state.savedProgressSeconds != null) {
                state.savedProgressSeconds * 1000 // Fallback to saved progress
            } else {
                0L // Start from beginning
            }
            
            // Deterministic next episode from the season list (guarded so an
            // unresolved index never points at episode 0 by accident).
            val nextEpisodeId = if (state.currentIndex >= 0)
                state.seasonEpisodes.getOrNull(state.currentIndex + 1)?.id
            else null

            // Real episode title from the season list (media.title is often
            // just the file name for episodes).
            val currentEpisode = if (state.currentIndex >= 0)
                state.seasonEpisodes.getOrNull(state.currentIndex)
            else null
            val episodeTitle = currentEpisode?.episodeTitle?.takeIf { it.isNotBlank() }
                ?: currentEpisode?.title?.takeIf { it.isNotBlank() }

            VideoPlayer(
                media = state.media,
                seriesTitle = state.seriesTitle,
                episodeTitle = episodeTitle,
                nextEpisodeId = nextEpisodeId,
                isVisible = true,
                onClose = onNavigateBack,
                startTime = actualStartTime,
                forceStartFromBeginning = forceStartFromBeginning,
                onProgress = { currentTime, duration ->
                    viewModel.updateProgress(currentTime, duration)
                },
                onPlayNext = { nextId ->
                    onNavigateToEpisode?.invoke(nextId)
                },
                mediaRepository = null, // VideoPlayer will get it from Hilt EntryPoint
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}