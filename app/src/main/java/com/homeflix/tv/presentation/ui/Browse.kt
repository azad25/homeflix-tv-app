package com.homeflix.tv.presentation.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.util.UnstableApi
import coil.imageLoader
import coil.request.ImageRequest
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** What a feed can ask the app to do. */
interface TileActions {
    fun open(tile: Tile)
    fun play(tile: Tile)
}

private val TileShape = RoundedCornerShape(Tv.TileCorner)

// ----------------------------------------------------------------------------------- buttons

/** Netflix TV pill button: white when focused, translucent otherwise. */
@Composable
fun PillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    focusRequester: FocusRequester? = null,
    compact: Boolean = false
) {
    var focused by remember { mutableStateOf(false) }
    val bg = if (focused) Tv.PillFocused else Tv.PillIdle
    val fg = if (focused) Color.Black else Color.White
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged { focused = it.isFocused }
            .clip(RoundedCornerShape(50))
            .background(bg)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(horizontal = if (compact) 16.dp else 22.dp, vertical = if (compact) 8.dp else 10.dp)
    ) {
        if (icon != null) Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(if (compact) 18.dp else 22.dp))
        Text(text, color = fg, fontSize = if (compact) 14.sp else 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

// ----------------------------------------------------------------------------------- tile

private fun tileHeightFor(style: RowStyle): Dp = when (style) {
    RowStyle.LANDSCAPE -> 150.dp
    else -> Tv.TileHeight
}

private fun posterWidth(h: Dp): Dp = h * (2f / 3f)
private fun landscapeWidth(h: Dp): Dp = h * (16f / 9f)

/**
 * A browse tile. Portrait poster at rest; once focus *settles* on it (so a held
 * D-pad never triggers it) it widens to a 16:9 backdrop with the title logo,
 * and the shared preview player starts the trailer on top of it.
 */
@UnstableApi
@Composable
fun BrowseTile(
    tile: Tile,
    style: RowStyle,
    focusKey: String,
    registry: FocusRegistry,
    onFocused: () -> Unit,
    onSettled: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    previewsEnabled: Boolean = true
) {
    val preview = LocalPreviewPlayer.current
    val epoch by (preview?.epoch ?: remember { kotlinx.coroutines.flow.MutableStateFlow(0) }).collectAsState()
    var focused by remember { mutableStateOf(false) }
    var settled by remember { mutableStateOf(false) }
    val h = tileHeightFor(style)
    val expandable = style != RowStyle.LANDSCAPE
    val expanded = expandable && settled

    LaunchedEffect(focused) {
        if (focused) {
            delay(110)
            settled = true
            delay(Tv.FocusMotionMs.toLong() + 20)
            onSettled()
        } else {
            settled = false
        }
    }
    LaunchedEffect(settled, epoch) {
        val p = preview ?: return@LaunchedEffect
        if (settled && previewsEnabled && expandable) p.request(tile.key, tile.previewUrl, revealDelayMs = 1_400)
        else p.cancel(tile.key)
    }

    val baseW = if (style == RowStyle.LANDSCAPE) landscapeWidth(h) else posterWidth(h)
    val width by animateDpAsState(
        targetValue = if (expanded) landscapeWidth(h) else baseW,
        animationSpec = tween(Tv.FocusMotionMs, easing = Tv.Ease),
        label = "tileWidth"
    )
    val ring by animateFloatAsState(if (focused) 1f else 0f, tween(120), label = "ring")

    Box(
        modifier = modifier
            .width(width)
            .height(h)
            .registerFocus(registry, focusKey)
            .onFocusChanged {
                if (it.isFocused != focused) {
                    focused = it.isFocused
                    if (it.isFocused) onFocused()
                }
            }
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .clip(TileShape)
            .background(Tv.Surface)
    ) {
        // Poster (or still for landscape rows) is always underneath.
        if (style == RowStyle.LANDSCAPE) {
            SizedImage(tile.backdropUrl, landscapeWidth(h), h, Modifier.fillMaxSize())
        } else {
            SizedImage(tile.posterUrl, posterWidth(h), h, Modifier.fillMaxSize())
        }
        if (expanded) {
            SizedImage(tile.backdropUrl, landscapeWidth(h), h, Modifier.fillMaxSize(), crossfadeMs = 220)
            PreviewVideo(tile.key, Modifier.fillMaxSize())
            Box(Modifier.fillMaxSize().background(Tv.BottomScrim))
            TitleLogo(
                logoUrl = tile.logoUrl,
                title = tile.title,
                maxWidth = landscapeWidth(h) * 0.5f,
                maxHeight = h * 0.32f,
                textStyle = TextStyle(color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold,
                    shadow = Shadow(Color.Black, blurRadius = 8f)),
                modifier = Modifier.align(Alignment.BottomStart).padding(12.dp)
            )
        }
        if (style == RowStyle.LANDSCAPE) {
            Box(Modifier.fillMaxSize().background(Tv.BottomScrim))
            Column(Modifier.align(Alignment.BottomStart).padding(start = 10.dp, end = 10.dp, bottom = 12.dp)) {
                Text(tile.title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                tile.subtitle?.let {
                    Text(it, color = Tv.TextSecondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        tile.progress?.let { p ->
            Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(4.dp).background(Color(0x66FFFFFF))) {
                Box(Modifier.fillMaxWidth(p.coerceIn(0.02f, 1f)).fillMaxHeight().background(Tv.Red))
            }
        }
        if (style == RowStyle.TOP10 && tile.rank != null && !expanded) {
            Text(
                tile.rank.toString(),
                color = Color.White,
                fontSize = 64.sp,
                fontWeight = FontWeight.Black,
                style = TextStyle(shadow = Shadow(Color.Black, blurRadius = 14f)),
                modifier = Modifier.align(Alignment.BottomStart).padding(start = 6.dp)
            )
        }
        if (tile.badge != null && style != RowStyle.LANDSCAPE && !expanded) {
            Text(
                tile.badge,
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 8.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Tv.Red)
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
        if (ring > 0f) {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = ring }
                    .border(3.dp, Tv.FocusRing, TileShape)
            )
        }
    }
}

// ----------------------------------------------------------------------------------- row

private val InfoBlockHeight = 66.dp

/**
 * A titled row whose focused tile is pinned to the left keyline while the row
 * glides underneath it. The focused title's meta line and synopsis show below
 * the row (Netflix 2025 layout); the space is reserved on every row so rows
 * never change height and vertical scrolling stays perfectly smooth.
 */
@OptIn(ExperimentalComposeUiApi::class)
@UnstableApi
@Composable
fun TileRow(
    row: ContentRow,
    active: Boolean,
    registry: FocusRegistry,
    actions: TileActions,
    onTileFocused: (focusKey: String) -> Unit,
    modifier: Modifier = Modifier,
    topPadding: Dp = 18.dp
) {
    val state = rememberSaveable(row.id, saver = LazyListState.Saver) { LazyListState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val density = LocalDensity.current
    var focusedIndex by rememberSaveable(row.id) { mutableIntStateOf(0) }
    val preview = LocalPreviewPlayer.current
    val previewsEnabled = preview?.tilePreviewsEnabled ?: false

    Column(modifier.fillMaxWidth().padding(top = topPadding)) {
        Text(
            row.title,
            color = if (active) Tv.TextPrimary else Tv.TextSecondary,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = Tv.Keyline, bottom = 10.dp)
        )
        ManualFocusScroll {
            LazyRow(
                state = state,
                contentPadding = PaddingValues(start = Tv.Keyline, end = Tv.Keyline * 4),
                horizontalArrangement = Arrangement.spacedBy(Tv.TileGap),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(tileHeightFor(row.style))
                    // Entering a row (▲/▼) lands on its remembered tile — the one
                    // pinned at the keyline — never on whatever sits under the
                    // previous row's (possibly expanded) tile.
                    .focusProperties {
                        enter = {
                            val idx = focusedIndex.coerceIn(0, (row.tiles.size - 1).coerceAtLeast(0))
                            val composed = state.layoutInfo.visibleItemsInfo.any { it.index == idx }
                            val tile = row.tiles.getOrNull(idx)
                            if (composed && tile != null) registry.requesterFor("${row.id}|${tile.key}")
                            else FocusRequester.Default
                        }
                    }
                    .focusGroup()
                    .throttleKeyRepeat()
            ) {
                itemsIndexed(row.tiles, key = { _, t -> t.key }, contentType = { _, _ -> row.style }) { index, tile ->
                    val focusKey = "${row.id}|${tile.key}"
                    BrowseTile(
                        tile = tile,
                        style = row.style,
                        focusKey = focusKey,
                        registry = registry,
                        previewsEnabled = previewsEnabled,
                        onFocused = {
                            focusedIndex = index
                            onTileFocused(focusKey)
                            scope.launch { state.glideTo(index) }
                            // Warm the next tiles' stills + trailers so moving on is instant.
                            val h = tileHeightFor(row.style)
                            val w = with(density) { landscapeWidth(h).roundToPx() }
                            val hp = with(density) { h.roundToPx() }
                            for (n in listOf(index + 1, index - 1, index + 2)) {
                                row.tiles.getOrNull(n)?.let { t ->
                                    context.imageLoader.enqueue(ImageRequest.Builder(context).data(t.backdropUrl).size(w, hp).build())
                                }
                            }
                            if (previewsEnabled) {
                                preview?.prefetch(listOfNotNull(
                                    row.tiles.getOrNull(index + 1)?.previewUrl,
                                    row.tiles.getOrNull(index - 1)?.previewUrl
                                ))
                            }
                        },
                        onSettled = { scope.launch { state.glideTo(index) } },
                        onClick = {
                            if (row.style == RowStyle.LANDSCAPE) actions.play(tile) else actions.open(tile)
                        }
                    )
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(InfoBlockHeight).padding(start = Tv.Keyline, top = 10.dp)) {
            val tile = if (active) row.tiles.getOrNull(focusedIndex) else null
            AnimatedContent(
                targetState = tile,
                transitionSpec = { fadeIn(tween(200, delayMillis = 60)) togetherWith fadeOut(tween(90)) },
                contentKey = { it?.key },
                label = "rowInfo"
            ) { t ->
                if (t != null) InfoBlock(t) else Spacer(Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun InfoBlock(tile: Tile) {
    Column(Modifier.fillMaxWidth(0.62f)) {
        val meta = listOfNotNull(tile.subtitle?.takeIf { tile.progress != null }, tile.meta.takeIf { it.isNotBlank() })
            .joinToString("  •  ")
        if (meta.isNotBlank()) {
            Text(meta, color = Tv.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
                overflow = TextOverflow.Ellipsis)
        }
        tile.description?.let {
            Text(it, color = Tv.TextSecondary, fontSize = 14.sp, lineHeight = 19.sp, maxLines = 2,
                overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 3.dp))
        }
    }
}

// ----------------------------------------------------------------------------------- hero

/** Full-bleed billboard: backdrop → muted trailer, logo, meta, synopsis, Play / More Info. */
@UnstableApi
@Composable
fun HeroBillboard(
    tile: Tile,
    active: Boolean,
    playRequester: FocusRequester,
    onPlay: () -> Unit,
    onMoreInfo: () -> Unit,
    modifier: Modifier = Modifier,
    playLabel: String = "Play"
) {
    val preview = LocalPreviewPlayer.current
    val epoch by (preview?.epoch ?: remember { kotlinx.coroutines.flow.MutableStateFlow(0) }).collectAsState()
    val heroKey = "hero:${tile.key}"
    LaunchedEffect(active, heroKey, epoch) {
        val p = preview ?: return@LaunchedEffect
        if (active) p.request(heroKey, tile.previewUrl, revealDelayMs = 2_000, dwellMs = 700) else p.cancel(heroKey)
    }
    val screenH = LocalConfiguration.current.screenHeightDp.dp
    Box(modifier.fillMaxWidth().height(screenH - 132.dp)) {
        SizedImage(tile.backdropUrl, 960.dp, 540.dp, Modifier.fillMaxSize(), alignment = Alignment.TopCenter, crossfadeMs = 300)
        PreviewVideo(heroKey, Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().background(Tv.LeftScrim))
        Box(Modifier.fillMaxSize().background(Tv.HeroBottomScrim))
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .padding(start = Tv.Keyline, bottom = 20.dp)
                .fillMaxWidth(0.5f)
        ) {
            TitleLogo(
                logoUrl = tile.logoUrl,
                title = tile.title,
                maxWidth = 380.dp,
                maxHeight = 130.dp,
                textStyle = TextStyle(color = Color.White, fontSize = 44.sp, fontWeight = FontWeight.Black,
                    lineHeight = 46.sp, shadow = Shadow(Color.Black, blurRadius = 12f))
            )
            if (tile.meta.isNotBlank()) {
                Text(tile.meta, color = Tv.TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, modifier = Modifier.padding(top = 14.dp))
            }
            tile.description?.let {
                Text(it, color = Tv.TextSecondary, fontSize = 15.sp, lineHeight = 21.sp, maxLines = 3,
                    overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 18.dp)) {
                PillButton(playLabel, onPlay, icon = Icons.Filled.PlayArrow, focusRequester = playRequester)
                PillButton("More Info", onMoreInfo, icon = Icons.Filled.Info)
            }
        }
    }
}

// ----------------------------------------------------------------------------------- feed

/**
 * The scrolling page used by Home / Shows / Movies / My HomeFlix.
 * Each section scrolls itself to the top keyline when it receives focus, so
 * the page moves in one eased glide per D-pad press with no focus/scroll fight.
 */
@UnstableApi
@Composable
fun BrowseFeed(
    feed: Feed,
    listState: LazyListState,
    registry: FocusRegistry,
    heroPlayRequester: FocusRequester,
    activeSection: Int,
    onSectionFocused: (Int) -> Unit,
    onTileFocused: (String) -> Unit,
    actions: TileActions,
    modifier: Modifier = Modifier,
    topInset: Dp = Tv.NavHeight,
    heroPlayLabel: (Tile) -> String = { "Play" }
) {
    val scope = rememberCoroutineScope()
    val hasHero = feed.hero != null
    ManualFocusScroll {
        LazyColumn(
            state = listState,
            modifier = modifier.fillMaxSize().background(Tv.Background),
            contentPadding = PaddingValues(bottom = 200.dp)
        ) {
            feed.hero?.let { hero ->
                item(key = "hero", contentType = "hero") {
                    HeroBillboard(
                        tile = hero,
                        active = activeSection == 0,
                        playRequester = heroPlayRequester,
                        onPlay = { actions.play(hero) },
                        onMoreInfo = { actions.open(hero) },
                        playLabel = heroPlayLabel(hero),
                        modifier = Modifier.onFocusChanged {
                            if (it.hasFocus) {
                                onSectionFocused(0)
                                scope.launch { listState.glideTo(0) }
                            }
                        }
                    )
                }
            }
            itemsIndexed(feed.rows, key = { _, r -> r.id }, contentType = { _, r -> r.style }) { i, row ->
                val section = i + if (hasHero) 1 else 0
                TileRow(
                    row = row,
                    active = activeSection == section,
                    registry = registry,
                    actions = actions,
                    onTileFocused = onTileFocused,
                    topPadding = if (!hasHero && i == 0) topInset + 8.dp else 18.dp,
                    modifier = Modifier.onFocusChanged {
                        if (it.hasFocus) {
                            onSectionFocused(section)
                            scope.launch { listState.glideTo(section) }
                        }
                    }
                )
            }
        }
    }
}

/** Keeps a little gradient under the top nav so it stays legible over the hero. */
val NavScrim: Brush = Brush.verticalGradient(0f to Color(0xCC000000), 1f to Color.Transparent)
