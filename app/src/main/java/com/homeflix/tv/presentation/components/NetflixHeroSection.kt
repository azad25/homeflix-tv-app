package com.homeflix.tv.presentation.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.homeflix.tv.domain.model.Media
import com.homeflix.tv.presentation.theme.NetflixRed
import com.homeflix.tv.presentation.theme.TextPrimary
import com.homeflix.tv.presentation.theme.TextSecondary
import com.homeflix.tv.util.ApiUtils
import kotlinx.coroutines.delay

@UnstableApi
@Composable
fun NetflixHeroSection(
    mediaList: List<Media>,
    currentIndex: Int,
    onPlayClick: (Media) -> Unit,
    onDetailsClick: (Media) -> Unit,
    onIndexChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    playButtonFocusRequester: FocusRequester? = null,
    onNavigateDown: (() -> Unit)? = null
) {
    if (mediaList.isEmpty()) return
    
    val currentMedia = mediaList[currentIndex]
    var playButtonFocused by remember { mutableStateOf(false) }
    var infoButtonFocused by remember { mutableStateOf(false) }
    val infoButtonFocusRequester = remember { FocusRequester() }
    
    LaunchedEffect(currentIndex, mediaList.size) {
        if (mediaList.size > 1) {
            delay(10000) // 10 seconds per slide
            val nextIndex = (currentIndex + 1) % mediaList.size
            onIndexChange(nextIndex)
        }
    }
    
    // REMOVED: Auto-focus to prevent scroll issues
    // Focus is managed by parent NetflixHomeScreen

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(480.dp)
    ) {
        // Netflix-style unified slide transition
        // Single Crossfade wraps backdrop + gradients + content for smooth transition
        androidx.compose.animation.Crossfade(
            targetState = currentIndex,
            animationSpec = tween(durationMillis = 1500),
            label = "hero_crossfade"
        ) { targetIndex ->
            val targetMedia = mediaList.getOrElse(targetIndex) { currentMedia }
            
            Box(modifier = Modifier.fillMaxSize()) {
                // Background banner
                AsyncImage(
                    model = coil.request.ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                        .data(ApiUtils.getBannerUrl(targetMedia))
                        .memoryCacheKey("banner_${targetMedia.id}")
                        .diskCacheKey("banner_${targetMedia.id}")
                        .crossfade(false) // Crossfade handled by parent
                        .build(),
                    contentDescription = targetMedia.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                
                // Netflix-style gradient overlays
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    Color.Black.copy(alpha = 0.8f),
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.4f)
                                ),
                                startX = 0f,
                                endX = 1200f
                            )
                        )
                )
                
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.6f)
                                ),
                                startY = 400f
                            )
                        )
                )
                
                // Staggered content reveal with alpha animations per slide
                val contentVisible = remember { mutableStateOf(false) }
                LaunchedEffect(targetMedia.id) {
                    contentVisible.value = false
                    delay(300) // Brief delay after crossfade starts
                    contentVisible.value = true
                }
                
                val contentAlpha by androidx.compose.animation.core.animateFloatAsState(
                    targetValue = if (contentVisible.value) 1f else 0f,
                    animationSpec = tween(durationMillis = 800),
                    label = "content_alpha"
                )
                
                Column(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 48.dp, top = 80.dp, end = 300.dp, bottom = 60.dp)
                        .fillMaxWidth()
                        .graphicsLayer { alpha = contentAlpha },
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Movie logo or title
                    var logoLoaded by remember { mutableStateOf(false) }
                    val logoUrl = ApiUtils.getLogoUrl(targetMedia)
                    
                    if (!logoLoaded) {
                        Text(
                            text = targetMedia.title,
                            style = MaterialTheme.typography.headlineLarge.copy(
                                fontWeight = FontWeight.Black,
                                color = TextPrimary
                            ),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth(0.8f)
                        )
                    }
                    
                    if (logoUrl != null) {
                        AsyncImage(
                            model = coil.request.ImageRequest.Builder(LocalContext.current)
                                .data(logoUrl)
                                .memoryCacheKey("logo_${targetMedia.id}")
                                .diskCacheKey("logo_${targetMedia.id}")
                                .crossfade(true)
                                .build(),
                            contentDescription = "${targetMedia.title} logo",
                            modifier = Modifier
                                .heightIn(max = 100.dp)
                                .fillMaxWidth(0.5f),
                            contentScale = ContentScale.Fit,
                            onSuccess = { logoLoaded = true },
                            onError = { logoLoaded = false }
                        )
                    }
                    
                    // Metadata row
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${(85..99).random()}% Match",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF46d369)
                            )
                        )
                        
                        targetMedia.year?.let { year ->
                            Text(
                                text = year.toString(),
                                style = MaterialTheme.typography.titleMedium.copy(
                                    color = TextSecondary
                                )
                            )
                        }
                        
                        if (targetMedia.rating > 0) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "★",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        color = Color(0xFFFFD700)
                                    )
                                )
                                Text(
                                    text = String.format("%.1f", targetMedia.rating),
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        color = TextSecondary,
                                        fontWeight = FontWeight.Medium
                                    )
                                )
                            }
                        }
                        
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color.Gray.copy(alpha = 0.8f)
                        ) {
                            Text(
                                text = targetMedia.certification ?: "TV-MA",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = TextPrimary,
                                    fontWeight = FontWeight.Bold
                                ),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                        
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = NetflixRed.copy(alpha = 0.8f)
                        ) {
                            Text(
                                text = if (targetMedia.quality?.contains("4K", ignoreCase = true) == true) "4K" else "HD",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                ),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                        
                        targetMedia.runtime?.let { runtime ->
                            Text(
                                text = "${runtime}m",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    color = TextSecondary
                                )
                            )
                        }
                    }
                    
                    // Genres and description
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        if (targetMedia.genreNames.isNotEmpty()) {
                            Text(
                                text = targetMedia.genreNames.take(3).joinToString(" • "),
                                style = MaterialTheme.typography.titleMedium.copy(
                                    color = TextSecondary
                                )
                            )
                        }
                        
                        targetMedia.description?.let { description ->
                            Text(
                                text = description,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = TextPrimary.copy(alpha = 0.9f),
                                    lineHeight = MaterialTheme.typography.bodyMedium.lineHeight * 1.2
                                ),
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.fillMaxWidth(0.7f)
                            )
                        }
                    }
                    
                    // Action buttons
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(top = 8.dp)
                    ) {
                        Button(
                            onClick = { onPlayClick(targetMedia) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (playButtonFocused) Color(0xFFE50914) else Color.White,
                                contentColor = if (playButtonFocused) Color.White else Color.Black
                            ),
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier
                                .height(44.dp)
                                .then(
                                    if (playButtonFocusRequester != null) {
                                        Modifier.focusRequester(playButtonFocusRequester)
                                    } else {
                                        Modifier
                                    }
                                )
                                .onFocusChanged { 
                                    playButtonFocused = it.isFocused
                                    // Don't pause auto-slide on initial focus - only pause on manual interaction
                                }
                                .onKeyEvent { keyEvent ->
                                    if (keyEvent.type == KeyEventType.KeyDown) {
                                        when (keyEvent.key) {
                                            Key.DirectionDown -> {
                                                onNavigateDown?.invoke()
                                                true
                                            }
                                            Key.DirectionLeft -> {
                                                if (mediaList.size > 1) {
                                                    val prevIndex = if (currentIndex > 0) currentIndex - 1 else mediaList.size - 1
                                                    onIndexChange(prevIndex)
                                                }
                                                true
                                            }
                                            Key.DirectionRight -> {
                                                if (mediaList.size > 1) {
                                                    val nextIndex = (currentIndex + 1) % mediaList.size
                                                    onIndexChange(nextIndex)
                                                }
                                                true
                                            }
                                            Key.DirectionUp -> {
                                                true
                                            }
                                            else -> false
                                        }
                                    } else false
                                },
                            elevation = ButtonDefaults.buttonElevation(
                                defaultElevation = if (playButtonFocused) 6.dp else 2.dp
                            )
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "▶",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = "Play Now",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
        
        // REMOVED: Navigation arrows to prevent focus issues
        // Users can navigate with LEFT/RIGHT keys on the play/info buttons
        
        // Netflix-style slide indicators with auto-slide status
        if (mediaList.size > 1) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(48.dp),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Slide indicators
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    mediaList.forEachIndexed { index, _ ->
                        Box(
                            modifier = Modifier
                                .size(if (index == currentIndex) 10.dp else 6.dp)
                                .clip(RoundedCornerShape(50)) // Circular shape
                                .background(
                                    if (index == currentIndex) Color.White else Color.White.copy(alpha = 0.4f)
                                )
                        )
                    }
                }
            }
        }
    }
}
