package com.homeflix.tv.presentation.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.homeflix.tv.domain.model.Media
import com.homeflix.tv.presentation.theme.FocusedBorder
import com.homeflix.tv.presentation.theme.TextPrimary
import com.homeflix.tv.presentation.theme.TextSecondary
import com.homeflix.tv.util.ApiUtils

/**
 * NETFLIX-STYLE TV Media Card
 * Handles its own focus, scaling, and visual feedback
 */
@Composable
fun NetflixMediaCard(
    media: Media,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }
    
    // Netflix-style scale animation on focus (reduced scale)
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.06f else 1.0f,
        animationSpec = tween(durationMillis = 200),
        label = "netflix_card_scale"
    )
    
    // Netflix-style card with scale animation and proper z-index
    Box(
        modifier = modifier
            .aspectRatio(2f / 3f)
            .graphicsLayer(scaleX = scale, scaleY = scale)
            .onFocusChanged { focusState ->
                isFocused = focusState.isFocused
            }
            .focusable()
            .onKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown &&
                    (keyEvent.key == Key.Enter || keyEvent.key == Key.DirectionCenter ||
                     keyEvent.nativeKeyEvent.keyCode == android.view.KeyEvent.KEYCODE_DPAD_CENTER)) {
                    onClick()
                    true
                } else false
            }
            .clickable { onClick() }
            .then(
                if (isFocused) {
                    Modifier
                        .border(2.dp, Color.White, RoundedCornerShape(8.dp))
                        .zIndex(10f)
                } else {
                    Modifier.zIndex(1f)
                }
            )
    ) {
        Card(
            modifier = Modifier.fillMaxSize(),
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color.Transparent
            ),
            elevation = CardDefaults.cardElevation(
                defaultElevation = if (isFocused) 12.dp else 4.dp
            )
        ) {
        Box {
            // Poster Image with caching
            AsyncImage(
                model = coil.request.ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                    .data(ApiUtils.getPosterUrl(media))
                    .memoryCacheKey("poster_${media.id}")
                    .diskCacheKey("poster_${media.id}")
                    .crossfade(false)
                    .build(),
                contentDescription = media.title,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop
            )
            
            // Overlay with title (shown on focus)
            if (isFocused) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Color.Black.copy(alpha = 0.8f),
                            RoundedCornerShape(8.dp)
                        )
                ) {
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = media.title,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary
                            ),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            media.year?.let { year ->
                                Text(
                                    text = year.toString(),
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = TextSecondary
                                    )
                                )
                            }
                            
                            if (media.rating > 0) {
                                Text(
                                    text = "★ ${String.format("%.1f", media.rating)}",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = TextSecondary
                                    )
                                )
                            }
                        }
                        
                        // Quality badge
                        media.quality?.let { quality ->
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = Color.Gray.copy(alpha = 0.3f)
                            ) {
                                Text(
                                    text = quality,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = TextPrimary
                                    ),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
/**

 * Legacy MediaCard - kept for compatibility
 */
@Composable
fun MediaCard(
    media: Media,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    NetflixMediaCard(media = media, onClick = onClick, modifier = modifier)
}
}