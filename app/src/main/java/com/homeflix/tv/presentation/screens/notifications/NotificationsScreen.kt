package com.homeflix.tv.presentation.screens.notifications

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.homeflix.tv.domain.model.Notification
import com.homeflix.tv.presentation.navigation.AppNavigator
import com.homeflix.tv.presentation.shell.ErrorState
import com.homeflix.tv.presentation.shell.LoadingState
import com.homeflix.tv.presentation.ui.SizedImage
import com.homeflix.tv.presentation.ui.Tv

/**
 * Notifications: a focusable list on the left; the focused item's artwork
 * fills the background on the right, Netflix "New & Hot" style.
 */
@Composable
fun NotificationsScreen(navigator: AppNavigator, vm: NotificationsViewModel = hiltViewModel()) {
    val state by vm.uiState.collectAsState()
    Box(Modifier.fillMaxSize().background(Tv.Background)) {
        when (val s = state) {
            NotificationsUiState.Loading -> LoadingState()
            is NotificationsUiState.Error -> ErrorState("Couldn't load notifications", onRetry = vm::loadNotifications)
            is NotificationsUiState.Success ->
                if (s.notifications.isEmpty()) ErrorState("You're all caught up", onRetry = vm::loadNotifications)
                else NotificationList(s.notifications, navigator)
        }
    }
}

@Composable
private fun NotificationList(items: List<Notification>, navigator: AppNavigator) {
    var focused by rememberSaveable { mutableIntStateOf(0) }
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { withFrameNanos { }; runCatching { first.requestFocus() } }
    val current = items.getOrNull(focused)

    Box(Modifier.fillMaxSize()) {
        Crossfade(targetState = current?.backdropUrl ?: current?.posterUrl, animationSpec = tween(350), label = "bg",
            modifier = Modifier.fillMaxSize()) { url ->
            if (url != null) SizedImage(url, 960.dp, 540.dp, Modifier.fillMaxSize(), crossfadeMs = 0)
        }
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(
            0f to Tv.Background, 0.5f to Color(0xE6141414), 0.85f to Color(0x66141414), 1f to Color(0x33141414))))

        Column(Modifier.fillMaxHeight().width(620.dp).padding(start = Tv.Keyline, top = 36.dp)) {
            Text("Notifications", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold)
            LazyColumn(
                contentPadding = PaddingValues(top = 18.dp, bottom = 80.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                itemsIndexed(items.distinctBy { it.id }, key = { _, n -> n.id }) { i, n ->
                    NotificationRow(
                        n = n,
                        modifier = if (i == 0) Modifier.focusRequester(first) else Modifier,
                        onFocused = { focused = i },
                        onClick = {
                            when (n.targetType) {
                                "series" -> n.targetId?.let(navigator::series)
                                "movie" -> n.targetId?.let(navigator::details)
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun NotificationRow(n: Notification, modifier: Modifier, onFocused: () -> Unit, onClick: () -> Unit) {
    var isFocused by remember { mutableStateOf(false) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged { isFocused = it.isFocused; if (it.isFocused) onFocused() }
            .clip(RoundedCornerShape(8.dp))
            .background(if (isFocused) Color(0x33FFFFFF) else Color.Transparent)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(10.dp)
    ) {
        Box(Modifier.width(178.dp).height(100.dp).clip(RoundedCornerShape(6.dp)).background(Tv.Surface)) {
            SizedImage(n.backdropUrl ?: n.posterUrl, 178.dp, 100.dp, Modifier.fillMaxSize())
        }
        Column(Modifier.padding(start = 16.dp).weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!n.read) Box(Modifier.size(8.dp).clip(CircleShape).background(Tv.Red))
                Text(
                    timeAgo(n.timestampSeconds),
                    color = Tv.TextMuted, fontSize = 13.sp,
                    modifier = Modifier.padding(start = if (!n.read) 8.dp else 0.dp)
                )
            }
            Text(n.title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
                overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
            Text(n.message.ifBlank { n.overview.orEmpty() }, color = if (isFocused) Color.White else Tv.TextSecondary,
                fontSize = 14.sp, lineHeight = 19.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp))
        }
    }
}

private fun timeAgo(seconds: Long): String {
    if (seconds <= 0) return "Recently"
    val diff = System.currentTimeMillis() / 1000 - seconds
    return when {
        diff < 3600 -> "Just now"
        diff < 86_400 -> "${diff / 3600}h ago"
        diff < 7 * 86_400 -> "${diff / 86_400}d ago"
        else -> java.text.SimpleDateFormat("MMM d", java.util.Locale.getDefault()).format(java.util.Date(seconds * 1000))
    }
}
