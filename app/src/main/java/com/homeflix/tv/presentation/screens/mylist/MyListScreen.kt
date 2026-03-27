package com.homeflix.tv.presentation.screens.mylist

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.homeflix.tv.domain.model.Media
import com.homeflix.tv.presentation.components.NetflixSideNavigation
import com.homeflix.tv.presentation.navigation.Screen
import com.homeflix.tv.presentation.theme.NetflixRed
import com.homeflix.tv.presentation.theme.TextPrimary
import com.homeflix.tv.presentation.theme.TextSecondary
import com.homeflix.tv.presentation.components.ContinueWatchingRow
import com.homeflix.tv.util.ApiUtils
import kotlinx.coroutines.delay

@Composable
fun MyListScreen(
    navController: NavController,
    viewModel: MyListViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val continueWatchingFocusRequester = remember { FocusRequester() }
    val myListFocusRequester = remember { FocusRequester() }
    
    // Auto-focus content when loaded
    LaunchedEffect(uiState) {
        if (uiState is MyListUiState.Success) {
            val successState = uiState as MyListUiState.Success
            delay(300)
            try {
                if (successState.continueWatching.isNotEmpty()) {
                    continueWatchingFocusRequester.requestFocus()
                } else if (successState.movies.isNotEmpty()) {
                    myListFocusRequester.requestFocus()
                }
            } catch (_: Exception) {}
        }
    }
    
    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Side Navigation
        NetflixSideNavigation(
            selectedRoute = "my-list",
            onNavigate = { route ->
                navController.navigate(route) {
                    popUpTo(Screen.Home.route) { inclusive = false }
                    launchSingleTop = true
                }
            },
            onNavigateToContent = {
                try {
                    val successState = uiState as? MyListUiState.Success
                    if (successState?.continueWatching?.isNotEmpty() == true) {
                        continueWatchingFocusRequester.requestFocus()
                    } else {
                        myListFocusRequester.requestFocus()
                    }
                } catch (_: Exception) {}
            }
        )
        
        // Main content
        when (val currentState = uiState) {
            is MyListUiState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = NetflixRed)
                }
            }
            
            is MyListUiState.Error -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Failed to load My List",
                            style = MaterialTheme.typography.headlineMedium,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = currentState.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { viewModel.loadMyList() }) {
                            Text("Retry")
                        }
                    }
                }
            }
            
            is MyListUiState.Success -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(start = 24.dp, top = 24.dp, end = 24.dp)
                ) {
                    // Title
                    item {
                        Text(
                            text = "My List",
                            style = MaterialTheme.typography.headlineLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            ),
                            modifier = Modifier.padding(bottom = 24.dp)
                        )
                    }
                    
                    // Continue Watching Section
                    if (currentState.continueWatching.isNotEmpty()) {
                        item {
                            ContinueWatchingRow(
                                continueWatchingItems = currentState.continueWatching,
                                onPlay = { media, startTimeMs ->
                                    navController.navigate(Screen.VideoPlayer.createRoute(media.id, startTime = startTimeMs))
                                },
                                onInfo = { media ->
                                    navController.navigate(Screen.Details.createRoute(media.id.toString()))
                                },
                                focusRequester = continueWatchingFocusRequester,
                                mediaTypeFilter = null, // Allow both movies and TV shows
                                applyHorizontalPadding = false, // Don't apply padding, we handle it in LazyColumn
                                modifier = Modifier.padding(bottom = 24.dp)
                            )
                        }
                    }
                    
                    // My List Grid
                    if (currentState.movies.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(300.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Your list is empty.\nBrowse movies and add them to your list!",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = TextSecondary,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                        }
                    } else {
                        // Grid items - render as individual items in LazyColumn
                        items(
                            items = currentState.movies.chunked(6), // 6 items per row
                            key = { it.first().id }
                        ) { rowItems ->
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 16.dp) // Add spacing between rows
                            ) {
                                rowItems.forEach { media ->
                                    MyListCard(
                                        media = media,
                                        onClick = {
                                            navController.navigate(Screen.Details.createRoute(media.id.toString()))
                                        },
                                        focusRequester = if (media == currentState.movies.firstOrNull()) myListFocusRequester else null,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                // Fill empty slots
                                repeat(6 - rowItems.size) {
                                    Spacer(modifier = Modifier.weight(1f))
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
private fun MyListCard(
    media: Media,
    onClick: () -> Unit,
    focusRequester: FocusRequester? = null,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }
    
    val scale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isFocused) 1.05f else 1.0f,
        animationSpec = androidx.compose.animation.core.tween(durationMillis = 200),
        label = "card_scale"
    )
    
    Box(
        modifier = modifier
            .aspectRatio(2f / 3f)
            .graphicsLayer(scaleX = scale, scaleY = scale)
            .then(
                if (focusRequester != null) Modifier.focusRequester(focusRequester)
                else Modifier
            )
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .onKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown &&
                    (keyEvent.key == Key.Enter || keyEvent.key == Key.DirectionCenter ||
                     keyEvent.nativeKeyEvent.keyCode == android.view.KeyEvent.KEYCODE_DPAD_CENTER)
                ) {
                    onClick()
                    true
                } else false
            }
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xFF1A1A1A))
    ) {
        // Poster
        AsyncImage(
            model = ApiUtils.getPosterUrl(media),
            contentDescription = media.title,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
        
        // Focus border
        if (isFocused) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.Transparent)
                    .then(
                        Modifier.background(Color.Transparent)
                    )
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.Transparent)
                    .padding(0.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(6.dp))
                        .then(
                            Modifier.background(Color.Transparent)
                        )
                )
            }
        }
        
        // Title at bottom
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(
                    androidx.compose.ui.graphics.Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f))
                    )
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
        
        // White border on focus
        if (isFocused) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.Transparent)
                    .then(
                        Modifier.padding(0.dp)
                    )
            )
            // Border overlay
            androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                drawRoundRect(
                    color = Color.White,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.dp.toPx()),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(6.dp.toPx())
                )
            }
        }
    }
}
