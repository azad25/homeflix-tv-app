package com.homeflix.tv.presentation.screens.notifications

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import kotlinx.coroutines.delay
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.homeflix.tv.domain.model.Notification
import com.homeflix.tv.presentation.components.NetflixSideNavigation
import com.homeflix.tv.presentation.navigation.Screen
import com.homeflix.tv.presentation.theme.*
import com.homeflix.tv.util.ApiUtils

/**
 * NOTIFICATIONS — display-only feed backed by GET /api/notifications.
 * Sidebar + Prime-styled list of cards (art, title, message, relative time,
 * type badge). No click-through / mark-read.
 */
@Composable
fun NotificationsScreen(
    navController: NavController,
    viewModel: NotificationsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val firstCardFocus = remember { FocusRequester() }

    // Move focus onto the first notification once loaded so it's selectable.
    LaunchedEffect(uiState) {
        val s = uiState
        if (s is NotificationsUiState.Success && s.notifications.isNotEmpty()) {
            delay(300)
            try { firstCardFocus.requestFocus() } catch (_: Exception) {}
        }
    }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(PrimeBg)
    ) {
        NetflixSideNavigation(
            selectedRoute = "notifications",
            onNavigate = { route ->
                when (route) {
                    "notifications" -> { /* already here */ }
                    "home" -> navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Home.route) { inclusive = false }
                        launchSingleTop = true
                    }
                    else -> navController.navigate(route) {
                        popUpTo(Screen.Home.route) { inclusive = false }
                        launchSingleTop = true
                    }
                }
            },
            onNavigateToContent = {
                try { firstCardFocus.requestFocus() } catch (_: Exception) {}
            }
        )

        Box(modifier = Modifier.fillMaxSize()) {
            when (val state = uiState) {
                is NotificationsUiState.Loading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = PrimeBlue)
                    }
                }

                is NotificationsUiState.Error -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text("Couldn't load notifications", color = TextPrimary, style = MaterialTheme.typography.titleLarge)
                            Text(state.message, color = PrimeTextDim)
                            Button(
                                onClick = { viewModel.loadNotifications() },
                                colors = ButtonDefaults.buttonColors(containerColor = PrimeBlue)
                            ) { Text("Retry") }
                        }
                    }
                }

                is NotificationsUiState.Success -> {
                    if (state.notifications.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    Icons.Default.Notifications,
                                    contentDescription = null,
                                    tint = PrimeTextDim,
                                    modifier = Modifier.size(56.dp)
                                )
                                Spacer(Modifier.height(12.dp))
                                Text("No notifications yet", color = PrimeTextDim, style = MaterialTheme.typography.titleMedium)
                            }
                        }
                    } else {
                        val listState = rememberLazyListState()
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(start = 40.dp, end = 56.dp, top = 32.dp, bottom = 40.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            item {
                                Text(
                                    text = "Notifications",
                                    style = MaterialTheme.typography.headlineMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary
                                    ),
                                    modifier = Modifier.padding(bottom = 12.dp)
                                )
                            }
                            itemsIndexed(state.notifications, key = { _, n -> n.id }) { index, notification ->
                                NotificationCard(
                                    notification = notification,
                                    onClick = {
                                        val id = notification.targetId ?: return@NotificationCard
                                        when (notification.targetType) {
                                            "series" -> navController.navigate(Screen.TvSeriesDetails.createRoute(id.toString()))
                                            "movie" -> navController.navigate(Screen.Details.createRoute(id.toString()))
                                        }
                                    },
                                    modifier = if (index == 0) Modifier.focusRequester(firstCardFocus) else Modifier
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NotificationCard(
    notification: Notification,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // These are local notifications, so always derive a guaranteed image from
    // the referenced media id (the backend backdrop/poster URLs are often empty).
    // Movies use the real BANNER art (/backdrops/{id} auto-downloads from TMDB),
    // not the video-frame thumbnail.
    val base = ApiUtils.getBaseUrl()
    val primaryArt = resolveAssetUrl(notification.backdropUrl ?: notification.posterUrl)
        ?: notification.targetId?.let { id ->
            if (notification.targetType == "series") "$base/series/$id/backdrop"
            else "$base/backdrops/$id"
        }
    val fallbackArt = notification.targetId?.let { id ->
        if (notification.targetType == "series") "$base/series/$id/poster"
        else "$base/thumbnails/$id"
    }
    var focused by remember { mutableStateOf(false) }
    val scale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (focused) 1.015f else 1f,
        animationSpec = androidx.compose.animation.core.tween(150),
        label = "notif_scale"
    )

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (focused) PrimeSurfaceHigh else PrimeSurface,
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer(scaleX = scale, scaleY = scale)
            .onFocusChanged { focused = it.isFocused }
            .onKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown &&
                    (keyEvent.key == Key.Enter || keyEvent.key == Key.DirectionCenter)
                ) {
                    onClick(); true
                } else false
            }
            .focusable()
            .clickable { onClick() }
            .then(
                if (focused) Modifier.border(2.dp, Color.White, RoundedCornerShape(10.dp))
                else Modifier
            )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Art (16:9) — banner-sized, with single-swap fallback
            Box(
                modifier = Modifier
                    .width(210.dp)
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(6.dp))
                    .background(PrimeBgDeep)
            ) {
                var artFailed by remember(notification.id) { mutableStateOf(false) }
                val model = if (artFailed) fallbackArt else (primaryArt ?: fallbackArt)
                if (model != null) {
                    AsyncImage(
                        model = coil.request.ImageRequest.Builder(LocalContext.current)
                            .data(model)
                            .crossfade(true)
                            .build(),
                        contentDescription = notification.title,
                        contentScale = ContentScale.Crop,
                        onError = { if (!artFailed && fallbackArt != null) artFailed = true },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            // Text
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!notification.read) {
                        Box(
                            Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(PrimeBlue)
                        )
                    }
                    Text(
                        text = notification.title,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                }
                if (notification.message.isNotBlank()) {
                    Text(
                        text = notification.message,
                        style = MaterialTheme.typography.bodyMedium.copy(color = TextPrimary.copy(alpha = 0.8f)),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    val label = notification.category?.takeIf { it.isNotBlank() }
                        ?: notification.type.replace('_', ' ').replaceFirstChar { it.uppercase() }
                    Box(
                        Modifier
                            .border(1.dp, BadgeOutline, RoundedCornerShape(3.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(label, color = PrimeTextDim, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                    }
                    Text(
                        text = relativeTime(notification.timestampSeconds),
                        color = PrimeTextDim,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }
    }
}

/** Resolve a notification asset URL: absolute stays, `/api/...` gets the host. */
private fun resolveAssetUrl(path: String?): String? {
    if (path.isNullOrBlank()) return null
    if (path.startsWith("http")) return path
    val root = ApiUtils.getRootUrl() // host without /api
    return if (path.startsWith("/")) "$root$path" else "$root/$path"
}

private fun relativeTime(timestampSeconds: Long): String {
    if (timestampSeconds <= 0) return ""
    val nowSec = System.currentTimeMillis() / 1000
    val diff = (nowSec - timestampSeconds).coerceAtLeast(0)
    return when {
        diff < 60 -> "Just now"
        diff < 3600 -> "${diff / 60}m ago"
        diff < 86400 -> "${diff / 3600}h ago"
        diff < 604800 -> "${diff / 86400}d ago"
        else -> "${diff / 604800}w ago"
    }
}
