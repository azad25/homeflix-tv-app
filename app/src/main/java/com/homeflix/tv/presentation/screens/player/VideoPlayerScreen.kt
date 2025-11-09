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
    viewModel: VideoPlayerViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    
    LaunchedEffect(mediaId) {
        viewModel.loadMedia(mediaId)
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
            VideoPlayer(
                media = state.media,
                isVisible = true,
                onClose = onNavigateBack,
                startTime = startTime,
                forceStartFromBeginning = forceStartFromBeginning,
                onProgress = { currentTime, duration ->
                    viewModel.updateProgress(currentTime, duration)
                },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}