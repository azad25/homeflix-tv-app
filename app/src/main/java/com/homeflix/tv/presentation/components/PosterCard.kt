package com.homeflix.tv.presentation.components

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
import com.homeflix.tv.presentation.theme.PrimeTextDim
import com.homeflix.tv.presentation.theme.TextPrimary

/**
 * Unified 2:3 poster card used across Browse / Search / My List / rows.
 * Prime look: white-border focus, graphicsLayer scale (cheap during D-pad
 * moves), single-swap image fallback (no onError URL chains).
 */
@Composable
fun PosterCard(
    posterUrl: String,
    fallbackUrl: String?,
    title: String,
    subtitle: String? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    cacheKey: String? = null
) {
    var focused by remember { mutableStateOf(false) }
    var failed by remember(posterUrl) { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.06f else 1f, tween(160), label = "poster_scale")

    val model = if (failed && fallbackUrl != null) fallbackUrl else posterUrl

    Box(
        modifier = modifier
            .aspectRatio(2f / 3f)
            .graphicsLayer(scaleX = scale, scaleY = scale)
            .onFocusChanged { focused = it.isFocused }
            .onKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown &&
                    (keyEvent.key == Key.Enter || keyEvent.key == Key.DirectionCenter)
                ) {
                    onClick(); true
                } else false
            }
            .focusable()
            .clickable { onClick() }
            .clip(RoundedCornerShape(8.dp))
            .then(if (focused) Modifier.border(2.dp, Color.White, RoundedCornerShape(8.dp)) else Modifier)
    ) {
        AsyncImage(
            model = coil.request.ImageRequest.Builder(LocalContext.current)
                .data(model)
                .apply {
                    if (cacheKey != null) {
                        memoryCacheKey(if (failed) "${cacheKey}_fb" else cacheKey)
                        diskCacheKey(if (failed) "${cacheKey}_fb" else cacheKey)
                    }
                }
                .crossfade(false)
                .build(),
            contentDescription = title,
            contentScale = ContentScale.Crop,
            onError = { if (!failed && fallbackUrl != null) failed = true },
            modifier = Modifier.fillMaxSize()
        )

        // Title overlay only when focused (keeps the grid clean)
        if (focused) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f)),
                            startY = 260f
                        )
                    )
            )
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(8.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall.copy(color = PrimeTextDim),
                        maxLines = 1
                    )
                }
            }
        }
    }
}
