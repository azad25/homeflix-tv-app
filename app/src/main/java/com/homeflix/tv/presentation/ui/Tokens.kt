package com.homeflix.tv.presentation.ui

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Design tokens for the v5 Netflix-style TV UI. */
object Tv {
    val Background = Color(0xFF141414)
    val Surface = Color(0xFF1F1F1F)
    val SurfaceHigh = Color(0xFF2B2B2B)
    val Red = Color(0xFFE50914)
    val TextPrimary = Color(0xFFFFFFFF)
    val TextSecondary = Color(0xFFBCBCBC)
    val TextMuted = Color(0xFF8C8C8C)
    val FocusRing = Color(0xFFFFFFFF)
    val PillFocused = Color(0xFFFFFFFF)
    val PillIdle = Color(0x33FFFFFF)

    /** Left keyline every row's focused tile is pinned to. */
    val Keyline = 48.dp
    val TileHeight = 196.dp
    val TileGap = 10.dp
    val TileCorner = 6.dp
    val NavHeight = 64.dp

    /** Netflix-like "emphasized" ease-out used for every focus motion. */
    val Ease = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    const val FocusMotionMs = 220
    const val ScrollMotionMs = 260

    val BottomScrim = Brush.verticalGradient(0f to Color.Transparent, 1f to Color(0xE6000000))
    val LeftScrim = Brush.horizontalGradient(
        0f to Color(0xF0141414), 0.45f to Color(0x99141414), 0.75f to Color.Transparent
    )
    val HeroBottomScrim = Brush.verticalGradient(
        0f to Color.Transparent, 0.6f to Color(0x00141414), 1f to Background
    )
}
