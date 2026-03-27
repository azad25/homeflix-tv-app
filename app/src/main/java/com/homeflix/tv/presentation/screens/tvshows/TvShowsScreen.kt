package com.homeflix.tv.presentation.screens.tvshows

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.zIndex
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.homeflix.tv.domain.model.Media
import com.homeflix.tv.domain.model.MediaType
import com.homeflix.tv.presentation.components.NetflixSideNavigation
import com.homeflix.tv.presentation.components.ContinueWatchingRow
import com.homeflix.tv.presentation.navigation.Screen
import com.homeflix.tv.presentation.theme.NetflixRed
import com.homeflix.tv.presentation.theme.TextPrimary
import com.homeflix.tv.presentation.theme.TextSecondary
import com.homeflix.tv.util.ApiUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch



@Composable
fun TvShowsScreen(
    navController: NavController,
    viewModel: TvShowsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val contentFocusRequester = remember { FocusRequester() }
    val continueWatchingFocusRequester = remember { FocusRequester() }
    val scrollState = rememberLazyListState()
    
    LaunchedEffect(Unit) {
        viewModel.loadTvShows()
    }
    
    // Refresh continue watching when returning from video player
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val lifecycleState by lifecycleOwner.lifecycle.currentStateFlow.collectAsState()
    var hasBeenResumed by remember { mutableStateOf(false) }
    
    LaunchedEffect(lifecycleState) {
        if (lifecycleState == androidx.lifecycle.Lifecycle.State.RESUMED) {
            if (hasBeenResumed) {
                // Not the first resume, so we're returning from another screen
                viewModel.refreshContinueWatching()
            }
            hasBeenResumed = true
        }
    }
    
    LaunchedEffect(uiState) {
        if (uiState is TvShowsUiState.Success) {
            delay(100)
            try {
                val successState = uiState as TvShowsUiState.Success
                // Focus continue watching if available, otherwise focus hero slider
                if (successState.continueWatchingEpisodes.isNotEmpty()) {
                    continueWatchingFocusRequester.requestFocus()
                } else {
                    contentFocusRequester.requestFocus()
                }
                // Scroll back to top AFTER focus to keep hero slider visible
                delay(150)
                scrollState.scrollToItem(0, 0)
            } catch (_: Exception) {}
        }
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
            },
            onNavigateToContent = {
                try {
                    contentFocusRequester.requestFocus()
                } catch (_: Exception) {}
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
                    text = "Latest TV Shows",
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
                    LazyColumn(
                        state = scrollState,
                        modifier = Modifier.fillMaxSize(),
                        userScrollEnabled = true
                    ) {
                        // Hero slider section for featured series
                        if (currentState.featuredSeries.isNotEmpty()) {
                            item {
                                TvShowsHeroSlider(
                                    featuredSeries = currentState.featuredSeries,
                                    onSeriesClick = { series ->
                                        navController.navigate(Screen.TvSeriesDetails.createRoute(series.id.toString()))
                                    },
                                    contentFocusRequester = contentFocusRequester
                                )
                            }
                        }
                        
                        // Series count indicator
                        item {
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
                        }
                        
                        // Continue Watching Episodes section
                        if (currentState.continueWatchingEpisodes.isNotEmpty()) {
                            item {
                                ContinueWatchingRow(
                                    continueWatchingItems = currentState.continueWatchingEpisodes,
                                    onPlay = { media, startTimeMs ->
                                        navController.navigate(Screen.VideoPlayer.createRoute(media.id, startTime = startTimeMs))
                                    },
                                    onInfo = { media ->
                                        navController.navigate(Screen.Details.createRoute(media.id.toString()))
                                    },
                                    focusRequester = continueWatchingFocusRequester,
                                    mediaTypeFilter = setOf(
                                        com.homeflix.tv.domain.model.MediaType.EPISODE,
                                        com.homeflix.tv.domain.model.MediaType.TV_SHOW
                                    ),
                                    applyHorizontalPadding = false,
                                    modifier = Modifier.padding(bottom = 24.dp, start = 32.dp)
                                )
                            }
                        }
                        
                        // TV Series horizontal slider row
                        item {
                            TvSeriesRow(
                                title = "All TV Series",
                                seriesList = currentState.series,
                                onSeriesClick = { series ->
                                    navController.navigate(Screen.TvSeriesDetails.createRoute(series.id.toString()))
                                }
                            )
                        }
                        
                        item {
                            Spacer(modifier = Modifier.height(48.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TvShowsHeroSlider(
    featuredSeries: List<TvSeries>,
    onSeriesClick: (TvSeries) -> Unit,
    contentFocusRequester: FocusRequester? = null
) {
    if (featuredSeries.isEmpty()) return
    
    var currentIndex by remember { mutableStateOf(0) }
    
    // Auto-slide every 10 seconds
    LaunchedEffect(currentIndex) {
        delay(10000)
        currentIndex = (currentIndex + 1) % featuredSeries.size
    }
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(400.dp)
    ) {
        // Netflix-style unified slide transition
        Crossfade(
            targetState = currentIndex,
            animationSpec = tween(durationMillis = 1500),
            label = "tvshows_hero_crossfade"
        ) { targetIndex ->
            val targetSeries = featuredSeries.getOrElse(targetIndex) { featuredSeries[0] }
            
            Box(modifier = Modifier.fillMaxSize()) {
                // Backdrop image
                AsyncImage(
                    model = ApiUtils.getSeriesBackdropUrl(targetSeries),
                    contentDescription = targetSeries.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                
                // Gradient overlays
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    Color.Black.copy(alpha = 0.9f),
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.4f)
                                )
                            )
                        )
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.8f)
                                ),
                                startY = 300f
                            )
                        )
                )
                
                // Staggered content reveal
                val contentVisible = remember { mutableStateOf(false) }
                LaunchedEffect(targetSeries.id) {
                    contentVisible.value = false
                    delay(300)
                    contentVisible.value = true
                }
                
                val contentAlpha by animateFloatAsState(
                    targetValue = if (contentVisible.value) 1f else 0f,
                    animationSpec = tween(durationMillis = 800),
                    label = "tvshows_content_alpha"
                )
                
                // Content
                Column(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 48.dp, end = 200.dp, bottom = 32.dp, top = 32.dp)
                        .graphicsLayer { alpha = contentAlpha },
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Series Logo with text fallback
                    var logoLoaded by remember(targetSeries.id) { mutableStateOf(false) }
                    
                    if (!logoLoaded) {
                        Text(
                            text = targetSeries.title,
                            style = MaterialTheme.typography.displaySmall.copy(
                                fontWeight = FontWeight.Black,
                                color = TextPrimary
                            ),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    
                    AsyncImage(
                        model = ApiUtils.getSeriesLogoUrl(targetSeries.id),
                        contentDescription = "${targetSeries.title} logo",
                        modifier = Modifier
                            .heightIn(max = 80.dp)
                            .fillMaxWidth(0.4f),
                        contentScale = ContentScale.Fit,
                        onSuccess = { logoLoaded = true },
                        onError = { logoLoaded = false }
                    )
                    
                    // Metadata row
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        targetSeries.year?.takeIf { it > 0 }?.let { year ->
                            Text(
                                text = year.toString(),
                                style = MaterialTheme.typography.titleMedium.copy(color = TextSecondary)
                            )
                        }
                    }
                    
                    // Description
                    targetSeries.description?.let { desc ->
                        Text(
                            text = desc,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = TextPrimary.copy(alpha = 0.9f)
                            ),
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth(0.7f)
                        )
                    }
                    
                    // View Details button
                    var viewDetailsFocused by remember { mutableStateOf(false) }
                    Button(
                        onClick = { onSeriesClick(targetSeries) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (viewDetailsFocused) Color(0xFFE50914) else Color.White,
                            contentColor = if (viewDetailsFocused) Color.White else Color.Black
                        ),
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.height(44.dp)
                            .then(
                                if (contentFocusRequester != null) {
                                    Modifier.focusRequester(contentFocusRequester)
                                } else Modifier
                            )
                            .onFocusChanged { viewDetailsFocused = it.isFocused }
                    ) {
                        Text(
                            text = "View Details",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }
            }
        }
        
        // Slide indicators
        if (featuredSeries.size > 1) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(32.dp)
            ) {
                featuredSeries.forEachIndexed { index, _ ->
                    Box(
                        modifier = Modifier
                            .size(if (index == currentIndex) 10.dp else 6.dp)
                            .clip(RoundedCornerShape(50))
                            .background(
                                if (index == currentIndex) Color.White else Color.White.copy(alpha = 0.4f)
                            )
                    )
                }
            }
        }
    }
}

@Composable
private fun TvSeriesRow(
    title: String,
    seriesList: List<TvSeries>,
    onSeriesClick: (TvSeries) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val itemFocusRequesters = remember(seriesList.size) {
        List(minOf(seriesList.size, 20)) { FocusRequester() }
    }
    
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        // Section Title
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall.copy(
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            ),
            modifier = Modifier.padding(start = 24.dp, bottom = 8.dp)
        )
        
        // Horizontal scrollable row
        LazyRow(
            state = listState,
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            userScrollEnabled = true,
            modifier = Modifier.fillMaxWidth()
        ) {
            itemsIndexed(seriesList) { index, series ->
                val itemFocusRequester = if (index < itemFocusRequesters.size) itemFocusRequesters[index] else null
                
                TvSeriesCard(
                    series = series,
                    onClick = { onSeriesClick(series) },
                    modifier = Modifier
                        .width(110.dp)
                        .then(
                            if (itemFocusRequester != null) {
                                Modifier.focusRequester(itemFocusRequester)
                            } else {
                                Modifier
                            }
                        )
                        .onFocusChanged { focusState ->
                            if (focusState.isFocused) {
                                // Auto-scroll to focused item
                                coroutineScope.launch {
                                    val targetIndex = when {
                                        index == 0 -> 0
                                        index >= seriesList.size - 2 -> maxOf(0, seriesList.size - 3)
                                        else -> maxOf(0, index - 1)
                                    }
                                    listState.animateScrollToItem(targetIndex)
                                }
                            }
                        }
                        .onKeyEvent { keyEvent ->
                            if (keyEvent.type == KeyEventType.KeyDown) {
                                when (keyEvent.key) {
                                    Key.DirectionLeft -> {
                                        if (index > 0 && index - 1 < itemFocusRequesters.size) {
                                            itemFocusRequesters[index - 1].requestFocus()
                                            true
                                        } else {
                                            false // Let system handle (moves to sidebar)
                                        }
                                    }
                                    Key.DirectionRight -> {
                                        if (index < seriesList.size - 1 && index + 1 < itemFocusRequesters.size) {
                                            itemFocusRequesters[index + 1].requestFocus()
                                        }
                                        true
                                    }
                                    else -> false
                                }
                            } else false
                        }
                )
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
            .onFocusChanged { focusState ->
                isFocused = focusState.isFocused
            }
            .focusable()
            .graphicsLayer(scaleX = scale, scaleY = scale)
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
                

            }
        }
    }
}