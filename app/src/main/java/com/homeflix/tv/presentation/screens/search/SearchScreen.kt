package com.homeflix.tv.presentation.screens.search

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import com.homeflix.tv.presentation.components.PosterCard
import com.homeflix.tv.presentation.components.ThumbLogoCard
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.key.*
import androidx.compose.ui.zIndex
import com.homeflix.tv.domain.model.MediaType
import kotlinx.coroutines.delay
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.homeflix.tv.domain.model.Media
import com.homeflix.tv.presentation.components.NetflixSideNavigation
import com.homeflix.tv.presentation.navigation.Screen
import com.homeflix.tv.presentation.theme.PrimeBg
import com.homeflix.tv.presentation.theme.PrimeBlue
import com.homeflix.tv.presentation.theme.TextPrimary
import com.homeflix.tv.presentation.theme.TextSecondary
import com.homeflix.tv.util.ApiUtils

enum class FocusArea {
    SIDEBAR, KEYBOARD, GENRES, CONTENT
}

@Composable
fun SearchScreen(
    navController: NavController,
    viewModel: SearchViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val genres by viewModel.genres.collectAsState()
    val topSearches by viewModel.topSearches.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    var currentFocusArea by remember { mutableStateOf(FocusArea.KEYBOARD) }
    
    // Focus requesters for different sections
    val sideNavFocusRequester = remember { FocusRequester() }
    val keyboardFocusRequester = remember { FocusRequester() }
    val genresFocusRequester = remember { FocusRequester() }
    val contentFocusRequester = remember { FocusRequester() }
    
    // QWERTY Virtual keyboard layout (compact)
    val keyboardRows = listOf(
        listOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p"),
        listOf("a", "s", "d", "f", "g", "h", "j", "k", "l"),
        listOf("z", "x", "c", "v", "b", "n", "m"),
        listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0")
    )
    
    // Handle search when query changes
    LaunchedEffect(searchQuery) {
        if (searchQuery.isNotBlank()) {
            viewModel.searchMedia(searchQuery)
        } else {
            viewModel.clearSearch()
        }
    }
    
    // Auto-focus keyboard on screen load
    LaunchedEffect(Unit) {
        delay(400)
        try {
            keyboardFocusRequester.requestFocus()
        } catch (_: Exception) {}
    }
    
    // TV-optimized layout
    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(PrimeBg)
    ) {
        // SIDE NAVIGATION (48dp width)
        NetflixSideNavigation(
            selectedRoute = "search",
            onNavigate = { route ->
                navController.navigate(route) {
                    popUpTo(Screen.Home.route) { inclusive = false }
                    launchSingleTop = true
                }
            },
            onNavigateToContent = {
                currentFocusArea = FocusArea.KEYBOARD
                try {
                    keyboardFocusRequester.requestFocus()
                } catch (_: Exception) {}
            }
        )
        
        // Main Content Area
        Row(
            modifier = Modifier.fillMaxSize()
        ) {
            // LEFT PANEL - Virtual Keyboard & Genres - NETFLIX PRINCIPLE: No container focus
            Column(
                modifier = Modifier
                    .width(320.dp) // Reduced from 400dp to 320dp
                    .fillMaxHeight()
                    .background(Color.Black.copy(alpha = 0.9f))
                    .padding(16.dp) // Reduced padding from 24dp to 16dp
            ) {
                // Search Input Display
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color.Black.copy(alpha = 0.7f)
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (searchQuery.isEmpty()) "Search..." else searchQuery,
                            color = if (searchQuery.isEmpty()) Color.Gray else Color.White,
                            fontSize = 16.sp,
                            modifier = Modifier.weight(1f)
                        )
                        if (searchQuery.isNotEmpty()) {
                            IconButton(
                                onClick = { searchQuery = "" },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = "Clear",
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
                
                // Virtual Keyboard (Compact)
                Column(
                    modifier = Modifier.padding(bottom = 16.dp) // Reduced from 24dp
                ) {
                    keyboardRows.forEachIndexed { rowIndex, row ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(2.dp) // Reduced from 4dp
                        ) {
                            row.forEachIndexed { keyIndex, key ->
                                VirtualKey(
                                    key = key,
                                    onClick = { searchQuery += key },
                                    modifier = Modifier.weight(1f),
                                    // Attach focusRequester to "Q" key (first key in first row)
                                    focusRequester = if (rowIndex == 0 && keyIndex == 0) keyboardFocusRequester else null
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(2.dp)) // Reduced from 4dp
                    }
                    
                    // Special keys row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp) // Reduced from 8dp
                    ) {
                        // Space key
                        VirtualKey(
                            key = "space",
                            onClick = { searchQuery += " " },
                            modifier = Modifier.weight(2f),
                            isSpecial = true
                        )
                        
                        // Backspace key
                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .height(32.dp) // Reduced from 40dp to 32dp
                                .clickable {
                                    if (searchQuery.isNotEmpty()) {
                                        searchQuery = searchQuery.dropLast(1)
                                    }
                                },
                            colors = CardDefaults.cardColors(
                                containerColor = Color.Gray.copy(alpha = 0.3f)
                            ),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Backspace,
                                    contentDescription = "Backspace",
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
                
                // Genre Categories
                Column {
                    Text(
                        text = "Categories",
                        color = Color.White,
                        fontSize = 16.sp, // Reduced from 18sp
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 8.dp) // Reduced from 12dp
                    )
                    
                    genres.forEach { genre ->
                        GenreItem(
                            genre = genre.name,
                            onClick = { 
                                searchQuery = genre.name
                                viewModel.searchByGenre(genre.name)
                            }
                        )
                    }
                }
            }
            
            // RIGHT PANEL - Top Searches & Results with LEFT arrow navigation
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
            ) {
                if (searchQuery.isEmpty()) {
                    // Top Searches Section
                    Text(
                        text = "Top Searches",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 24.dp)
                    )
                    
                    // Top searches — landscape thumb+logo grid
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(4),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(topSearches.take(8), key = { it.id }) { media ->
                            PosterCard(
                                posterUrl = ApiUtils.getPosterUrl(media),
                                fallbackUrl = ApiUtils.getThumbnailUrl(media),
                                title = media.title,
                                subtitle = media.year?.toString(),
                                onClick = { navController.navigate(Screen.Details.createRoute(media.id.toString())) },
                                cacheKey = "poster_${media.id}"
                            )
                        }
                    }
                } else {
                    // Search Results
                    Text(
                        text = "Search Results",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    
                    val currentState = uiState
                    when (currentState) {
                        is SearchUiState.Loading -> {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(color = PrimeBlue)
                            }
                        }
                        
                        is SearchUiState.Success -> {
                            if (currentState.results.isEmpty()) {
                                // No results - show Top Searches instead of error message
                                Text(
                                    text = "Top Searches",
                                    color = Color.White,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(bottom = 24.dp)
                                )
                                
                                LazyVerticalGrid(
                                    columns = GridCells.Fixed(4),
                                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(14.dp),
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    items(topSearches.take(8), key = { it.id }) { media ->
                                        PosterCard(
                                posterUrl = ApiUtils.getPosterUrl(media),
                                fallbackUrl = ApiUtils.getThumbnailUrl(media),
                                title = media.title,
                                subtitle = media.year?.toString(),
                                onClick = { navController.navigate(Screen.Details.createRoute(media.id.toString())) },
                                cacheKey = "poster_${media.id}"
                            )
                                    }
                                }
                            } else {
                                val movieResults = currentState.results.filter { it.type == MediaType.MOVIE }
                                LazyVerticalGrid(
                                    columns = GridCells.Fixed(4),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(16.dp),
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    itemsIndexed(movieResults, key = { _, m -> m.id }) { index, media ->
                                        PosterCard(
                                            posterUrl = ApiUtils.getPosterUrl(media),
                                            fallbackUrl = ApiUtils.getThumbnailUrl(media),
                                            title = media.title,
                                            subtitle = media.year?.toString(),
                                            onClick = { navController.navigate(Screen.Details.createRoute(media.id.toString())) },
                                            cacheKey = "poster_${media.id}",
                                            modifier = if (index == 0) Modifier.focusRequester(contentFocusRequester) else Modifier
                                        )
                                    }
                                }
                            }
                        }
                        
                        else -> {
                            // Initial state or Error - show Top Searches (default content)
                            Text(
                                text = "Top Searches",
                                color = Color.White,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(bottom = 24.dp)
                            )
                            
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(4),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(topSearches.take(12), key = { it.id }) { media ->
                                    PosterCard(
                                        posterUrl = ApiUtils.getPosterUrl(media),
                                        fallbackUrl = ApiUtils.getThumbnailUrl(media),
                                        title = media.title,
                                        subtitle = media.year?.toString(),
                                        onClick = { navController.navigate(Screen.Details.createRoute(media.id.toString())) },
                                        cacheKey = "poster_${media.id}"
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VirtualKey(
    key: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isSpecial: Boolean = false,
    focusRequester: FocusRequester? = null
) {
    var isFocused by remember { mutableStateOf(false) }
    
    // Scale animation on focus
    val scale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isFocused) 1.5f else 1.0f,
        animationSpec = androidx.compose.animation.core.tween(durationMillis = 150),
        label = "virtual_key_scale"
    )
    
    Card(
        modifier = modifier
            .height(32.dp)
            .graphicsLayer(scaleX = scale, scaleY = scale)
            .then(
                if (focusRequester != null) {
                    Modifier.focusRequester(focusRequester)
                } else {
                    Modifier
                }
            )
            .onFocusChanged { focusState ->
                isFocused = focusState.isFocused
            }
            .focusable()
            .onKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown &&
                    (keyEvent.key == Key.Enter || keyEvent.key == Key.DirectionCenter ||
                     keyEvent.nativeKeyEvent.keyCode == android.view.KeyEvent.KEYCODE_DPAD_CENTER)) {
                    onClick()
                    true
                } else false
            }
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = when {
                isFocused -> Color.White.copy(alpha = 0.3f)
                isSpecial -> Color.Gray.copy(alpha = 0.5f)
                else -> Color.Gray.copy(alpha = 0.3f)
            }
        ),
        shape = RoundedCornerShape(6.dp)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (key == "space") "SPACE" else key.uppercase(),
                color = Color.White,
                fontSize = 12.sp, // Reduced from 14sp to 12sp
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun GenreItem(
    genre: String,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { focusState ->
                isFocused = focusState.isFocused
            }
            .focusable()
            .onKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown &&
                    (keyEvent.key == Key.Enter || keyEvent.key == Key.DirectionCenter ||
                     keyEvent.nativeKeyEvent.keyCode == android.view.KeyEvent.KEYCODE_DPAD_CENTER)) {
                    onClick()
                    true
                } else false
            }
            .clickable { onClick() }
            .padding(vertical = 8.dp, horizontal = 12.dp)
            .background(
                color = if (isFocused) PrimeBlue.copy(alpha = 0.8f) else Color.Transparent,
                shape = RoundedCornerShape(6.dp)
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = genre,
            color = if (isFocused) Color.White else Color.White.copy(alpha = 0.8f),
            fontSize = 13.sp,
            fontWeight = if (isFocused) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
private fun TopSearchCard(
    media: Media,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    
    // Scale animation on focus (matching homepage NetflixMediaCard)
    val scale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isFocused) 1.1f else 1.0f,
        animationSpec = androidx.compose.animation.core.tween(durationMillis = 200),
        label = "top_search_card_scale"
    )
    
    // Netflix-style card with border focus (matching homepage)
    Box(
        modifier = Modifier
            .aspectRatio(2f / 3f)
            .graphicsLayer(scaleX = scale, scaleY = scale)
            .onFocusChanged { focusState ->
                isFocused = focusState.isFocused
            }
            .focusable()
            .onKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown &&
                    (keyEvent.key == Key.Enter || keyEvent.key == Key.DirectionCenter ||
                     keyEvent.nativeKeyEvent.keyCode == android.view.KeyEvent.KEYCODE_DPAD_CENTER)) {
                    onClick()
                    true
                } else false
            }
            .clickable { onClick() }
            .then(
                if (isFocused) {
                    Modifier
                        .border(2.dp, Color.White, RoundedCornerShape(8.dp))
                        .zIndex(10f)
                } else {
                    Modifier.zIndex(1f)
                }
            )
    ) {
        Card(
            modifier = Modifier.fillMaxSize(),
            colors = CardDefaults.cardColors(
                containerColor = Color.Gray.copy(alpha = 0.3f)
            ),
            shape = RoundedCornerShape(8.dp)
        ) {
            Box(
                modifier = Modifier.fillMaxSize()
            ) {
                // Movie Poster
                AsyncImage(
                    model = ApiUtils.getPosterUrl(media),
                    contentDescription = media.title,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop
                )
                
                // Title overlay
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Color.Black.copy(alpha = 0.7f),
                            RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp)
                        )
                        .align(Alignment.BottomCenter)
                        .padding(8.dp)
                ) {
                    Text(
                        text = media.title,
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                
                // "TOP 10" badge (like in screenshot)
                Surface(
                    modifier = Modifier
                        .padding(8.dp)
                        .align(Alignment.TopEnd),
                    color = PrimeBlue,
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = "TOP\n10",
                        color = Color.White,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }
                
                // Rating badge if available
                if (media.rating > 0) {
                    Surface(
                        modifier = Modifier
                            .padding(8.dp)
                            .align(Alignment.TopStart),
                        color = Color.Black.copy(alpha = 0.7f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = null,
                                tint = Color(0xFFFFD700),
                                modifier = Modifier.size(10.dp)
                            )
                            Text(
                                text = String.format("%.1f", media.rating),
                                color = Color.White,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NetflixMovieCard(
    media: Media,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }
    
    // Subtle scale on focus
    val scale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isFocused) 1.05f else 1.0f,
        animationSpec = androidx.compose.animation.core.tween(durationMillis = 200),
        label = "search_movie_card_scale"
    )
    
    // Simple border-only focus style (matching home page)
    Box(
        modifier = modifier
            .aspectRatio(2f / 3f)
            .graphicsLayer(scaleX = scale, scaleY = scale)
            .onFocusChanged { focusState ->
                isFocused = focusState.isFocused
            }
            .focusable()
            .onKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown &&
                    (keyEvent.key == Key.Enter || keyEvent.key == Key.DirectionCenter ||
                     keyEvent.nativeKeyEvent.keyCode == android.view.KeyEvent.KEYCODE_DPAD_CENTER)) {
                    onClick()
                    true
                } else false
            }
            .clickable { onClick() }
            .then(
                if (isFocused) {
                    Modifier.border(2.dp, Color.White, RoundedCornerShape(6.dp))
                } else Modifier
            )
    ) {
        Card(
            modifier = Modifier.fillMaxSize(),
            shape = RoundedCornerShape(6.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color.Transparent
            ),
            elevation = CardDefaults.cardElevation(
                defaultElevation = if (isFocused) 6.dp else 2.dp
            )
        ) {
            Box {
                // Movie Poster
                AsyncImage(
                    model = ApiUtils.getPosterUrl(media),
                    contentDescription = media.title,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(6.dp)),
                    contentScale = ContentScale.Crop
                )
                
                // Title at bottom (always visible, no full overlay)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.8f)
                                )
                            ),
                            RoundedCornerShape(bottomStart = 6.dp, bottomEnd = 6.dp)
                        )
                        .padding(8.dp)
                ) {
                    Text(
                        text = media.title,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontWeight = FontWeight.Medium,
                            color = Color.White
                        ),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}