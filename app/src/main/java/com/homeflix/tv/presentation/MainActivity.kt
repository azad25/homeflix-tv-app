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
    @Inject lateinit var updater: com.homeflix.tv.update.AppUpdater

    override fun onStart() {
        super.onStart()
        updater.onForeground()
    }

    override fun onStop() {
        super.onStop()
        // You may have just watched something: refresh HomeFlix's home-screen row.
        com.homeflix.tv.launcher.HomeScreenSync.refreshSoon(this)
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        com.homeflix.tv.launcher.DeepLinks.offer(intent) // a card on the TV home screen
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) com.homeflix.tv.launcher.DeepLinks.offer(intent)

        setContent {
            // Pin font scale to 1.0 so the TV's accessibility font size can't
            // inflate the 10-foot typography (Netflix/Prime do the same).
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density = base.density, fontScale = 1f)) {
                HomeFlixTVTheme {
                    // Lists that follow focus on their own glide smoothly; browse rows
                    // and pages opt into their own keyline scrolling (ManualFocusScroll).
                    com.homeflix.tv.presentation.ui.SmoothFocusScroll {
                        Box(Modifier.fillMaxSize().background(Tv.Background)) {
                            HomeFlixNavigation(previewPlayer = previewPlayer, castController = castController, updater = updater)
                        }
                    }
                }
            }
        }
    }
}
