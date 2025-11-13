package com.homeflix.tv.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.media3.common.util.UnstableApi
import com.homeflix.tv.presentation.screens.browse.BrowseScreen
import com.homeflix.tv.presentation.screens.details.DetailsScreen
import com.homeflix.tv.presentation.screens.home.NetflixHomeScreen
import com.homeflix.tv.presentation.screens.player.VideoPlayerScreen
import com.homeflix.tv.presentation.screens.search.SearchScreen

@UnstableApi
@Composable
fun HomeFlixNavigation(
    navController: NavHostController = rememberNavController()
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Home.route
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
            val mediaId = backStackEntry.arguments?.getString("mediaId")?.toIntOrNull() ?: 0
            val startTime = backStackEntry.arguments?.getString("startTime")?.toLongOrNull() ?: 0L
            val forceStart = backStackEntry.arguments?.getString("forceStart")?.toBooleanStrictOrNull() ?: false
            val resume = backStackEntry.arguments?.getString("resume")?.toBooleanStrictOrNull() ?: false
            
            VideoPlayerScreen(
                mediaId = mediaId,
                startTime = startTime,
                forceStartFromBeginning = forceStart,
                resumeFromProgress = resume,
                onNavigateBack = {
                    navController.popBackStack()
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
    object Details : Screen("details/{mediaId}") {
        fun createRoute(mediaId: String) = "details/$mediaId"
        val arguments = listOf(
            androidx.navigation.navArgument("mediaId") {
                type = androidx.navigation.NavType.StringType
            }
        )
    }
    object VideoPlayer : Screen("player/{mediaId}?startTime={startTime}&forceStart={forceStart}&resume={resume}") {
        fun createRoute(
            mediaId: Int, 
            startTime: Long = 0L, 
            forceStartFromBeginning: Boolean = false,
            resumeFromProgress: Boolean = false
        ) = "player/$mediaId?startTime=$startTime&forceStart=$forceStartFromBeginning&resume=$resumeFromProgress"
        
        val arguments = listOf(
            androidx.navigation.navArgument("mediaId") {
                type = androidx.navigation.NavType.StringType
            },
            androidx.navigation.navArgument("startTime") {
                type = androidx.navigation.NavType.StringType
                defaultValue = "0"
            },
            androidx.navigation.navArgument("forceStart") {
                type = androidx.navigation.NavType.StringType
                defaultValue = "false"
            },
            androidx.navigation.navArgument("resume") {
                type = androidx.navigation.NavType.StringType
                defaultValue = "false"
            }
        )
    }
}