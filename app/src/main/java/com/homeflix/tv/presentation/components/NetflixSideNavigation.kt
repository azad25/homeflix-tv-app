package com.homeflix.tv.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
 * Professional focus management with proper state handling
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
        NavItem(Icons.Default.List, "browse", "Browse Movies")
    )
    
    // Professional focus state management
    var focusedIndex by remember { mutableStateOf(navItems.indexOfFirst { it.route == selectedRoute }.takeIf { it >= 0 } ?: 1) }
    val focusRequesters = remember(navItems.size) { List(navItems.size) { FocusRequester() } }
    var isInitialized by remember { mutableStateOf(false) }
    
    // CRITICAL FIX: NEVER auto-focus sidebar - only focus when LEFT arrow pressed
    LaunchedEffect(selectedRoute) {
        if (!isInitialized) {
            val targetIndex = navItems.indexOfFirst { it.route == selectedRoute }.takeIf { it >= 0 } ?: 1
            focusedIndex = targetIndex
            // ABSOLUTELY NO auto-focus - let content have focus
            isInitialized = true
        }
    }
    
    // Professional D-pad navigation with proper bounds checking
    Column(
        modifier = modifier
            .width(48.dp)
            .fillMaxHeight()
            .background(Color.Black.copy(alpha = 0.9f))
            .padding(vertical = 16.dp)
            .onKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown) {
                    when (keyEvent.key) {
                        Key.DirectionUp -> {
                            val newIndex = (focusedIndex - 1).coerceAtLeast(0)
                            if (newIndex != focusedIndex) {
                                focusedIndex = newIndex
                                try {
                                    focusRequesters[newIndex].requestFocus()
                                } catch (e: Exception) {
                                    // Ignore focus errors
                                }
                            }
                            true
                        }
                        Key.DirectionDown -> {
                            val newIndex = (focusedIndex + 1).coerceAtMost(navItems.size - 1)
                            if (newIndex != focusedIndex) {
                                focusedIndex = newIndex
                                try {
                                    focusRequesters[newIndex].requestFocus()
                                } catch (e: Exception) {
                                    // Ignore focus errors
                                }
                            }
                            true
                        }
                        Key.DirectionRight -> {
                            onNavigateToContent?.invoke()
                            true
                        }
                        Key.DirectionCenter, Key.Enter -> {
                            onNavigate(navItems[focusedIndex].route)
                            true
                        }
                        else -> false
                    }
                } else false
            },
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        navItems.forEachIndexed { index, item ->
            NavIconButton(
                item = item,
                isSelected = selectedRoute == item.route,
                isFocused = focusedIndex == index,
                onClick = { 
                    focusedIndex = index
                    onNavigate(item.route) 
                },
                focusRequester = focusRequesters[index],
                onFocusChanged = { focused ->
                    if (focused && focusedIndex != index) {
                        focusedIndex = index
                    }
                }
            )
        }
    }
}

@Composable
private fun NavIconButton(
    item: NavItem,
    isSelected: Boolean,
    isFocused: Boolean,
    onClick: () -> Unit,
    focusRequester: FocusRequester,
    onFocusChanged: (Boolean) -> Unit
) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .background(
                color = when {
                    isSelected -> Color(0xFFE50914) // Netflix Red
                    isFocused -> Color.White.copy(alpha = 0.1f)
                    else -> Color.Transparent
                },
                shape = androidx.compose.foundation.shape.RoundedCornerShape(6.dp)
            )
            .border(
                width = if (isFocused) 2.dp else 0.dp,
                color = if (isFocused) Color.White else Color.Transparent,
                shape = androidx.compose.foundation.shape.RoundedCornerShape(6.dp)
            )
            .focusRequester(focusRequester)
            .focusable()
            .clickable(onClick = onClick)
            .onFocusChanged { onFocusChanged(it.isFocused) },
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
