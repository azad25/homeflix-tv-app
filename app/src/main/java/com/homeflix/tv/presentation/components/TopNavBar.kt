package com.homeflix.tv.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.homeflix.tv.presentation.theme.NetflixRed
import com.homeflix.tv.presentation.theme.TextPrimary

@Composable
fun TopNavBar(
    currentScreen: String,
    onNavigateToSearch: () -> Unit,
    onNavigateToBrowse: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.8f))
            .padding(horizontal = 48.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Logo
        Text(
            text = "HOMEFLIX",
            style = MaterialTheme.typography.headlineLarge.copy(
                fontWeight = FontWeight.Bold,
                color = NetflixRed
            )
        )
        
        // Navigation Items
        Row(
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            NavItem(
                text = "Home",
                isSelected = currentScreen == "Home",
                onClick = { /* Already on home */ }
            )
            
            NavItem(
                text = "Movies",
                isSelected = currentScreen == "Movies",
                onClick = onNavigateToBrowse
            )
            
            NavItem(
                text = "TV Shows",
                isSelected = currentScreen == "TV Shows",
                onClick = onNavigateToBrowse
            )
            
            NavItem(
                text = "My List",
                isSelected = currentScreen == "My List",
                onClick = { /* TODO: Navigate to My List */ }
            )
            
            NavItem(
                text = "Search",
                isSelected = currentScreen == "Search",
                onClick = onNavigateToSearch
            )
        }
    }
}

@Composable
private fun NavItem(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }
    
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(
                if (isFocused) Color.White.copy(alpha = 0.1f) 
                else Color.Transparent
            )
            .focusable()
            .onFocusChanged { isFocused = it.isFocused }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                color = if (isSelected) TextPrimary else TextPrimary.copy(alpha = 0.7f)
            )
        )
    }
}