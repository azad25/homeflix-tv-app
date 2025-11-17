package com.homeflix.tv.presentation.screens.tvshows

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.homeflix.tv.presentation.components.NetflixSideNavigation
import com.homeflix.tv.presentation.navigation.Screen
import com.homeflix.tv.presentation.theme.NetflixRed
import com.homeflix.tv.presentation.theme.TextPrimary
import com.homeflix.tv.presentation.theme.TextSecondary
import com.homeflix.tv.util.ApiUtils



private fun getEpisodeBackdropUrl(episode: Episode, series: com.homeflix.tv.presentation.screens.tvshows.TvSeries): String {
    val apiUrl = ApiUtils.getBaseUrl()
    
    // First try episode thumbnail if available
    episode.thumbnailPath?.let { thumbnailPath ->
        if (thumbnailPath.startsWith("http")) {
            return thumbnailPath
        }
    }
    
    // Try episode thumbnail endpoint first
    val episodeThumbnailUrl = "$apiUrl/thumbnails/${episode.id}"
    
    // If episode thumbnail fails, fallback to series backdrop
    // Note: In practice, we should check if episode thumbnail exists
    // For now, we'll try episode first, then series as fallback in onError
    return episodeThumbnailUrl
}

@Composable
fun TvSeriesSeasonScreen(
    seriesId: String,
    seasonNumber: Int,
    navController: NavController,
    viewModel: TvSeriesSeasonViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    
    LaunchedEffect(seriesId, seasonNumber) {
        viewModel.loadSeasonDetails(seriesId, seasonNumber)
    }
    
    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Side Navigation
        NetflixSideNavigation(
            selectedRoute = "tv-series",
            onNavigate = { route ->
                navController.navigate(route) {
                    popUpTo(Screen.Home.route) { inclusive = false }
                    launchSingleTop = true
                }
            }
        )
        
        // Main Content
        val currentState = uiState
        when (currentState) {
            is TvSeriesSeasonUiState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = NetflixRed)
                }
            }
            
            is TvSeriesSeasonUiState.Error -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Error loading season",
                            style = MaterialTheme.typography.headlineMedium,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = currentState.message,
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { navController.popBackStack() }
                        ) {
                            Text("Go Back")
                        }
                    }
                }
            }
            
            is TvSeriesSeasonUiState.Success -> {
                val series = currentState.series
                val season = currentState.season
                val episodes = currentState.episodes
                val scrollState = rememberLazyListState()
                
                LazyColumn(
                    state = scrollState,
                    userScrollEnabled = true,
                    modifier = Modifier.fillMaxSize()
                ) {
                    item {
                        // Hero Section with Backdrop and Poster (matching web app)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(480.dp)
                        ) {
                            // Background Backdrop Image
                            AsyncImage(
                                model = ApiUtils.getSeriesBackdropUrl(series),
                                contentDescription = series.title,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                            
                            // Gradient Overlays (matching web app)
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
                            
                            // Hero Content with Poster (TMDB Style)
                            Row(
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(48.dp)
                                    .fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(24.dp),
                                verticalAlignment = Alignment.Bottom
                            ) {
                                // Series Poster
                                Box(
                                    modifier = Modifier
                                        .width(200.dp)
                                        .aspectRatio(2f / 3f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color.Gray.copy(alpha = 0.3f))
                                ) {
                                    AsyncImage(
                                        model = ApiUtils.getSeriesPosterUrl(series),
                                        contentDescription = series.title,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                }
                                
                                // Series Details
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    // Back Button
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .clickable {
                                                navController.navigate(Screen.TvSeriesDetails.createRoute(seriesId))
                                            }
                                            .padding(bottom = 8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ArrowBack,
                                            contentDescription = "Back",
                                            tint = Color.White,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "Back to ${series.title}",
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                color = Color.White.copy(alpha = 0.8f)
                                            )
                                        )
                                    }
                                    
                                    // TV Series Badge
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Star, // Use TV icon if available
                                            contentDescription = null,
                                            tint = NetflixRed,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Text(
                                            text = "TV SERIES",
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                color = NetflixRed,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        )
                                    }
                                    
                                    // Series Title
                                    Text(
                                        text = series.title,
                                        style = MaterialTheme.typography.displayMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = TextPrimary
                                        )
                                    )
                                    
                                    // Season Title
                                    Text(
                                        text = season.name,
                                        style = MaterialTheme.typography.headlineMedium.copy(
                                            fontWeight = FontWeight.SemiBold,
                                            color = NetflixRed
                                        )
                                    )
                                    
                                    // Episode Count and Series Info
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "${episodes.size} Episodes",
                                            style = MaterialTheme.typography.titleMedium.copy(
                                                color = TextSecondary
                                            )
                                        )
                                        
                                        if (series.rating > 0) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Star,
                                                    contentDescription = null,
                                                    tint = Color(0xFFFFD700),
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Text(
                                                    text = String.format("%.1f", series.rating),
                                                    style = MaterialTheme.typography.titleMedium.copy(
                                                        color = TextSecondary
                                                    )
                                                )
                                            }
                                        }
                                        
                                        series.year?.let { year ->
                                            Text(
                                                text = year.toString(),
                                                style = MaterialTheme.typography.titleMedium.copy(
                                                    color = TextSecondary
                                                )
                                            )
                                        }
                                    }
                                    
                                    // Season Description
                                    season.description?.let { description ->
                                        Text(
                                            text = description,
                                            style = MaterialTheme.typography.bodyLarge.copy(
                                                color = TextPrimary.copy(alpha = 0.9f),
                                                lineHeight = MaterialTheme.typography.bodyLarge.lineHeight * 1.2
                                            ),
                                            maxLines = 3,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.fillMaxWidth(0.8f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                    
                    // Episodes List
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp)
                        ) {
                            Text(
                                text = "Episodes",
                                style = MaterialTheme.typography.headlineSmall.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    color = TextPrimary
                                ),
                                modifier = Modifier.padding(bottom = 24.dp)
                            )
                        }
                    }
                    
                    itemsIndexed(episodes) { index, episode ->
                        EpisodeCard(
                            episode = episode,
                            episodeNumber = index + 1,
                            series = series,
                            onClick = {
                                // Play episode with autoplay for next episodes
                                navController.navigate(
                                    Screen.VideoPlayer.createRoute(episode.id)
                                )
                            },
                            modifier = Modifier.padding(horizontal = 32.dp, vertical = 8.dp)
                        )
                    }
                    
                    // Bottom padding
                    item {
                        Spacer(modifier = Modifier.height(48.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun EpisodeCard(
    episode: Episode,
    episodeNumber: Int,
    series: com.homeflix.tv.presentation.screens.tvshows.TvSeries,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }
    
    // Scale animation on focus
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.02f else 1.0f,
        animationSpec = tween(durationMillis = 200),
        label = "episode_card_scale"
    )
    
    Card(
        modifier = modifier
            .fillMaxWidth()
            .scale(scale)
            .focusable()
            .onFocusChanged { focusState ->
                isFocused = focusState.isFocused
            }
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = if (isFocused) Color.White.copy(alpha = 0.1f) else Color.Transparent
        ),
        border = if (isFocused) {
            androidx.compose.foundation.BorderStroke(2.dp, NetflixRed)
        } else null,
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Episode Number
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(
                        NetflixRed.copy(alpha = 0.2f),
                        RoundedCornerShape(24.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = episodeNumber.toString(),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = NetflixRed
                    )
                )
            }
            
            // Episode Backdrop Thumbnail (16:9 like web app)
            Box(
                modifier = Modifier
                    .width(200.dp)
                    .aspectRatio(16f / 9f)
                    .background(
                        Color.Gray.copy(alpha = 0.3f),
                        RoundedCornerShape(8.dp)
                    )
            ) {
                var episodeImageUrl by remember { mutableStateOf(getEpisodeBackdropUrl(episode, series)) }
                var fallbackLevel by remember { mutableStateOf(0) }
                
                AsyncImage(
                    model = episodeImageUrl,
                    contentDescription = episode.title,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop,
                    onError = {
                        when (fallbackLevel) {
                            0 -> {
                                // Fallback to series backdrop (episodes don't have their own)
                                episodeImageUrl = ApiUtils.getSeriesBackdropUrl(series)
                                fallbackLevel = 1
                            }
                            1 -> {
                                // Final fallback to series thumbnail
                                episodeImageUrl = "${ApiUtils.getBaseUrl()}/thumbnails/${series.id}"
                                fallbackLevel = 2
                            }
                        }
                    }
                )
                
                // Play overlay on focus
                if (isFocused) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Color.Black.copy(alpha = 0.6f),
                                RoundedCornerShape(6.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .background(
                                    Color.White,
                                    RoundedCornerShape(16.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "▶",
                                color = Color.Black,
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                    }
                }
                
                // Duration badge
                episode.duration?.let { duration ->
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(4.dp)
                            .background(
                                Color.Black.copy(alpha = 0.8f),
                                RoundedCornerShape(4.dp)
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "${duration}m",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = Color.White
                            )
                        )
                    }
                }
            }
            
            // Episode Info
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = episode.title,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (episode.rating > 0) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = null,
                                tint = Color(0xFFFFD700),
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = String.format("%.1f", episode.rating),
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = TextSecondary
                                )
                            )
                        }
                    }
                    

                }
                
                episode.description?.let { description ->
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = TextSecondary
                        ),
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            
            // Play Button
            if (isFocused) {
                Button(
                    onClick = onClick,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NetflixRed
                    ),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text("▶ Play")
                }
            }
        }
    }
}