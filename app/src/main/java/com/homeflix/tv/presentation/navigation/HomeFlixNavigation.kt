package com.homeflix.tv.presentation.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.media3.common.util.UnstableApi
import com.homeflix.tv.presentation.screens.browse.BrowseScreen
import com.homeflix.tv.presentation.screens.details.DetailsScreen
import com.homeflix.tv.presentation.screens.home.NetflixHomeScreen
import com.homeflix.tv.presentation.screens.mylist.MyListScreen
import com.homeflix.tv.presentation.screens.notifications.NotificationsScreen
import com.homeflix.tv.presentation.screens.player.VideoPlayerScreen
import com.homeflix.tv.presentation.screens.search.SearchScreen
import com.homeflix.tv.presentation.screens.tvshows.TvShowsScreen
import com.homeflix.tv.presentation.screens.tvshows.TvSeriesDetailsScreen
import com.homeflix.tv.presentation.screens.tvshows.TvSeriesSeasonScreen

@UnstableApi
@Composable
fun HomeFlixNavigation(
    navController: NavHostController = rememberNavController()
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Home.route,
        // Global cross-fade for every route — gives the player (and all media)
        // a smooth fade in on open and fade out on close.
        enterTransition = { fadeIn(tween(250)) },
        exitTransition = { fadeOut(tween(250)) },
        popEnterTransition = { fadeIn(tween(250)) },
        popExitTransition = { fadeOut(tween(250)) }
    ) {
        composable(Screen.Home.route) {
            NetflixHomeScreen(navController = navController)
        }
        
        composable(Screen.Browse.route) {
            BrowseScreen(navController = navController)
        }
        
        composable(Screen.Search.route) {
            SearchScreen(navController = navController)
        }
        
        composable(Screen.TvShows.route) {
            TvShowsScreen(navController = navController)
        }
        
        composable(Screen.MyList.route) {
            MyListScreen(navController = navController)
        }

        composable(Screen.Notifications.route) {
            NotificationsScreen(navController = navController)
        }

        composable(
            route = Screen.TvSeriesDetails.route,
            arguments = Screen.TvSeriesDetails.arguments
        ) { backStackEntry ->
            val seriesId = backStackEntry.arguments?.getString("seriesId") ?: ""
            TvSeriesDetailsScreen(
                seriesId = seriesId,
                navController = navController
            )
        }
        
        composable(
            route = Screen.TvSeriesSeason.route,
            arguments = Screen.TvSeriesSeason.arguments
        ) { backStackEntry ->
            val seriesId = backStackEntry.arguments?.getString("seriesId") ?: ""
            val seasonNumber = backStackEntry.arguments?.getInt("seasonNumber") ?: 1
            TvSeriesSeasonScreen(
                seriesId = seriesId,
                seasonNumber = seasonNumber,
                navController = navController
            )
        }
        
        composable(
            route = Screen.Details.route,
            arguments = Screen.Details.arguments
        ) { backStackEntry ->
            val mediaId = backStackEntry.arguments?.getString("mediaId") ?: ""
            DetailsScreen(
                mediaId = mediaId,
                navController = navController
            )
        }
        
        composable(
            route = Screen.VideoPlayer.route,
            arguments = Screen.VideoPlayer.arguments
        ) { backStackEntry ->
            val mediaId = backStackEntry.arguments?.getInt("mediaId") ?: 0
            val startTime = backStackEntry.arguments?.getLong("startTime") ?: 0L
            val forceStart = backStackEntry.arguments?.getBoolean("forceStart") ?: false
            
            VideoPlayerScreen(
                mediaId = mediaId,
                startTime = startTime,
                forceStartFromBeginning = forceStart,
                onNavigateBack = {
                    navController.popBackStack()
                },
                onNavigateToEpisode = { nextEpisodeId ->
                    // Navigate to next episode, replacing current player
                    navController.navigate(Screen.VideoPlayer.createRoute(nextEpisodeId)) {
                        popUpTo(Screen.VideoPlayer.route) { inclusive = true }
                    }
                }
            )
        }
    }
}

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object Browse : Screen("browse") {
        fun createRoute(type: String = "") = if (type.isNotEmpty()) "browse?type=$type" else "browse"
    }
    object Search : Screen("search")
    object TvShows : Screen("tv-shows")
    object MyList : Screen("my-list")
    object Notifications : Screen("notifications")
    object TvSeriesDetails : Screen("tv-series/{seriesId}") {
        fun createRoute(seriesId: String) = "tv-series/$seriesId"
        val arguments = listOf(
            androidx.navigation.navArgument("seriesId") {
                type = androidx.navigation.NavType.StringType
            }
        )
    }
    object TvSeriesSeason : Screen("tv-series/{seriesId}/season/{seasonNumber}") {
        fun createRoute(seriesId: String, seasonNumber: Int) = "tv-series/$seriesId/season/$seasonNumber"
        val arguments = listOf(
            androidx.navigation.navArgument("seriesId") {
                type = androidx.navigation.NavType.StringType
            },
            androidx.navigation.navArgument("seasonNumber") {
                type = androidx.navigation.NavType.IntType
            }
        )
    }
    object Details : Screen("details/{mediaId}") {
        fun createRoute(mediaId: String) = "details/$mediaId"
        val arguments = listOf(
            androidx.navigation.navArgument("mediaId") {
                type = androidx.navigation.NavType.StringType
            }
        )
    }
    object VideoPlayer : Screen("player/{mediaId}?startTime={startTime}&forceStart={forceStart}") {
        fun createRoute(
            mediaId: Int, 
            startTime: Long = 0L, 
            forceStartFromBeginning: Boolean = false
        ) = "player/$mediaId?startTime=$startTime&forceStart=$forceStartFromBeginning"
        
        val arguments = listOf(
            androidx.navigation.navArgument("mediaId") {
                type = androidx.navigation.NavType.IntType
            },
            androidx.navigation.navArgument("startTime") {
                type = androidx.navigation.NavType.LongType
                defaultValue = 0L
            },
            androidx.navigation.navArgument("forceStart") {
                type = androidx.navigation.NavType.BoolType
                defaultValue = false
            }
        )
    }
}