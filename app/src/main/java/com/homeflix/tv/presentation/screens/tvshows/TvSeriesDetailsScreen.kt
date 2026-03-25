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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import com.homeflix.tv.presentation.theme.NetflixRed
import com.homeflix.tv.presentation.theme.TextPrimary
import com.homeflix.tv.presentation.theme.TextSecondary
import com.homeflix.tv.util.ApiUtils



@Composable
fun TvSeriesDetailsScreen(
    seriesId: String,
    navController: NavController,
    viewModel: TvSeriesDetailsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    
    LaunchedEffect(seriesId) {
        viewModel.loadSeriesDetails(seriesId)
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
            is TvSeriesDetailsUiState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = NetflixRed)
                }
            }
            
            is TvSeriesDetailsUiState.Error -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Error loading series",
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
            
            is TvSeriesDetailsUiState.Success -> {
                val series = currentState.series
                val scrollState = rememberLazyListState()
                
                LazyColumn(
                    state = scrollState,
                    userScrollEnabled = true,
                    modifier = Modifier.fillMaxSize()
                ) {
                    item {
                        // Hero Section with Backdrop
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(400.dp)
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
                            Row(
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(32.dp)
                                    .fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(24.dp)
                            ) {
                                // Poster
                                AsyncImage(
                                    model = ApiUtils.getSeriesPosterUrl(series),
                                    contentDescription = series.title,
                                    modifier = Modifier
                                        .width(160.dp)
                                        .aspectRatio(2f / 3f)
                                        .clip(RoundedCornerShape(6.dp)),
                                    contentScale = ContentScale.Crop
                                )
                                
                                // Details
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
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
                                    
                                    // Try series logo from local assets
                                    val seriesLogoUrl = series.bannerPath?.split("/")?.lastOrNull()?.substringBeforeLast(".")?.let {
                                        "${ApiUtils.getBaseUrl()}/admin/assets/${it}_logo.png"
                                    } ?: "${ApiUtils.getBaseUrl()}/admin/assets/logo_${series.id}.png"
                                    
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
                                    
                                    // Metadata Row
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        series.year?.let { year ->
                                            Text(
                                                text = year.toString(),
                                                style = MaterialTheme.typography.titleMedium.copy(
                                                    color = TextSecondary
                                                )
                                            )
                                        }
                                        
                                        if (series.rating > 0) {
                                            Text(
                                                text = "★ ${String.format("%.1f", series.rating)}",
                                                style = MaterialTheme.typography.titleMedium.copy(
                                                    color = TextSecondary
                                                )
                                            )
                                        }
                                        
                                        Text(
                                            text = "${series.totalSeasons} Season${if (series.totalSeasons != 1) "s" else ""}",
                                            style = MaterialTheme.typography.titleMedium.copy(
                                                color = TextSecondary
                                            )
                                        )
                                        
                                        Text(
                                            text = "${series.totalEpisodes} Episodes",
                                            style = MaterialTheme.typography.titleMedium.copy(
                                                color = TextSecondary
                                            )
                                        )
                                    }
                                    
                                    // Genres
                                    if (series.genres.isNotEmpty()) {
                                        Text(
                                            text = series.genres.joinToString(" • "),
                                            style = MaterialTheme.typography.titleMedium.copy(
                                                color = TextSecondary
                                            )
                                        )
                                    }
                                    
                                    // Description
                                    series.description?.let { description ->
                                        Text(
                                            text = description,
                                            style = MaterialTheme.typography.bodyLarge.copy(
                                                color = TextPrimary.copy(alpha = 0.9f)
                                            ),
                                            maxLines = 4,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    
                                    // Action Buttons
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        // Play First Episode Button
                                        Button(
                                            onClick = { 
                                                // Navigate to first season to play first episode
                                                navController.navigate(Screen.TvSeriesSeason.createRoute(seriesId, 1))
                                            },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = Color.White,
                                                contentColor = Color.Black
                                            ),
                                            shape = RoundedCornerShape(4.dp),
                                            modifier = Modifier.height(44.dp)
                                        ) {
                                            Text(
                                                text = "▶ Play",
                                                style = MaterialTheme.typography.titleMedium.copy(
                                                    fontWeight = FontWeight.Bold
                                                )
                                            )
                                        }
                                        
                                        // Add to List Button
                                        OutlinedButton(
                                            onClick = { 
                                                // TODO: Add to watchlist
                                            },
                                            colors = ButtonDefaults.outlinedButtonColors(
                                                contentColor = TextPrimary,
                                                containerColor = Color.Black.copy(alpha = 0.5f)
                                            ),
                                            shape = RoundedCornerShape(4.dp),
                                            modifier = Modifier.height(44.dp)
                                        ) {
                                            Text(
                                                text = "+ My List",
                                                style = MaterialTheme.typography.titleMedium
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    
                    // Seasons Section
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(48.dp)
                        ) {
                            Text(
                                text = "Seasons",
                                style = MaterialTheme.typography.headlineSmall.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    color = TextPrimary
                                ),
                                modifier = Modifier.padding(bottom = 24.dp)
                            )
                            
                            // Seasons Grid
                            Column(
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                currentState.seasons.forEach { season ->
                                    SeasonCard(
                                        season = season,
                                        series = series,
                                        onClick = {
                                            navController.navigate(
                                                Screen.TvSeriesSeason.createRoute(seriesId, season.seasonNumber)
                                            )
                                        }
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

@Composable
private fun SeasonCard(
    season: Season,
    series: com.homeflix.tv.presentation.screens.tvshows.TvSeries,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }
    
    // Scale animation on focus
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.02f else 1.0f,
        animationSpec = tween(durationMillis = 200),
        label = "season_card_scale"
    )
    
    Card(
        modifier = modifier
            .fillMaxWidth()
            .scale(scale)
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
            // Season Backdrop Thumbnail (like web app)
            Box(
                modifier = Modifier
                    .width(160.dp)
                    .aspectRatio(16f / 9f)
                    .background(
                        Color.Gray.copy(alpha = 0.3f),
                        RoundedCornerShape(8.dp)
                    )
            ) {
                // Use series backdrop (seasons don't have their own)
                AsyncImage(
                    model = ApiUtils.getSeriesBackdropUrl(series),
                    contentDescription = season.name,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop
                )
                
                // Season number overlay
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .background(
                            Color.Black.copy(alpha = 0.7f),
                            RoundedCornerShape(4.dp)
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "S${season.seasonNumber}",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    )
                }
            }
            
            // Season Info
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = season.name,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                )
                
                Text(
                    text = "${season.episodeCount} Episodes",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = TextSecondary
                    )
                )
                
                season.description?.let { description ->
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = TextSecondary
                        ),
                        maxLines = 2,
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