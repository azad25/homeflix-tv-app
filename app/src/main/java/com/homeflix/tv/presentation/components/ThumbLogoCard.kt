package com.homeflix.tv.presentation.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.homeflix.tv.domain.model.Media
import com.homeflix.tv.presentation.theme.PrimeTextDim
import com.homeflix.tv.presentation.theme.TextPrimary
import com.homeflix.tv.util.ApiUtils
import kotlinx.coroutines.launch

/**
 * Landscape 16:9 card built from a THUMBNAIL/backdrop with the title LOGO
 * composited over it (falls back to text when no logo). This is the
 * Prime/Netflix "art + logo" card — used to mix with plain 2:3 posters so the
 * UI isn't a wall of identical posters.
 *
 * The logo position varies per title (left/center/right, derived from the
 * media id) so rows don't look like every logo was stamped in the same spot.
 */
@Composable
fun ThumbLogoCard(
    media: Media,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp? = 300.dp, // null → fill the width given by the caller (grid cell)
    onNavigateLeft: (() -> Unit)? = null,
    onNavigateRight: (() -> Unit)? = null,
    onNavigateUp: (() -> Unit)? = null
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.06f else 1f, tween(160), label = "tl_scale")

    // Dynamic logo anchor: stable per title, varied across a row
    val logoAlignment = remember(media.id) {
        when (media.id % 3) {
            0 -> Alignment.BottomStart
            1 -> Alignment.BottomCenter
            else -> Alignment.BottomEnd
        }
    }

    Box(
        modifier = modifier
            .then(if (width != null) Modifier.width(width) else Modifier.fillMaxWidth())
            .aspectRatio(16f / 9f)
            .graphicsLayer(scaleX = scale, scaleY = scale)
            .onFocusChanged { focused = it.isFocused }
            .onKeyEvent { k ->
                if (k.type == KeyEventType.KeyDown) {
                    when (k.key) {
                        Key.Enter, Key.DirectionCenter -> { onClick(); true }
                        Key.DirectionLeft ->
                            if (onNavigateLeft != null) { onNavigateLeft(); true } else false
                        Key.DirectionRight ->
                            if (onNavigateRight != null) { onNavigateRight(); true } else false
                        Key.DirectionUp ->
                            if (onNavigateUp != null) { onNavigateUp(); true } else false
                        else -> false
                    }
                } else false
            }
            .focusable()
            .clickable { onClick() }
            .clip(RoundedCornerShape(8.dp))
            .then(if (focused) Modifier.border(2.dp, Color.White, RoundedCornerShape(8.dp)) else Modifier)
    ) {
        // Backdrop / thumbnail
        AsyncImage(
            model = coil.request.ImageRequest.Builder(LocalContext.current)
                .data(ApiUtils.getBackdropUrl(media))
                .memoryCacheKey("backdrop_${media.id}")
                .diskCacheKey("backdrop_${media.id}")
                .crossfade(false)
                .build(),
            contentDescription = media.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        // Gradient for logo/title legibility
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

        // Logo composited at the per-title anchor, text fallback
        val logoUrl = ApiUtils.getLogoUrl(media)
        var logoOk by remember(media.id) { mutableStateOf(logoUrl != null) }
        Box(
            modifier = Modifier
                .align(if (logoOk && logoUrl != null) logoAlignment else Alignment.BottomStart)
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            if (logoOk && logoUrl != null) {
                AsyncImage(
                    model = coil.request.ImageRequest.Builder(LocalContext.current)
                        .data(logoUrl)
                        .memoryCacheKey("logo_${media.id}")
                        .diskCacheKey("logo_${media.id}")
                        .build(),
                    contentDescription = media.title,
                    contentScale = ContentScale.Fit,
                    onError = { logoOk = false },
                    modifier = Modifier.heightIn(max = 36.dp).widthIn(max = 170.dp)
                )
            } else {
                Column {
                    Text(
                        text = media.title,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = TextPrimary),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        media.year?.let { Text(it.toString(), style = MaterialTheme.typography.labelSmall, color = PrimeTextDim) }
                        if (media.rating > 0) Text("★ ${String.format("%.1f", media.rating)}", style = MaterialTheme.typography.labelSmall, color = PrimeTextDim)
                    }
                }
            }
        }
    }
}

/**
 * A horizontal row of [ThumbLogoCard]s with a section title — the landscape
 * counterpart to MediaRow, for mixing card styles across the home page.
 *
 * Handles LEFT/RIGHT internally via per-item FocusRequesters so a parent key
 * handler can never swallow in-row navigation. At the first card, LEFT calls
 * [onNavigateLeftAtStart] (e.g. reveal the sidebar).
 */
@Composable
fun ThumbLogoRow(
    title: String,
    mediaList: List<Media>,
    onMediaClick: (Media) -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    onNavigateUp: (() -> Unit)? = null,
    onNavigateLeftAtStart: (() -> Unit)? = null
) {
    if (mediaList.isEmpty()) return
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val itemRequesters = remember(mediaList.size) {
        List(minOf(mediaList.size, 30)) { FocusRequester() }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold, color = TextPrimary),
            modifier = Modifier.padding(start = 48.dp, bottom = 8.dp)
        )
        LazyRow(
            state = listState,
            contentPadding = PaddingValues(horizontal = 48.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            itemsIndexed(mediaList, key = { _, m -> m.id }) { index, media ->
                ThumbLogoCard(
                    media = media,
                    onClick = { onMediaClick(media) },
                    // In-row LEFT/RIGHT is left to the focus system (works now
                    // that no parent consumes direction keys); only the first
                    // card's LEFT is intercepted for the sidebar reveal.
                    onNavigateLeft = if (index == 0) onNavigateLeftAtStart else null,
                    onNavigateUp = onNavigateUp,
                    modifier = if (index == 0 && focusRequester != null)
                        Modifier.focusRequester(focusRequester) else Modifier
                )
            }
        }
    }
}
