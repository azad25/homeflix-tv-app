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
    onRefreshClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    if (mediaList.isEmpty()) return
    
    val context = LocalContext.current
    val currentMedia = mediaList[currentIndex]
    var playButtonFocused by remember { mutableStateOf(false) }
    var infoButtonFocused by remember { mutableStateOf(false) }
    val playButtonFocusRequester = remember { FocusRequester() }
    val infoButtonFocusRequester = remember { FocusRequester() }
    
    // Preview video state
    var showPreview by remember { mutableStateOf(false) }
    var exoPlayer by remember { mutableStateOf<ExoPlayer?>(null) }
    
    // Auto-focus play button when hero changes
    LaunchedEffect(currentIndex) {
        playButtonFocusRequester.requestFocus()
        showPreview = false
        
        // Start preview after 3 seconds
        delay(3000)
        showPreview = true
    }
    
    // Initialize preview video player
    LaunchedEffect(currentMedia.id, showPreview) {
        if (showPreview) {
            exoPlayer?.release()
            
            val player = ExoPlayer.Builder(context).build().apply {
                val previewUrl = ApiUtils.getPreviewClipUrl(currentMedia.id)
                val mediaItem = MediaItem.fromUri(previewUrl)
                setMediaItem(mediaItem)
                prepare()
                playWhenReady = true
                repeatMode = Player.REPEAT_MODE_ONE
                volume = 0f // Muted preview
            }
            
            exoPlayer = player
        }
    }
    
    // Cleanup player
    DisposableEffect(Unit) {
        onDispose {
            exoPlayer?.release()
        }
    }
    
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(720.dp) // Full screen height for TV
    ) {
        // Background image (always show as fallback)
        AsyncImage(
            model = ApiUtils.getBannerUrl(currentMedia),
            contentDescription = currentMedia.title,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
        
        // Preview video overlay (Netflix-style)
        if (showPreview && exoPlayer != null) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        player = exoPlayer
                        useController = false
                        setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }
        
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
                    .padding(start = 80.dp, end = 400.dp, bottom = 100.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(24.dp)
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
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    // Play Button
                    Button(
                        onClick = { onPlayClick(media) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White,
                            contentColor = Color.Black
                        ),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier
                            .height(56.dp)
                            .focusRequester(playButtonFocusRequester)
                            .onFocusChanged { playButtonFocused = it.isFocused }
                            .onKeyEvent { keyEvent ->
                                when {
                                    keyEvent.key == Key.DirectionRight && keyEvent.type == KeyEventType.KeyDown -> {
                                        infoButtonFocusRequester.requestFocus()
                                        true
                                    }
                                    keyEvent.key == Key.DirectionUp && keyEvent.type == KeyEventType.KeyDown -> {
                                        val newIndex = if (currentIndex > 0) currentIndex - 1 else mediaList.size - 1
                                        onIndexChange(newIndex)
                                        true
                                    }
                                    keyEvent.key == Key.DirectionDown && keyEvent.type == KeyEventType.KeyDown -> {
                                        val newIndex = (currentIndex + 1) % mediaList.size
                                        onIndexChange(newIndex)
                                        true
                                    }
                                    else -> false
                                }
                            },
                        elevation = ButtonDefaults.buttonElevation(
                            defaultElevation = if (playButtonFocused) 8.dp else 4.dp
                        )
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "▶",
                                style = MaterialTheme.typography.titleLarge
                            )
                            Text(
                                text = "Play",
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }
                    }
                    
                    // More Info Button
                    OutlinedButton(
                        onClick = { onDetailsClick(media) },
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = TextPrimary,
                            containerColor = Color.Black.copy(alpha = 0.5f)
                        ),
                        border = ButtonDefaults.outlinedButtonBorder.copy(
                            width = 2.dp,
                            brush = Brush.linearGradient(
                                colors = if (infoButtonFocused) {
                                    listOf(Color.White, Color.White.copy(alpha = 0.8f))
                                } else {
                                    listOf(Color.Gray, Color.Gray.copy(alpha = 0.6f))
                                }
                            )
                        ),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier
                            .height(56.dp)
                            .focusRequester(infoButtonFocusRequester)
                            .onFocusChanged { infoButtonFocused = it.isFocused }
                            .onKeyEvent { keyEvent ->
                                when {
                                    keyEvent.key == Key.DirectionLeft && keyEvent.type == KeyEventType.KeyDown -> {
                                        playButtonFocusRequester.requestFocus()
                                        true
                                    }
                                    keyEvent.key == Key.DirectionUp && keyEvent.type == KeyEventType.KeyDown -> {
                                        val newIndex = if (currentIndex > 0) currentIndex - 1 else mediaList.size - 1
                                        onIndexChange(newIndex)
                                        true
                                    }
                                    keyEvent.key == Key.DirectionDown && keyEvent.type == KeyEventType.KeyDown -> {
                                        val newIndex = (currentIndex + 1) % mediaList.size
                                        onIndexChange(newIndex)
                                        true
                                    }
                                    else -> false
                                }
                            }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "ⓘ",
                                style = MaterialTheme.typography.titleLarge
                            )
                            Text(
                                text = "More Info",
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.SemiBold
                                )
                            )
                        }
                    }
                }
                
                // Refresh recommendations button (if provided)
                onRefreshClick?.let { refreshClick ->
                    Row(
                        modifier = Modifier.padding(top = 16.dp)
                    ) {
                        OutlinedButton(
                            onClick = refreshClick,
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = NetflixRed,
                                containerColor = Color.Transparent
                            ),
                            border = ButtonDefaults.outlinedButtonBorder.copy(
                                width = 1.dp,
                                brush = Brush.linearGradient(
                                    colors = listOf(NetflixRed, NetflixRed.copy(alpha = 0.8f))
                                )
                            ),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.height(40.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "🔄",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    text = "Refresh Recommendations",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.Medium
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
        
        // Netflix-style slide indicators
        if (mediaList.size > 1) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(80.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                mediaList.forEachIndexed { index, _ ->
                    Box(
                        modifier = Modifier
                            .width(if (index == currentIndex) 24.dp else 8.dp)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(
                                if (index == currentIndex) Color.White else Color.White.copy(alpha = 0.4f)
                            )
                    )
                }
            }
        }
    }
}