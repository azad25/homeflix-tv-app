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
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.homeflix.tv.domain.model.Media
import com.homeflix.tv.presentation.theme.NetflixRed
import com.homeflix.tv.presentation.theme.TextPrimary
import com.homeflix.tv.presentation.theme.TextSecondary
import com.homeflix.tv.util.ApiUtils

@Composable
fun RecommendationSection(
    currentMedia: Media,
    onPlay: (Media) -> Unit,
    onInfo: (Media) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RecommendationViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    
    LaunchedEffect(currentMedia.id) {
        viewModel.loadRecommendations(currentMedia)
    }
    
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(32.dp)
    ) {
        when (val state = uiState) {
            is RecommendationUiState.Loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = NetflixRed,
                        modifier = Modifier.size(48.dp)
                    )
                }
            }
            
            is RecommendationUiState.Success -> {
                // Recommended For You
                if (state.personalizedRecommendations.isNotEmpty()) {
                    NetflixRecommendationRow(
                        title = "Recommended For You",
                        mediaList = state.personalizedRecommendations,
                        onPlay = onPlay,
                        onInfo = onInfo
                    )
                }
                
                // More Like This
                if (state.similarRecommendations.isNotEmpty()) {
                    NetflixRecommendationRow(
                        title = "More Like This",
                        mediaList = state.similarRecommendations,
                        onPlay = onPlay,
                        onInfo = onInfo
                    )
                }
                
                // Genre-based recommendations
                if (state.genreRecommendations.isNotEmpty() && currentMedia.genres.isNotEmpty()) {
                    val genreName = currentMedia.genres.first().name
                    NetflixRecommendationRow(
                        title = "More $genreName Movies",
                        mediaList = state.genreRecommendations,
                        onPlay = onPlay,
                        onInfo = onInfo
                    )
                }
                
                // Trending Now
                if (state.trendingRecommendations.isNotEmpty()) {
                    NetflixRecommendationRow(
                        title = "Trending Now",
                        mediaList = state.trendingRecommendations,
                        onPlay = onPlay,
                        onInfo = onInfo
                    )
                }
                
                // Top Rated
                if (state.topRatedRecommendations.isNotEmpty()) {
                    NetflixRecommendationRow(
                        title = "Top Rated",
                        mediaList = state.topRatedRecommendations,
                        onPlay = onPlay,
                        onInfo = onInfo
                    )
                }
                
                // You Might Also Like (fallback)
                if (state.mixedRecommendations.isNotEmpty()) {
                    NetflixRecommendationRow(
                        title = "You Might Also Like",
                        mediaList = state.mixedRecommendations,
                        onPlay = onPlay,
                        onInfo = onInfo
                    )
                }
            }
            
            is RecommendationUiState.Error -> {
                // Show error state or fallback content
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Unable to load recommendations",
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

@Composable
private fun NetflixRecommendationRow(
    title: String,
    mediaList: List<Media>,
    onPlay: (Media) -> Unit,
    onInfo: (Media) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth()
    ) {
        // Section Title
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            ),
            modifier = Modifier.padding(horizontal = 48.dp).padding(bottom = 16.dp)
        )
        
        // Horizontal scrolling row
        LazyRow(
            contentPadding = PaddingValues(horizontal = 48.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(mediaList) { media ->
                NetflixRecommendationCard(
                    media = media,
                    onPlay = { onPlay(media) },
                    onInfo = { onInfo(media) }
                )
            }
        }
    }
}

@Composable
private fun NetflixRecommendationCard(
    media: Media,
    onPlay: () -> Unit,
    onInfo: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }
    
    // Scale animation on focus (reduced scale)
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.06f else 1.0f,
        animationSpec = tween(durationMillis = 200),
        label = "recommendation_card_scale"
    )
    
    // Netflix-style card with scale animation and proper z-index
    Box(
        modifier = modifier
            .width(220.dp)
            .aspectRatio(16f / 9f)
            .graphicsLayer(scaleX = scale, scaleY = scale)
            .onFocusChanged { focusState ->
                isFocused = focusState.isFocused
            }
            .focusable()
            .onKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown &&
                    (keyEvent.key == Key.Enter || keyEvent.key == Key.DirectionCenter ||
                     keyEvent.nativeKeyEvent.keyCode == android.view.KeyEvent.KEYCODE_DPAD_CENTER)) {
                    onInfo()
                    true
                } else false
            }
            .clickable { onInfo() }
            .then(
                if (isFocused) {
                    Modifier
                        .border(2.dp, Color.White, RoundedCornerShape(8.dp))
                        .zIndex(10f)
                } else {
                    Modifier.zIndex(1f)
                }
            )
    ) {
        Card(
            modifier = Modifier.fillMaxSize(),
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.cardColors(
            containerColor = Color.Transparent
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isFocused) 8.dp else 2.dp
        )
    ) {
        Box {
            // Background Image with fallback chain (banner → poster → thumbnail)
            var currentImageUrl by remember { mutableStateOf(ApiUtils.getBannerUrl(media)) }
            var fallbackLevel by remember { mutableStateOf(0) }
            
            AsyncImage(
                model = currentImageUrl,
                contentDescription = media.title,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop,
                onError = {
                    when (fallbackLevel) {
                        0 -> {
                            // Fallback to poster
                            currentImageUrl = ApiUtils.getPosterUrl(media)
                            fallbackLevel = 1
                        }
                        1 -> {
                            // Fallback to thumbnail
                            currentImageUrl = ApiUtils.getThumbnailUrl(media)
                            fallbackLevel = 2
                        }
                    }
                }
            )
            
            // Gradient overlay
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.8f)
                            )
                        )
                    )
            )
            
            // Netflix-style overlay on focus
            if (isFocused) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Color.Black.copy(alpha = 0.3f),
                            RoundedCornerShape(8.dp)
                        )
                ) {
                    // Play button
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(56.dp)
                            .background(
                                Color.White,
                                RoundedCornerShape(28.dp)
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
            }
            
            // Content overlay at bottom
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(12.dp)
            ) {
                // Title
                Text(
                    text = media.title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                
                // Metadata row
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    // Year
                    media.year?.let { year ->
                        Text(
                            text = year.toString(),
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = TextSecondary
                            )
                        )
                    }
                    
                    // Rating
                    if (media.rating > 0) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = null,
                                tint = Color(0xFFFFD700), // Gold
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = String.format("%.1f", media.rating),
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = TextSecondary
                                )
                            )
                        }
                    }
                    
                    // Quality badge
                    media.quality?.let { quality ->
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = NetflixRed.copy(alpha = 0.8f)
                        ) {
                            Text(
                                text = when {
                                    quality.contains("4K", ignoreCase = true) -> "4K"
                                    quality.contains("1080", ignoreCase = true) -> "HD"
                                    quality.contains("720", ignoreCase = true) -> "720p"
                                    else -> "HD"
                                },
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                ),
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                
                // Genres (if focused)
                if (isFocused && media.genres.isNotEmpty()) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        media.genres.take(2).forEach { genre ->
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = Color.Gray.copy(alpha = 0.6f)
                            ) {
                                Text(
                                    text = genre.name,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = Color.White
                                    ),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
}