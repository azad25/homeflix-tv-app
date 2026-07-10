package com.homeflix.tv.presentation.screens.tvshows

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.homeflix.tv.presentation.components.CertBadge
import com.homeflix.tv.presentation.components.ContinueWatchingRow
import com.homeflix.tv.presentation.components.SidebarOverlay
import com.homeflix.tv.presentation.components.HeroActionButton
import com.homeflix.tv.presentation.navigation.Screen
import com.homeflix.tv.presentation.theme.*
import com.homeflix.tv.util.ApiUtils
import kotlinx.coroutines.delay

/**
 * TV SERIES DETAILS - Prime Video style.
 *
 * Full-bleed backdrop hero with series info, then an inline season selector
 * and Prime-style episode tiles (16:9 still, number, title, duration,
 * synopsis) - no extra navigation hop to see episodes.
 */
@UnstableApi
@Composable
fun TvSeriesDetailsScreen(
    seriesId: String,
    navController: NavController,
    viewModel: TvSeriesDetailsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val episodes by viewModel.episodes.collectAsState()
    val selectedSeason by viewModel.selectedSeason.collectAsState()
    val episodesLoading by viewModel.episodesLoading.collectAsState()
    val continueWatching by viewModel.continueWatching.collectAsState()

    val playFocusRequester = remember { FocusRequester() }
    // Hoisted above the when(state) so episode-list scroll survives returning
    // from the player (screen stays on the back stack).
    val listState = rememberLazyListState()
    // Overlay nav rail (LEFT/Back reveals it over the cinematic hero)
    var showSidebar by remember { mutableStateOf(false) }

    LaunchedEffect(seriesId) {
        viewModel.loadSeriesDetails(seriesId)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PrimeBg)
            .onKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown) {
                    when (keyEvent.key) {
                        Key.DirectionLeft -> {
                            if (!showSidebar) { showSidebar = true; true } else false
                        }
                        Key.Back -> {
                            if (showSidebar) { showSidebar = false; true } else false
                        }
                        else -> false
                    }
                } else false
            }
    ) {
        when (val state = uiState) {
            is TvSeriesDetailsUiState.Success -> {
                val series = state.series

                LaunchedEffect(series.id) {
                    // Focus Play, then pin to top so the hero + focus don't leave
                    // the page scrolled into the episode list.
                    delay(350)
                    repeat(8) {
                        try { playFocusRequester.requestFocus(); return@repeat } catch (_: Exception) { delay(40) }
                    }
                    delay(60)
                    listState.scrollToItem(0)
                }

                LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                    // ── HERO ─────────────────────────────────────────────
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(440.dp)
                        ) {
                            AsyncImage(
                                model = coil.request.ImageRequest.Builder(LocalContext.current)
                                    .data(ApiUtils.getSeriesBackdropUrl(series))
                                    .memoryCacheKey("series_backdrop_${series.id}")
                                    .diskCacheKey("series_backdrop_${series.id}")
                                    .crossfade(true)
                                    .build(),
                                contentDescription = series.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                            Box(
                                Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.horizontalGradient(
                                            colors = listOf(
                                                PrimeBgDeep.copy(alpha = 0.96f),
                                                PrimeBgDeep.copy(alpha = 0.6f),
                                                Color.Transparent
                                            ),
                                            endX = 1500f
                                        )
                                    )
                            )
                            Box(
                                Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.verticalGradient(
                                            colors = listOf(Color.Transparent, PrimeBg),
                                            startY = 700f
                                        )
                                    )
                            )

                            Column(
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(start = 56.dp, bottom = 36.dp, end = 480.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(
                                    text = "HOMEFLIX ORIGINAL SERIES",
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        color = NetflixRed,
                                        fontWeight = FontWeight.Black,
                                        letterSpacing = 3.sp
                                    )
                                )

                                // Series logo or title
                                var logoOk by remember(series.id) { mutableStateOf(true) }
                                if (logoOk) {
                                    AsyncImage(
                                        model = ApiUtils.getSeriesLogoUrl(series.id),
                                        contentDescription = series.title,
                                        modifier = Modifier
                                            .heightIn(max = 110.dp)
                                            .widthIn(max = 420.dp),
                                        contentScale = ContentScale.Fit,
                                        onError = { logoOk = false }
                                    )
                                } else {
                                    Text(
                                        text = series.title,
                                        style = MaterialTheme.typography.displaySmall.copy(
                                            fontWeight = FontWeight.Black,
                                            color = TextPrimary
                                        ),
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                // Metadata
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (series.rating > 0) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Text("★", color = RatingGold, style = MaterialTheme.typography.titleSmall)
                                            Text(
                                                String.format("%.1f", series.rating),
                                                color = TextPrimary,
                                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
                                            )
                                        }
                                    }
                                    series.year?.let {
                                        Text(it.toString(), color = PrimeTextDim, style = MaterialTheme.typography.titleSmall)
                                    }
                                    Text(
                                        "${series.totalSeasons} Season${if (series.totalSeasons != 1) "s" else ""}",
                                        color = PrimeTextDim,
                                        style = MaterialTheme.typography.titleSmall
                                    )
                                    Text(
                                        "${series.totalEpisodes} Episodes",
                                        color = PrimeTextDim,
                                        style = MaterialTheme.typography.titleSmall
                                    )
                                    if (series.genres.isNotEmpty()) {
                                        Text(
                                            series.genres.take(2).joinToString(" • "),
                                            color = PrimeTextDim,
                                            style = MaterialTheme.typography.titleSmall
                                        )
                                    }
                                    CertBadge("TV-MA")
                                }

                                series.description?.let { desc ->
                                    Text(
                                        text = desc,
                                        style = MaterialTheme.typography.bodyLarge.copy(
                                            color = TextPrimary.copy(alpha = 0.92f),
                                            lineHeight = 24.sp
                                        ),
                                        maxLines = 3,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                // Play first episode of the selected season
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    HeroActionButton(
                                        label = "Play S${selectedSeason} E1",
                                        icon = { Icon(Icons.Default.PlayArrow, null, Modifier.size(26.dp)) },
                                        primary = true,
                                        focusRequester = playFocusRequester,
                                        onClick = {
                                            episodes.firstOrNull()?.let { ep ->
                                                navController.navigate(Screen.VideoPlayer.createRoute(ep.id))
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // ── CONTINUE WATCHING (this series) ──────────────────
                    if (continueWatching.isNotEmpty()) {
                        item {
                            ContinueWatchingRow(
                                continueWatchingItems = continueWatching,
                                onPlay = { media, startMs ->
                                    navController.navigate(Screen.VideoPlayer.createRoute(media.id, startTime = startMs))
                                },
                                onInfo = { /* already on the series page */ },
                                mediaTypeFilter = null,
                                modifier = Modifier.padding(top = 8.dp, bottom = 8.dp)
                            )
                        }
                    }

                    // ── SEASON SELECTOR ──────────────────────────────────
                    item {
                        Column(modifier = Modifier.padding(top = 8.dp)) {
                            Text(
                                text = "Episodes",
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                ),
                                modifier = Modifier.padding(start = 56.dp, bottom = 12.dp)
                            )
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 56.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                items(state.seasons) { season ->
                                    SeasonChip(
                                        label = "Season ${season.seasonNumber}",
                                        selected = season.seasonNumber == selectedSeason,
                                        onClick = { viewModel.selectSeason(seriesId, season.seasonNumber) }
                                    )
                                }
                            }
                        }
                    }

                    // ── EPISODE TILES (Prime style) ─────────────────────
                    if (episodesLoading) {
                        item {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .height(200.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(color = PrimeBlue)
                            }
                        }
                    } else {
                        itemsIndexed(episodes) { index, episode ->
                            EpisodeTile(
                                episode = episode,
                                episodeNumber = index + 1,
                                onClick = {
                                    navController.navigate(Screen.VideoPlayer.createRoute(episode.id))
                                },
                                modifier = Modifier.padding(
                                    start = 56.dp, end = 56.dp,
                                    top = if (index == 0) 18.dp else 10.dp
                                )
                            )
                        }
                        item { Spacer(Modifier.height(56.dp)) }
                    }
                }
            }

            is TvSeriesDetailsUiState.Loading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = PrimeBlue, strokeWidth = 4.dp)
                }
            }

            is TvSeriesDetailsUiState.Error -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            "Couldn't load this series",
                            style = MaterialTheme.typography.headlineSmall,
                            color = TextPrimary
                        )
                        Text(state.message, color = PrimeTextDim)
                        Button(
                            onClick = { viewModel.loadSeriesDetails(seriesId) },
                            colors = ButtonDefaults.buttonColors(containerColor = PrimeBlue)
                        ) { Text("Try Again") }
                    }
                }
            }
        }

        // Overlay nav rail (LEFT reveals it, RIGHT/Back dismisses)
        SidebarOverlay(
            visible = showSidebar,
            selectedRoute = "tv-shows",
            onNavigate = { route -> navController.navigate(route) },
            onDismiss = { showSidebar = false }
        )
    }
}

@Composable
private fun SeasonChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val bg = when {
        focused -> PrimeBlue
        selected -> PrimeSurfaceHigh
        else -> PrimeSurface
    }

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = bg,
        border = if (selected && !focused) androidx.compose.foundation.BorderStroke(1.dp, PrimeBlue) else null,
        modifier = Modifier
            .onFocusChanged { focused = it.isFocused }
            .onKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown &&
                    (keyEvent.key == Key.Enter || keyEvent.key == Key.DirectionCenter)
                ) {
                    onClick(); true
                } else false
            }
            .focusable()
            .clickable { onClick() }
    ) {
        Text(
            text = label,
            color = if (focused || selected) TextPrimary else PrimeTextDim,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
        )
    }
}

/**
 * Prime-style episode tile: 16:9 still on the left, episode number + title,
 * duration and synopsis on the right. Whole tile is one focus target.
 */
@Composable
private fun EpisodeTile(
    episode: Episode,
    episodeNumber: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.015f else 1f, tween(150), label = "ep_scale")

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (focused) PrimeSurfaceHigh else Color.Transparent,
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer(scaleX = scale, scaleY = scale)
            .onFocusChanged { focused = it.isFocused }
            .onKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown &&
                    (keyEvent.key == Key.Enter || keyEvent.key == Key.DirectionCenter)
                ) {
                    onClick(); true
                } else false
            }
            .focusable()
            .clickable { onClick() }
            .then(
                if (focused) Modifier.border(2.dp, Color.White, RoundedCornerShape(10.dp))
                else Modifier
            )
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // Episode still with play glyph on focus
            Box(
                modifier = Modifier
                    .width(232.dp)
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(6.dp))
            ) {
                // Always hit the auto-downloading /episode-stills endpoint;
                // swap to the thumbnail exactly once if it truly 404s.
                var stillFailed by remember(episode.id) { mutableStateOf(false) }
                val stillModel = if (stillFailed)
                    ApiUtils.getEpisodeThumbnailUrl(episode)
                else
                    ApiUtils.getEpisodeStillUrl(episode.id)
                AsyncImage(
                    model = coil.request.ImageRequest.Builder(LocalContext.current)
                        .data(stillModel)
                        .memoryCacheKey(if (stillFailed) "episode_thumb_${episode.id}" else "episode_still_${episode.id}")
                        .diskCacheKey(if (stillFailed) "episode_thumb_${episode.id}" else "episode_still_${episode.id}")
                        .crossfade(true)
                        .build(),
                    onError = { if (!stillFailed) stillFailed = true },
                    contentDescription = episode.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                if (focused) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.35f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.PlayArrow,
                            contentDescription = "Play",
                            tint = Color.White,
                            modifier = Modifier.size(44.dp)
                        )
                    }
                }
            }

            // Episode info
            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "$episodeNumber. ${episode.episodeTitle ?: episode.title}",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    episode.duration?.takeIf { it > 0 }?.let { mins ->
                        Text(
                            "${mins}min",
                            color = PrimeTextDim,
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
                episode.description?.takeIf { it.isNotBlank() }?.let { desc ->
                    Text(
                        text = desc,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = TextPrimary.copy(alpha = 0.8f)
                        ),
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
