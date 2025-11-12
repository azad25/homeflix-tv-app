package com.homeflix.tv.presentation.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
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
    
    // Auto-focus play button when hero changes
    LaunchedEffect(currentIndex) {
        playButtonFocusRequester?.requestFocus()
    }
    
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(480.dp) // Reduced height for better scaling
    ) {
        // Background banner image with caching (Netflix/Prime style)
        AsyncImage(
            model = coil.request.ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                .data(ApiUtils.getBannerUrl(currentMedia))
                .memoryCacheKey("banner_${currentMedia.id}")
                .diskCacheKey("banner_${currentMedia.id}")
                .crossfade(true)
                .build(),
            contentDescription = currentMedia.title,
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
        
        // Content
        AnimatedContent(
            targetState = currentMedia,
            transitionSpec = {
                fadeIn(animationSpec = tween(800)) togetherWith 
                fadeOut(animationSpec = tween(400))
            },
            label = "hero_content"
        ) { media ->
            Column(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 48.dp, end = 300.dp, bottom = 60.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Netflix-style title
                Text(
                    text = media.title,
                    style = MaterialTheme.typography.displayLarge.copy(
                        fontWeight = FontWeight.Black,
                        color = TextPrimary
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(0.8f)
                )
                
                // Metadata row
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Netflix match percentage (simulated)
                    Text(
                        text = "${(85..99).random()}% Match",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF46d369) // Netflix green
                        )
                    )
                    
                    // Year
                    media.year?.let { year ->
                        Text(
                            text = year.toString(),
                            style = MaterialTheme.typography.titleMedium.copy(
                                color = TextSecondary
                            )
                        )
                    }
                    
                    // Age rating
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = Color.Gray.copy(alpha = 0.8f)
                    ) {
                        Text(
                            text = media.certification ?: "TV-MA",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold
                            ),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                    
                    // Quality badge
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = NetflixRed.copy(alpha = 0.8f)
                    ) {
                        Text(
                            text = if (media.quality?.contains("4K", ignoreCase = true) == true) "4K" else "HD",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            ),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                    
                    // Duration
                    media.runtime?.let { runtime ->
                        Text(
                            text = "${runtime}m",
                            style = MaterialTheme.typography.titleMedium.copy(
                                color = TextSecondary
                            )
                        )
                    }
                }
                
                // Genres
                if (media.genreNames.isNotEmpty()) {
                    Text(
                        text = media.genreNames.take(3).joinToString(" • "),
                        style = MaterialTheme.typography.titleMedium.copy(
                            color = TextSecondary
                        )
                    )
                }
                
                // Description
                media.description?.let { description ->
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            color = TextPrimary.copy(alpha = 0.9f),
                            lineHeight = MaterialTheme.typography.bodyLarge.lineHeight * 1.2
                        ),
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth(0.7f)
                    )
                }
                
                // Netflix-style action buttons
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    // Play Button - RESTORED NAVIGATION
                    Button(
                        onClick = { onPlayClick(media) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White,
                            contentColor = Color.Black
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
                            .onFocusChanged { playButtonFocused = it.isFocused }
                            .onKeyEvent { keyEvent ->
                                if (keyEvent.type == KeyEventType.KeyDown) {
                                    when (keyEvent.key) {
                                        Key.DirectionRight -> {
                                            try {
                                                infoButtonFocusRequester.requestFocus()
                                            } catch (e: Exception) {
                                                // Ignore focus errors
                                            }
                                            true
                                        }
                                        Key.DirectionLeft -> {
                                            val newIndex = if (currentIndex > 0) currentIndex - 1 else mediaList.size - 1
                                            onIndexChange(newIndex)
                                            true
                                        }
                                        Key.DirectionDown -> {
                                            onNavigateDown?.invoke()
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
                                text = "Play",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }
                    }
                    
                    // More Info Button - RESTORED NAVIGATION
                    OutlinedButton(
                        onClick = { onDetailsClick(media) },
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = TextPrimary,
                            containerColor = Color.Black.copy(alpha = 0.5f)
                        ),
                        border = ButtonDefaults.outlinedButtonBorder.copy(
                            width = 1.dp,
                            brush = Brush.linearGradient(
                                colors = if (infoButtonFocused) {
                                    listOf(Color.White, Color.White.copy(alpha = 0.8f))
                                } else {
                                    listOf(Color.Gray, Color.Gray.copy(alpha = 0.6f))
                                }
                            )
                        ),
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier
                            .height(44.dp)
                            .focusRequester(infoButtonFocusRequester)
                            .onFocusChanged { infoButtonFocused = it.isFocused }
                            .onKeyEvent { keyEvent ->
                                if (keyEvent.type == KeyEventType.KeyDown) {
                                    when (keyEvent.key) {
                                        Key.DirectionLeft -> {
                                            try {
                                                playButtonFocusRequester?.requestFocus()
                                            } catch (e: Exception) {
                                                // Ignore focus errors
                                            }
                                            true
                                        }
                                        Key.DirectionRight -> {
                                            val newIndex = (currentIndex + 1) % mediaList.size
                                            onIndexChange(newIndex)
                                            true
                                        }
                                        Key.DirectionDown -> {
                                            onNavigateDown?.invoke()
                                            true
                                        }
                                        else -> false
                                    }
                                } else false
                            }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "ⓘ",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "More Info",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.SemiBold
                                )
                            )
                        }
                    }
                }
                

            }
        }
        
        // Netflix-style circular slide indicators
        if (mediaList.size > 1) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(48.dp),
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