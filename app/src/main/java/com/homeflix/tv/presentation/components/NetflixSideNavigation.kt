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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

data class NavItem(
    val icon: ImageVector,
    val route: String,
    val contentDescription: String
)

/**
 * Netflix-style side navigation for Android TV
 * - 60dp wide icon-only vertical bar
 * - White icons on black background
 * - Red background for selected item
 * - White border for focused item
 */
@Composable
fun NetflixSideNavigation(
    selectedRoute: String,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val navItems = listOf(
        NavItem(Icons.Default.Search, "search", "Search"),
        NavItem(Icons.Default.Home, "home", "Home"),
        NavItem(Icons.Default.List, "browse", "Browse Movies"),
        NavItem(Icons.Default.List, "my_list", "My List")
    )
    
    Column(
        modifier = modifier
            .width(60.dp)
            .fillMaxHeight()
            .background(Color.Black)
            .padding(vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        navItems.forEach { item ->
            NavIconButton(
                item = item,
                isSelected = selectedRoute == item.route,
                onClick = { onNavigate(item.route) }
            )
        }
    }
}

@Composable
private fun NavIconButton(
    item: NavItem,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    
    Box(
        modifier = Modifier
            .size(44.dp)
            .background(
                color = when {
                    isSelected -> Color(0xFFE50914) // Netflix Red
                    else -> Color.Transparent
                },
                shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp)
            )
            .border(
                width = if (isFocused) 3.dp else 0.dp,
                color = if (isFocused) Color.White else Color.Transparent,
                shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp)
            )
            .focusable()
            .clickable(onClick = onClick)
            .onFocusChanged { isFocused = it.isFocused },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = item.icon,
            contentDescription = item.contentDescription,
            tint = Color.White,
            modifier = Modifier.size(24.dp)
        )
    }
}
