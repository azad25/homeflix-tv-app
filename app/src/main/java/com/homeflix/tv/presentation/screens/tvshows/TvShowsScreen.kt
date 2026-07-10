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
import androidx.compose.material.icons.filled.PlayArrow
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
import com.homeflix.tv.presentation.components.ContinueWatchingSeriesRow
import com.homeflix.tv.presentation.components.HeroActionButton
import com.homeflix.tv.presentation.navigation.Screen
import com.homeflix.tv.presentation.theme.PrimeBg
import com.homeflix.tv.presentation.theme.RatingGold
import com.homeflix.tv.presentation.theme.PrimeBlue
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
                // Always focus the hero first, then pin to top (never land
                // scrolled into Continue Watching / rows).
                contentFocusRequester.requestFocus()
                delay(150)
                scrollState.scrollToItem(0, 0)
            } catch (_: Exception) {}
        }
    }
    
    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(PrimeBg)
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
        
        // Main Content — hero is the immersive top element (no separate header)
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            val currentState = uiState
            when (currentState) {
                is TvShowsUiState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = PrimeBlue)
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
                                    containerColor = PrimeBlue
                                )
                            ) {
                                Text("Retry")
                            }
                        }
                    }
                }
                
                is TvShowsUiState.Success -> {
                    // ── Section presence + item indices for a deterministic
                    // UP chain (disposed rows can't be reached by default
                    // focus search; each row scrolls to + focuses the row
                    // above it).
                    val hasHero = currentState.featuredSeries.isNotEmpty()
                    val hasCW = currentState.continueWatchingSeries.isNotEmpty()
                    val popularSeries = remember(currentState.series) {
                        currentState.series.sortedByDescending { it.rating }.take(15)
                    }
                    val hasPopular = popularSeries.isNotEmpty()
                    val genreBuckets = listOf(
                        "Action" to listOf("action", "adventure"),
                        "Drama" to listOf("drama"),
                        "Comedy" to listOf("comedy"),
                        "Sci-Fi & Fantasy" to listOf("sci-fi", "science fiction", "fantasy"),
                        "Crime & Mystery" to listOf("crime", "mystery", "thriller"),
                        "Animation" to listOf("animation", "kids")
                    )
                    val genreRows = remember(currentState.series) {
                        genreBuckets.mapNotNull { (label, keys) ->
                            val list = currentState.series.filter { s ->
                                s.genres.any { g -> keys.any { k -> g.contains(k, ignoreCase = true) } }
                            }.take(15)
                            if (list.size >= 3) label to list else null
                        }
                    }

                    val heroCount = if (hasHero) 1 else 0
                    val cwIndex = heroCount
                    val popularIndex = cwIndex + (if (hasCW) 1 else 0)
                    val firstGenreIndex = popularIndex + (if (hasPopular) 1 else 0)
                    val allIndex = firstGenreIndex + genreRows.size

                    val popularFocusRequester = remember { FocusRequester() }
                    val genreFocusRequesters = remember(genreRows.size) {
                        List(genreRows.size) { FocusRequester() }
                    }
                    val allFocusRequester = remember { FocusRequester() }

                    val scope = rememberCoroutineScope()
                    val upToHero: () -> Unit = {
                        scope.launch {
                            scrollState.animateScrollToItem(0)
                            repeat(6) {
                                try { contentFocusRequester.requestFocus(); return@launch } catch (_: Exception) { delay(50) }
                            }
                        }
                    }
                    val navUpTo: (Int, FocusRequester) -> Unit = { itemIndex, requester ->
                        scope.launch {
                            scrollState.animateScrollToItem(itemIndex)
                            repeat(6) {
                                try { requester.requestFocus(); return@launch } catch (_: Exception) { delay(50) }
                            }
                        }
                    }
                    val upFromPopular: () -> Unit =
                        if (hasCW) ({ navUpTo(cwIndex, continueWatchingFocusRequester) }) else upToHero
                    fun upFromGenre(i: Int): () -> Unit = when {
                        i > 0 -> ({ navUpTo(firstGenreIndex + i - 1, genreFocusRequesters[i - 1]) })
                        hasPopular -> ({ navUpTo(popularIndex, popularFocusRequester) })
                        hasCW -> ({ navUpTo(cwIndex, continueWatchingFocusRequester) })
                        else -> upToHero
                    }
                    val upFromAll: () -> Unit = when {
                        genreRows.isNotEmpty() -> ({ navUpTo(allIndex - 1, genreFocusRequesters.last()) })
                        hasPopular -> ({ navUpTo(popularIndex, popularFocusRequester) })
                        hasCW -> ({ navUpTo(cwIndex, continueWatchingFocusRequester) })
                        else -> upToHero
                    }

                    LazyColumn(
                        state = scrollState,
                        modifier = Modifier.fillMaxSize(),
                        userScrollEnabled = true
                    ) {
                        // Hero slider section for featured series
                        if (hasHero) {
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

                        // Continue Watching — one card per series (series banner),
                        // resumes the in-progress episode from its saved position.
                        if (hasCW) {
                            item {
                                ContinueWatchingSeriesRow(
                                    items = currentState.continueWatchingSeries,
                                    onResume = { episodeMediaId, startMs ->
                                        navController.navigate(Screen.VideoPlayer.createRoute(episodeMediaId, startTime = startMs))
                                    },
                                    focusRequester = continueWatchingFocusRequester,
                                    onNavigateUp = upToHero,
                                    modifier = Modifier.padding(bottom = 24.dp)
                                )
                            }
                        }

                        // Popular series — banner (thumb + logo) cards
                        if (hasPopular) {
                            item {
                                TvSeriesRow(
                                    title = "Popular Series",
                                    seriesList = popularSeries,
                                    onSeriesClick = { series ->
                                        navController.navigate(Screen.TvSeriesDetails.createRoute(series.id.toString()))
                                    },
                                    focusRequester = popularFocusRequester,
                                    onNavigateUp = upFromPopular
                                )
                            }
                        }

                        // Genre-grouped banner rows
                        genreRows.forEachIndexed { gi, (label, list) ->
                            item {
                                TvSeriesRow(
                                    title = label,
                                    seriesList = list,
                                    onSeriesClick = { series ->
                                        navController.navigate(Screen.TvSeriesDetails.createRoute(series.id.toString()))
                                    },
                                    focusRequester = genreFocusRequesters[gi],
                                    onNavigateUp = upFromGenre(gi)
                                )
                            }
                        }

                        // All series row
                        item {
                            TvSeriesRow(
                                title = "All TV Series",
                                seriesList = currentState.series,
                                onSeriesClick = { series ->
                                    navController.navigate(Screen.TvSeriesDetails.createRoute(series.id.toString()))
                                },
                                focusRequester = allFocusRequester,
                                onNavigateUp = upFromAll
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
            .height(470.dp)
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
                                // Fade into the page background so the hero
                                // blends seamlessly into the rows below.
                                colors = listOf(Color.Transparent, PrimeBg),
                                startY = 260f
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
                    
                    // Metadata row: ★ rating · year · seasons · genres
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (targetSeries.rating > 0) {
                            Text(
                                text = "★ ${String.format("%.1f", targetSeries.rating)}",
                                style = MaterialTheme.typography.titleMedium.copy(color = RatingGold, fontWeight = FontWeight.SemiBold)
                            )
                        }
                        targetSeries.year?.takeIf { it > 0 }?.let { year ->
                            Text(year.toString(), style = MaterialTheme.typography.titleMedium.copy(color = TextSecondary))
                        }
                        if (targetSeries.totalSeasons > 0) {
                            Text(
                                "${targetSeries.totalSeasons} Season${if (targetSeries.totalSeasons != 1) "s" else ""}",
                                style = MaterialTheme.typography.titleMedium.copy(color = TextSecondary)
                            )
                        }
                        if (targetSeries.genres.isNotEmpty()) {
                            Text(
                                targetSeries.genres.take(2).joinToString(" • "),
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
                    
                    // Proper red Play/Episodes action (Prime-style), replaces the
                    // plain "View Details" button.
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 4.dp)) {
                        HeroActionButton(
                            label = "Episodes",
                            icon = { Icon(Icons.Default.PlayArrow, null, Modifier.size(20.dp)) },
                            primary = true,
                            focusRequester = contentFocusRequester,
                            onClick = { onSeriesClick(targetSeries) }
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

/**
 * Banner-card row for TV series — Prime/Netflix "art + logo" cards (16:9
 * backdrop with the series logo composited at a per-title anchor), replacing
 * the old wall of 2:3 posters.
 */
@Composable
private fun TvSeriesRow(
    title: String,
    seriesList: List<TvSeries>,
    onSeriesClick: (TvSeries) -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    onNavigateUp: (() -> Unit)? = null
) {
    val listState = rememberLazyListState()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 24.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            ),
            modifier = Modifier.padding(start = 48.dp, bottom = 8.dp)
        )

        LazyRow(
            state = listState,
            contentPadding = PaddingValues(horizontal = 48.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            userScrollEnabled = true,
            modifier = Modifier.fillMaxWidth()
        ) {
            itemsIndexed(seriesList, key = { _, s -> s.id }) { index, series ->
                TvSeriesBannerCard(
                    series = series,
                    onClick = { onSeriesClick(series) },
                    onNavigateUp = onNavigateUp,
                    modifier = if (index == 0 && focusRequester != null)
                        Modifier.focusRequester(focusRequester) else Modifier
                )
            }
        }
    }
}

@Composable
private fun TvSeriesBannerCard(
    series: TvSeries,
    onClick: () -> Unit,
    onNavigateUp: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.06f else 1.0f,
        animationSpec = tween(durationMillis = 160),
        label = "tv_series_card_scale"
    )

    // Dynamic logo anchor: stable per series, varied across a row
    val logoAlignment = remember(series.id) {
        when (series.id % 3) {
            0 -> Alignment.BottomStart
            1 -> Alignment.BottomCenter
            else -> Alignment.BottomEnd
        }
    }

    Box(
        modifier = modifier
            .width(280.dp)
            .aspectRatio(16f / 9f)
            .graphicsLayer(scaleX = scale, scaleY = scale)
            .onFocusChanged { isFocused = it.isFocused }
            .onKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown) {
                    when (keyEvent.key) {
                        Key.Enter, Key.DirectionCenter -> { onClick(); true }
                        Key.DirectionUp ->
                            if (onNavigateUp != null) { onNavigateUp(); true } else false
                        else -> false
                    }
                } else false
            }
            .focusable()
            .clickable { onClick() }
            .clip(RoundedCornerShape(8.dp))
            .then(
                if (isFocused) Modifier.border(2.dp, Color.White, RoundedCornerShape(8.dp))
                else Modifier
            )
    ) {
        // Series backdrop with a single-swap poster fallback
        var failed by remember(series.id) { mutableStateOf(false) }
        AsyncImage(
            model = coil.request.ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                .data(
                    if (failed) "${ApiUtils.getBaseUrl()}/posters/${series.id}"
                    else ApiUtils.getSeriesBackdropUrl(series)
                )
                .memoryCacheKey(if (failed) "series_poster_${series.id}" else "series_backdrop_${series.id}")
                .diskCacheKey(if (failed) "series_poster_${series.id}" else "series_backdrop_${series.id}")
                .crossfade(false)
                .build(),
            contentDescription = series.title,
            contentScale = ContentScale.Crop,
            onError = { if (!failed) failed = true },
            modifier = Modifier.fillMaxSize()
        )

        // Legibility gradient
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f)),
                        startY = 120f
                    )
                )
        )

        // Series logo at the per-title anchor, text fallback
        var logoOk by remember(series.id) { mutableStateOf(true) }
        Box(
            modifier = Modifier
                .align(if (logoOk) logoAlignment else Alignment.BottomStart)
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            if (logoOk) {
                AsyncImage(
                    model = coil.request.ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                        .data(ApiUtils.getSeriesLogoUrl(series.id))
                        .memoryCacheKey("series_logo_${series.id}")
                        .diskCacheKey("series_logo_${series.id}")
                        .build(),
                    contentDescription = series.title,
                    contentScale = ContentScale.Fit,
                    onError = { logoOk = false },
                    modifier = Modifier.heightIn(max = 34.dp).widthIn(max = 160.dp)
                )
            } else {
                Column {
                    Text(
                        text = series.title,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = TextPrimary),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (series.rating > 0) {
                            Text(
                                "★ ${String.format("%.1f", series.rating)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary
                            )
                        }
                        if (series.totalSeasons > 0) {
                            Text(
                                "${series.totalSeasons} Season${if (series.totalSeasons != 1) "s" else ""}",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary
                            )
                        }
                    }
                }
            }
        }
    }
}