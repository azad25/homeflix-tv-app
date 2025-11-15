package com.homeflix.tv.presentation.screens.player

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import com.homeflix.tv.presentation.components.VideoPlayer


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
            // Show error message and navigate back
            LaunchedEffect(state.message) {
                onNavigateBack()
            }
        }
        
        is VideoPlayerUiState.Success -> {
            // Use saved progress from ViewModel or provided startTime
            val actualStartTime = if (state.savedProgressSeconds != null) {
                state.savedProgressSeconds * 1000 // Convert seconds to milliseconds
            } else {
                startTime
            }
            
            VideoPlayer(
                media = state.media,
                isVisible = true,
                onClose = onNavigateBack,
                startTime = actualStartTime,
                forceStartFromBeginning = forceStartFromBeginning,
                onProgress = { currentTime, duration ->
                    viewModel.updateProgress(currentTime, duration)
                },
                onPlayNext = { nextEpisode ->
                    // Navigate to next episode
                    onNavigateToEpisode?.invoke(nextEpisode.id)
                },
                mediaRepository = null, // VideoPlayer will get it from Hilt EntryPoint
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}