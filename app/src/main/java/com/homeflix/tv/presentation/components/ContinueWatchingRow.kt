package com.homeflix.tv.presentation.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.zIndex
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.homeflix.tv.domain.model.Media
import com.homeflix.tv.presentation.theme.NetflixRed
import com.homeflix.tv.presentation.theme.TextPrimary
import com.homeflix.tv.presentation.theme.TextSecondary
import com.homeflix.tv.util.ApiUtils

data class ContinueWatchingItem(
    val media: Media,
    val progress: Float, // 0.0 to 1.0
    val progressSeconds: Long, // Actual progress in seconds
    val lastWatched: String? = null
)

@Composable
fun ContinueWatchingRow(
    continueWatchingItems: List<ContinueWatchingItem>,
    onPlay: (Media, Long) -> Unit, // Pass media and progressMs (milliseconds)
    onInfo: (Media) -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    onNavigateUp: (() -> Unit)? = null,
    onNavigateDown: (() -> Unit)? = null
) {
    if (continueWatchingItems.isNullOrEmpty()) {
        return
    }
    
    // Additional validation - filter out any invalid items and show ONLY movies
    val validItems = remember(continueWatchingItems) {
        continueWatchingItems.filterNotNull().filter { item ->
            try {
                item.media != null && 
                item.media.id > 0 && 
                !item.media.title.isNullOrBlank() &&
                item.progress >= 0f &&
                item.progress <= 1f &&
                // STRICT FILTER: Only show movies, exclude all TV episodes
                item.media.type == com.homeflix.tv.domain.model.MediaType.MOVIE
            } catch (e: Exception) {
                false
            }
        }
    }
    
    if (validItems.isEmpty()) {
        return
    }
    
    // Render the continue watching section
    Column(
        modifier = modifier.padding(horizontal = 60.dp)
    ) {
        // Section Title
        Text(
            text = "Continue Watching",
            style = MaterialTheme.typography.headlineSmall.copy(
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            ),
            modifier = Modifier.padding(bottom = 16.dp)
        )
        
        // Continue Watching Items
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(end = 60.dp)
        ) {
            items(validItems.size) { index ->
                val item = validItems[index]
                ContinueWatchingCard(
                    item = item,
                    onPlay = { 
                        // Convert seconds to milliseconds
                        onPlay(item.media, item.progressSeconds * 1000)
                    },
                    onInfo = { onInfo(item.media) },
                    onNavigateUp = onNavigateUp,
                    onNavigateDown = onNavigateDown,
                    modifier = if (index == 0 && focusRequester != null) {
                        Modifier.focusRequester(focusRequester)
                    } else {
                        Modifier
                    }
                )
            }
        }
    }
}

@Composable
private fun ContinueWatchingCard(
    item: ContinueWatchingItem,
    onPlay: () -> Unit,
    onInfo: () -> Unit,
    modifier: Modifier = Modifier,
    onNavigateUp: (() -> Unit)? = null,
    onNavigateDown: (() -> Unit)? = null
) {
    var isFocused by remember { mutableStateOf(false) }
    
    // Scale animation on focus (reduced scale)
    val scale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isFocused) 1.08f else 1.0f,
        animationSpec = androidx.compose.animation.core.tween(durationMillis = 200),
        label = "continue_watching_scale"
    )
    
    Box(
        modifier = modifier
            .width(300.dp)
            .height(170.dp)
            .scale(scale)
            .onFocusChanged { focusState ->
                isFocused = focusState.isFocused
            }
            .focusable()
            .onKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown) {
                    when (keyEvent.key) {
                        Key.Enter, Key.DirectionCenter -> {
                            onPlay()
                            true
                        }
                        Key.DirectionUp -> {
                            onNavigateUp?.invoke()
                            onNavigateUp != null
                        }
                        Key.DirectionDown -> {
                            onNavigateDown?.invoke()
                            onNavigateDown != null
                        }
                        else -> {
                            if (keyEvent.nativeKeyEvent.keyCode == android.view.KeyEvent.KEYCODE_DPAD_CENTER) {
                                onPlay()
                                true
                            } else false
                        }
                    }
                } else false
            }
            .clickable { onPlay() }
            .then(
                if (isFocused) {
                    Modifier
                        .border(
                            width = 3.dp,
                            color = Color.White,
                            shape = RoundedCornerShape(8.dp)
                        )
                        .zIndex(10f)
                } else {
                    Modifier.zIndex(1f)
                }
            )
    ) {
        // Card content with focus border
        Card(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (isFocused) {
                        Modifier
                            .background(
                                Color.White.copy(alpha = 0.1f),
                                RoundedCornerShape(8.dp)
                            )
                            .padding(2.dp)
                    } else {
                        Modifier
                    }
                ),
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color.Transparent
            ),
            elevation = CardDefaults.cardElevation(
                defaultElevation = if (isFocused) 8.dp else 2.dp
            ),
            border = if (isFocused) {
                androidx.compose.foundation.BorderStroke(
                    width = 3.dp,
                    color = Color.White
                )
            } else null
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
        // Background Image - use backdrop (TMDB backdrop first, then thumbnail fallback)
        AsyncImage(
            model = ApiUtils.getBackdropUrl(item.media),
            contentDescription = item.media.title,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
        
        // Dark overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.7f)
                        )
                    )
                )
        )
        
        // Progress Bar
        LinearProgressIndicator(
            progress = item.progress,
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .align(Alignment.BottomCenter),
            color = NetflixRed,
            trackColor = Color.White.copy(alpha = 0.3f)
        )
        
        // Play Button (center)
        if (isFocused) {
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .align(Alignment.Center)
                    .background(
                        Color.White.copy(alpha = 0.9f),
                        RoundedCornerShape(30.dp)
                    )
                    .clickable { onPlay() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Play",
                    tint = Color.Black,
                    modifier = Modifier.size(32.dp)
                )
            }
        }
        
        // Title and Progress Info
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp)
                .fillMaxWidth()
        ) {
            Text(
                text = item.media.title,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            
            Spacer(modifier = Modifier.height(4.dp))
            
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${(item.progress * 100).toInt()}% watched",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                )
                
                item.lastWatched?.let { lastWatched ->
                    Text(
                        text = "• $lastWatched",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 11.sp,
                            color = Color.White.copy(alpha = 0.6f)
                        )
                    )
                }
            }
        }
        
        // Info Button (top right)
        if (isFocused) {
            IconButton(
                onClick = onInfo,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .background(
                        Color.Black.copy(alpha = 0.6f),
                        RoundedCornerShape(20.dp)
                    )
            ) {
                Text(
                    text = "i",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
            }
        }
    }
}