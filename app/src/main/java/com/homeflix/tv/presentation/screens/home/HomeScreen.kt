package com.homeflix.tv.presentation.screens.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.homeflix.tv.presentation.components.ContinueWatchingRow
import com.homeflix.tv.presentation.components.HeroSection
import com.homeflix.tv.presentation.navigation.Screen
import com.homeflix.tv.presentation.components.MediaRow
import com.homeflix.tv.presentation.components.TopNavBar

@Composable
fun HomeScreen(
    navController: NavController,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Top Navigation Bar
        TopNavBar(
            currentScreen = "Home",
            onNavigateToSearch = {
                navController.navigate(Screen.Search.route)
            },
            onNavigateToBrowse = {
                navController.navigate(Screen.Browse.route)
            }
        )
        val currentState = uiState
        when (currentState) {
            is HomeUiState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            
            is HomeUiState.Error -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Error loading content",
                            style = MaterialTheme.typography.headlineMedium,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = currentState.message,
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { viewModel.loadHomeContent() }
                        ) {
                            Text("Retry")
                        }
                    }
                }
            }
            
            is HomeUiState.Success -> {
                // Debug logging for UI updates
                android.util.Log.d("HomeScreen", "UI Success state: ${currentState.continueWatching.size} continue watching items")
                
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    // Hero Section
                    if (currentState.featuredMedia.isNotEmpty()) {
                        item {
                            HeroSection(
                                media = currentState.featuredMedia.first(),
                                onPlayClick = { media ->
                                    navController.navigate(Screen.VideoPlayer.createRoute(media.id))
                                },
                                onDetailsClick = { media ->
                                    navController.navigate(Screen.Details.createRoute(media.id.toString()))
                                }
                            )
                        }
                    }
                    
                    // Continue Watching - Always show for debugging
                    item {
                        if (currentState.continueWatching.isNotEmpty()) {
                            android.util.Log.d("HomeScreen", "Rendering ContinueWatchingRow with ${currentState.continueWatching.size} items")
                            ContinueWatchingRow(
                                continueWatchingItems = currentState.continueWatching,
                                onPlay = { media ->
                                    try {
                                        navController.navigate(Screen.VideoPlayer.createRoute(media.id, resumeFromProgress = true))
                                    } catch (e: Exception) {
                                        android.util.Log.e("HomeScreen", "Error navigating to video player: ${e.message}")
                                    }
                                },
                                onInfo = { media ->
                                    try {
                                        navController.navigate(Screen.Details.createRoute(media.id.toString()))
                                    } catch (e: Exception) {
                                        android.util.Log.e("HomeScreen", "Error navigating to details: ${e.message}")
                                    }
                                }
                            )
                        } else {
                            // Debug: Show empty state with more info
                            Column(
                                modifier = Modifier.padding(horizontal = 60.dp)
                            ) {
                                Text(
                                    text = "Continue Watching",
                                    style = MaterialTheme.typography.headlineSmall,
                                    modifier = Modifier.padding(bottom = 16.dp)
                                )
                                Text(
                                    text = "No recently watched items found.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                                
                                Spacer(modifier = Modifier.height(8.dp))
                                
                                Text(
                                    text = "Debug Info: ${currentState.continueWatching.size} items loaded",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                                )
                                
                                Spacer(modifier = Modifier.height(8.dp))
                                
                                Button(
                                    onClick = { 
                                        android.util.Log.d("HomeScreen", "Refresh button clicked")
                                        viewModel.refreshRecentlyWatched()
                                    }
                                ) {
                                    Text("Refresh")
                                }
                            }
                        }
                    }
                    
                    // Trending Movies
                    if (currentState.trending.isNotEmpty()) {
                        item {
                            MediaRow(
                                title = "Trending Movies",
                                mediaList = currentState.trending,
                                onMediaClick = { media ->
                                    navController.navigate(Screen.Details.createRoute(media.id.toString()))
                                }
                            )
                        }
                    }
                    
                    // Popular Movies
                    if (currentState.popularMovies.isNotEmpty()) {
                        item {
                            MediaRow(
                                title = "Popular Movies",
                                mediaList = currentState.popularMovies,
                                onMediaClick = { media ->
                                    navController.navigate(Screen.Details.createRoute(media.id.toString()))
                                }
                            )
                        }
                    }
                    
                    // Recently Added Movies
                    if (currentState.recentlyAdded.isNotEmpty()) {
                        item {
                            MediaRow(
                                title = "Recently Added",
                                mediaList = currentState.recentlyAdded,
                                onMediaClick = { media ->
                                    navController.navigate(Screen.Details.createRoute(media.id.toString()))
                                }
                            )
                        }
                    }
                    
                    // Bottom padding
                    item {
                        Spacer(modifier = Modifier.height(32.dp))
                    }
                }
            }
        }
    }
}