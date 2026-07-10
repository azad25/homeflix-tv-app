package com.homeflix.tv.presentation.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
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
import coil.compose.AsyncImage
import com.homeflix.tv.domain.model.Media
import com.homeflix.tv.domain.model.MediaType
import com.homeflix.tv.presentation.theme.NetflixRed
import com.homeflix.tv.presentation.theme.PrimeTextDim
import com.homeflix.tv.presentation.theme.TextPrimary
import com.homeflix.tv.util.ApiUtils

data class ContinueWatchingItem(
    val media: Media,
    val progress: Float,           // 0.0 to 1.0
    val progressSeconds: Long,     // Actual progress in seconds
    val lastWatched: String? = null
)

/**
 * CONTINUE WATCHING — Prime-style 16:9 cards with a bottom gradient, a brand
 * progress bar, and episode awareness (shows "S{n}E{n} · title" for episodes).
 */
@Composable
fun ContinueWatchingRow(
    continueWatchingItems: List<ContinueWatchingItem>,
    onPlay: (Media, Long) -> Unit, // media + progressMs
    onInfo: (Media) -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    onNavigateUp: (() -> Unit)? = null,
    onNavigateDown: (() -> Unit)? = null,
    mediaTypeFilter: Set<MediaType>? = setOf(MediaType.MOVIE),
    applyHorizontalPadding: Boolean = true
) {
    val validItems = remember(continueWatchingItems, mediaTypeFilter) {
        continueWatchingItems.filter { item ->
            item.media.id > 0 && item.media.title.isNotBlank() &&
                item.progress in 0f..1f &&
                (mediaTypeFilter == null || item.media.type in mediaTypeFilter)
        }
    }
    if (validItems.isEmpty()) return

    // Edge padding lives in the LazyRow contentPadding (not the parent Column)
    // so the scaled focus border of the FIRST card isn't clipped at the row's
    // left bound — the row spans full width and clips only at the screen edge.
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "Continue Watching",
            style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            ),
            modifier = Modifier.padding(
                start = if (applyHorizontalPadding) 48.dp else 0.dp,
                bottom = 8.dp
            )
        )

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = if (applyHorizontalPadding)
                PaddingValues(horizontal = 48.dp) else PaddingValues(end = 48.dp)
        ) {
            items(validItems, key = { it.media.id }) { item ->
                ContinueWatchingCard(
                    item = item,
                    onPlay = { onPlay(item.media, item.progressSeconds * 1000) },
                    onInfo = { onInfo(item.media) },
                    onNavigateUp = onNavigateUp,
                    modifier = if (item == validItems.first() && focusRequester != null)
                        Modifier.focusRequester(focusRequester) else Modifier
                )
            }
        }
    }
}

@Composable
private fun ContinueWatchingCard(
    item: ContinueWatchingItem,
    onPlay: () -> Unit,
    onInfo: () -> Unit,
    onNavigateUp: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.06f else 1f, tween(160), label = "cw_scale")
    val media = item.media

    Box(
        modifier = modifier
            .width(300.dp)
            .aspectRatio(16f / 9f)
            .graphicsLayer(scaleX = scale, scaleY = scale)
            .onFocusChanged { focused = it.isFocused }
            .onKeyEvent { k ->
                if (k.type == KeyEventType.KeyDown) {
                    when (k.key) {
                        Key.Enter, Key.DirectionCenter -> { onPlay(); true }
                        Key.DirectionUp -> if (onNavigateUp != null) { onNavigateUp(); true } else false
                        else -> false
                    }
                } else false
            }
            .focusable()
            .clickable { onPlay() }
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
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f)),
                        startY = 120f
                    )
                )
        )

        if (focused) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .align(Alignment.Center)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.92f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = "Play", tint = Color.Black, modifier = Modifier.size(30.dp))
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            // Episode-aware title
            val primary = if (media.type == MediaType.EPISODE && media.seasonNumber != null && media.episodeNumber != null)
                "S${media.seasonNumber}E${media.episodeNumber} · ${media.title}"
            else media.title
            Text(
                text = primary,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = TextPrimary),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { item.progress },
                color = NetflixRed,
                trackColor = Color.White.copy(alpha = 0.3f),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp))
            )
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "${(item.progress * 100).toInt()}% watched",
                    style = MaterialTheme.typography.labelSmall.copy(color = Color.White.copy(alpha = 0.85f))
                )
                item.lastWatched?.let {
                    Text("• $it", style = MaterialTheme.typography.labelSmall.copy(color = PrimeTextDim))
                }
            }
        }
    }
}
