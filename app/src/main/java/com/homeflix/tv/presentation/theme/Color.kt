package com.homeflix.tv.presentation.theme

import androidx.compose.ui.graphics.Color

// Netflix Brand Colors
val NetflixRed = Color(0xFFE50914)
val NetflixBlack = Color(0xFF000000)
val NetflixDarkGray = Color(0xFF141414)
val NetflixMediumGray = Color(0xFF2F2F2F)
val NetflixLightGray = Color(0xFF564D4D)
val NetflixWhite = Color(0xFFFFFFFF)

// Additional UI Colors
val FocusedBorder = Color(0xFFFFFFFF)
val UnfocusedBorder = Color(0xFF333333)
val OverlayBackground = Color(0x80000000)
val CardBackground = Color(0xFF1A1A1A)
val TextPrimary = Color(0xFFFFFFFF)
val TextSecondary = Color(0xFFB3B3B3)
val TextTertiary = Color(0xFF808080)

// Status Colors
val SuccessGreen = Color(0xFF46D369)
val WarningYellow = Color(0xFFFFC107)
val ErrorRed = Color(0xFFDC3545)
val InfoBlue = Color(0xFF17A2B8)

// ── HomeFlix design system (RED + BLACK) ────────────────────────────
// Names kept as "Prime*" only so existing references compile; the VALUES
// are HomeFlix black canvas + Netflix-red accent. Changing these recolors
// the whole app.
val PrimeBg = Color(0xFF0B0B0B)          // page background (near-black)
val PrimeBgDeep = Color(0xFF000000)      // hero gradient target (black)
val PrimeSurface = Color(0xFF1A1A1A)     // cards, chips
val PrimeSurfaceHigh = Color(0xFF2A2A2A) // focused surface
val PrimeBlue = Color(0xFFE50914)        // CTA / focus accent = Netflix red
val PrimeBlueDark = Color(0xFFB0060F)
val PrimeTextDim = Color(0xFFB3B3B3)     // secondary text (neutral grey)
val RatingGold = Color(0xFFFFB43A)       // star ratings
val BadgeOutline = Color(0xFF3A3A3A)     // certification chip border
val Top10Stroke = Color(0xFF444444)      // outlined big numbers