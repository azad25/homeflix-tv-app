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
import com.homeflix.tv.presentation.components.CinematicHero
import com.homeflix.tv.presentation.components.NetflixSideNavigation
import com.homeflix.tv.presentation.components.MediaRow
import com.homeflix.tv.presentation.components.ContinueWatchingRow
import com.homeflix.tv.presentation.components.FeaturedBanner
import com.homeflix.tv.presentation.components.Top10Row
import com.homeflix.tv.presentation.components.ThumbLogoRow
import com.homeflix.tv.presentation.navigation.Screen
import com.homeflix.tv.presentation.theme.NetflixRed
import com.homeflix.tv.presentation.theme.PrimeBg
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
    // Per-section requesters for the deterministic UP chain (rows scrolled out
    // of view are disposed, so default focus search can't find them — each row
    // explicitly scrolls to + focuses the row above it).
    val featuredFocusRequester = remember { FocusRequester() }
    val top10FocusRequester = remember { FocusRequester() }
    val trendingFocusRequester = remember { FocusRequester() }
    val actionFocusRequester = remember { FocusRequester() }
    
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

    // UP from the first content row → scroll list to top and focus the hero
    // (fixes "can't scroll back to top" when the hero item is disposed).
    // Focus is retried: right after the scroll the freshly-composed hero's
    // FocusRequester may not be attached yet, and a single silent failure
    // left focus stranded at the bottom.
    val onNavigateUpToHero: () -> Unit = {
        coroutineScope.launch {
            listState.animateScrollToItem(0)
            repeat(8) {
                try {
                    heroPlayButtonFocusRequester.requestFocus()
                    listState.scrollToItem(0, 0)
                    return@launch
                } catch (_: Exception) { delay(50) }
            }
        }
    }
    
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
            .background(PrimeBg)
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
                        "notifications" -> navController.navigate(Screen.Notifications.route)
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
                    .background(PrimeBg)
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
                        // ── Deterministic UP chain ─────────────────────────
                        // Section presence + LazyColumn item indices, so every
                        // row can scroll to and focus the row above it.
                        val moviesOnlyHero = currentState.featuredMedia.filter { it.type == MediaType.MOVIE }
                        val hasHero = moviesOnlyHero.isNotEmpty()
                        val hasCW = currentState.continueWatching.isNotEmpty()
                        val hasFeatured = currentState.latestMovies.isNotEmpty()
                        val hasTop10 = currentState.popularMovies.isNotEmpty()
                        val hasTrending = currentState.trendingMovies.isNotEmpty()
                        val heroCount = if (hasHero) 1 else 0
                        val cwIndex = heroCount + 1 // +1 for spacer item
                        val featuredIndex = cwIndex + (if (hasCW) 1 else 0)
                        val top10Index = featuredIndex + (if (hasFeatured) 1 else 0)
                        val trendingIndex = top10Index + (if (hasTop10) 1 else 0)

                        val navUpTo: (Int, FocusRequester) -> Unit = { itemIndex, requester ->
                            coroutineScope.launch {
                                listState.animateScrollToItem(itemIndex)
                                repeat(6) {
                                    try { requester.requestFocus(); return@launch } catch (_: Exception) { delay(50) }
                                }
                            }
                        }
                        // UP from each section → nearest present section above it
                        val upFromFeatured: () -> Unit =
                            if (hasCW) ({ navUpTo(cwIndex, firstRowFocusRequester) }) else onNavigateUpToHero
                        val upFromTop10: () -> Unit = when {
                            hasFeatured -> ({ navUpTo(featuredIndex, featuredFocusRequester) })
                            hasCW -> ({ navUpTo(cwIndex, firstRowFocusRequester) })
                            else -> onNavigateUpToHero
                        }
                        val upFromTrending: () -> Unit = when {
                            hasTop10 -> ({ navUpTo(top10Index, top10FocusRequester) })
                            hasFeatured -> ({ navUpTo(featuredIndex, featuredFocusRequester) })
                            hasCW -> ({ navUpTo(cwIndex, firstRowFocusRequester) })
                            else -> onNavigateUpToHero
                        }
                        val upFromAction: () -> Unit = when {
                            hasTrending -> ({ navUpTo(trendingIndex, trendingFocusRequester) })
                            hasTop10 -> ({ navUpTo(top10Index, top10FocusRequester) })
                            hasFeatured -> ({ navUpTo(featuredIndex, featuredFocusRequester) })
                            hasCW -> ({ navUpTo(cwIndex, firstRowFocusRequester) })
                            else -> onNavigateUpToHero
                        }

                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .fillMaxSize()
                                .background(PrimeBg),
                            userScrollEnabled = true
                        ) {
                        // HERO SECTION as LazyColumn item
                        if (currentState.featuredMedia.isNotEmpty()) {
                            val moviesOnly = moviesOnlyHero
                            if (moviesOnly.isNotEmpty()) {
                                item {
                                    val safeIndex = currentHeroIndex % moviesOnly.size
                                    CinematicHero(
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
                                    onNavigateUp = onNavigateUpToHero,
                                    modifier = Modifier.padding(bottom = 24.dp)
                                )
                            }
                        }
                        
                        // FEATURED - half/half banner (one big + rotating list)
                        // built from the latest movies.
                        if (currentState.latestMovies.isNotEmpty()) {
                            item {
                                FeaturedBanner(
                                    title = "Featured",
                                    mediaList = currentState.latestMovies,
                                    onMediaClick = { media ->
                                        navController.navigate(Screen.Details.createRoute(media.id.toString()))
                                    },
                                    focusRequester = featuredFocusRequester,
                                    onNavigateUp = upFromFeatured,
                                    modifier = Modifier.padding(bottom = 28.dp)
                                )
                            }
                        }

                        // TOP 10 - Prime-style big rank numbers (most-watched)
                        if (currentState.popularMovies.isNotEmpty()) {
                            item {
                                Top10Row(
                                    title = "Top 10 on HomeFlix",
                                    mediaList = currentState.popularMovies,
                                    onMediaClick = { media ->
                                        navController.navigate(Screen.Details.createRoute(media.id.toString()))
                                    },
                                    focusRequester = top10FocusRequester,
                                    onNavigateUp = upFromTop10,
                                    modifier = Modifier.padding(bottom = 28.dp)
                                )
                            }
                        }

                        // Trending — landscape thumb+logo row (mixes card styles)
                        if (currentState.trendingMovies.isNotEmpty()) {
                            item {
                                ThumbLogoRow(
                                    title = "Trending Now",
                                    mediaList = currentState.trendingMovies,
                                    onMediaClick = { media ->
                                        navController.navigate(Screen.Details.createRoute(media.id.toString()))
                                    },
                                    focusRequester = trendingFocusRequester,
                                    onNavigateUp = upFromTrending,
                                    modifier = Modifier.padding(bottom = 24.dp)
                                )
                            }
                        }

                        // Action Movies (single genre row keeps the page tight)
                        if (currentState.actionMovies.isNotEmpty()) {
                            item {
                                MediaRow(
                                    title = "Action",
                                    mediaList = currentState.actionMovies,
                                    onMediaClick = { media ->
                                        navController.navigate(Screen.Details.createRoute(media.id.toString()))
                                    },
                                    focusRequester = actionFocusRequester,
                                    onNavigateUp = upFromAction,
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
                        .background(PrimeBg)
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
                        .background(PrimeBg)
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
