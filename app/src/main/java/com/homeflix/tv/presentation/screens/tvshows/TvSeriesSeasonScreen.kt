package com.homeflix.tv.presentation.screens.tvshows

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.homeflix.tv.presentation.components.NetflixSideNavigation
import com.homeflix.tv.presentation.navigation.Screen
import com.homeflix.tv.presentation.theme.PrimeBg
import com.homeflix.tv.presentation.theme.NetflixRed
import com.homeflix.tv.presentation.theme.TextPrimary
import com.homeflix.tv.presentation.theme.TextSecondary
import com.homeflix.tv.util.ApiUtils
import kotlinx.coroutines.delay
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester



// Primary episode still: ALWAYS the auto-downloading endpoint (do not gate on
// episodeStillPath — the backend omits it when empty, which would stop the
// auto-download from ever running). Thumbnail is the Coil error fallback.
private fun getEpisodeStillUrl(episode: Episode): String =
    ApiUtils.getEpisodeStillUrl(episode.id)

private fun getEpisodeThumbUrl(episode: Episode): String {
    episode.thumbnailPath?.let { if (it.startsWith("http")) return it }
    return "${ApiUtils.getBaseUrl()}/thumbnails/${episode.id}"
}

@Composable
fun TvSeriesSeasonScreen(
    seriesId: String,
    seasonNumber: Int,
    navController: NavController,
    viewModel: TvSeriesSeasonViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val contentFocusRequester = remember { FocusRequester() }
    
    // Hoisted above the when(state) so scroll survives state re-emission and
    // returning from the player while this screen stays on the back stack.
    val scrollState = rememberLazyListState()

    LaunchedEffect(seriesId, seasonNumber) {
        viewModel.loadSeasonDetails(seriesId, seasonNumber)
    }

    // Auto-focus content when loaded
    LaunchedEffect(uiState) {
        if (uiState is TvSeriesSeasonUiState.Success) {
            delay(400)
            try {
                contentFocusRequester.requestFocus()
            } catch (_: Exception) {}
        }
    }
    
    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(PrimeBg)
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
                                    // Back Button (TV-friendly)
                                    Button(
                                        onClick = {
                                            navController.navigate(Screen.TvSeriesDetails.createRoute(seriesId))
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color.White.copy(alpha = 0.15f),
                                            contentColor = Color.White
                                        ),
                                        shape = RoundedCornerShape(6.dp),
                                        modifier = Modifier
                                            .height(36.dp)
                                            .padding(bottom = 4.dp)
                                            .focusRequester(contentFocusRequester)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ArrowBack,
                                            contentDescription = "Back",
                                            tint = Color.White,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Back to ${series.title}",
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                color = Color.White.copy(alpha = 0.9f)
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
                                    
                                    // Series Logo + text fallback
                                    var logoLoaded by remember { mutableStateOf(false) }
                                    
                                    if (!logoLoaded) {
                                        Text(
                                            text = series.title,
                                            style = MaterialTheme.typography.displayMedium.copy(
                                                fontWeight = FontWeight.Bold,
                                                color = TextPrimary
                                            )
                                        )
                                    }
                                    
                                    // Try series logo from API
                                    val seriesLogoUrl = ApiUtils.getSeriesLogoUrl(series.id)
                                    
                                    coil.compose.AsyncImage(
                                        model = coil.request.ImageRequest.Builder(LocalContext.current)
                                            .data(seriesLogoUrl)
                                            .crossfade(true)
                                            .build(),
                                        contentDescription = "${series.title} logo",
                                        modifier = Modifier
                                            .heightIn(max = 80.dp)
                                            .fillMaxWidth(0.5f),
                                        contentScale = ContentScale.Fit,
                                        onSuccess = { logoLoaded = true },
                                        onError = { logoLoaded = false }
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
                    
                    // Season Navigation Row
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 32.dp, vertical = 16.dp)
                        ) {
                            Text(
                                text = "Seasons",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    color = TextSecondary
                                ),
                                modifier = Modifier.padding(bottom = 12.dp)
                            )
                            
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(series.totalSeasons) { index ->
                                    val sNum = index + 1
                                    var btnFocused by remember { mutableStateOf(false) }
                                    val isCurrentSeason = sNum == seasonNumber
                                    
                                    Button(
                                        onClick = {
                                            if (!isCurrentSeason) {
                                                navController.navigate(
                                                    Screen.TvSeriesSeason.createRoute(seriesId, sNum)
                                                )
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (isCurrentSeason) NetflixRed
                                                else if (btnFocused) Color.White.copy(alpha = 0.2f)
                                                else Color.White.copy(alpha = 0.1f),
                                            contentColor = Color.White
                                        ),
                                        shape = RoundedCornerShape(6.dp),
                                        modifier = Modifier
                                            .onFocusChanged { btnFocused = it.isFocused }
                                    ) {
                                        Text(
                                            text = "Season $sNum",
                                            style = MaterialTheme.typography.titleSmall.copy(
                                                fontWeight = if (isCurrentSeason) FontWeight.Bold else FontWeight.Medium
                                            )
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
                                .padding(horizontal = 32.dp)
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
            .graphicsLayer(scaleX = scale, scaleY = scale)
            .onFocusChanged { focusState ->
                isFocused = focusState.isFocused
            }
            .focusable()
            .onKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown &&
                    (keyEvent.key == Key.Enter || keyEvent.key == Key.DirectionCenter ||
                     keyEvent.nativeKeyEvent.keyCode == android.view.KeyEvent.KEYCODE_DPAD_CENTER)) {
                    onClick()
                    true
                } else false
            }
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = if (isFocused) Color.White.copy(alpha = 0.1f) else Color.Transparent
        ),
        border = if (isFocused) {
            androidx.compose.foundation.BorderStroke(2.dp, Color.White)
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
                // Still (auto-download endpoint) → thumbnail on a single failure.
                var stillFailed by remember(episode.id) { mutableStateOf(false) }
                val episodeImageUrl = if (stillFailed) getEpisodeThumbUrl(episode) else getEpisodeStillUrl(episode)

                AsyncImage(
                    model = coil.request.ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                        .data(episodeImageUrl)
                        .memoryCacheKey(if (stillFailed) "ep_thumb_${episode.id}" else "ep_still_${episode.id}")
                        .diskCacheKey(if (stillFailed) "ep_thumb_${episode.id}" else "ep_still_${episode.id}")
                        .crossfade(true)
                        .build(),
                    contentDescription = episode.title,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop,
                    onError = { if (!stillFailed) stillFailed = true }
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