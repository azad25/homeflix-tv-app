package com.homeflix.tv.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.homeflix.tv.domain.model.Media
import com.homeflix.tv.presentation.theme.NetflixRed
import com.homeflix.tv.presentation.theme.TextPrimary
import com.homeflix.tv.presentation.theme.TextSecondary
import com.homeflix.tv.util.ApiUtils

data class ContinueWatchingItem(
    val media: Media,
    val progress: Float, // 0.0 to 1.0
    val lastWatched: String? = null
)

@Composable
fun ContinueWatchingRow(
    continueWatchingItems: List<ContinueWatchingItem>,
    onPlay: (Media) -> Unit,
    onInfo: (Media) -> Unit,
    modifier: Modifier = Modifier
) {
    // Debug logging with null safety
    android.util.Log.d("ContinueWatchingRow", "Rendering with ${continueWatchingItems?.size ?: 0} items")
    
    // Null safety check
    if (continueWatchingItems.isNullOrEmpty()) {
        android.util.Log.d("ContinueWatchingRow", "No items to display")
        // Show debug info instead of returning early
        Column(
            modifier = modifier.padding(horizontal = 60.dp)
        ) {
            Text(
                text = "Continue Watching (Debug)",
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                ),
                modifier = Modifier.padding(bottom = 16.dp)
            )
            Text(
                text = "No items loaded. Check logs for API errors.",
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = TextSecondary
                )
            )
        }
        return
    }
    
    // Additional validation - filter out any invalid items
    val validItems = remember(continueWatchingItems) {
        continueWatchingItems.filterNotNull().filter { item ->
            try {
                item.media != null && 
                item.media.id > 0 && 
                !item.media.title.isNullOrBlank() &&
                item.progress >= 0f &&
                item.progress <= 1f
            } catch (e: Exception) {
                android.util.Log.w("ContinueWatchingRow", "Invalid item filtered out: ${e.message}")
                false
            }
        }
    }
    
    if (validItems.isEmpty()) {
        android.util.Log.w("ContinueWatchingRow", "All items were invalid after filtering")
        return
    }
    
    // Render the continue watching section
    Column(
        modifier = modifier.padding(horizontal = 60.dp)
    ) {
        // Section Title
        Text(
            text = "Continue Watching",
            style = MaterialTheme.typography.headlineSmall.copy(
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            ),
            modifier = Modifier.padding(bottom = 16.dp)
        )
        
        // Continue Watching Items
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(end = 60.dp)
        ) {
            items(validItems) { item ->
                ContinueWatchingCard(
                    item = item,
                    onPlay = { 
                        try {
                            onPlay(item.media) 
                        } catch (e: Exception) {
                            android.util.Log.e("ContinueWatchingRow", "Error in onPlay: ${e.message}")
                        }
                    },
                    onInfo = { 
                        try {
                            onInfo(item.media) 
                        } catch (e: Exception) {
                            android.util.Log.e("ContinueWatchingRow", "Error in onInfo: ${e.message}")
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun ContinueWatchingCard(
    item: ContinueWatchingItem,
    onPlay: () -> Unit,
    onInfo: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }
    
    Box(
        modifier = modifier
            .width(320.dp)
            .height(180.dp)
            .clip(RoundedCornerShape(8.dp))
            .border(
                width = if (isFocused) 3.dp else 0.dp,
                color = if (isFocused) Color.White else Color.Transparent,
                shape = RoundedCornerShape(8.dp)
            )
            .focusable()
            .onFocusChanged { isFocused = it.isFocused }
            .clickable { onPlay() }
    ) {
        // Background Image
        AsyncImage(
            model = ApiUtils.getThumbnailUrl(item.media),
            contentDescription = item.media.title,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
        
        // Dark overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.7f)
                        )
                    )
                )
        )
        
        // Progress Bar
        LinearProgressIndicator(
            progress = item.progress,
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .align(Alignment.BottomCenter),
            color = NetflixRed,
            trackColor = Color.White.copy(alpha = 0.3f)
        )
        
        // Play Button (center)
        if (isFocused) {
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .align(Alignment.Center)
                    .background(
                        Color.White.copy(alpha = 0.9f),
                        RoundedCornerShape(30.dp)
                    )
                    .clickable { onPlay() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Play",
                    tint = Color.Black,
                    modifier = Modifier.size(32.dp)
                )
            }
        }
        
        // Title and Progress Info
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp)
                .fillMaxWidth()
        ) {
            Text(
                text = item.media.title,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            
            Spacer(modifier = Modifier.height(4.dp))
            
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${(item.progress * 100).toInt()}% watched",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                )
                
                item.lastWatched?.let { lastWatched ->
                    Text(
                        text = "• $lastWatched",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 11.sp,
                            color = Color.White.copy(alpha = 0.6f)
                        )
                    )
                }
            }
        }
        
        // Info Button (top right)
        if (isFocused) {
            IconButton(
                onClick = onInfo,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .background(
                        Color.Black.copy(alpha = 0.6f),
                        RoundedCornerShape(20.dp)
                    )
            ) {
                Text(
                    text = "i",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}