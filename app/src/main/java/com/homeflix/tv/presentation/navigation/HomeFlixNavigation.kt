package com.homeflix.tv.presentation.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.homeflix.tv.core.PreviewPlayer
import com.homeflix.tv.presentation.screens.details.DetailsScreen
import com.homeflix.tv.presentation.screens.feeds.HomeFeedViewModel
import com.homeflix.tv.presentation.screens.feeds.MoviesFeedViewModel
import com.homeflix.tv.presentation.screens.feeds.MyHomeFlixViewModel
import com.homeflix.tv.presentation.screens.feeds.ShowsFeedViewModel
import com.homeflix.tv.presentation.screens.notifications.NotificationsScreen
import com.homeflix.tv.presentation.screens.notifications.unreadCount
import com.homeflix.tv.presentation.screens.player.VideoPlayerScreen
import com.homeflix.tv.presentation.screens.search.SearchPage
import com.homeflix.tv.presentation.screens.tvshows.TvSeriesDetailsScreen
import com.homeflix.tv.presentation.shell.AppShell
import com.homeflix.tv.presentation.shell.FeedPage
import com.homeflix.tv.presentation.shell.ShellTab
import com.homeflix.tv.presentation.ui.Tile
import com.homeflix.tv.presentation.ui.TileActions
import com.homeflix.tv.presentation.ui.TileKind

/** Navigation that ignores repeated presses while a transition is running. */
class AppNavigator(private val nav: NavHostController, private val preview: PreviewPlayer) : TileActions {
    private fun canNavigate(): Boolean =
        nav.currentBackStackEntry?.lifecycle?.currentState?.isAtLeast(Lifecycle.State.RESUMED) != false

    fun go(route: String) { if (canNavigate()) nav.navigate(route) { launchSingleTop = true } }

    fun back() { if (canNavigate()) nav.popBackStack() }

    /** From any page: return to the browse screens on [tab]. */
    fun goTab(tab: com.homeflix.tv.presentation.shell.ShellTab) {
        com.homeflix.tv.presentation.shell.ShellRequests.tab.value = tab
        if (!nav.popBackStack(Screen.Home.route, inclusive = false)) {
            nav.navigate(Screen.Home.route) { launchSingleTop = true }
        }
    }

    /** Full-screen Live TV on [channelId], or the last channel. */
    fun liveTv(channelId: String? = null) {
        if (!canNavigate()) return
        preview.hardStop()
        nav.navigate(Screen.LiveTv.createRoute(channelId)) { launchSingleTop = true }
    }

    /** Full-screen Coming Soon trailers, starting at [key] ("movie:123" / "tv:456"). */
    fun comingSoon(key: String? = null) {
        if (!canNavigate()) return
        preview.hardStop()
        nav.navigate(Screen.ComingSoon.createRoute(key)) { launchSingleTop = true }
    }

    fun details(mediaId: Int) = go(Screen.Details.createRoute(mediaId.toString()))
    fun series(seriesId: Int) = go(Screen.TvSeriesDetails.createRoute(seriesId.toString()))

    fun player(mediaId: Int, startMs: Long = 0L, fromStart: Boolean = false) {
        if (!canNavigate()) return
        preview.hardStop() // release the preview decoder before the movie starts
        nav.navigate(Screen.VideoPlayer.createRoute(mediaId, startMs, fromStart)) { launchSingleTop = true }
    }

    override fun open(tile: Tile) = when (tile.kind) {
        TileKind.MOVIE -> details(tile.id)
        TileKind.SERIES -> series(tile.id)
        TileKind.EPISODE -> tile.seriesId?.let { series(it) } ?: details(tile.id)
        TileKind.CHANNEL -> liveTv(tile.channelId)
        TileKind.COMING -> comingSoon(tile.comingKey)
    }

    override fun play(tile: Tile) = when (tile.kind) {
        TileKind.SERIES -> series(tile.id)
        TileKind.CHANNEL -> liveTv(tile.channelId)
        TileKind.COMING -> comingSoon(tile.comingKey)
        else -> player(tile.id, tile.resumeMs)
    }
}

@UnstableApi
@Composable
fun HomeFlixNavigation(
    previewPlayer: PreviewPlayer,
    castController: com.homeflix.tv.cast.CastController,
    updater: com.homeflix.tv.update.AppUpdater? = null,
    navController: NavHostController = rememberNavController()
) {
    val navigator = remember(navController, previewPlayer) { AppNavigator(navController, previewPlayer) }

    // A cast from the web app: open the player, replacing whatever is on top.
    val castRequest by castController.playRequests.collectAsState()
    androidx.compose.runtime.LaunchedEffect(castRequest) {
        val req = castRequest ?: return@LaunchedEffect
        previewPlayer.hardStop()
        navController.navigate(
            Screen.VideoPlayer.createRoute(req.mediaId, req.positionMs, forceStartFromBeginning = req.positionMs <= 0, exact = true)
        ) {
            popUpTo(Screen.Home.route) { inclusive = false }
        }
        castController.consumePlayRequest(req)
    }

    // A card on the TV home screen: open that title, or resume it.
    val homeLink by com.homeflix.tv.launcher.DeepLinks.pending.collectAsState()
    androidx.compose.runtime.LaunchedEffect(homeLink) {
        val link = homeLink ?: return@LaunchedEffect
        com.homeflix.tv.launcher.DeepLinks.consume(link)
        // Start from the browse screens so Back from the title lands on Home.
        navController.popBackStack(Screen.Home.route, inclusive = false)
        when (link) {
            is com.homeflix.tv.launcher.HomeLink.Play -> {
                previewPlayer.hardStop()
                navController.navigate(Screen.VideoPlayer.createRoute(link.mediaId, link.positionMs, false)) { launchSingleTop = true }
            }
            is com.homeflix.tv.launcher.HomeLink.Movie -> navController.navigate(Screen.Details.createRoute(link.mediaId.toString())) { launchSingleTop = true }
            is com.homeflix.tv.launcher.HomeLink.Series -> navController.navigate(Screen.TvSeriesDetails.createRoute(link.seriesId.toString())) { launchSingleTop = true }
            com.homeflix.tv.launcher.HomeLink.Home -> Unit
        }
    }

    NavHost(
        navController = navController,
        startDestination = Screen.Home.route,
        enterTransition = { fadeIn(tween(220)) },
        exitTransition = { fadeOut(tween(180)) },
        popEnterTransition = { fadeIn(tween(220)) },
        popExitTransition = { fadeOut(tween(180)) }
    ) {
        composable(Screen.Home.route) { entry ->
            BrowseShell(entry, navigator, previewPlayer, updater)
        }

        composable(Screen.Notifications.route) {
            NotificationsScreen(navigator = navigator)
        }

        composable(Screen.About.route) {
            com.homeflix.tv.presentation.screens.settings.AboutScreen(navigator)
        }

        composable(Screen.LiveTv.route, arguments = Screen.LiveTv.arguments) { entry ->
            com.homeflix.tv.livetv.LiveTvScreen(
                channelId = entry.arguments?.getString("channel"),
                onExit = { navigator.back() }
            )
        }

        composable(Screen.ComingSoon.route, arguments = Screen.ComingSoon.arguments) { entry ->
            com.homeflix.tv.comingsoon.ComingSoonScreen(
                startKey = entry.arguments?.getString("start"),
                onExit = { navigator.back() }
            )
        }

        composable(Screen.Settings.route) {
            com.homeflix.tv.presentation.screens.settings.SettingsScreen(navigator)
        }

        composable(Screen.TvSeriesDetails.route, arguments = Screen.TvSeriesDetails.arguments) { entry ->
            val seriesId = entry.arguments?.getString("seriesId")?.toIntOrNull() ?: 0
            TvSeriesDetailsScreen(seriesId = seriesId, navigator = navigator, previewPlayer = previewPlayer)
        }

        composable(Screen.Details.route, arguments = Screen.Details.arguments) { entry ->
            val mediaId = entry.arguments?.getString("mediaId")?.toIntOrNull() ?: 0
            DetailsScreen(mediaId = mediaId, navigator = navigator, previewPlayer = previewPlayer)
        }

        composable(
            route = Screen.VideoPlayer.route,
            arguments = Screen.VideoPlayer.arguments,
            enterTransition = { fadeIn(tween(160)) },
            exitTransition = { fadeOut(tween(160)) },
            popExitTransition = { fadeOut(tween(160)) }
        ) { entry ->
            val mediaId = entry.arguments?.getInt("mediaId") ?: 0
            val startTime = entry.arguments?.getLong("startTime") ?: 0L
            val forceStart = entry.arguments?.getBoolean("forceStart") ?: false
            val exact = entry.arguments?.getBoolean("exact") ?: false
            VideoPlayerScreen(
                mediaId = mediaId,
                startTimeMs = startTime,
                forceStartFromBeginning = forceStart,
                exactStart = exact,
                onClose = { navigator.back() },
                onForceClose = {
                    if (navController.currentBackStackEntry?.destination?.route == Screen.VideoPlayer.route) {
                        navController.popBackStack()
                    }
                },
                onPlayNext = { nextId ->
                    navController.navigate(Screen.VideoPlayer.createRoute(nextId, 0L, false)) {
                        popUpTo(Screen.VideoPlayer.route) { inclusive = true }
                    }
                }
            )
        }
    }
}

@UnstableApi
@Composable
private fun BrowseShell(
    entry: NavBackStackEntry,
    navigator: AppNavigator,
    previewPlayer: PreviewPlayer,
    updater: com.homeflix.tv.update.AppUpdater?
) {
    // Bell badge in the top nav; re-checked whenever we come back to browsing.
    val notificationsVm: com.homeflix.tv.presentation.screens.notifications.NotificationsViewModel = hiltViewModel(entry)
    val notificationsState by notificationsVm.uiState.collectAsState()
    androidx.lifecycle.compose.LifecycleEventEffect(androidx.lifecycle.Lifecycle.Event.ON_RESUME) { notificationsVm.loadNotifications() }
    val unread = notificationsState.unreadCount
    // "Live TV" on the top menu while the server has Live TV turned on.
    val liveVm: com.homeflix.tv.livetv.LiveTvEntryViewModel = hiltViewModel(entry)
    val liveEnabled by liveVm.enabled.collectAsState()
    val comingVm: com.homeflix.tv.comingsoon.ComingSoonEntryViewModel = hiltViewModel(entry)
    val comingAvailable by comingVm.available.collectAsState()
    AppShell(
        previewPlayer = previewPlayer,
        actions = navigator,
        onOpenNotifications = { navigator.go(Screen.Notifications.route) },
        onOpenSettings = { navigator.go(Screen.Settings.route) },
        onOpenAbout = { navigator.go(Screen.About.route) },
        unreadNotifications = unread,
        updater = updater,
        onOpenLiveTv = if (liveEnabled) ({ navigator.liveTv() }) else null,
        onOpenComingSoon = if (comingAvailable) ({ navigator.comingSoon() }) else null
    ) { tab, shell ->
        when (tab) {
            ShellTab.HOME -> {
                val vm: HomeFeedViewModel = hiltViewModel(entry)
                val state by vm.state.collectAsState()
                FeedPage(state, shell, navigator, onRetry = vm::refresh, onResumed = vm::refreshAfterReturn,
                    onOpenSettings = { navigator.go(Screen.Settings.route) })
            }
            ShellTab.SHOWS -> {
                val vm: ShowsFeedViewModel = hiltViewModel(entry)
                val state by vm.state.collectAsState()
                FeedPage(state, shell, navigator, onRetry = vm::refresh, onResumed = vm::refreshAfterReturn,
                    onOpenSettings = { navigator.go(Screen.Settings.route) },
                    heroPlayLabel = { "Episodes" })
            }
            ShellTab.MOVIES -> {
                val vm: MoviesFeedViewModel = hiltViewModel(entry)
                val state by vm.state.collectAsState()
                FeedPage(state, shell, navigator, onRetry = vm::refresh, onResumed = vm::refreshAfterReturn,
                    onOpenSettings = { navigator.go(Screen.Settings.route) })
            }
            ShellTab.MINE -> {
                val vm: MyHomeFlixViewModel = hiltViewModel(entry)
                val state by vm.state.collectAsState()
                FeedPage(state, shell, navigator, onRetry = vm::refresh, onResumed = vm::refreshAfterReturn,
                    onOpenSettings = { navigator.go(Screen.Settings.route) })
            }
            ShellTab.SEARCH -> SearchPage(shell = shell, actions = navigator)
        }
    }
}

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object Notifications : Screen("notifications")
    object TvSeriesDetails : Screen("tv-series/{seriesId}") {
        fun createRoute(seriesId: String) = "tv-series/$seriesId"
        val arguments = listOf(navArgument("seriesId") { type = NavType.StringType })
    }
    object Details : Screen("details/{mediaId}") {
        fun createRoute(mediaId: String) = "details/$mediaId"
        val arguments = listOf(navArgument("mediaId") { type = NavType.StringType })
    }
    object Settings : Screen("settings")
    object ComingSoon : Screen("comingsoon?start={start}") {
        fun createRoute(key: String?) = if (key.isNullOrBlank()) "comingsoon" else "comingsoon?start=" + android.net.Uri.encode(key)
        val arguments = listOf(navArgument("start") { type = NavType.StringType; nullable = true; defaultValue = null })
    }
    object LiveTv : Screen("livetv?channel={channel}") {
        fun createRoute(channelId: String?) =
            if (channelId.isNullOrBlank()) "livetv" else "livetv?channel=" + android.net.Uri.encode(channelId)
        val arguments = listOf(navArgument("channel") { type = NavType.StringType; nullable = true; defaultValue = null })
    }
    object About : Screen("about")
    object VideoPlayer : Screen("player/{mediaId}?startTime={startTime}&forceStart={forceStart}&exact={exact}") {
        fun createRoute(mediaId: Int, startTime: Long = 0L, forceStartFromBeginning: Boolean = false, exact: Boolean = false) =
            "player/$mediaId?startTime=$startTime&forceStart=$forceStartFromBeginning&exact=$exact"

        val arguments = listOf(
            navArgument("mediaId") { type = NavType.IntType },
            navArgument("startTime") { type = NavType.LongType; defaultValue = 0L },
            navArgument("forceStart") { type = NavType.BoolType; defaultValue = false },
            navArgument("exact") { type = NavType.BoolType; defaultValue = false }
        )
    }
}
