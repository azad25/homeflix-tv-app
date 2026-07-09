package com.homeflix.tv.presentation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.fragment.app.FragmentActivity
import androidx.media3.common.util.UnstableApi
import com.homeflix.tv.presentation.navigation.HomeFlixNavigation
import com.homeflix.tv.presentation.theme.HomeFlixTVTheme
import dagger.hilt.android.AndroidEntryPoint

@UnstableApi
@AndroidEntryPoint
class MainActivity : FragmentActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContent {
            // Pin the app's font scale to 1.0 so the TV's system "font size"
            // accessibility setting can't inflate our typography — this keeps
            // text sizes consistent across every TV (Netflix/Prime do the same).
            val base = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density = base.density, fontScale = 1f)
            ) {
                HomeFlixTVTheme {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        HomeFlixNavigation()
                    }
                }
            }
        }
    }
}