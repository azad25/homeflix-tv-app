package com.homeflix.tv.presentation.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.*
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

data class NavItem(
    val icon: ImageVector,
    val route: String,
    val contentDescription: String
)

/**
 * NETFLIX-LEVEL Android TV Side Navigation
 * COMPLETELY PASSIVE - Only responds to direct focus, never steals it
 */
@Composable
fun NetflixSideNavigation(
    selectedRoute: String,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier,
    onNavigateToContent: (() -> Unit)? = null
) {
    val navItems = listOf(
        NavItem(Icons.Default.Search, "search", "Search"),
        NavItem(Icons.Default.Home, "home", "Home"),
        NavItem(Icons.Default.List, "browse", "Browse Movies"),
        NavItem(Icons.Default.Tv, "tv-shows", "TV Shows")
    )
    
    // NETFLIX PRINCIPLE: Each icon is independently focusable
    // NO container focus management, NO auto-focus, NO key interception
    Column(
        modifier = modifier
            .width(48.dp)
            .fillMaxHeight()
            .background(Color.Black.copy(alpha = 0.9f)),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        navItems.forEachIndexed { index, item ->
            NetflixNavIcon(
                item = item,
                isSelected = selectedRoute == item.route,
                onClick = { onNavigate(item.route) },
                onNavigateRight = onNavigateToContent,
                // Focus the home icon (middle one) when sidebar is focused
                autoFocus = item.route == selectedRoute
            )
            if (index < navItems.size - 1) {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

/**
 * Individual Netflix-style navigation icon - COMPLETELY INDEPENDENT
 */
@Composable
private fun NetflixNavIcon(
    item: NavItem,
    isSelected: Boolean,
    onClick: () -> Unit,
    onNavigateRight: (() -> Unit)? = null,
    autoFocus: Boolean = false
) {
    var isFocused by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    
    // Netflix-style scale animation on focus
    val scale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isFocused) 1.5f else 1.0f,
        animationSpec = androidx.compose.animation.core.tween(durationMillis = 200),
        label = "nav_icon_scale"
    )
    
    // Auto-focus this icon when requested
    LaunchedEffect(autoFocus) {
        if (autoFocus) {
            delay(100) // Small delay to ensure UI is ready
            try {
                focusRequester.requestFocus()
            } catch (e: Exception) {
                // Ignore focus errors
            }
        }
    }
    
    Box(
        modifier = Modifier
            .size(36.dp)
            .scale(scale)
            .background(
                color = when {
                    isSelected -> Color(0xFFE50914) // Netflix Red
                    isFocused -> Color.White.copy(alpha = 0.2f)
                    else -> Color.Transparent
                },
                shape = androidx.compose.foundation.shape.RoundedCornerShape(6.dp)
            )
            .focusRequester(focusRequester)
            .focusable()
            .onFocusChanged { isFocused = it.isFocused }
            .clickable(onClick = onClick)
            .onKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown && keyEvent.key == Key.DirectionRight) {
                    // Only handle RIGHT arrow to exit sidebar
                    onNavigateRight?.invoke()
                    true
                } else false
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = item.icon,
            contentDescription = item.contentDescription,
            tint = Color.White,
            modifier = Modifier.size(20.dp)
        )
    }
}
