package com.homeflix.tv.presentation

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.activity.ComponentActivity
import androidx.media3.common.util.UnstableApi
import com.homeflix.tv.core.PreviewPlayer
import com.homeflix.tv.presentation.navigation.HomeFlixNavigation
import com.homeflix.tv.presentation.theme.HomeFlixTVTheme
import com.homeflix.tv.presentation.ui.Tv
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@UnstableApi
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var previewPlayer: PreviewPlayer
    @Inject lateinit var castController: com.homeflix.tv.cast.CastController

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        setContent {
            // Pin font scale to 1.0 so the TV's accessibility font size can't
            // inflate the 10-foot typography (Netflix/Prime do the same).
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density = base.density, fontScale = 1f)) {
                HomeFlixTVTheme {
                    Box(Modifier.fillMaxSize().background(Tv.Background)) {
                        HomeFlixNavigation(previewPlayer = previewPlayer, castController = castController)
                    }
                }
            }
        }
    }
}
