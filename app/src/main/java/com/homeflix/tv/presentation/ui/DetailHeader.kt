package com.homeflix.tv.presentation.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.util.UnstableApi

/**
 * Full-bleed title page header shared by movie and series details: backdrop
 * that turns into the muted trailer, title treatment, meta, synopsis and an
 * action row. Leaves [peek] of space so the next row is visible below.
 */
@UnstableApi
@Composable
fun DetailHeader(
    previewKey: String,
    previewUrl: String?,
    previewActive: Boolean,
    backdropUrl: String,
    logoUrl: String?,
    title: String,
    meta: String,
    description: String?,
    modifier: Modifier = Modifier,
    progress: Float? = null,
    progressLabel: String? = null,
    credits: List<String> = emptyList(),
    peek: Dp = 120.dp,
    actions: @Composable RowScope.() -> Unit
) {
    val preview = LocalPreviewPlayer.current
    val epoch by (preview?.epoch ?: remember { kotlinx.coroutines.flow.MutableStateFlow(0) }).collectAsState()
    LaunchedEffect(previewActive, previewKey, epoch) {
        val p = preview ?: return@LaunchedEffect
        if (previewActive) p.request(previewKey, previewUrl, revealDelayMs = 1_800, dwellMs = 600) else p.cancel(previewKey)
    }
    val screenH = LocalConfiguration.current.screenHeightDp.dp
    Box(modifier.fillMaxWidth().height(screenH - peek)) {
        SizedImage(backdropUrl, 960.dp, 540.dp, Modifier.fillMaxSize(), alignment = Alignment.TopCenter, crossfadeMs = 300)
        PreviewVideo(previewKey, Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().background(Tv.LeftScrim))
        Box(Modifier.fillMaxSize().background(Tv.HeroBottomScrim))
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .padding(start = Tv.Keyline + 8.dp, bottom = 24.dp)
                .fillMaxWidth(0.52f)
        ) {
            TitleLogo(
                logoUrl = logoUrl,
                title = title,
                maxWidth = 400.dp,
                maxHeight = 140.dp,
                textStyle = TextStyle(color = Color.White, fontSize = 46.sp, fontWeight = FontWeight.Black,
                    lineHeight = 48.sp, shadow = Shadow(Color.Black, blurRadius = 12f))
            )
            if (meta.isNotBlank()) {
                Text(meta, color = Tv.TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, modifier = Modifier.padding(top = 14.dp))
            }
            if (progress != null && progress > 0f) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 10.dp)) {
                    Box(Modifier.width(180.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(Color(0x55FFFFFF))) {
                        Box(Modifier.fillMaxWidth(progress.coerceIn(0.02f, 1f)).fillMaxHeight().background(Tv.Red))
                    }
                    progressLabel?.let { Text(it, color = Tv.TextSecondary, fontSize = 14.sp, modifier = Modifier.padding(start = 12.dp)) }
                }
            }
            description?.let {
                Text(it, color = Tv.TextSecondary, fontSize = 16.sp, lineHeight = 22.sp, maxLines = 3,
                    overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 10.dp))
            }
            credits.forEach {
                Text(it, color = Tv.TextMuted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp))
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 18.dp),
                content = actions
            )
        }
    }
}
