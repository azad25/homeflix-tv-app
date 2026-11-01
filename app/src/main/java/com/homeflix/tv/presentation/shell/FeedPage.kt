package com.homeflix.tv.presentation.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.media3.common.util.UnstableApi
import com.homeflix.tv.presentation.screens.feeds.FeedState
import com.homeflix.tv.presentation.ui.BrowseFeed
import com.homeflix.tv.presentation.ui.PillButton
import com.homeflix.tv.presentation.ui.Tile
import com.homeflix.tv.presentation.ui.TileActions
import com.homeflix.tv.presentation.ui.Tv
import com.homeflix.tv.presentation.ui.rememberFocusRegistry

/**
 * A tab page backed by a [FeedState]. Remembers (across tab switches and
 * trips to Details/Player) which tile had focus and restores it exactly.
 */
@UnstableApi
@Composable
fun FeedPage(
    state: FeedState,
    shell: ShellController,
    actions: TileActions,
    onRetry: () -> Unit,
    onResumed: () -> Unit,
    heroPlayLabel: (Tile) -> String = { "Play" }
) {
    val listState = rememberLazyListState()
    val registry = rememberFocusRegistry()
    val heroPlay = remember { FocusRequester() }
    var activeSection by rememberSaveable { mutableIntStateOf(0) }
    var savedFocus by rememberSaveable { mutableStateOf<String?>(null) }
    var resumedOnce by rememberSaveable { mutableStateOf(false) }

    // Coming back from the player/details: refresh progress rows quietly.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (resumedOnce) onResumed()
        resumedOnce = true
    }

    LaunchedEffect(activeSection) { shell.pageAtTop = activeSection == 0 }

    // Focus moved up into the nav: glide the page back to the top (Netflix "Back → menu").
    LaunchedEffect(shell.navFocused) {
        if (shell.navFocused) {
            activeSection = 0
            if (listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0) {
                listState.animateScrollToItem(0)
            }
        }
    }

    // ▼ from the top nav enters this page at its billboard (or first tile).
    LaunchedEffect(state.feed.hero != null, state.feed.rows.firstOrNull()?.id) {
        shell.pageEntry = when {
            state.feed.hero != null -> heroPlay
            else -> state.feed.rows.firstOrNull()?.let { r -> r.tiles.firstOrNull()?.let { registry.requesterFor("${r.id}|${it.key}") } }
        }
    }

    // Put focus back where the user left it (launch, return from a screen, OK on a tab).
    val ready = !state.feed.isEmpty
    LaunchedEffect(shell.restoreFocusPending, ready) {
        if (!shell.restoreFocusPending || !ready) return@LaunchedEffect
        withFrameNanos { }
        withFrameNanos { }
        val atTop = shell.enterAtTop
        shell.enterAtTop = false
        if (atTop && listState.firstVisibleItemIndex > 0) listState.scrollToItem(0)
        val restored = (!atTop && registry.tryFocus(savedFocus)) ||
            (state.feed.hero != null && runCatching { heroPlay.requestFocus(); true }.getOrDefault(false)) ||
            registry.tryFocus(state.feed.rows.firstOrNull()?.let { r -> r.tiles.firstOrNull()?.let { "${r.id}|${it.key}" } })
        if (!restored) shell.focusNav()
        shell.restoreFocusPending = false
    }

    Box(Modifier.fillMaxSize().background(Tv.Background)) {
        when {
            ready -> BrowseFeed(
                feed = state.feed,
                listState = listState,
                registry = registry,
                heroPlayRequester = heroPlay,
                activeSection = if (shell.navFocused) 0 else activeSection,
                onSectionFocused = { activeSection = it },
                onTileFocused = { savedFocus = it },
                actions = actions,
                heroPlayLabel = heroPlayLabel
            )
            state.loading -> LoadingState()
            else -> ErrorState(state.error ?: "Nothing to show", onRetry)
        }
    }
}

@Composable
fun LoadingState(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = Tv.Red, strokeWidth = 4.dp, modifier = Modifier.size(52.dp))
    }
}

@Composable
fun ErrorState(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    val retry = remember { FocusRequester() }
    LaunchedEffect(Unit) { withFrameNanos { }; runCatching { retry.requestFocus() } }
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Text(message, color = Tv.TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
            PillButton("Try Again", onRetry, icon = Icons.Filled.Refresh, focusRequester = retry,
                modifier = Modifier.padding(top = 4.dp))
        }
    }
}
