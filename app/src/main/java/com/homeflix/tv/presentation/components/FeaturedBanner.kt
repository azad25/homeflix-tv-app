package com.homeflix.tv.presentation.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.homeflix.tv.domain.model.Media
import com.homeflix.tv.presentation.theme.PrimeTextDim
import com.homeflix.tv.presentation.theme.RatingGold
import com.homeflix.tv.presentation.theme.TextPrimary
import com.homeflix.tv.util.ApiUtils
import kotlinx.coroutines.delay

/**
 * FEATURED BANNER — one large 16:9 hero on the left + a rotating list of a few
 * smaller landscape picks on the right (the "Featured" half/half section).
 * Moving focus to a right pick previews it in the big banner; the big banner
 * auto-rotates through the pool when nothing is focused.
 */
@Composable
fun FeaturedBanner(
    title: String,
    mediaList: List<Media>,
    onMediaClick: (Media) -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    onNavigateUp: (() -> Unit)? = null
) {
    val items = remember(mediaList) { mediaList.take(6) }
    if (items.isEmpty()) return

    var bigIndex by remember(items) { mutableStateOf(0) }
    var anyFocused by remember { mutableStateOf(false) }

    // Auto-rotate the hero while the user isn't interacting with the section
    LaunchedEffect(items, anyFocused) {
        if (!anyFocused && items.size > 1) {
            while (true) {
                delay(8000)
                if (anyFocused) break
                bigIndex = (bigIndex + 1) % items.size
            }
        }
    }

    val big = items[bigIndex]
    // Right column: the other items, up to 3
    val side = remember(items, bigIndex) {
        items.filterIndexed { i, _ -> i != bigIndex }.take(3)
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            ),
            modifier = Modifier.padding(start = 48.dp, bottom = 8.dp)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp)
                .padding(start = 48.dp, end = 48.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── Big hero (left) ────────────────────────────────────────
            var bigFocused by remember { mutableStateOf(false) }
            val bigScale by animateFloatAsState(if (bigFocused) 1.02f else 1f, tween(160), label = "big_scale")
            Box(
                modifier = Modifier
                    .weight(0.62f)
                    .fillMaxHeight()
                    .graphicsLayer(scaleX = bigScale, scaleY = bigScale)
                    .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
                    .onFocusChanged { bigFocused = it.isFocused; if (it.isFocused) anyFocused = true }
                    .onKeyEvent { k ->
                        if (k.type == KeyEventType.KeyDown) {
                            when (k.key) {
                                Key.Enter, Key.DirectionCenter -> { onMediaClick(big); true }
                                Key.DirectionUp -> if (onNavigateUp != null) { onNavigateUp(); true } else false
                                else -> false
                            }
                        } else false
                    }
                    .focusable()
                    .clickable { onMediaClick(big) }
                    .clip(RoundedCornerShape(10.dp))
                    .then(if (bigFocused) Modifier.border(2.dp, Color.White, RoundedCornerShape(10.dp)) else Modifier)
            ) {
                Crossfade(targetState = big.id, animationSpec = tween(500), label = "big_bg") { _ ->
                    AsyncImage(
                        model = coil.request.ImageRequest.Builder(LocalContext.current)
                            .data(ApiUtils.getBackdropUrl(big))
                            .memoryCacheKey("backdrop_${big.id}")
                            .diskCacheKey("backdrop_${big.id}")
                            .crossfade(false)
                            .build(),
                        contentDescription = big.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.9f)),
                                startY = 200f
                            )
                        )
                )
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(20.dp)
                ) {
                    val logoUrl = ApiUtils.getLogoUrl(big)
                    var logoOk by remember(big.id) { mutableStateOf(logoUrl != null) }
                    if (logoOk && logoUrl != null) {
                        AsyncImage(
                            model = logoUrl,
                            contentDescription = big.title,
                            contentScale = ContentScale.Fit,
                            onError = { logoOk = false },
                            modifier = Modifier.heightIn(max = 64.dp).widthIn(max = 260.dp)
                        )
                    } else {
                        Text(
                            text = big.title,
                            style = MaterialTheme.typography.headlineSmall.copy(
                                fontWeight = FontWeight.Black,
                                color = TextPrimary
                            ),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (big.rating > 0) {
                            Text("★ ${String.format("%.1f", big.rating)}", color = RatingGold, style = MaterialTheme.typography.labelLarge)
                        }
                        big.year?.let { Text(it.toString(), color = PrimeTextDim, style = MaterialTheme.typography.labelLarge) }
                        if (big.genreNames.isNotEmpty()) {
                            Text(big.genreNames.take(2).joinToString(" • "), color = PrimeTextDim, style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }

            // ── Rotating side list (right) ─────────────────────────────
            Column(
                modifier = Modifier
                    .weight(0.38f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                side.forEachIndexed { i, media ->
                    SidePick(
                        media = media,
                        onFocused = {
                            anyFocused = true
                            val idx = items.indexOfFirst { it.id == media.id }
                            if (idx >= 0) bigIndex = idx
                        },
                        onClick = { onMediaClick(media) },
                        // Topmost side pick participates in the UP chain
                        onNavigateUp = if (i == 0) onNavigateUp else null,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun SidePick(
    media: Media,
    onFocused: () -> Unit,
    onClick: () -> Unit,
    onNavigateUp: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged { if (it.isFocused) { focused = true; onFocused() } else focused = false }
            .onKeyEvent { k ->
                if (k.type == KeyEventType.KeyDown) {
                    when (k.key) {
                        Key.Enter, Key.DirectionCenter -> { onClick(); true }
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
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f)),
                        startY = 60f
                    )
                )
        )
        Text(
            text = media.title,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold, color = TextPrimary),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.align(Alignment.BottomStart).padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}
