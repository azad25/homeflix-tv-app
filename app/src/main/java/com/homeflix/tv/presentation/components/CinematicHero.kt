package com.homeflix.tv.presentation.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
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
import androidx.media3.common.util.UnstableApi
import coil.compose.AsyncImage
import com.homeflix.tv.domain.model.Media
import com.homeflix.tv.presentation.theme.NetflixRed
import com.homeflix.tv.presentation.theme.*
import com.homeflix.tv.util.ApiUtils
import kotlinx.coroutines.delay

/**
 * CINEMATIC HERO - Prime Video layout with Netflix slide behavior.
 *
 * Full-bleed backdrop that auto-plays a muted preview clip after a short
 * dwell, Prime-style left gradient and bottom fade into the page background,
 * metadata badges, and focusable Play / More Info actions.
 */
@UnstableApi
@Composable
fun CinematicHero(
    mediaList: List<Media>,
    currentIndex: Int,
    onPlayClick: (Media) -> Unit,
    onDetailsClick: (Media) -> Unit,
    onIndexChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    playButtonFocusRequester: FocusRequester? = null,
    onNavigateDown: (() -> Unit)? = null
) {
    if (mediaList.isEmpty()) return
    val safeIndex = currentIndex.coerceIn(0, mediaList.size - 1)
    val currentMedia = mediaList[safeIndex]

    // Auto-advance (longer dwell so the preview video gets screen time)
    LaunchedEffect(safeIndex, mediaList.size) {
        if (mediaList.size > 1) {
            delay(16_000)
            onIndexChange((safeIndex + 1) % mediaList.size)
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(440.dp) // fit within TV viewport so focusing Play doesn't scroll
    ) {
        // Backdrop + delayed muted preview clip
        androidx.compose.animation.Crossfade(
            targetState = safeIndex,
            animationSpec = tween(durationMillis = 900),
            label = "hero_bg"
        ) { idx ->
            val media = mediaList.getOrElse(idx) { currentMedia }
            Box(Modifier.fillMaxSize()) {
                BackgroundVideo(
                    backdropUrl = ApiUtils.getBackdropUrl(media),
                    videoUrl = ApiUtils.getPreviewClipUrl(media.id),
                    startDelayMs = 3000,
                    playbackEnabled = idx == safeIndex,
                    contentDescription = media.title
                )
            }
        }

        // Video stays clean at the top; only the bottom blends into the page
        // black so the rows appear to sit on the hero (Netflix-style scrim).
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            PrimeBg.copy(alpha = 0.55f),
                            PrimeBg
                        ),
                        startY = 460f
                    )
                )
        )

        // Content column (left-aligned like Prime)
        val contentAlpha = remember { mutableStateOf(false) }
        LaunchedEffect(currentMedia.id) {
            contentAlpha.value = false
            delay(250)
            contentAlpha.value = true
        }
        val alpha by animateFloatAsState(
            targetValue = if (contentAlpha.value) 1f else 0f,
            animationSpec = tween(700), label = "hero_content"
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 56.dp, bottom = 40.dp, end = 500.dp)
                .graphicsLayer { this.alpha = alpha },
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Brand strip (Prime shows "prime" above the logo)
            Text(
                text = "HOMEFLIX",
                style = MaterialTheme.typography.labelLarge.copy(
                    color = NetflixRed,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 3.sp
                )
            )

            // Logo with text fallback
            val logoUrl = ApiUtils.getLogoUrl(currentMedia)
            var logoOk by remember(currentMedia.id) { mutableStateOf(logoUrl != null) }
            if (logoOk && logoUrl != null) {
                AsyncImage(
                    model = coil.request.ImageRequest.Builder(LocalContext.current)
                        .data(logoUrl)
                        .memoryCacheKey("logo_${currentMedia.id}")
                        .diskCacheKey("logo_${currentMedia.id}")
                        .build(),
                    contentDescription = currentMedia.title,
                    modifier = Modifier
                        .heightIn(max = 110.dp)
                        .widthIn(max = 420.dp),
                    contentScale = ContentScale.Fit,
                    onError = { logoOk = false }
                )
            } else {
                Text(
                    text = currentMedia.title,
                    style = MaterialTheme.typography.displaySmall.copy(
                        fontWeight = FontWeight.Black,
                        color = TextPrimary
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Prime-style teal accent line ("New Season" equivalent)
            Text(
                text = "Newly Added",
                style = MaterialTheme.typography.titleSmall.copy(
                    color = NetflixRed,
                    fontWeight = FontWeight.SemiBold
                )
            )

            // Metadata row: ★ rating · year · runtime · genres · cert · quality
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (currentMedia.rating > 0) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("★", color = RatingGold, style = MaterialTheme.typography.titleSmall)
                        Text(
                            String.format("%.1f", currentMedia.rating),
                            color = TextPrimary,
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
                        )
                    }
                }
                currentMedia.year?.let {
                    Text(it.toString(), color = PrimeTextDim, style = MaterialTheme.typography.titleSmall)
                }
                currentMedia.runtime?.let {
                    Text("${it / 60}h ${it % 60}m", color = PrimeTextDim, style = MaterialTheme.typography.titleSmall)
                }
                if (currentMedia.genreNames.isNotEmpty()) {
                    Text(
                        currentMedia.genreNames.take(2).joinToString(" • "),
                        color = PrimeTextDim,
                        style = MaterialTheme.typography.titleSmall
                    )
                }
                currentMedia.certification?.takeIf { it.isNotBlank() }?.let { CertBadge(it) }
                QualityBadge(currentMedia.quality)
            }

            // Description
            currentMedia.description?.let { desc ->
                Text(
                    text = desc,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        color = TextPrimary.copy(alpha = 0.92f),
                        lineHeight = 24.sp
                    ),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Actions: Play (Prime-blue focus) + More Info
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(top = 6.dp)
            ) {
                HeroActionButton(
                    label = "Play",
                    icon = { Icon(Icons.Default.PlayArrow, null, Modifier.size(20.dp)) },
                    primary = true,
                    focusRequester = playButtonFocusRequester,
                    onClick = { onPlayClick(currentMedia) },
                    onNavigateDown = onNavigateDown,
                    onNavigateLeft = {
                        if (mediaList.size > 1) onIndexChange(if (safeIndex > 0) safeIndex - 1 else mediaList.size - 1)
                    },
                    onNavigateRight = null
                )
                HeroActionButton(
                    label = "More Info",
                    icon = { Icon(Icons.Default.Info, null, Modifier.size(20.dp)) },
                    primary = false,
                    onClick = { onDetailsClick(currentMedia) },
                    onNavigateDown = onNavigateDown,
                    onNavigateLeft = null,
                    onNavigateRight = {
                        if (mediaList.size > 1) onIndexChange((safeIndex + 1) % mediaList.size)
                    }
                )
            }
        }

        // Slide dots (bottom-center like Prime)
        if (mediaList.size > 1) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 28.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                mediaList.forEachIndexed { i, _ ->
                    Box(
                        Modifier
                            .size(width = if (i == safeIndex) 22.dp else 7.dp, height = 7.dp)
                            .clip(RoundedCornerShape(50))
                            .background(if (i == safeIndex) TextPrimary else TextPrimary.copy(alpha = 0.35f))
                    )
                }
            }
        }
    }
}

@Composable
fun CertBadge(text: String) {
    Box(
        Modifier
            .border(1.dp, BadgeOutline, RoundedCornerShape(3.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(text, color = PrimeTextDim, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
    }
}

@Composable
fun QualityBadge(quality: String?) {
    val label = when {
        quality?.contains("4K", true) == true || quality?.contains("2160", true) == true -> "4K UHD"
        else -> "HD"
    }
    Box(
        Modifier
            .background(PrimeSurface, RoundedCornerShape(3.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(label, color = TextPrimary, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
    }
}

/**
 * Prime-style pill button: dark translucent at rest, Prime blue when focused.
 */
@Composable
fun HeroActionButton(
    label: String,
    icon: @Composable () -> Unit,
    primary: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    onNavigateDown: (() -> Unit)? = null,
    onNavigateLeft: (() -> Unit)? = null,
    onNavigateRight: (() -> Unit)? = null
) {
    var focused by remember { mutableStateOf(false) }
    val bg = when {
        focused -> if (primary) Color.White else PrimeBlue
        primary -> Color.White.copy(alpha = 0.9f)
        else -> PrimeSurface.copy(alpha = 0.85f)
    }
    val fg = when {
        focused -> if (primary) Color.Black else Color.White
        primary -> Color.Black
        else -> TextPrimary
    }
    val scale by animateFloatAsState(if (focused) 1.06f else 1f, tween(150), label = "btn_scale")

    Surface(
        shape = RoundedCornerShape(6.dp),
        color = bg,
        modifier = modifier
            .graphicsLayer(scaleX = scale, scaleY = scale)
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged { focused = it.isFocused }
            .onKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown) {
                    when (keyEvent.key) {
                        Key.Enter, Key.DirectionCenter -> {
                            onClick(); true
                        }
                        Key.DirectionDown -> {
                            onNavigateDown?.invoke() != null
                        }
                        Key.DirectionLeft -> {
                            if (onNavigateLeft != null) {
                                onNavigateLeft(); true
                            } else false
                        }
                        Key.DirectionRight -> {
                            if (onNavigateRight != null) {
                                onNavigateRight(); true
                            } else false
                        }
                        Key.DirectionUp -> true // consume - nothing above hero
                        else -> false
                    }
                } else false
            }
            .focusable()
            .clickable { onClick() }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 9.dp)
        ) {
            CompositionLocalProvider(LocalContentColor provides fg) { icon() }
            Text(
                label,
                color = fg,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
        }
    }
}
