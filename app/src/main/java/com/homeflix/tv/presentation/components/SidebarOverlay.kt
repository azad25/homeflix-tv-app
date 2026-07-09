package com.homeflix.tv.presentation.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.zIndex
import kotlinx.coroutines.delay

/**
 * Full-bleed detail screens (movie / series) stay cinematic, but pressing
 * LEFT/Back reveals the nav rail as an overlay that slides in from the left.
 * RIGHT/Back on the rail dismisses it back to the content.
 *
 * Usage: wrap the screen content, hoist `visible` state, and have the
 * content's leftmost focus handler set `visible = true` on LEFT/Back.
 */
@Composable
fun SidebarOverlay(
    visible: Boolean,
    selectedRoute: String,
    onNavigate: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val railFocus = remember { FocusRequester() }

    // Move focus into the rail once it's shown so D-pad drives it.
    LaunchedEffect(visible) {
        if (visible) {
            repeat(15) {
                try {
                    railFocus.requestFocus(); return@LaunchedEffect
                } catch (_: Exception) {
                    delay(40)
                }
            }
        }
    }

    if (visible) {
        // Scrim so the rail reads clearly over the hero
        Box(
            modifier = Modifier
                .fillMaxSize()
                .zIndex(50f)
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(Color.Black.copy(alpha = 0.85f), Color.Transparent),
                        endX = 260f
                    )
                )
        )
    }

    AnimatedVisibility(
        visible = visible,
        enter = slideInHorizontally(tween(200)) { -it } + fadeIn(tween(200)),
        exit = slideOutHorizontally(tween(180)) { -it } + fadeOut(tween(180)),
        modifier = Modifier.zIndex(60f)
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) {
            NetflixSideNavigation(
                selectedRoute = selectedRoute,
                onNavigate = { route ->
                    onDismiss()
                    onNavigate(route)
                },
                onNavigateToContent = onDismiss,
                modifier = Modifier.focusRequester(railFocus)
            )
        }
    }
}
