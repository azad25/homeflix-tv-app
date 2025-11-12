package com.homeflix.tv.presentation.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
// Removed LazyColumn imports - using simple Column now
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
import com.homeflix.tv.presentation.navigation.Screen
import com.homeflix.tv.presentation.theme.NetflixRed
import com.homeflix.tv.presentation.theme.TextPrimary
import kotlinx.coroutines.delay

/**
 * SIMPLIFIED NETFLIX-STYLE HOME SCREEN - FIXED NAVIGATION
 * Based on sample TV apps - simple focus management
 */
@UnstableApi
@Composable
fun NetflixHomeScreen(
    navController: NavController,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var currentHeroIndex by remember { mutableStateOf(0) }
    
    // RESTORED Focus management with crash protection
    val sideNavFocusRequester = remember { FocusRequester() }
    val heroPlayButtonFocusRequester = remember { FocusRequester() }
    val firstRowFocusRequester = remember { FocusRequester() }
    
    var isOnSideNav by remember { mutableStateOf(false) }
    var isOnHero by remember { mutableStateOf(true) }
    var isInitialized by remember { mutableStateOf(false) }
    
    // SAFE initialization with error handling
    LaunchedEffect(uiState) {
        if (uiState is HomeUiState.Success && !isInitialized) {
            delay(300)
            try {
                heroPlayButtonFocusRequester.requestFocus()
                isOnHero = true
                isOnSideNav = false
            } catch (e: Exception) {
                // Ignore focus errors - let Android TV handle it
            }
            isInitialized = true
        }
    }
    
    // RESTORED D-PAD NAVIGATION with crash protection
    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown) {
                    when (keyEvent.key) {
                        Key.DirectionLeft -> {
                            if (!isOnSideNav) {
                                try {
                                    sideNavFocusRequester.requestFocus()
                                    isOnSideNav = true
                                    isOnHero = false
                                } catch (e: Exception) {
                                    // Ignore focus errors
                                }
                                true
                            } else false
                        }
                        Key.DirectionRight -> {
                            if (isOnSideNav) {
                                try {
                                    if (isOnHero) {
                                        heroPlayButtonFocusRequester.requestFocus()
                                    } else {
                                        firstRowFocusRequester.requestFocus()
                                    }
                                    isOnSideNav = false
                                } catch (e: Exception) {
                                    // Ignore focus errors
                                }
                                true
                            } else false
                        }
                        else -> false
                    }
                } else false
            }
    ) {
        // RESTORED SIDE NAVIGATION with crash protection
        NetflixSideNavigation(
            selectedRoute = "home",
            onNavigate = { route ->
                when (route) {
                    "search" -> navController.navigate(Screen.Search.route)
                    "home" -> { /* Already on home */ }
                    "browse" -> navController.navigate(Screen.Browse.route)
                }
            },
            onNavigateToContent = {
                try {
                    heroPlayButtonFocusRequester.requestFocus()
                    isOnHero = true
                    isOnSideNav = false
                } catch (e: Exception) {
                    // Ignore focus errors
                }
            },
            modifier = Modifier.focusRequester(sideNavFocusRequester)
        )
        
        // Main content area with crash prevention
        Box(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f)
                .background(Color.Black)
        ) {
            // Wrap everything in a safe try-catch to prevent crashes
            val currentState = uiState
            
            when (currentState) {
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
                    // RESTORED CONTENT with proper focus
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black)
                            .focusable(false)
                    ) {
                        // HERO SECTION
                        if (currentState.featuredMedia.isNotEmpty()) {
                            val moviesOnly = currentState.featuredMedia.filter { it.type == MediaType.MOVIE }
                            if (moviesOnly.isNotEmpty()) {
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
                                        try {
                                            firstRowFocusRequester.requestFocus()
                                            isOnHero = false
                                        } catch (e: Exception) {
                                            // Ignore focus errors
                                        }
                                    }
                                )
                            }
                        }
                        
                        // CONTENT ROWS - NO AUTO-SCROLL
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 24.dp, bottom = 48.dp)
                                .focusable(false), // Prevent focus on container
                            verticalArrangement = Arrangement.spacedBy(24.dp)
                        ) {
                            // Continue Watching
                            if (currentState.continueWatching.isNotEmpty()) {
                                val continueWatchingMedia = currentState.continueWatching.mapNotNull { 
                                    try { it.media } catch (e: Exception) { null } 
                                }
                                if (continueWatchingMedia.isNotEmpty()) {
                                    MediaRow(
                                        title = "Continue Watching",
                                        mediaList = continueWatchingMedia,
                                        onMediaClick = { media ->
                                            navController.navigate(Screen.VideoPlayer.createRoute(media.id))
                                        },
                                        focusRequester = firstRowFocusRequester,
                                        onNavigateUp = {
                                            try {
                                                heroPlayButtonFocusRequester.requestFocus()
                                                isOnHero = true
                                            } catch (e: Exception) {
                                                // Ignore focus errors
                                            }
                                        }
                                    )
                                }
                            }
                            
                            // Trending Now
                            if (currentState.trending.isNotEmpty()) {
                                val trendingRowFocusRequester = remember { FocusRequester() }
                                MediaRow(
                                    title = "Trending Now",
                                    mediaList = currentState.trending,
                                    onMediaClick = { media ->
                                        navController.navigate(Screen.Details.createRoute(media.id.toString()))
                                    },
                                    focusRequester = if (currentState.continueWatching.isEmpty()) firstRowFocusRequester else trendingRowFocusRequester,
                                    onNavigateUp = {
                                        if (currentState.continueWatching.isNotEmpty()) {
                                            firstRowFocusRequester.requestFocus()
                                        } else {
                                            try {
                                                heroPlayButtonFocusRequester.requestFocus()
                                                isOnHero = true
                                            } catch (e: Exception) {
                                                // Ignore focus errors
                                            }
                                        }
                                    }
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
