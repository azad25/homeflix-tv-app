package com.homeflix.tv.presentation.screens.mylist

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.homeflix.tv.presentation.components.ContinueWatchingRow
import com.homeflix.tv.presentation.components.PosterCard
import com.homeflix.tv.presentation.navigation.Screen
import com.homeflix.tv.presentation.theme.*
import com.homeflix.tv.util.ApiUtils
import kotlinx.coroutines.delay

/**
 * MY LIST — Prime/Netflix style. Clean header, Continue Watching rail, then a
 * responsive poster grid of saved titles using the shared PosterCard. Polished
 * empty state.
 */
@Composable
fun MyListScreen(
    navController: NavController,
    viewModel: MyListViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val continueWatchingFocusRequester = remember { FocusRequester() }
    val gridFocusRequester = remember { FocusRequester() }
    val gridState = rememberLazyGridState()

    LaunchedEffect(uiState) {
        if (uiState is MyListUiState.Success) {
            val s = uiState as MyListUiState.Success
            delay(300)
            try {
                if (s.continueWatching.isNotEmpty()) continueWatchingFocusRequester.requestFocus()
                else if (s.movies.isNotEmpty()) gridFocusRequester.requestFocus()
            } catch (_: Exception) {}
        }
    }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(PrimeBg)
    ) {
        com.homeflix.tv.presentation.components.NetflixSideNavigation(
            selectedRoute = "my-list",
            onNavigate = { route ->
                navController.navigate(route) {
                    popUpTo(Screen.Home.route) { inclusive = false }
                    launchSingleTop = true
                }
            },
            onNavigateToContent = {
                try {
                    val s = uiState as? MyListUiState.Success
                    if (s?.continueWatching?.isNotEmpty() == true) continueWatchingFocusRequester.requestFocus()
                    else gridFocusRequester.requestFocus()
                } catch (_: Exception) {}
            }
        )

        Box(modifier = Modifier.fillMaxSize()) {
            when (val state = uiState) {
                is MyListUiState.Loading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = PrimeBlue)
                    }
                }

                is MyListUiState.Error -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("Couldn't load My List", color = TextPrimary, style = MaterialTheme.typography.titleLarge)
                            Text(state.message, color = PrimeTextDim, textAlign = TextAlign.Center)
                            Button(onClick = { viewModel.loadMyList() }, colors = ButtonDefaults.buttonColors(containerColor = PrimeBlue)) {
                                Text("Retry")
                            }
                        }
                    }
                }

                is MyListUiState.Success -> {
                    // Incremental rendering: start with 3 rows and append as the
                    // user scrolls near the bottom — the full list (with every
                    // poster request) never loads up front.
                    var visibleCount by remember(state.movies.size) {
                        mutableStateOf(minOf(18, state.movies.size))
                    }
                    val shouldLoadMore by remember {
                        derivedStateOf {
                            val last = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                            last >= gridState.layoutInfo.totalItemsCount - 7
                        }
                    }
                    LaunchedEffect(shouldLoadMore) {
                        if (shouldLoadMore && visibleCount < state.movies.size) {
                            visibleCount = minOf(visibleCount + 18, state.movies.size)
                        }
                    }

                    LazyVerticalGrid(
                        columns = GridCells.Fixed(6),
                        state = gridState,
                        contentPadding = PaddingValues(start = 40.dp, end = 56.dp, top = 32.dp, bottom = 40.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        // Header
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Text(
                                text = "My List",
                                style = MaterialTheme.typography.headlineLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                ),
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                        }

                        // Continue Watching rail
                        if (state.continueWatching.isNotEmpty()) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                ContinueWatchingRow(
                                    continueWatchingItems = state.continueWatching,
                                    onPlay = { media, startMs ->
                                        navController.navigate(Screen.VideoPlayer.createRoute(media.id, startTime = startMs))
                                    },
                                    onInfo = { media ->
                                        navController.navigate(Screen.Details.createRoute(media.id.toString()))
                                    },
                                    focusRequester = continueWatchingFocusRequester,
                                    mediaTypeFilter = null,
                                    applyHorizontalPadding = false,
                                    modifier = Modifier.padding(bottom = 12.dp)
                                )
                            }
                        }

                        if (state.movies.isEmpty()) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(280.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                        Box(
                                            modifier = Modifier
                                                .size(72.dp)
                                                .clip(CircleShape)
                                                .background(PrimeSurface),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(Icons.Default.BookmarkBorder, null, tint = PrimeTextDim, modifier = Modifier.size(36.dp))
                                        }
                                        Text("Your list is empty", color = TextPrimary, style = MaterialTheme.typography.titleMedium)
                                        Text(
                                            "Add movies and shows to watch them later",
                                            color = PrimeTextDim,
                                            style = MaterialTheme.typography.bodyMedium,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            }
                        } else {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                Text(
                                    "Saved",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold, color = TextPrimary),
                                    modifier = Modifier.padding(top = 8.dp)
                                )
                            }
                            items(state.movies.take(visibleCount), key = { it.id }) { media ->
                                PosterCard(
                                    posterUrl = ApiUtils.getPosterUrl(media),
                                    fallbackUrl = ApiUtils.getThumbnailUrl(media),
                                    title = media.title,
                                    subtitle = media.year?.toString(),
                                    onClick = { navController.navigate(Screen.Details.createRoute(media.id.toString())) },
                                    cacheKey = "poster_${media.id}",
                                    modifier = if (media == state.movies.first()) Modifier.focusRequester(gridFocusRequester) else Modifier
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
