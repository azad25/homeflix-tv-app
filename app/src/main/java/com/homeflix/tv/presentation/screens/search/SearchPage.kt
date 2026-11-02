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
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.focus.focusProperties
import com.homeflix.tv.presentation.ui.throttleKeyRepeat
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
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

/** Compact QWERTY layout: numbers, three letter rows (offset like a real keyboard). */
private val QWERTY = listOf("1234567890", "qwertyuiop", "asdfghjkl", "zxcvbnm")

/** Netflix-style search: on-screen keyboard + genres on the left, results grid on the right. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun SearchPage(shell: ShellController, actions: TileActions, vm: SearchViewModel = hiltViewModel()) {
    val state by vm.state.collectAsState()
    SearchContent(
        state = state,
        shell = shell,
        actions = actions,
        onType = vm::type,
        onBackspace = vm::backspace,
        onClear = vm::clear,
        onGenre = vm::selectGenre
    )
}

/**
 * Stateless search UI. Text can be entered three ways, all into the same query:
 * the on-screen keyboard (D-pad + OK), a hardware / Bluetooth keyboard, or the
 * remote's number keys — typing works wherever focus is on this page.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun SearchContent(
    state: SearchState,
    shell: ShellController,
    actions: TileActions,
    onType: (String) -> Unit,
    onBackspace: () -> Unit,
    onClear: () -> Unit,
    onGenre: (String) -> Unit
) {
    val firstKey = remember { FocusRequester() }

    LaunchedEffect(Unit) { shell.pageAtTop = true; shell.pageEntry = firstKey }
    LaunchedEffect(shell.restoreFocusPending) {
        if (!shell.restoreFocusPending) return@LaunchedEffect
        withFrameNanos { }
        runCatching { firstKey.requestFocus() }
        shell.enterAtTop = false
        shell.restoreFocusPending = false
    }

    Row(
        Modifier
            .fillMaxSize()
            .hardwareTyping(onType, onBackspace, onClear)
            .background(Tv.Background)
            .padding(top = Tv.NavHeight + 16.dp)
    ) {
        // ---------------------------------------------------------------- keyboard + genres
        Column(Modifier.width(340.dp).fillMaxSize().padding(start = Tv.Keyline, end = 16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(38.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Tv.Surface)
                    .padding(horizontal = 12.dp)
            ) {
                Icon(Icons.Filled.Search, null, tint = Tv.TextMuted, modifier = Modifier.size(18.dp))
                Text(
                    state.query.ifEmpty { "Search movies & shows" },
                    color = if (state.query.isEmpty()) Tv.TextMuted else Color.White,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 10.dp)
                )
            }
            QwertyKeyboard(firstKey, onType, onBackspace, onClear)
            LazyColumn(Modifier.padding(top = 16.dp).fillMaxWidth(), contentPadding = PaddingValues(bottom = 40.dp)) {
                items(state.genres, key = { it }) { g ->
                    GenreItem(g, selected = state.genre == g) { onGenre(g) }
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
                modifier = Modifier.fillMaxSize().focusRestorer().throttleKeyRepeat()
            ) {
                val results = state.results.distinctBy { it.key }
                items(results, key = { it.key }) { tile -> ResultTile(tile) { actions.open(tile) } }
            }
        }
    }
}

/**
 * Small QWERTY keyboard with keycap-style keys. Each letter row is ten key
 * widths wide; shorter rows are centred with half-key insets, as on a real
 * keyboard, so ▲/▼ land on the key above/below.
 */
@Composable
private fun QwertyKeyboard(firstKey: FocusRequester, onType: (String) -> Unit, onBackspace: () -> Unit, onClear: () -> Unit) {
    // ▲/▼ go to the key at the same position in the next row (w ▼ s, l ▲ o),
    // not to whichever key's centre happens to be nearest.
    val grid = remember { QWERTY.mapIndexed { r, keys -> List(keys.length) { c -> if (r == 1 && c == 0) firstKey else FocusRequester() } } }
    Column(Modifier.padding(top = 10.dp).focusRestorer(), verticalArrangement = Arrangement.spacedBy(KeyGap)) {
        QWERTY.forEachIndexed { r, keys ->
            Row(horizontalArrangement = Arrangement.spacedBy(KeyGap)) {
                val inset = (10 - keys.length) / 2f
                if (inset > 0f) Spacer(Modifier.weight(inset))
                keys.forEachIndexed { c, ch ->
                    val k = ch.toString()
                    Key(
                        text = k,
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(grid[r][c])
                            .focusProperties {
                                grid.getOrNull(r + 1)?.let { below -> down = below[minOf(c, below.size - 1)] }
                                grid.getOrNull(r - 1)?.let { above -> up = above[minOf(c, above.size - 1)] }
                            }
                    ) { onType(k) }
                }
                if (inset > 0f) Spacer(Modifier.weight(inset))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(KeyGap)) {
            Key(icon = Icons.Filled.SpaceBar, label = "space", modifier = Modifier.weight(5f)) { onType(" ") }
            Key(icon = Icons.AutoMirrored.Filled.Backspace, label = "delete", modifier = Modifier.weight(2.5f),
                onLongClear = onClear) { onBackspace() }
            Key(text = "clear", small = true, modifier = Modifier.weight(2.5f)) { onClear() }
        }
    }
}

private val KeyGap = 3.dp

@Composable
private fun Key(
    modifier: Modifier = Modifier,
    text: String? = null,
    icon: ImageVector? = null,
    label: String? = null,
    small: Boolean = false,
    onLongClear: (() -> Unit)? = null,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(5.dp)
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .height(30.dp)
            .onFocusChanged { focused = it.isFocused }
            .clip(shape)
            // Keycap: a lighter face over a darker lip at the bottom.
            .background(if (focused) Color(0xFFBDBDBD) else Color(0xFF151515))
            .padding(bottom = 2.dp)
            .clip(shape)
            .background(if (focused) Color.White else Color(0xFF2C2C2C))
            .border(1.dp, if (focused) Color.White else Color(0x1AFFFFFF), shape)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
    ) {
        val fg = if (focused) Color.Black else Color(0xFFE6E6E6)
        if (icon != null) Icon(icon, contentDescription = label, tint = fg, modifier = Modifier.size(17.dp))
        else Text(text.orEmpty(), color = fg, fontSize = if (small) 11.sp else 14.sp, fontWeight = FontWeight.Medium)
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

/**
 * Hardware keyboard / remote number keys → search query. Runs as a *preview*
 * handler so a printable key types even while an on-screen key or a result is
 * focused; arrows, OK/Enter, Tab and Back are left alone for navigation.
 */
internal fun Modifier.hardwareTyping(
    onType: (String) -> Unit,
    onBackspace: () -> Unit,
    onClear: () -> Unit
): Modifier = onPreviewKeyEvent { e ->
    if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
    val ne = e.nativeKeyEvent
    when (ne.keyCode) {
        android.view.KeyEvent.KEYCODE_DEL -> {
            if (ne.isCtrlPressed || ne.isAltPressed) onClear() else onBackspace()
            return@onPreviewKeyEvent true
        }
        android.view.KeyEvent.KEYCODE_FORWARD_DEL, android.view.KeyEvent.KEYCODE_CLEAR -> {
            onClear(); return@onPreviewKeyEvent true
        }
    }
    if (ne.isCtrlPressed || ne.isAltPressed || ne.isMetaPressed) return@onPreviewKeyEvent false
    val code = ne.unicodeChar
    if (code == 0) return@onPreviewKeyEvent false
    val c = code.toChar()
    if (c.isLetterOrDigit() || c == ' ' || c in "'-&:.,!?") {
        onType(c.lowercase())
        true
    } else false
}
