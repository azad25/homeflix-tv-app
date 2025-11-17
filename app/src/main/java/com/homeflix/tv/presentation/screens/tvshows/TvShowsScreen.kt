package com.homeflix.tv.presentation.screens.tvshows

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.zIndex
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.homeflix.tv.domain.model.Media
import com.homeflix.tv.domain.model.MediaType
import com.homeflix.tv.presentation.components.NetflixSideNavigation
import com.homeflix.tv.presentation.navigation.Screen
import com.homeflix.tv.presentation.theme.NetflixRed
import com.homeflix.tv.presentation.theme.TextPrimary
import com.homeflix.tv.presentation.theme.TextSecondary
import com.homeflix.tv.util.ApiUtils



@Composable
fun TvShowsScreen(
    navController: NavController,
    viewModel: TvShowsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    
    LaunchedEffect(Unit) {
        viewModel.loadTvShows()
    }
    
    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // SIDE NAVIGATION
        NetflixSideNavigation(
            selectedRoute = "tv-shows",
            onNavigate = { route ->
                navController.navigate(route) {
                    popUpTo(Screen.Home.route) { inclusive = false }
                    launchSingleTop = true
                }
            }
        )
        
        // Main Content
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Header
            Column(
                modifier = Modifier.padding(32.dp)
            ) {
                Text(
                    text = "TV Shows",
                    style = MaterialTheme.typography.displayMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                )
                Text(
                    text = "Latest series first • Sorted by recently added",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = TextSecondary
                    ),
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            
            val currentState = uiState
            when (currentState) {
                is TvShowsUiState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = NetflixRed)
                    }
                }
                
                is TvShowsUiState.Error -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Error loading TV shows",
                                style = MaterialTheme.typography.headlineMedium.copy(
                                    color = TextPrimary
                                ),
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = currentState.message,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = TextSecondary
                                ),
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = { viewModel.loadTvShows() },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = NetflixRed
                                )
                            ) {
                                Text("Retry")
                            }
                        }
                    }
                }
                
                is TvShowsUiState.Success -> {
                    // Series count indicator
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 32.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = "Showing ${currentState.series.size} TV series",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = NetflixRed,
                                fontWeight = FontWeight.Medium
                            )
                        )
                    }
                    
                    // TV Series grid
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 160.dp),
                        contentPadding = PaddingValues(24.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        userScrollEnabled = true,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(
                            items = currentState.series,
                            key = { series -> series.id }
                        ) { series ->
                            TvSeriesCard(
                                series = series,
                                onClick = {
                                    navController.navigate(Screen.TvSeriesDetails.createRoute(series.id.toString()))
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TvSeriesCard(
    series: TvSeries,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }
    
    // Netflix-style scale animation on focus (matching MediaCard)
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.1f else 1.0f,
        animationSpec = tween(durationMillis = 200),
        label = "tv_series_card_scale"
    )
    
    // Netflix-style card with proper z-index management
    Box(
        modifier = modifier
            .aspectRatio(2f / 3f)
            .scale(scale)
            .focusable()
            .onFocusChanged { focusState ->
                isFocused = focusState.isFocused
            }
            .clickable { onClick() }
            .then(
                if (isFocused) {
                    Modifier
                        .background(
                            Color.White.copy(alpha = 0.1f),
                            RoundedCornerShape(6.dp)
                        )
                        .zIndex(10f) // Bring focused card to front
                } else {
                    Modifier.zIndex(1f)
                }
            )
    ) {
        Card(
            modifier = Modifier.fillMaxSize(),
            shape = RoundedCornerShape(6.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color.Transparent
            ),
            elevation = CardDefaults.cardElevation(
                defaultElevation = if (isFocused) 8.dp else 2.dp
            )
        ) {
            Box {
                // Series image with proper fallback chain (matching web app exactly)
                var currentImageUrl by remember { mutableStateOf(ApiUtils.getSeriesPosterUrl(series)) }
                var fallbackLevel by remember { mutableStateOf(0) }
                
                if (fallbackLevel >= 3) {
                    // Final fallback: Gradient with series title
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                                    colors = listOf(
                                        NetflixRed.copy(alpha = 0.8f),
                                        NetflixRed.copy(alpha = 0.6f)
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = series.title.take(1).uppercase(),
                                style = MaterialTheme.typography.displayLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            )
                            Text(
                                text = series.title,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = Color.White,
                                    textAlign = TextAlign.Center
                                ),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 8.dp)
                            )
                        }
                    }
                } else {
                    // Try poster with proper fallback chain (matching web app exactly)
                    AsyncImage(
                        model = currentImageUrl,
                        contentDescription = series.title,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(6.dp)),
                        contentScale = ContentScale.Crop,
                        onError = {
                            when (fallbackLevel) {
                                0 -> {
                                    // First fallback: Try /api/posters/{id} endpoint (matching web app)
                                    currentImageUrl = "${ApiUtils.getBaseUrl()}/posters/${series.id}"
                                    fallbackLevel = 1
                                    android.util.Log.d("TvSeriesCard", "Fallback to posters API: $currentImageUrl")
                                }
                                1 -> {
                                    // Second fallback: Try thumbnail endpoint (matching web app)
                                    currentImageUrl = "${ApiUtils.getBaseUrl()}/thumbnails/${series.id}"
                                    fallbackLevel = 2
                                    android.util.Log.d("TvSeriesCard", "Fallback to thumbnail: $currentImageUrl")
                                }
                                2 -> {
                                    // Final fallback to gradient
                                    fallbackLevel = 3
                                    android.util.Log.d("TvSeriesCard", "All image sources failed, showing gradient")
                                }
                            }
                        }
                    )
                }
                
                // Netflix-style overlay on focus
                if (isFocused) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Color.Black.copy(alpha = 0.7f),
                                RoundedCornerShape(6.dp)
                            )
                    ) {
                        // Play button
                        Box(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .size(48.dp)
                                .background(
                                    NetflixRed,
                                    RoundedCornerShape(24.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "▶",
                                color = Color.White,
                                style = MaterialTheme.typography.headlineSmall
                            )
                        }
                        
                        // Series info at bottom
                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(8.dp)
                        ) {
                            Text(
                                text = series.title,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                ),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(top = 4.dp)
                            ) {
                                series.year?.let { year ->
                                    Text(
                                        text = year.toString(),
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            color = TextSecondary
                                        )
                                    )
                                }
                                
                                if (series.rating > 0) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Star,
                                            contentDescription = null,
                                            tint = Color(0xFFFFD700),
                                            modifier = Modifier.size(12.dp)
                                        )
                                        Text(
                                            text = String.format("%.1f", series.rating),
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                color = TextSecondary
                                            )
                                        )
                                    }
                                }
                            }
                            
                            // Seasons info
                            Text(
                                text = "${series.totalSeasons} Season${if (series.totalSeasons != 1) "s" else ""}",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = TextSecondary
                                ),
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}