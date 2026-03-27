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
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import com.homeflix.tv.domain.model.MediaType
import com.homeflix.tv.presentation.components.NetflixHeroSection
import com.homeflix.tv.presentation.components.NetflixSideNavigation
import com.homeflix.tv.presentation.components.MediaRow
import com.homeflix.tv.presentation.components.ContinueWatchingRow
import com.homeflix.tv.presentation.navigation.Screen
import com.homeflix.tv.presentation.theme.NetflixRed
import com.homeflix.tv.presentation.theme.TextPrimary
import kotlinx.coroutines.delay
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner

/**
 * NETFLIX-LEVEL Android TV Home Screen
 * Professional focus management and navigation
 */

enum class FocusArea {
    SIDEBAR, HERO, CONTENT
}
@UnstableApi
@Composable
fun NetflixHomeScreen(
    navController: NavController,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var currentHeroIndex by remember { mutableStateOf(0) }
    val listState = rememberLazyListState()
    
    // NETFLIX-LEVEL Focus Management
    val sideNavFocusRequester = remember { FocusRequester() }
    val heroPlayButtonFocusRequester = remember { FocusRequester() }
    val firstRowFocusRequester = remember { FocusRequester() }
    val latestMoviesFocusRequester = remember { FocusRequester() }
    
    // Professional focus state management
    var currentFocusArea by remember { mutableStateOf(FocusArea.HERO) }
    var isInitialized by remember { mutableStateOf(false) }
    
    // NETFLIX-STYLE FOCUS MANAGEMENT: Start with hero section
    LaunchedEffect(uiState) {
        if (uiState is HomeUiState.Success && !isInitialized) {
            delay(300) // Allow UI to settle
            try {
                // Focus hero section first (like Netflix)
                heroPlayButtonFocusRequester.requestFocus()
                currentFocusArea = FocusArea.HERO
                isInitialized = true
                // Scroll back to top AFTER focus to keep hero slider fully visible
                delay(150)
                listState.scrollToItem(0, 0)
            } catch (e: Exception) {
                android.util.Log.e("HomeScreen", "Failed to set initial focus", e)
            }
        }
    }
    
    // Refresh continue watching when returning from video player
    val lifecycleOwner = LocalLifecycleOwner.current
    val lifecycleState by lifecycleOwner.lifecycle.currentStateFlow.collectAsState()
    var hasBeenResumed by remember { mutableStateOf(false) }
    
    LaunchedEffect(lifecycleState) {
        if (lifecycleState == Lifecycle.State.RESUMED) {
            if (hasBeenResumed) {
                // Not the first resume, so we're returning from another screen
                viewModel.refreshRecentlyWatched()
            }
            hasBeenResumed = true
        }
    }
    
    // Smooth scrolling management
    val coroutineScope = rememberCoroutineScope()
    
    // Ensure LazyColumn starts at top and handles smooth scrolling
    LaunchedEffect(uiState) {
        if (uiState is HomeUiState.Success) {
            // Reset scroll position to top when content loads
            delay(100)
            // Scroll will be handled in LazyColumn scope
        }
    }
    
    // CRITICAL FIX: Full screen loading overlay to prevent sidebar focus during loading
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Main content layout
        Row(
            modifier = Modifier.fillMaxSize()
        ) {
            // SIDE NAVIGATION with proper focus exit
            NetflixSideNavigation(
                selectedRoute = "home",
                onNavigate = { route ->
                    when (route) {
                        "search" -> navController.navigate(Screen.Search.route)
                        "home" -> { /* Already on home */ }
                        "browse" -> navController.navigate(Screen.Browse.route)
                        "my-list" -> navController.navigate(Screen.MyList.route)
                        "tv-shows" -> navController.navigate(Screen.TvShows.route)
                    }
                },
                onNavigateToContent = {
                    // Exit sidebar and go to hero
                    currentFocusArea = FocusArea.HERO
                    try {
                        heroPlayButtonFocusRequester.requestFocus()
                    } catch (e: Exception) {
                        // Fallback to first content row
                        currentFocusArea = FocusArea.CONTENT
                        try {
                            firstRowFocusRequester.requestFocus()
                        } catch (e2: Exception) {
                            // Let user navigate manually
                        }
                    }
                },
                modifier = Modifier.focusRequester(sideNavFocusRequester)
            )
            
            // Main content area
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
                    .background(Color.Black)
                    .onKeyEvent { keyEvent ->
                        if (keyEvent.type == KeyEventType.KeyDown) {
                            when (keyEvent.key) {
                                Key.Back -> {
                                    // Netflix behavior: Back button focuses navigation
                                    currentFocusArea = FocusArea.SIDEBAR
                                    try {
                                        sideNavFocusRequester.requestFocus()
                                    } catch (e: Exception) {
                                        // If sidebar focus fails, let system handle back
                                        false
                                    }
                                    true
                                }
                                else -> false
                            }
                        } else false
                    }
            ) {
                // Wrap everything in a safe try-catch to prevent crashes
                val currentState = uiState
                
                when (currentState) {
                    is HomeUiState.Success -> {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black),
                            userScrollEnabled = true
                        ) {
                        // HERO SECTION as LazyColumn item
                        if (currentState.featuredMedia.isNotEmpty()) {
                            val moviesOnly = currentState.featuredMedia.filter { it.type == MediaType.MOVIE }
                            if (moviesOnly.isNotEmpty()) {
                                item {
                                    val safeIndex = currentHeroIndex % moviesOnly.size
                                    NetflixHeroSection(
                                        mediaList = moviesOnly,
                                        currentIndex = safeIndex,
                                        onPlayClick = { media ->
                                            navController.navigate(Screen.VideoPlayer.createRoute(media.id))
                                        },
                                        onDetailsClick = { media ->
                                            navController.navigate(Screen.Details.createRoute(media.id.toString()))
                                        },
                                        onIndexChange = { newIndex ->
                                            currentHeroIndex = newIndex
                                        },
                                        playButtonFocusRequester = heroPlayButtonFocusRequester,
                                        onNavigateDown = {
                                            currentFocusArea = FocusArea.CONTENT
                                            try {
                                                firstRowFocusRequester.requestFocus()
                                            } catch (e: Exception) {
                                                // Ignore focus errors
                                            }
                                        }
                                    )
                                }
                            }
                        }
                        
                        // Spacer item
                        item {
                            Spacer(modifier = Modifier.height(24.dp))
                        }
                        
                        // Continue Watching as LazyColumn item
                        if (currentState.continueWatching.isNotEmpty()) {
                            item {
                                ContinueWatchingRow(
                                    continueWatchingItems = currentState.continueWatching,
                                    onPlay = { media, startTimeMs ->
                                        // Navigate with resume time
                                        navController.navigate(Screen.VideoPlayer.createRoute(media.id, startTime = startTimeMs))
                                    },
                                    onInfo = { media ->
                                        navController.navigate(Screen.Details.createRoute(media.id.toString()))
                                    },
                                    focusRequester = firstRowFocusRequester,
                                    modifier = Modifier.padding(bottom = 24.dp)
                                )
                            }
                        }
                        
                        // Latest Movies as LazyColumn item (replaces duplicate Trending/Popular/Recently Added rows)
                        if (currentState.latestMovies.isNotEmpty()) {
                            item {
                                MediaRow(
                                    title = "Latest Movies",
                                    mediaList = currentState.latestMovies,
                                    onMediaClick = { media ->
                                        navController.navigate(Screen.Details.createRoute(media.id.toString()))
                                    },
                                    focusRequester = if (currentState.continueWatching.isEmpty()) firstRowFocusRequester else latestMoviesFocusRequester,
                                    modifier = Modifier.padding(bottom = 24.dp)
                                )
                            }
                        }
                        
                        // Trending Movies
                        if (currentState.trendingMovies.isNotEmpty()) {
                            item {
                                MediaRow(
                                    title = "🔥 Trending Now",
                                    mediaList = currentState.trendingMovies,
                                    onMediaClick = { media ->
                                        navController.navigate(Screen.Details.createRoute(media.id.toString()))
                                    },
                                    modifier = Modifier.padding(bottom = 24.dp)
                                )
                            }
                        }
                        
                        // Popular Movies
                        if (currentState.popularMovies.isNotEmpty()) {
                            item {
                                MediaRow(
                                    title = "⭐ Popular on HomeFlix",
                                    mediaList = currentState.popularMovies,
                                    onMediaClick = { media ->
                                        navController.navigate(Screen.Details.createRoute(media.id.toString()))
                                    },
                                    modifier = Modifier.padding(bottom = 24.dp)
                                )
                            }
                        }
                        
                        // Action Movies
                        if (currentState.actionMovies.isNotEmpty()) {
                            item {
                                MediaRow(
                                    title = "Action",
                                    mediaList = currentState.actionMovies,
                                    onMediaClick = { media ->
                                        navController.navigate(Screen.Details.createRoute(media.id.toString()))
                                    },
                                    modifier = Modifier.padding(bottom = 24.dp)
                                )
                            }
                        }
                        
                        // Drama Movies
                        if (currentState.dramaMovies.isNotEmpty()) {
                            item {
                                MediaRow(
                                    title = "Drama",
                                    mediaList = currentState.dramaMovies,
                                    onMediaClick = { media ->
                                        navController.navigate(Screen.Details.createRoute(media.id.toString()))
                                    },
                                    modifier = Modifier.padding(bottom = 24.dp)
                                )
                            }
                        }
                        
                        // Sci-Fi Movies
                        if (currentState.sciFiMovies.isNotEmpty()) {
                            item {
                                MediaRow(
                                    title = "Sci-Fi",
                                    mediaList = currentState.sciFiMovies,
                                    onMediaClick = { media ->
                                        navController.navigate(Screen.Details.createRoute(media.id.toString()))
                                    },
                                    modifier = Modifier.padding(bottom = 24.dp)
                                )
                            }
                        }
                        
                            // Bottom padding item
                            item {
                                Spacer(modifier = Modifier.height(48.dp))
                            }
                        }
                    }
                    
                    is HomeUiState.Loading -> {
                        // Loading state is handled by the overlay below
                    }
                    
                    is HomeUiState.Error -> {
                        // Error state is handled by the overlay below
                    }
                }
            }
        }
        
        // CRITICAL FIX: Full screen loading overlay that covers everything including sidebar
        val currentState = uiState
        when (currentState) {
            is HomeUiState.Loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black)
                        .focusable(false), // Prevent any focus during loading
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
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black)
                        .focusable(false),
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
            
            else -> {
                // Success state - content is already rendered above
            }
        }
    }
}
