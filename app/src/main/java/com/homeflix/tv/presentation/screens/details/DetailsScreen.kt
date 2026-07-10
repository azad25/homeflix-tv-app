package com.homeflix.tv.presentation.screens.details

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import com.homeflix.tv.presentation.components.SidebarOverlay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.homeflix.tv.presentation.components.BackgroundVideo
import com.homeflix.tv.presentation.components.CertBadge
import com.homeflix.tv.presentation.components.HeroActionButton
import com.homeflix.tv.presentation.components.MediaRow
import com.homeflix.tv.presentation.components.ThumbLogoRow
import com.homeflix.tv.presentation.components.QualityBadge
import com.homeflix.tv.presentation.navigation.Screen
import com.homeflix.tv.presentation.theme.NetflixRed
import com.homeflix.tv.presentation.theme.*
import com.homeflix.tv.util.ApiUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * MOVIE DETAILS - Prime Video style full-screen info page.
 *
 * Full-bleed backdrop that fades into an auto-playing muted preview clip,
 * strong left gradient, metadata badges, resume-aware Play action and a
 * "More like this" row.
 */
@UnstableApi
@Composable
fun DetailsScreen(
    mediaId: String,
    navController: NavController,
    viewModel: DetailsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val isInMyList by viewModel.isInMyList.collectAsState()
    val similar by viewModel.similar.collectAsState()

    val playFocusRequester = remember { FocusRequester() }
    val detailListState = androidx.compose.foundation.lazy.rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    // Overlay nav rail (LEFT/Back reveals it over the cinematic hero)
    var showSidebar by remember { mutableStateOf(false) }

    // Reload progress when returning from the player
    val lifecycleOwner = LocalLifecycleOwner.current
    val lifecycleState by lifecycleOwner.lifecycle.currentStateFlow.collectAsState()
    var resumeCount by remember { mutableIntStateOf(0) }
    LaunchedEffect(lifecycleState) {
        if (lifecycleState == Lifecycle.State.RESUMED) {
            resumeCount++
            if (resumeCount > 1) viewModel.loadMediaDetails(mediaId)
        }
    }

    LaunchedEffect(mediaId) {
        viewModel.loadMediaDetails(mediaId)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PrimeBg)
            .onKeyEvent { keyEvent ->
                // NOTE: LEFT is NOT intercepted here — a root LEFT handler
                // consumed every left press and broke in-row navigation
                // (More Like This). The sidebar opens from the leftmost
                // focusables instead (Play button / first row card).
                if (keyEvent.type == KeyEventType.KeyDown) {
                    when (keyEvent.key) {
                        Key.Back -> {
                            if (showSidebar) { showSidebar = false; true } else false
                        }
                        else -> false
                    }
                } else false
            }
    ) {
        when (val state = uiState) {
            is DetailsUiState.Success -> {
                val media = state.media

                LaunchedEffect(media.id) {
                    // Focus Play; the hero item is exactly viewport-height so
                    // focusing it never scrolls the video out of view.
                    delay(350)
                    repeat(8) {
                        try { playFocusRequester.requestFocus(); return@repeat } catch (_: Exception) { delay(40) }
                    }
                    delay(60)
                    detailListState.scrollToItem(0)
                }

                // NETFLIX LAYOUT: the video is a FIXED full-bleed layer behind
                // the content — scrolling and focus can never clip or push it.
                // Content (info + rows) scrolls OVER it.
                Box(Modifier.fillMaxSize()) {
                    // Backdrop -> auto-playing muted preview (fixed layer)
                    BackgroundVideo(
                        backdropUrl = ApiUtils.getBackdropUrl(media),
                        videoUrl = ApiUtils.getPreviewClipUrl(media.id),
                        startDelayMs = 2200,
                        contentDescription = media.title
                    )
                    // Clean video at the top; bottom blends into page black
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        PrimeBg.copy(alpha = 0.6f),
                                        PrimeBg
                                    ),
                                    startY = 500f
                                )
                            )
                    )
                }

                LazyColumn(state = detailListState, modifier = Modifier.fillMaxSize()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .fillParentMaxHeight()
                        ) {
                            // Info column
                            Column(
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(start = 48.dp, bottom = 36.dp, end = 480.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                // Logo or title
                                val logoUrl = ApiUtils.getLogoUrl(media)
                                var logoOk by remember(media.id) { mutableStateOf(logoUrl != null) }
                                if (logoOk && logoUrl != null) {
                                    AsyncImage(
                                        model = coil.request.ImageRequest.Builder(LocalContext.current)
                                            .data(logoUrl)
                                            .memoryCacheKey("logo_${media.id}")
                                            .diskCacheKey("logo_${media.id}")
                                            .build(),
                                        contentDescription = media.title,
                                        modifier = Modifier
                                            .heightIn(max = 120.dp)
                                            .widthIn(max = 440.dp),
                                        contentScale = ContentScale.Fit,
                                        onError = { logoOk = false }
                                    )
                                } else {
                                    Text(
                                        text = media.title,
                                        style = MaterialTheme.typography.displaySmall.copy(
                                            fontWeight = FontWeight.Black,
                                            color = TextPrimary
                                        ),
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                media.tagline?.takeIf { it.isNotBlank() }?.let {
                                    Text(
                                        text = it,
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            color = NetflixRed,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    )
                                }

                                // Metadata badges row
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (media.rating > 0) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Text("★", color = RatingGold, style = MaterialTheme.typography.titleSmall)
                                            Text(
                                                String.format("%.1f", media.rating),
                                                color = TextPrimary,
                                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
                                            )
                                            if (media.voteCount > 0) {
                                                Text(
                                                    "(${media.voteCount})",
                                                    color = PrimeTextDim,
                                                    style = MaterialTheme.typography.bodySmall
                                                )
                                            }
                                        }
                                    }
                                    media.year?.let {
                                        Text(it.toString(), color = PrimeTextDim, style = MaterialTheme.typography.titleSmall)
                                    }
                                    val mins = media.runtime ?: (media.duration / 60)
                                    if (mins > 0) {
                                        Text(
                                            "${mins / 60}h ${mins % 60}m",
                                            color = PrimeTextDim,
                                            style = MaterialTheme.typography.titleSmall
                                        )
                                    }
                                    media.certification?.takeIf { it.isNotBlank() }?.let { CertBadge(it) }
                                    QualityBadge(media.quality)
                                }

                                if (media.genreNames.isNotEmpty()) {
                                    Text(
                                        media.genreNames.take(4).joinToString("  •  "),
                                        color = PrimeTextDim,
                                        style = MaterialTheme.typography.titleSmall
                                    )
                                }

                                // Description (smaller + tighter line spacing)
                                media.description?.let { desc ->
                                    Text(
                                        text = desc,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            color = TextPrimary.copy(alpha = 0.88f),
                                            fontSize = 14.sp,
                                            lineHeight = 20.sp
                                        ),
                                        maxLines = 3,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                // Director / cast (Prime shows these under the synopsis)
                                if (media.director.isNotEmpty()) {
                                    MetaLine("Director", media.director.take(2).joinToString(", "))
                                }
                                val castLine = media.cast.ifEmpty { media.stars }
                                if (castLine.isNotEmpty()) {
                                    MetaLine("Starring", castLine.take(4).joinToString(", "))
                                }

                                // Resume progress
                                state.watchProgress?.let { progress ->
                                    if (progress > 0.01f && progress < 0.97f) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            LinearProgressIndicator(
                                                progress = { progress },
                                                color = PrimeBlue,
                                                trackColor = PrimeSurface,
                                                modifier = Modifier
                                                    .width(220.dp)
                                                    .height(4.dp)
                                            )
                                            Text(
                                                "${(progress * 100).toInt()}% watched",
                                                color = PrimeTextDim,
                                                style = MaterialTheme.typography.labelMedium
                                            )
                                        }
                                    }
                                }

                                // Actions
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    modifier = Modifier.padding(top = 4.dp)
                                ) {
                                    val canResume = (state.progressSeconds ?: 0L) > 30 &&
                                        (state.watchProgress ?: 0f) < 0.97f
                                    HeroActionButton(
                                        label = if (canResume) "Resume" else "Play",
                                        icon = { Icon(Icons.Default.PlayArrow, null, Modifier.size(20.dp)) },
                                        primary = true,
                                        focusRequester = playFocusRequester,
                                        onClick = {
                                            val start = if (canResume) (state.progressSeconds ?: 0L) * 1000 else 0L
                                            navController.navigate(
                                                Screen.VideoPlayer.createRoute(media.id, startTime = start)
                                            )
                                        },
                                        // Leftmost focusable: LEFT reveals the nav rail
                                        onNavigateLeft = { showSidebar = true }
                                    )
                                    if (canResume) {
                                        HeroActionButton(
                                            label = "Start Over",
                                            icon = { Icon(Icons.Default.Refresh, null, Modifier.size(20.dp)) },
                                            primary = false,
                                            onClick = {
                                                navController.navigate(
                                                    Screen.VideoPlayer.createRoute(
                                                        media.id,
                                                        forceStartFromBeginning = true
                                                    )
                                                )
                                            }
                                        )
                                    }
                                    HeroActionButton(
                                        label = if (isInMyList) "In My List" else "My List",
                                        icon = {
                                            Icon(
                                                if (isInMyList) Icons.Default.Check else Icons.Default.Add,
                                                null,
                                                Modifier.size(20.dp)
                                            )
                                        },
                                        primary = false,
                                        onClick = { viewModel.addToMyList(media.id.toString()) }
                                    )
                                }
                            }
                        }
                    }

                    // More like this — solid background so it reads as a sheet
                    // sliding over the video when focused/scrolled up.
                    if (similar.isNotEmpty()) {
                        item {
                            Box(Modifier.background(PrimeBg)) {
                                ThumbLogoRow(
                                    title = "More like this",
                                    mediaList = similar,
                                    onMediaClick = { m ->
                                        navController.navigate(Screen.Details.createRoute(m.id.toString()))
                                    },
                                    onNavigateLeftAtStart = { showSidebar = true },
                                    modifier = Modifier.padding(top = 16.dp, bottom = 48.dp)
                                )
                            }
                        }
                    }
                }
            }

            is DetailsUiState.Loading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = PrimeBlue, strokeWidth = 4.dp)
                }
            }

            is DetailsUiState.Error -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            "Couldn't load this title",
                            style = MaterialTheme.typography.headlineSmall,
                            color = TextPrimary
                        )
                        Text(state.message, color = PrimeTextDim)
                        Button(
                            onClick = { viewModel.loadMediaDetails(mediaId) },
                            colors = ButtonDefaults.buttonColors(containerColor = PrimeBlue)
                        ) { Text("Try Again") }
                    }
                }
            }
        }

        // Overlay nav rail (LEFT reveals it, RIGHT/Back dismisses)
        SidebarOverlay(
            visible = showSidebar,
            selectedRoute = "",
            onNavigate = { route -> navController.navigate(route) },
            onDismiss = { showSidebar = false }
        )
    }
}

@Composable
private fun MetaLine(label: String, value: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "$label:",
            color = PrimeTextDim,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
        )
        Text(
            value,
            color = TextPrimary.copy(alpha = 0.9f),
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
