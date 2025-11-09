package com.homeflix.tv.presentation.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import com.homeflix.tv.domain.model.MediaType
import com.homeflix.tv.presentation.components.NetflixHeroSection
import com.homeflix.tv.presentation.components.NetflixSideNavigation
import com.homeflix.tv.presentation.components.MediaRow
import com.homeflix.tv.presentation.navigation.Screen
import com.homeflix.tv.presentation.theme.NetflixRed
import com.homeflix.tv.presentation.theme.TextPrimary
import kotlinx.coroutines.delay

/**
 * Netflix-style home screen for Android TV
 * - 60dp side navigation (icon-only)
 * - Hero section with movies only
 * - Content rows below
 * - Proper focus management
 */
@UnstableApi
@Composable
fun NetflixHomeScreen(
    navController: NavController,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var currentHeroIndex by remember { mutableStateOf(0) }
    val firstCardFocusRequester = remember { FocusRequester() }
    val scrollState = rememberLazyListState()
    
    // Auto-slide hero section every 10 seconds
    LaunchedEffect(uiState) {
        val currentState = uiState
        if (currentState is HomeUiState.Success && currentState.featuredMedia.isNotEmpty()) {
            while (true) {
                delay(10000)
                currentHeroIndex = (currentHeroIndex + 1) % currentState.featuredMedia.size
            }
        }
    }
    
    // Set initial focus to first card and ensure scroll starts at top
    LaunchedEffect(Unit) {
        // Ensure scroll starts at top
        scrollState.scrollToItem(0)
        delay(800) // Wait for content to load
        firstCardFocusRequester.requestFocus()
    }
    
    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Netflix-style side navigation (60dp icon bar)
        NetflixSideNavigation(
            selectedRoute = "home",
            onNavigate = { route ->
                when (route) {
                    "search" -> navController.navigate(Screen.Search.route)
                    "home" -> { /* Already on home */ }
                    "browse" -> navController.navigate(Screen.Browse.route)
                    "my_list" -> navController.navigate(Screen.Browse.createRoute("my_list"))
                }
            }
        )
        
        // Main content area
        Box(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f)
        ) {
            when (val currentState = uiState) {
                is HomeUiState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            CircularProgressIndicator(
                                color = NetflixRed,
                                strokeWidth = 4.dp,
                                modifier = Modifier.size(48.dp)
                            )
                            Text(
                                text = "Loading HomeFlix...",
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimary
                            )
                        }
                    }
                }
                
                is HomeUiState.Error -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.padding(48.dp)
                        ) {
                            Text(
                                text = "Something went wrong",
                                style = MaterialTheme.typography.headlineMedium.copy(
                                    fontWeight = FontWeight.Bold
                                ),
                                color = TextPrimary,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = currentState.message,
                                style = MaterialTheme.typography.bodyLarge,
                                color = TextPrimary.copy(alpha = 0.7f),
                                textAlign = TextAlign.Center
                            )
                            Button(
                                onClick = { viewModel.loadHomeContent() },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = NetflixRed
                                )
                            ) {
                                Text("Try Again")
                            }
                        }
                    }
                }
                
                is HomeUiState.Success -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        state = scrollState,
                        userScrollEnabled = true
                    ) {
                        // Hero Section (Movies Only)
                        if (currentState.featuredMedia.isNotEmpty()) {
                            item {
                                // Filter to movies only (no TV episodes)
                                val moviesOnly = currentState.featuredMedia.filter { 
                                    it.type == MediaType.MOVIE 
                                }
                                
                                if (moviesOnly.isNotEmpty()) {
                                    NetflixHeroSection(
                                        mediaList = moviesOnly,
                                        currentIndex = currentHeroIndex % moviesOnly.size,
                                        onPlayClick = { media ->
                                            navController.navigate(Screen.VideoPlayer.createRoute(media.id))
                                        },
                                        onDetailsClick = { media ->
                                            navController.navigate(Screen.Details.createRoute(media.id.toString()))
                                        },
                                        onIndexChange = { newIndex ->
                                            currentHeroIndex = newIndex
                                        },
                                        onRefreshClick = {
                                            viewModel.refreshFeaturedContent()
                                            currentHeroIndex = 0
                                        }
                                    )
                                }
                            }
                        }
                        
                        // Content Rows
                        item {
                            Column(
                                modifier = Modifier.padding(start = 48.dp, top = 24.dp, bottom = 24.dp, end = 48.dp),
                                verticalArrangement = Arrangement.spacedBy(32.dp)
                            ) {
                                // Continue Watching
                                if (currentState.continueWatching.isNotEmpty()) {
                                    MediaRow(
                                        title = "Continue Watching",
                                        mediaList = currentState.continueWatching.map { it.media },
                                        onMediaClick = { media ->
                                            navController.navigate(Screen.VideoPlayer.createRoute(media.id))
                                        },
                                        focusRequester = firstCardFocusRequester
                                    )
                                }
                                
                                // Trending Now
                                if (currentState.trending.isNotEmpty()) {
                                    MediaRow(
                                        title = "Trending Now",
                                        mediaList = currentState.trending,
                                        onMediaClick = { media ->
                                            navController.navigate(Screen.Details.createRoute(media.id.toString()))
                                        },
                                        focusRequester = if (currentState.continueWatching.isEmpty()) firstCardFocusRequester else null
                                    )
                                }
                                
                                // Popular Movies
                                if (currentState.popularMovies.isNotEmpty()) {
                                    MediaRow(
                                        title = "Popular Movies",
                                        mediaList = currentState.popularMovies,
                                        onMediaClick = { media ->
                                            navController.navigate(Screen.Details.createRoute(media.id.toString()))
                                        }
                                    )
                                }
                                
                                // Popular TV Shows
                                if (currentState.popularTVShows.isNotEmpty()) {
                                    MediaRow(
                                        title = "Popular TV Shows",
                                        mediaList = currentState.popularTVShows,
                                        onMediaClick = { media ->
                                            navController.navigate(Screen.Details.createRoute(media.id.toString()))
                                        }
                                    )
                                }
                                
                                // Recently Added
                                if (currentState.recentlyAdded.isNotEmpty()) {
                                    MediaRow(
                                        title = "Recently Added",
                                        mediaList = currentState.recentlyAdded,
                                        onMediaClick = { media ->
                                            navController.navigate(Screen.Details.createRoute(media.id.toString()))
                                        }
                                    )
                                }
                                
                                // Recommended
                                if (currentState.recommended.isNotEmpty()) {
                                    MediaRow(
                                        title = "Recommended for You",
                                        mediaList = currentState.recommended,
                                        onMediaClick = { media ->
                                            navController.navigate(Screen.Details.createRoute(media.id.toString()))
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
