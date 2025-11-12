package com.homeflix.tv.presentation.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = NetflixRed,
    secondary = NetflixDarkGray,
    tertiary = NetflixLightGray,
    background = NetflixBlack,
    surface = NetflixDarkGray,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color.White,
    onSurface = Color.White,
)

private val LightColorScheme = lightColorScheme(
    primary = NetflixRed,
    secondary = NetflixDarkGray,
    tertiary = NetflixLightGray,
    background = Color.White,
    surface = Color.White,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.Black,
    onBackground = Color.Black,
    onSurface = Color.Black,
)

@Composable
fun HomeFlixTVTheme(
    darkTheme: Boolean = true, // ALWAYS DARK THEME FOR TV
    content: @Composable () -> Unit
) {
    // FORCE DARK THEME ALWAYS
    val colorScheme = DarkColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}