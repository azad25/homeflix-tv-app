package com.homeflix.tv.presentation.screens.details

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.media3.common.util.UnstableApi
import com.homeflix.tv.core.PreviewPlayer
import com.homeflix.tv.domain.model.Media
import com.homeflix.tv.presentation.navigation.AppNavigator
import com.homeflix.tv.presentation.shell.ErrorState
import com.homeflix.tv.presentation.shell.LoadingState
import com.homeflix.tv.presentation.shell.SubPageTopBar
import com.homeflix.tv.presentation.ui.DetailHeader
import com.homeflix.tv.presentation.ui.LocalPreviewPlayer
import com.homeflix.tv.presentation.ui.ManualFocusScroll
import com.homeflix.tv.presentation.ui.MediaFormat
import com.homeflix.tv.presentation.ui.PillButton
import com.homeflix.tv.presentation.ui.PreviewLifecycle
import com.homeflix.tv.presentation.ui.TileRow
import com.homeflix.tv.presentation.ui.Tiles
import com.homeflix.tv.presentation.ui.Tv
import com.homeflix.tv.presentation.ui.glideTo
import com.homeflix.tv.presentation.ui.rememberFocusRegistry
import com.homeflix.tv.util.ApiUtils
import kotlinx.coroutines.launch

/** Movie title page: billboard header with trailer + "More Like This". Back returns to the exact tile. */
@UnstableApi
@Composable
fun DetailsScreen(
    mediaId: Int,
    navigator: AppNavigator,
    previewPlayer: PreviewPlayer,
    vm: DetailsViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsState()
    LaunchedEffect(mediaId) { vm.load(mediaId) }
    var resumed by rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (resumed) vm.refreshProgress()
        resumed = true
    }
    PreviewLifecycle(previewPlayer)

    CompositionLocalProvider(LocalPreviewPlayer provides previewPlayer) {
        Box(Modifier.fillMaxSize().background(Tv.Background)) {
            val media = state.media
            when {
                media != null -> DetailsContent(media, state, navigator, onMyList = vm::toggleMyList)
                state.loading -> LoadingState()
                else -> ErrorState(state.error ?: "Couldn't load this title.", onRetry = { vm.load(mediaId) })
            }
        }
    }
}

@UnstableApi
@Composable
private fun DetailsContent(media: Media, state: DetailsState, navigator: AppNavigator, onMyList: () -> Unit) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val registry = rememberFocusRegistry()
    val play = remember { FocusRequester() }
    var section by rememberSaveable { mutableIntStateOf(0) }
    LaunchedEffect(media.id) {
        withFrameNanos { }
        runCatching { play.requestFocus() }
    }
    val tile = remember(media) { Tiles.movie(media, "d") }
    val credits = remember(media) {
        buildList {
            val cast = (media.cast.ifEmpty { media.stars }).take(4)
            if (cast.isNotEmpty()) add("Starring: ${cast.joinToString(", ")}")
            if (media.director.isNotEmpty()) add("Director: ${media.director.take(2).joinToString(", ")}")
        }
    }
    Box(Modifier.fillMaxSize()) {
    ManualFocusScroll {
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            item(key = "header") {
                DetailHeader(
                    previewKey = "details:${media.id}",
                    previewUrl = tile.previewUrl,
                    previewActive = section == 0,
                    backdropUrl = ApiUtils.getBackdropUrl(media),
                    logoUrl = ApiUtils.getLogoUrl(media),
                    title = media.title,
                    meta = MediaFormat.metaLine(media),
                    description = MediaFormat.description(media),
                    progress = state.progress,
                    progressLabel = state.progressLabel,
                    credits = credits,
                    modifier = Modifier.onFocusChanged {
                        if (it.hasFocus) { section = 0; scope.launch { listState.glideTo(0) } }
                    }
                ) {
                    val resume = state.resumeMs > 0
                    PillButton(if (resume) "Resume" else "Play", { navigator.player(media.id, state.resumeMs) },
                        icon = Icons.Filled.PlayArrow, focusRequester = play)
                    if (resume) {
                        PillButton("Play from beginning", { navigator.player(media.id, 0, fromStart = true) }, icon = Icons.Filled.Replay)
                    }
                    state.inMyList?.let { inList ->
                        PillButton(if (inList) "In My List" else "My List", onMyList,
                            icon = if (inList) Icons.Filled.Check else Icons.Filled.Add)
                    }
                }
            }
            state.similar?.let { row ->
                item(key = "similar") {
                    TileRow(
                        row = row,
                        active = section == 1,
                        registry = registry,
                        actions = navigator,
                        onTileFocused = {},
                        modifier = Modifier.onFocusChanged {
                            if (it.hasFocus) { section = 1; scope.launch { listState.glideTo(1) } }
                        }
                    )
                }
            }
            item(key = "pad") { Spacer(Modifier.height(220.dp)) }
        }
    }
    SubPageTopBar(navigator, visible = section == 0)
    }
}
