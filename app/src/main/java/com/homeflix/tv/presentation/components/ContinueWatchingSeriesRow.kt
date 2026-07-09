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
import com.homeflix.tv.presentation.screens.tvshows.ContinueWatchingSeries
import com.homeflix.tv.presentation.theme.NetflixRed
import com.homeflix.tv.presentation.theme.PrimeTextDim
import com.homeflix.tv.presentation.theme.TextPrimary
import com.homeflix.tv.util.ApiUtils

/**
 * Continue Watching for TV — one landscape card per series showing the SERIES
 * banner. Selecting a card resumes the in-progress episode from its saved
 * position.
 */
@Composable
fun ContinueWatchingSeriesRow(
    items: List<ContinueWatchingSeries>,
    onResume: (episodeMediaId: Int, startMs: Long) -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    onNavigateUp: (() -> Unit)? = null
) {
    if (items.isEmpty()) return

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "Continue Watching",
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold, color = TextPrimary),
            modifier = Modifier.padding(start = 48.dp, bottom = 8.dp)
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 48.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(items, key = { it.seriesId }) { item ->
                SeriesCwCard(
                    item = item,
                    onResume = { onResume(item.episodeMediaId, item.progressSeconds * 1000) },
                    onNavigateUp = onNavigateUp,
                    modifier = if (item == items.first() && focusRequester != null)
                        Modifier.focusRequester(focusRequester) else Modifier
                )
            }
        }
    }
}

@Composable
private fun SeriesCwCard(
    item: ContinueWatchingSeries,
    onResume: () -> Unit,
    onNavigateUp: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.06f else 1f, tween(160), label = "cws_scale")
    val bannerUrl = "${ApiUtils.getBaseUrl()}/series/${item.seriesId}/backdrop"

    Box(
        modifier = modifier
            .width(300.dp)
            .aspectRatio(16f / 9f)
            .graphicsLayer(scaleX = scale, scaleY = scale)
            .onFocusChanged { focused = it.isFocused }
            .onKeyEvent { k ->
                if (k.type == KeyEventType.KeyDown) {
                    when (k.key) {
                        Key.Enter, Key.DirectionCenter -> { onResume(); true }
                        Key.DirectionUp -> if (onNavigateUp != null) { onNavigateUp(); true } else false
                        else -> false
                    }
                } else false
            }
            .focusable()
            .clickable { onResume() }
            .clip(RoundedCornerShape(8.dp))
            .then(if (focused) Modifier.border(2.dp, Color.White, RoundedCornerShape(8.dp)) else Modifier)
    ) {
        AsyncImage(
            model = coil.request.ImageRequest.Builder(LocalContext.current)
                .data(bannerUrl)
                .memoryCacheKey("series_backdrop_${item.seriesId}")
                .diskCacheKey("series_backdrop_${item.seriesId}")
                .crossfade(false)
                .build(),
            contentDescription = item.seriesTitle,
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
                Icon(Icons.Default.PlayArrow, "Resume", tint = Color.Black, modifier = Modifier.size(30.dp))
            }
        }
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Text(
                text = item.seriesTitle,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = TextPrimary),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val epLabel = if (item.seasonNumber != null && item.episodeNumber != null)
                "S${item.seasonNumber} E${item.episodeNumber} · ${item.lastWatched}"
            else item.lastWatched
            Text(
                text = epLabel,
                style = MaterialTheme.typography.labelMedium.copy(color = PrimeTextDim),
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
        }
    }
}
