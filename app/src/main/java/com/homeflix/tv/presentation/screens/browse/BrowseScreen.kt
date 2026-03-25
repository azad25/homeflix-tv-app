package com.homeflix.tv.presentation.screens.browse

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.zIndex
import com.homeflix.tv.domain.model.MediaType
import kotlinx.coroutines.delay
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.homeflix.tv.domain.model.Media
import com.homeflix.tv.presentation.components.NetflixSideNavigation
import com.homeflix.tv.presentation.navigation.Screen
import com.homeflix.tv.presentation.theme.NetflixRed
import com.homeflix.tv.presentation.theme.TextPrimary
import com.homeflix.tv.presentation.theme.TextSecondary
import com.homeflix.tv.util.ApiUtils

enum class FocusArea {
    SIDEBAR, CONTENT
}

@Composable
fun BrowseScreen(
    navController: NavController,
    viewModel: BrowseViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    
    // NETFLIX-LEVEL focus management
    val sideNavFocusRequester = remember { FocusRequester() }
    val contentFocusRequester = remember { FocusRequester() }
    var currentFocusArea by remember { mutableStateOf(FocusArea.CONTENT) }
    
    // Auto-focus content when loaded
    LaunchedEffect(uiState) {
        if (uiState is BrowseUiState.Success) {
            delay(300)
            try {
                contentFocusRequester.requestFocus()
            } catch (_: Exception) {}
        }
    }
    
    // SIMPLIFIED Layout - let components handle their own focus
    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // SIDE NAVIGATION
        NetflixSideNavigation(
            selectedRoute = "browse",
            onNavigate = { route ->
                navController.navigate(route) {
                    popUpTo(Screen.Home.route) { inclusive = false }
                    launchSingleTop = true
                }
            },
            onNavigateToContent = {
                currentFocusArea = FocusArea.CONTENT
                try {
                    contentFocusRequester.requestFocus()
                } catch (_: Exception) {}
            }
        )
        
        // Main Content - NETFLIX PRINCIPLE: No container focus management
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Header with latest content indication
            Column(
                modifier = Modifier.padding(32.dp)
            ) {
                Text(
                    text = "Browse Movies",
                    style = MaterialTheme.typography.displayMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                )
                Text(
                    text = "Latest content first • Sorted by recently added",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = TextSecondary
                    ),
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            val currentState = uiState
            when (currentState) {
                is BrowseUiState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = NetflixRed)
                    }
                }
                
                is BrowseUiState.Error -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Error loading movies",
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
                                onClick = { viewModel.loadBrowseContent() },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = NetflixRed
                                )
                            ) {
                                Text("Retry")
                            }
                        }
                    }
                }
                
                is BrowseUiState.Success -> {
                    // Movie count indicator with pagination info
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 32.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = "Showing ${currentState.movies.size} movies" + 
                                   if (currentState.hasMore) " • Load more available" else " • All movies loaded",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = NetflixRed,
                                fontWeight = FontWeight.Medium
                            )
                        )
                        
                        if (currentState.isLoadingMore) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                CircularProgressIndicator(
                                    color = NetflixRed,
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp
                                )
                                Text(
                                    text = "Loading more...",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = TextSecondary
                                    )
                                )
                            }
                        }
                    }
                    
                    // PAGINATED movie grid with scroll-triggered auto-loading
                    val gridState = rememberLazyGridState()
                    
                    // Auto-load more when near the end of the grid
                    LaunchedEffect(gridState.firstVisibleItemIndex, currentState.movies.size) {
                        val totalItems = currentState.movies.size
                        val lastVisible = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                        if (lastVisible >= totalItems - 6 && currentState.hasMore && !currentState.isLoadingMore) {
                            viewModel.loadMoreMovies()
                        }
                    }
                    
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 120.dp),
                        state = gridState,
                        contentPadding = PaddingValues(24.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        userScrollEnabled = true,
                        modifier = Modifier.fillMaxSize()
                            .focusRequester(contentFocusRequester)
                    ) {
                        items(
                            items = currentState.movies,
                            key = { media -> media.id }
                        ) { media ->
                            NetflixMovieCard(
                                media = media,
                                onClick = {
                                    navController.navigate(Screen.Details.createRoute(media.id.toString()))
                                }
                            )
                        }
                        
                        // Loading indicator at bottom
                        if (currentState.isLoadingMore) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(
                                        color = NetflixRed,
                                        modifier = Modifier.size(24.dp),
                                        strokeWidth = 2.dp
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
private fun NetflixMovieCard(
    media: Media,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }
    
    // Subtle scale animation on focus
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.05f else 1.0f,
        animationSpec = tween(durationMillis = 200),
        label = "browse_movie_card_scale"
    )
    
    // Border-only focus style (matching home page)
    Box(
        modifier = modifier
            .aspectRatio(2f / 3f)
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
            .clickable { onClick() }
            .then(
                if (isFocused) {
                    Modifier
                        .border(2.dp, Color.White, RoundedCornerShape(6.dp))
                        .zIndex(10f)
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
                defaultElevation = if (isFocused) 6.dp else 2.dp
            )
        ) {
            Box {
                // Movie Poster
                AsyncImage(
                    model = ApiUtils.getPosterUrl(media),
                    contentDescription = media.title,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(6.dp)),
                    contentScale = ContentScale.Crop
                )
                
                // Title at bottom (always visible, no full overlay)
                if (isFocused) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                            .background(
                                Color.Black.copy(alpha = 0.8f),
                                RoundedCornerShape(bottomStart = 6.dp, bottomEnd = 6.dp)
                            )
                            .padding(6.dp)
                    ) {
                        Column {
                            Text(
                                text = media.title,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontWeight = FontWeight.Medium,
                                    color = Color.White
                                ),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(top = 2.dp)
                            ) {
                                media.year?.let { year ->
                                    Text(
                                        text = year.toString(),
                                        style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary)
                                    )
                                }
                                if (media.rating > 0) {
                                    Text(
                                        text = "★ ${String.format("%.1f", media.rating)}",
                                        style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary)
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