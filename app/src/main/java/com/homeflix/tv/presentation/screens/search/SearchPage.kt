package com.homeflix.tv.presentation.screens.search

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SpaceBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.homeflix.tv.presentation.shell.ShellController
import com.homeflix.tv.presentation.ui.SizedImage
import com.homeflix.tv.presentation.ui.Tile
import com.homeflix.tv.presentation.ui.TileActions
import com.homeflix.tv.presentation.ui.Tv

private val KEYS = ("abcdefghijklmnopqrstuvwxyz1234567890").map { it.toString() }

/** Netflix-style search: on-screen keyboard + genres on the left, results grid on the right. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun SearchPage(shell: ShellController, actions: TileActions, vm: SearchViewModel = hiltViewModel()) {
    val state by vm.state.collectAsState()
    val firstKey = remember { FocusRequester() }

    LaunchedEffect(Unit) { shell.pageAtTop = true }
    LaunchedEffect(shell.restoreFocusPending) {
        if (!shell.restoreFocusPending) return@LaunchedEffect
        withFrameNanos { }
        runCatching { firstKey.requestFocus() }
        shell.enterAtTop = false
        shell.restoreFocusPending = false
    }

    Row(Modifier.fillMaxSize().background(Tv.Background).padding(top = Tv.NavHeight + 16.dp)) {
        // ---------------------------------------------------------------- keyboard + genres
        Column(Modifier.width(340.dp).fillMaxSize().padding(start = Tv.Keyline, end = 16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Tv.Surface)
                    .padding(horizontal = 12.dp)
            ) {
                Icon(Icons.Filled.Search, null, tint = Tv.TextMuted, modifier = Modifier.size(20.dp))
                Text(
                    state.query.ifEmpty { "Search movies & shows" },
                    color = if (state.query.isEmpty()) Tv.TextMuted else Color.White,
                    fontSize = 17.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 10.dp)
                )
            }
            Column(Modifier.padding(top = 12.dp).focusRestorer(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Key(icon = Icons.Filled.SpaceBar, label = "space", modifier = Modifier.weight(1f)) { vm.type(" ") }
                    Key(icon = Icons.AutoMirrored.Filled.Backspace, label = "delete", modifier = Modifier.weight(1f),
                        onLongClear = vm::clear) { vm.backspace() }
                }
                KEYS.chunked(6).forEachIndexed { r, rowKeys ->
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        rowKeys.forEachIndexed { c, k ->
                            Key(
                                text = k,
                                modifier = Modifier.weight(1f).then(if (r == 0 && c == 0) Modifier.focusRequester(firstKey) else Modifier)
                            ) { vm.type(k) }
                        }
                    }
                }
            }
            LazyColumn(Modifier.padding(top = 16.dp).fillMaxWidth(), contentPadding = PaddingValues(bottom = 40.dp)) {
                items(state.genres, key = { it }) { g ->
                    GenreItem(g, selected = state.genre == g) { vm.selectGenre(g) }
                }
            }
        }

        // ---------------------------------------------------------------- results
        Column(Modifier.weight(1f).fillMaxSize().padding(end = Tv.Keyline)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(state.title, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
                if (state.loading) {
                    CircularProgressIndicator(color = Tv.Red, strokeWidth = 3.dp, modifier = Modifier.padding(start = 14.dp).size(22.dp))
                }
            }
            val grid = rememberLazyGridState()
            LaunchedEffect(state.results) { grid.scrollToItem(0) }
            LazyVerticalGrid(
                state = grid,
                columns = GridCells.Fixed(5),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(top = 16.dp, bottom = 80.dp, end = 4.dp, start = 4.dp),
                modifier = Modifier.fillMaxSize().focusRestorer()
            ) {
                items(state.results, key = { it.key }) { tile -> ResultTile(tile) { actions.open(tile) } }
            }
        }
    }
}

@Composable
private fun Key(
    modifier: Modifier = Modifier,
    text: String? = null,
    icon: ImageVector? = null,
    label: String? = null,
    onLongClear: (() -> Unit)? = null,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .height(42.dp)
            .onFocusChanged { focused = it.isFocused }
            .clip(RoundedCornerShape(4.dp))
            .background(if (focused) Color.White else Color(0x14FFFFFF))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
    ) {
        if (icon != null) Icon(icon, contentDescription = label, tint = if (focused) Color.Black else Color.White, modifier = Modifier.size(22.dp))
        else Text(text.orEmpty(), color = if (focused) Color.Black else Color.White, fontSize = 19.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun GenreItem(name: String, selected: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Text(
        name,
        color = when { focused -> Color.Black; selected -> Color.White; else -> Tv.TextSecondary },
        fontSize = 17.sp,
        fontWeight = if (selected || focused) FontWeight.SemiBold else FontWeight.Normal,
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { focused = it.isFocused }
            .clip(RoundedCornerShape(4.dp))
            .background(if (focused) Color.White else Color.Transparent)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 9.dp)
    )
}

@Composable
private fun ResultTile(tile: Tile, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.06f else 1f, tween(Tv.FocusMotionMs, easing = Tv.Ease), label = "s")
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(2f / 3f)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .onFocusChanged { focused = it.isFocused }
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .clip(RoundedCornerShape(Tv.TileCorner))
            .background(Tv.Surface)
            .then(if (focused) Modifier.border(3.dp, Color.White, RoundedCornerShape(Tv.TileCorner)) else Modifier)
    ) {
        SizedImage(tile.posterUrl, 150.dp, 225.dp, Modifier.fillMaxSize())
        if (focused) {
            Text(
                tile.title,
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .background(Tv.BottomScrim)
                    .padding(8.dp)
            )
        }
        tile.badge?.let {
            if (!focused) Text(it, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 6.dp)
                    .clip(RoundedCornerShape(3.dp)).background(Tv.Red).padding(horizontal = 5.dp, vertical = 2.dp))
        }
    }
}
