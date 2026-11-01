package com.homeflix.tv.presentation.ui

import android.view.TextureView
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.homeflix.tv.core.PreviewPlayer

/** The shared muted preview player, provided by the app shell / details screens. */
@UnstableApi
val LocalPreviewPlayer = staticCompositionLocalOf<PreviewPlayer?> { null }

/**
 * Image decoded at the size it is drawn — never a full-resolution bitmap in a
 * small tile (saves memory bandwidth, which is what drops frames on TV GPUs).
 */
@Composable
fun SizedImage(
    url: String?,
    width: Dp,
    height: Dp,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    alignment: Alignment = Alignment.Center,
    crossfadeMs: Int = 180,
    contentDescription: String? = null,
    onError: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val wPx = with(density) { width.roundToPx() }.coerceAtLeast(1)
    val hPx = with(density) { height.roundToPx() }.coerceAtLeast(1)
    val request = remember(url, wPx, hPx) {
        ImageRequest.Builder(context)
            .data(url)
            .size(wPx, hPx)
            .crossfade(crossfadeMs)
            .build()
    }
    AsyncImage(
        model = request,
        contentDescription = contentDescription,
        contentScale = contentScale,
        alignment = alignment,
        modifier = modifier,
        onError = if (onError != null) { _ -> onError() } else null
    )
}

/** Title-treatment logo with an automatic text fallback when the logo is missing. */
@Composable
fun TitleLogo(
    logoUrl: String?,
    title: String,
    maxWidth: Dp,
    maxHeight: Dp,
    textStyle: TextStyle,
    modifier: Modifier = Modifier,
    alignment: Alignment = Alignment.BottomStart
) {
    var failed by remember(logoUrl) { mutableStateOf(logoUrl.isNullOrBlank()) }
    if (!failed) {
        SizedImage(
            url = logoUrl,
            width = maxWidth,
            height = maxHeight,
            contentScale = ContentScale.Fit,
            alignment = alignment,
            contentDescription = title,
            modifier = modifier.sizeIn(maxWidth = maxWidth, maxHeight = maxHeight),
            onError = { failed = true }
        )
    } else {
        Text(text = title, style = textStyle, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = modifier)
    }
}

/**
 * Hosts the shared preview player's video for [key]. Composes a TextureView
 * only while this key's clip is loaded and fades it in once frames are
 * actually playing — the still image underneath shows until then.
 */
@UnstableApi
@Composable
fun PreviewVideo(key: String, modifier: Modifier = Modifier) {
    val preview = LocalPreviewPlayer.current ?: return
    val active by preview.activeKey.collectAsState()
    if (active != key) return
    val visibleKey by preview.visibleKey.collectAsState()
    val alpha by animateFloatAsState(
        targetValue = if (visibleKey == key) 1f else 0f,
        animationSpec = tween(if (visibleKey == key) 380 else 160),
        label = "previewAlpha"
    )
    Box(modifier) {
        AndroidView(
            factory = { ctx -> TextureView(ctx).also { preview.attachSurface(it) } },
            onRelease = { preview.detachSurface(it) },
            modifier = Modifier.fillMaxSize().graphicsLayer { this.alpha = alpha }
        )
    }
}
