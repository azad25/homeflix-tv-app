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
    
    // CRITICAL FIX: NO AUTO-FOCUS to prevent scroll issues
    LaunchedEffect(uiState) {
        if (uiState is HomeUiState.Success && !isInitialized) {
            currentFocusArea = FocusArea.HERO
            isInitialized = true
            
            // MINIMAL delay, then focus hero WITHOUT scrolling
            delay(50)
            try {
                heroPlayButtonFocusRequester.requestFocus()
            } catch (e: Exception) {
                // Let user navigate manually - no fallback focus
            }
        }
    }
    
    // NETFLIX-LEVEL Layout with professional navigation
    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown) {
                    when (keyEvent.key) {
                        Key.DirectionLeft -> {
                            // ONLY move to sidebar from hero/content, and ONLY if not already in sidebar
                            if (currentFocusArea != FocusArea.SIDEBAR) {
                                currentFocusArea = FocusArea.SIDEBAR
                                try {
                                    sideNavFocusRequester.requestFocus()
                                } catch (e: Exception) {
                                    // Ignore focus errors
                                }
                                true
                            } else {
                                false // Let sidebar handle internal navigation
                            }
                        }
                        Key.DirectionRight -> {
                            // ALWAYS move from sidebar to hero/content
                            if (currentFocusArea == FocusArea.SIDEBAR) {
                                currentFocusArea = FocusArea.HERO
                                try {
                                    heroPlayButtonFocusRequester.requestFocus()
                                } catch (e: Exception) {
                                    // Fallback to content
                                    currentFocusArea = FocusArea.CONTENT
                                    try {
                                        firstRowFocusRequester.requestFocus()
                                    } catch (e2: Exception) {
                                        // Ignore
                                    }
                                }
                                true
                            } else {
                                false // Let content handle internal navigation
                            }
                        }
                        else -> false
                    }
                } else false
            }
    ) {
        // NETFLIX-LEVEL SIDE NAVIGATION
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
                currentFocusArea = FocusArea.HERO
                try {
                    heroPlayButtonFocusRequester.requestFocus()
                } catch (e: Exception) {
                    // Fallback to content
                    currentFocusArea = FocusArea.CONTENT
                    try {
                        firstRowFocusRequester.requestFocus()
                    } catch (e2: Exception) {
                        // Ignore
                    }
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
                    // FIXED: LazyColumn with state to ensure it starts at top
                    val listState = rememberLazyListState()
                    
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black)
                            .focusable(false)
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
                            val continueWatchingMedia = currentState.continueWatching.mapNotNull { 
                                try { it.media } catch (e: Exception) { null } 
                            }
                            if (continueWatchingMedia.isNotEmpty()) {
                                item {
                                    MediaRow(
                                        title = "Continue Watching",
                                        mediaList = continueWatchingMedia,
                                        onMediaClick = { media ->
                                            navController.navigate(Screen.VideoPlayer.createRoute(media.id))
                                        },
                                        focusRequester = firstRowFocusRequester,
                                        onNavigateUp = {
                                            currentFocusArea = FocusArea.HERO
                                            try {
                                                heroPlayButtonFocusRequester.requestFocus()
                                            } catch (e: Exception) {
                                                // Ignore focus errors
                                            }
                                        },
                                        modifier = Modifier.padding(bottom = 24.dp)
                                    )
                                }
                            }
                        }
                        
                        // Trending Now as LazyColumn item
                        if (currentState.trending.isNotEmpty()) {
                            item {
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
                                            currentFocusArea = FocusArea.HERO
                                            try {
                                                heroPlayButtonFocusRequester.requestFocus()
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
                                MediaRow(
                                    title = "Popular Movies",
                                    mediaList = currentState.popularMovies,
                                    onMediaClick = { media ->
                                        navController.navigate(Screen.Details.createRoute(media.id.toString()))
                                    },
                                    modifier = Modifier.padding(bottom = 24.dp)
                                )
                            }
                        }
                        
                        // Recently Added as LazyColumn item
                        if (currentState.recentlyAdded.isNotEmpty()) {
                            item {
                                MediaRow(
                                    title = "Recently Added",
                                    mediaList = currentState.recentlyAdded,
                                    onMediaClick = { media ->
                                        navController.navigate(Screen.Details.createRoute(media.id.toString()))
                                    },
                                    modifier = Modifier.padding(bottom = 24.dp)
                                )
                            }
                        }
                        
                        // Recommended as LazyColumn item
                        if (currentState.recommended.isNotEmpty()) {
                            item {
                                MediaRow(
                                    title = "Recommended for You",
                                    mediaList = currentState.recommended,
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
            }
        }
    }
}
