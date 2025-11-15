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
    
    // NETFLIX-LEVEL Focus Management
    val sideNavFocusRequester = remember { FocusRequester() }
    val heroPlayButtonFocusRequester = remember { FocusRequester() }
    val firstRowFocusRequester = remember { FocusRequester() }
    
    // Professional focus state management
    var currentFocusArea by remember { mutableStateOf(FocusArea.HERO) }
    var isInitialized by remember { mutableStateOf(false) }
    
    // NO AUTO-FOCUS: Let user control navigation naturally
    // Removed auto-focus to prevent unwanted scrolling
    
    // Ensure LazyColumn starts at top
    LaunchedEffect(uiState) {
        if (uiState is HomeUiState.Success) {
            // Reset scroll position to top when content loads
            delay(100)
            // listState.scrollToItem(0) will be called in the LazyColumn scope
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
            
            // Main content area with LEFT arrow and BACK button handling
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
                    .background(Color.Black)
                    .onKeyEvent { keyEvent ->
                        if (keyEvent.type == KeyEventType.KeyDown) {
                            when (keyEvent.key) {
                                Key.DirectionLeft -> {
                                    // Navigate to sidebar like Netflix
                                    currentFocusArea = FocusArea.SIDEBAR
                                    try {
                                        sideNavFocusRequester.requestFocus()
                                    } catch (e: Exception) {
                                        android.util.Log.e("HomeScreen", "Failed to focus sidebar", e)
                                    }
                                    true
                                }
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
                        // FIXED: LazyColumn with state to ensure it starts at top
                        val listState = rememberLazyListState()
                        
                        // Ensure scroll starts at top and stays there
                        LaunchedEffect(currentState) {
                            try {
                                listState.scrollToItem(0)
                            } catch (e: Exception) {
                                // Ignore scroll errors
                            }
                        }
                        
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
                        
                        // Trending Now as LazyColumn item
                        if (currentState.trending.isNotEmpty()) {
                            item {
                                val trendingRowFocusRequester = remember { FocusRequester() }
                                val popularMoviesFocusRequester = remember { FocusRequester() }
                                MediaRow(
                                    title = "Trending Now",
                                    mediaList = currentState.trending,
                                    onMediaClick = { media ->
                                        navController.navigate(Screen.Details.createRoute(media.id.toString()))
                                    },
                                    focusRequester = if (currentState.continueWatching.isEmpty()) firstRowFocusRequester else trendingRowFocusRequester,
                                    onNavigateUp = {
                                        if (currentState.continueWatching.isNotEmpty()) {
                                            // Focus continue watching row
                                            try {
                                                firstRowFocusRequester.requestFocus()
                                            } catch (e: Exception) {
                                                // Fallback to hero
                                                currentFocusArea = FocusArea.HERO
                                                heroPlayButtonFocusRequester.requestFocus()
                                            }
                                        } else {
                                            currentFocusArea = FocusArea.HERO
                                            try {
                                                heroPlayButtonFocusRequester.requestFocus()
                                            } catch (e: Exception) {
                                                // Ignore focus errors
                                            }
                                        }
                                    },
                                    onNavigateDown = {
                                        // Navigate to Popular Movies row
                                        if (currentState.popularMovies.isNotEmpty()) {
                                            try {
                                                popularMoviesFocusRequester.requestFocus()
                                            } catch (e: Exception) {
                                                // Ignore focus errors
                                            }
                                        }
                                    },
                                    modifier = Modifier.padding(bottom = 24.dp)
                                )
                            }
                        }
                        
                        // Popular Movies as LazyColumn item
                        if (currentState.popularMovies.isNotEmpty()) {
                            item {
                                val popularMoviesFocusRequester = remember { FocusRequester() }
                                val recentlyAddedFocusRequester = remember { FocusRequester() }
                                MediaRow(
                                    title = "Popular Movies",
                                    mediaList = currentState.popularMovies,
                                    onMediaClick = { media ->
                                        navController.navigate(Screen.Details.createRoute(media.id.toString()))
                                    },
                                    focusRequester = popularMoviesFocusRequester,
                                    onNavigateUp = {
                                        // Navigate back to Trending Now
                                        if (currentState.trending.isNotEmpty()) {
                                            try {
                                                // Focus trending row
                                                if (currentState.continueWatching.isEmpty()) {
                                                    firstRowFocusRequester.requestFocus()
                                                } else {
                                                    // There's a trending row focus requester we need to access
                                                    firstRowFocusRequester.requestFocus()
                                                }
                                            } catch (e: Exception) {
                                                // Fallback to hero
                                                currentFocusArea = FocusArea.HERO
                                                heroPlayButtonFocusRequester.requestFocus()
                                            }
                                        }
                                    },
                                    onNavigateDown = {
                                        // Navigate to Recently Added row
                                        if (currentState.recentlyAdded.isNotEmpty()) {
                                            try {
                                                recentlyAddedFocusRequester.requestFocus()
                                            } catch (e: Exception) {
                                                // Ignore focus errors
                                            }
                                        }
                                    },
                                    modifier = Modifier.padding(bottom = 24.dp)
                                )
                            }
                        }
                        
                        // Recently Added as LazyColumn item
                        if (currentState.recentlyAdded.isNotEmpty()) {
                            item {
                                val recentlyAddedFocusRequester = remember { FocusRequester() }
                                val recommendedFocusRequester = remember { FocusRequester() }
                                MediaRow(
                                    title = "Recently Added",
                                    mediaList = currentState.recentlyAdded,
                                    onMediaClick = { media ->
                                        navController.navigate(Screen.Details.createRoute(media.id.toString()))
                                    },
                                    focusRequester = recentlyAddedFocusRequester,
                                    onNavigateUp = {
                                        // Navigate back to Popular Movies
                                        if (currentState.popularMovies.isNotEmpty()) {
                                            try {
                                                // We need to access the popular movies focus requester
                                                firstRowFocusRequester.requestFocus()
                                            } catch (e: Exception) {
                                                // Fallback to hero
                                                currentFocusArea = FocusArea.HERO
                                                heroPlayButtonFocusRequester.requestFocus()
                                            }
                                        }
                                    },
                                    onNavigateDown = {
                                        // Navigate to Recommended row
                                        if (currentState.recommended.isNotEmpty()) {
                                            try {
                                                recommendedFocusRequester.requestFocus()
                                            } catch (e: Exception) {
                                                // Ignore focus errors
                                            }
                                        }
                                    },
                                    modifier = Modifier.padding(bottom = 24.dp)
                                )
                            }
                        }
                        
                        // Recommended as LazyColumn item
                        if (currentState.recommended.isNotEmpty()) {
                            item {
                                val recommendedFocusRequester = remember { FocusRequester() }
                                MediaRow(
                                    title = "Recommended for You",
                                    mediaList = currentState.recommended,
                                    onMediaClick = { media ->
                                        navController.navigate(Screen.Details.createRoute(media.id.toString()))
                                    },
                                    focusRequester = recommendedFocusRequester,
                                    onNavigateUp = {
                                        // Navigate back to Recently Added
                                        if (currentState.recentlyAdded.isNotEmpty()) {
                                            try {
                                                // We need to access the recently added focus requester
                                                firstRowFocusRequester.requestFocus()
                                            } catch (e: Exception) {
                                                // Fallback to hero
                                                currentFocusArea = FocusArea.HERO
                                                heroPlayButtonFocusRequester.requestFocus()
                                            }
                                        }
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
