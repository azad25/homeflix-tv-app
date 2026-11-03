package com.homeflix.tv.presentation.screens.tvshows

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.homeflix.tv.comingsoon.ComingSoonActions
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.media3.common.util.UnstableApi
import com.homeflix.tv.core.PreviewPlayer
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
import com.homeflix.tv.presentation.ui.Tile
import com.homeflix.tv.presentation.ui.TileActions
import com.homeflix.tv.presentation.ui.TileRow
import com.homeflix.tv.presentation.ui.Tiles
import com.homeflix.tv.presentation.ui.Tv
import com.homeflix.tv.presentation.ui.glideTo
import com.homeflix.tv.presentation.ui.rememberFocusRegistry
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Show title page: billboard, Play/Resume, season picker and the episode row. */
@UnstableApi
@Composable
fun TvSeriesDetailsScreen(
    seriesId: Int,
    navigator: AppNavigator,
    previewPlayer: PreviewPlayer,
    vm: TvSeriesDetailsViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsState()
    LaunchedEffect(seriesId) { vm.load(seriesId) }
    var resumed by rememberSaveable { mutableStateOf(false) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (resumed) vm.refreshProgress()
        resumed = true
    }
    PreviewLifecycle(previewPlayer)

    CompositionLocalProvider(LocalPreviewPlayer provides previewPlayer) {
        Box(Modifier.fillMaxSize().background(Tv.Background)) {
            val series = state.series
            when {
                series != null -> SeriesContent(series, state, navigator, onSeason = vm::selectSeason, onMyList = vm::toggleMyList, onRate = vm::rate)
                state.loading -> LoadingState()
                else -> ErrorState(state.error ?: "Couldn't load this show.", onRetry = { vm.load(seriesId) })
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@UnstableApi
@Composable
private fun SeriesContent(series: TvSeries, state: SeriesState, navigator: AppNavigator, onSeason: (Int) -> Unit, onMyList: () -> Unit, onRate: (Int) -> Unit) {
    val csVm: com.homeflix.tv.comingsoon.ComingSoonPanelViewModel = androidx.hilt.navigation.compose.hiltViewModel()
    LaunchedEffect(series.tmdbId) { csVm.load("tv", series.tmdbId) }
    val comingSoon by csVm.item.collectAsState()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val registry = rememberFocusRegistry()
    val play = remember { FocusRequester() }
    var section by rememberSaveable { mutableIntStateOf(0) }
    var savedFocus by rememberSaveable { mutableStateOf<String?>(null) }
    val tile = remember(series) { Tiles.series(series, "sd") }

    LaunchedEffect(series.id) {
        withFrameNanos { }
        if (!registry.tryFocus(savedFocus)) runCatching { play.requestFocus() }
    }

    // Episodes always play directly (with their own resume point).
    val episodeActions = remember(navigator) {
        object : TileActions {
            override fun open(tile: Tile) = navigator.player(tile.id, tile.resumeMs)
            override fun play(tile: Tile) = navigator.player(tile.id, tile.resumeMs)
        }
    }

    Box(Modifier.fillMaxSize()) {
    ManualFocusScroll {
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            item(key = "header") {
                val resume = state.resume
                DetailHeader(
                    previewKey = "series:${series.id}",
                    // Series have no trailer of their own: preview the newest uploaded episode.
                    previewUrl = (state.previewEpisodeId ?: state.resume?.episodeId ?: state.firstEpisodeId)
                        ?.let { com.homeflix.tv.util.ApiUtils.getPreviewClipUrl(it) },
                    previewActive = section == 0,
                    backdropUrl = tile.backdropUrl,
                    logoUrl = tile.logoUrl,
                    title = series.title,
                    meta = tile.meta,
                    description = tile.description,
                    progress = resume?.progress,
                    progressLabel = resume?.let { MediaFormat.episodeLabel(it.season, it.episode, null) },
                    peek = 150.dp,
                    modifier = Modifier.onFocusChanged {
                        if (it.hasFocus) { section = 0; scope.launch { listState.glideTo(0) } }
                    }
                ) {
                    if (resume != null) {
                        PillButton(
                            "Resume " + MediaFormat.episodeLabel(resume.season, resume.episode, null),
                            { navigator.player(resume.episodeId, resume.positionMs) },
                            icon = Icons.Filled.PlayArrow, focusRequester = play
                        )
                        state.firstEpisodeId?.let { first ->
                            PillButton("Play from S1:E1", { navigator.player(first, 0, fromStart = true) }, icon = Icons.Filled.Replay)
                        }
                    } else {
                        PillButton("Play", { state.firstEpisodeId?.let { navigator.player(it, 0) } },
                            icon = Icons.Filled.PlayArrow, focusRequester = play)
                    }
                    state.inMyList?.let { inList ->
                        PillButton(if (inList) "In My List" else "My List", onMyList,
                            icon = if (inList) Icons.Filled.Check else Icons.Filled.Add)
                    }
                    com.homeflix.tv.presentation.ui.RateButton(state.rating, onRate)
                    // A new season on the way: its trailer and Notify Me.
                    comingSoon?.let { cs -> ComingSoonActions(cs, { navigator.comingSoon(cs.key) }, csVm::toggleNotify) }
                }
            }
            if (state.seasons.size > 1) {
                item(key = "seasons") {
                    SeasonPicker(
                        seasons = state.seasons,
                        selected = state.selectedSeason,
                        onSelect = onSeason,
                        modifier = Modifier.onFocusChanged {
                            if (it.hasFocus) { section = 1; scope.launch { listState.glideTo(1) } }
                        }
                    )
                }
            }
            state.episodes?.let { row ->
                val idx = if (state.seasons.size > 1) 2 else 1
                item(key = "episodes") {
                    TileRow(
                        row = row,
                        active = section == idx,
                        registry = registry,
                        actions = episodeActions,
                        onTileFocused = { savedFocus = it },
                        topPadding = 6.dp,
                        modifier = Modifier.onFocusChanged {
                            if (it.hasFocus) { section = idx; scope.launch { listState.glideTo(idx) } }
                        }
                    )
                }
            }
            item(key = "pad") { Spacer(Modifier.height(240.dp)) }
        }
    }
    SubPageTopBar(navigator, visible = section == 0)
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun SeasonPicker(seasons: List<Season>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    var focusedSeason by remember { mutableStateOf<Int?>(null) }
    // Switch season as focus rests on a chip (debounced so sweeping is cheap).
    LaunchedEffect(focusedSeason) {
        val s = focusedSeason ?: return@LaunchedEffect
        delay(350)
        if (s != selected) onSelect(s)
    }
    Column(modifier.fillMaxWidth().padding(top = 8.dp)) {
        // The page scrolls itself (ManualFocusScroll); the chips must still follow focus.
        com.homeflix.tv.presentation.ui.SmoothFocusScroll {
        LazyRow(
            contentPadding = PaddingValues(horizontal = Tv.Keyline),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.focusRestorer()
        ) {
            items(seasons, key = { it.seasonNumber }) { season ->
                var focused by remember { mutableStateOf(false) }
                val isSelected = season.seasonNumber == selected
                Text(
                    season.name.takeIf { it.isNotBlank() } ?: "Season ${season.seasonNumber}",
                    color = when { focused -> Color.Black; isSelected -> Color.White; else -> Tv.TextSecondary },
                    fontSize = 16.sp,
                    fontWeight = if (isSelected || focused) FontWeight.SemiBold else FontWeight.Medium,
                    modifier = Modifier
                        .onFocusChanged {
                            focused = it.isFocused
                            if (it.isFocused) focusedSeason = season.seasonNumber
                        }
                        .clip(RoundedCornerShape(50))
                        .background(when { focused -> Color.White; isSelected -> Color(0x40FFFFFF); else -> Color.Transparent })
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                            onSelect(season.seasonNumber)
                        }
                        .padding(horizontal = 18.dp, vertical = 8.dp)
                )
            }
        }
        }
    }
}
