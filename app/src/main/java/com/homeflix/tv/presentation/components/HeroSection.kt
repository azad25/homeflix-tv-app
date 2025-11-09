package com.homeflix.tv.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.homeflix.tv.domain.model.Media
import com.homeflix.tv.presentation.theme.NetflixRed
import com.homeflix.tv.presentation.theme.TextPrimary
import com.homeflix.tv.util.ApiUtils

@Composable
fun HeroSection(
    media: Media,
    onPlayClick: (Media) -> Unit,
    onDetailsClick: (Media) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(400.dp)
    ) {
        // Background Image
        AsyncImage(
            model = ApiUtils.getBannerUrl(media),
            contentDescription = media.title,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
        
        // Gradient Overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.8f),
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.6f)
                        )
                    )
                )
        )
        
        // Content
        Column(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(48.dp)
                .fillMaxWidth(0.5f),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Title
            Text(
                text = media.title,
                style = MaterialTheme.typography.displayMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                ),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            
            // Description
            media.description?.let { description ->
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        color = TextPrimary.copy(alpha = 0.9f)
                    ),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
            
            // Metadata
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                media.year?.let { year ->
                    Text(
                        text = year.toString(),
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = TextPrimary.copy(alpha = 0.7f)
                        )
                    )
                }
                
                media.certification?.let { rating ->
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = Color.Gray.copy(alpha = 0.3f)
                    ) {
                        Text(
                            text = rating,
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = TextPrimary
                            ),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
                
                if (media.rating > 0) {
                    Text(
                        text = "★ ${String.format("%.1f", media.rating)}",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = TextPrimary.copy(alpha = 0.7f)
                        )
                    )
                }
            }
            
            // Action Buttons
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Play Button
                Button(
                    onClick = { onPlayClick(media) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NetflixRed
                    ),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.height(48.dp)
                ) {
                    Text(
                        text = "▶ Play",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                }
                
                // More Info Button
                OutlinedButton(
                    onClick = { onDetailsClick(media) },
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = TextPrimary
                    ),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.height(48.dp)
                ) {
                    Text(
                        text = "ⓘ More Info",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        }
    }
}