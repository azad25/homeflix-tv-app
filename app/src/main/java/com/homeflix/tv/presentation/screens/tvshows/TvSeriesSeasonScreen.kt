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
                        // Hero Section with Series Banner
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(300.dp)
                        ) {
                            // Background Banner Image
                            AsyncImage(
                                model = ApiUtils.getSeriesBannerUrl(series),
                                contentDescription = series.title,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                            
                            // Gradient Overlay
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
                            
                            // Content
                            Column(
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(32.dp)
                                    .fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
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
                                
                                // Series Title
                                Text(
                                    text = series.title,
                                    style = MaterialTheme.typography.displaySmall.copy(
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
                                
                                // Episode Count
                                Text(
                                    text = "${episodes.size} Episodes",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        color = TextSecondary
                                    )
                                )
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
            
            // Episode Thumbnail
            Box(
                modifier = Modifier
                    .width(160.dp)
                    .aspectRatio(16f / 9f)
                    .background(
                        Color.Gray.copy(alpha = 0.3f),
                        RoundedCornerShape(6.dp)
                    )
            ) {
                AsyncImage(
                    model = ApiUtils.getEpisodeThumbnailUrl(episode),
                    contentDescription = episode.title,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(6.dp)),
                    contentScale = ContentScale.Crop
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
                    
                    episode.airDate?.let { airDate ->
                        Text(
                            text = airDate,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = TextSecondary
                            )
                        )
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