package com.homeflix.tv.presentation.screens.details

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.*
import androidx.compose.ui.draw.clip
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
import com.homeflix.tv.presentation.components.RecommendationSection
import com.homeflix.tv.presentation.navigation.Screen
import com.homeflix.tv.presentation.theme.NetflixRed
import com.homeflix.tv.presentation.theme.TextPrimary
import com.homeflix.tv.presentation.theme.TextSecondary
import com.homeflix.tv.util.ApiUtils

@Composable
fun DetailsScreen(
    mediaId: String,
    navController: NavController,
    viewModel: DetailsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    
    LaunchedEffect(mediaId) {
        viewModel.loadMediaDetails(mediaId)
    }
    
    // Netflix-style layout with BLACK background
    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black) // FORCE BLACK BACKGROUND
    ) {
        // Side Navigation (48dp icon bar)
        NetflixSideNavigation(
            selectedRoute = "details",
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
        is DetailsUiState.Loading -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }
        
        is DetailsUiState.Error -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Error loading details",
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
        
        is DetailsUiState.Success -> {
            val media = currentState.media
            
            val scrollState = rememberLazyListState()
            
            // NO AUTO-SCROLL - Let user control navigation
            // LaunchedEffect removed to prevent interference with D-pad navigation
            
            LazyColumn(
                state = scrollState,
                userScrollEnabled = true,
                modifier = Modifier.fillMaxSize()
            ) {
                item {
                    // Hero Section with Backdrop (Netflix/Prime style)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(400.dp)
                    ) {
                        // Background Banner Image
                        AsyncImage(
                            model = ApiUtils.getBannerUrl(media),
                            contentDescription = media.title,
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
                                model = ApiUtils.getPosterUrl(media),
                                contentDescription = media.title,
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
                                // Title
                                Text(
                                    text = media.title,
                                    style = MaterialTheme.typography.displayMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary
                                    )
                                )
                                
                                // Metadata Row
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    media.year?.let { year ->
                                        Text(
                                            text = year.toString(),
                                            style = MaterialTheme.typography.titleMedium.copy(
                                                color = TextSecondary
                                            )
                                        )
                                    }
                                    
                                    media.certification?.let { rating ->
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = Color.Gray.copy(alpha = 0.3f)
                                        ) {
                                            Text(
                                                text = rating,
                                                style = MaterialTheme.typography.bodyMedium.copy(
                                                    color = TextPrimary
                                                ),
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            )
                                        }
                                    }
                                    
                                    if (media.rating > 0) {
                                        Text(
                                            text = "★ ${String.format("%.1f", media.rating)}",
                                            style = MaterialTheme.typography.titleMedium.copy(
                                                color = TextSecondary
                                            )
                                        )
                                    }
                                    
                                    media.runtime?.let { runtime ->
                                        Text(
                                            text = "${runtime}min",
                                            style = MaterialTheme.typography.titleMedium.copy(
                                                color = TextSecondary
                                            )
                                        )
                                    }
                                }
                                
                                // Genres
                                if (media.genreNames.isNotEmpty()) {
                                    Text(
                                        text = media.genreNames.joinToString(" • "),
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
                                    // Continue Watching or Play Button based on progress
                                    if (currentState.watchProgress != null && currentState.watchProgress > 0.05f) {
                                        // Continue Watching Button (primary)
                                        Button(
                                            onClick = { 
                                                navController.navigate(Screen.VideoPlayer.createRoute(media.id, resumeFromProgress = true))
                                            },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = Color.White,
                                                contentColor = Color.Black
                                            ),
                                            shape = RoundedCornerShape(4.dp),
                                            modifier = Modifier.height(44.dp)
                                        ) {
                                            Text(
                                                text = "▶ Continue Watching",
                                                style = MaterialTheme.typography.titleMedium.copy(
                                                    fontWeight = FontWeight.Bold
                                                )
                                            )
                                        }
                                        
                                        // Play from Beginning Button (secondary)
                                        OutlinedButton(
                                            onClick = { 
                                                navController.navigate(Screen.VideoPlayer.createRoute(media.id, forceStartFromBeginning = true))
                                            },
                                            colors = ButtonDefaults.outlinedButtonColors(
                                                contentColor = TextPrimary,
                                                containerColor = Color.Black.copy(alpha = 0.5f)
                                            ),
                                            shape = RoundedCornerShape(4.dp),
                                            modifier = Modifier.height(44.dp)
                                        ) {
                                            Text(
                                                text = "↻ Play from Beginning",
                                                style = MaterialTheme.typography.titleMedium
                                            )
                                        }
                                    } else {
                                        // Regular Play Button (no progress)
                                        Button(
                                            onClick = { 
                                                navController.navigate(Screen.VideoPlayer.createRoute(media.id))
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
                                
                                // Progress indicator if watching progress exists
                                currentState.watchProgress?.let { progress ->
                                    if (progress > 0.05f) {
                                        Column(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Text(
                                                text = "${(progress * 100).toInt()}% watched",
                                                style = MaterialTheme.typography.bodyMedium.copy(
                                                    color = TextSecondary
                                                )
                                            )
                                            LinearProgressIndicator(
                                                progress = progress,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(4.dp)
                                                    .clip(RoundedCornerShape(2.dp)),
                                                color = NetflixRed,
                                                trackColor = Color.White.copy(alpha = 0.3f)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                
                // Additional Details Section
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(48.dp),
                        verticalArrangement = Arrangement.spacedBy(24.dp)
                    ) {
                        // Cast & Crew
                        if (media.cast.isNotEmpty() || media.director.isNotEmpty()) {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "Cast & Crew",
                                    style = MaterialTheme.typography.headlineSmall.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        color = TextPrimary
                                    )
                                )
                                
                                if (media.director.isNotEmpty()) {
                                    Text(
                                        text = "Director: ${media.director.joinToString(", ")}",
                                        style = MaterialTheme.typography.bodyLarge.copy(
                                            color = TextSecondary
                                        )
                                    )
                                }
                                
                                if (media.cast.isNotEmpty()) {
                                    Text(
                                        text = "Cast: ${media.cast.take(5).joinToString(", ")}",
                                        style = MaterialTheme.typography.bodyLarge.copy(
                                            color = TextSecondary
                                        )
                                    )
                                }
                            }
                        }
                        
                        // Technical Details
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Details",
                                style = MaterialTheme.typography.headlineSmall.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    color = TextPrimary
                                )
                            )
                            
                            media.quality?.let { quality ->
                                Text(
                                    text = "Quality: $quality",
                                    style = MaterialTheme.typography.bodyLarge.copy(
                                        color = TextSecondary
                                    )
                                )
                            }
                            
                            media.language?.let { language ->
                                Text(
                                    text = "Language: $language",
                                    style = MaterialTheme.typography.bodyLarge.copy(
                                        color = TextSecondary
                                    )
                                )
                            }
                        }
                    }
                }
                
                // Recommendations Section
                item {
                    RecommendationSection(
                        currentMedia = media,
                        onPlay = { recommendedMedia ->
                            navController.navigate(Screen.VideoPlayer.createRoute(recommendedMedia.id))
                        },
                        onInfo = { recommendedMedia ->
                            navController.navigate(Screen.Details.createRoute(recommendedMedia.id.toString()))
                        },
                        modifier = Modifier.padding(vertical = 32.dp)
                    )
                }
            }
        }
        }
    }
}