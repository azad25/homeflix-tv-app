package com.homeflix.tv.presentation.screens.notifications

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material3.Icon
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
import com.homeflix.tv.presentation.ui.throttleKeyRepeat
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Movie
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.ui.graphics.graphicsLayer
import androidx.hilt.navigation.compose.hiltViewModel
import com.homeflix.tv.domain.model.Notification
import com.homeflix.tv.presentation.navigation.AppNavigator
import com.homeflix.tv.presentation.shell.ErrorState
import com.homeflix.tv.presentation.shell.LoadingState
import com.homeflix.tv.presentation.shell.SubPageTopBar
import com.homeflix.tv.presentation.ui.MediaFormat
import com.homeflix.tv.presentation.ui.PillButton
import com.homeflix.tv.presentation.ui.SizedImage
import com.homeflix.tv.presentation.ui.TitleLogo
import com.homeflix.tv.presentation.ui.Tv

/**
 * Notifications: filters, a day-grouped list, and the focused item's details.
 */
@Composable
fun NotificationsScreen(navigator: AppNavigator, vm: NotificationsViewModel = hiltViewModel()) {
    val state by vm.uiState.collectAsState()
    // Seen = read: clears the red dot on the nav bell.
    LaunchedEffect(state is NotificationsUiState.Success) { vm.markAllRead() }
    Box(Modifier.fillMaxSize().background(Tv.Background)) {
        when (val s = state) {
            NotificationsUiState.Loading -> LoadingState()
            is NotificationsUiState.Error -> ErrorState("Couldn't load notifications", onRetry = vm::loadNotifications)
            is NotificationsUiState.Success ->
                if (s.notifications.isEmpty()) EmptyNotifications()
                else NotificationsContent(s.notifications.distinctBy { it.id }, navigator)
        }
        SubPageTopBar(navigator)
    }
}

private fun open(n: Notification, navigator: AppNavigator) {
    when (n.targetType) {
        "series" -> n.targetId?.let(navigator::series)
        "movie" -> n.targetId?.let(navigator::details)
    }
}

private enum class NotifFilter(val label: String) { ALL("All"), MOVIES("Movies"), SHOWS("Shows") }

private fun NotifFilter.matches(n: Notification) = when (this) {
    NotifFilter.ALL -> true
    NotifFilter.MOVIES -> n.targetType == "movie"
    NotifFilter.SHOWS -> n.targetType == "series"
}

/** "Today", "Yesterday", "This Week", "Earlier". */
private fun dayGroup(seconds: Long): String {
    if (seconds <= 0) return "Earlier"
    val cal = java.util.Calendar.getInstance()
    cal.set(java.util.Calendar.HOUR_OF_DAY, 0); cal.set(java.util.Calendar.MINUTE, 0)
    cal.set(java.util.Calendar.SECOND, 0); cal.set(java.util.Calendar.MILLISECOND, 0)
    val today = cal.timeInMillis / 1000
    return when {
        seconds >= today -> "Today"
        seconds >= today - 86_400 -> "Yesterday"
        seconds >= today - 6 * 86_400 -> "This Week"
        else -> "Earlier"
    }
}

/**
 * Notifications, Apple TV style: filter chips and a day-grouped list on the
 * left; the focused item as a large artwork card with its details on the
 * right, over a dim wash of the same artwork.
 */
@Composable
private fun NotificationsContent(items: List<Notification>, navigator: AppNavigator) {
    var filter by rememberSaveable { mutableStateOf(NotifFilter.ALL) }
    val shown = remember(items, filter) { items.filter { filter.matches(it) }.sortedByDescending { it.timestampSeconds } }
    var focusedId by rememberSaveable { mutableStateOf<String?>(null) }
    val current = shown.firstOrNull { it.id == focusedId } ?: shown.firstOrNull()
    val first = remember { FocusRequester() }
    val listState = rememberLazyListState()
    LaunchedEffect(Unit) { withFrameNanos { }; runCatching { first.requestFocus() } }
    val unread = items.count { !it.read }

    Box(Modifier.fillMaxSize()) {
        // Dim wash of the focused artwork (no full-strength full-screen image: lighter and calmer).
        Crossfade(targetState = current?.id, animationSpec = tween(400), label = "bg", modifier = Modifier.fillMaxSize()) { id ->
            val n = shown.firstOrNull { it.id == id }
            if (n != null) Box(Modifier.fillMaxSize().graphicsLayer { alpha = 0.28f }) {
                FallbackImage(n.imageCandidates, 960.dp, 540.dp, Modifier.fillMaxSize())
            }
        }
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(
            0f to Tv.Background, 0.45f to Color(0xF0141414), 1f to Color(0xB3141414))))

        Row(Modifier.fillMaxSize()) {
            // ---------------------------------------------------------------- list
            Column(Modifier.width(500.dp).fillMaxHeight().padding(start = Tv.Keyline, top = Tv.NavHeight + 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Notifications", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    if (unread > 0) {
                        Text("$unread new", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 10.dp).clip(RoundedCornerShape(50))
                                .background(Tv.Red).padding(horizontal = 8.dp, vertical = 2.dp))
                    }
                }
                Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NotifFilter.entries.forEach { f ->
                        val count = items.count { f.matches(it) }
                        if (f == NotifFilter.ALL || count > 0) {
                            FilterChip("${f.label}  $count", selected = f == filter) { filter = f; focusedId = null }
                        }
                    }
                }
                val grouped = remember(shown) { shown.groupBy { dayGroup(it.timestampSeconds) } }
                LazyColumn(
                    state = listState,
                    modifier = Modifier.throttleKeyRepeat(),
                    contentPadding = PaddingValues(top = 10.dp, bottom = 60.dp, end = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    var firstCard = true
                    grouped.forEach { (day, list) ->
                        item(key = "h:$day") {
                            Text(day.uppercase(), color = Tv.TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                                letterSpacing = 1.2.sp, modifier = Modifier.padding(start = 4.dp, top = 10.dp, bottom = 2.dp))
                        }
                        list.forEach { n ->
                            val isFirst = firstCard
                            firstCard = false
                            item(key = n.id) {
                                NotificationCard(
                                    n = n,
                                    modifier = if (isFirst) Modifier.focusRequester(first) else Modifier,
                                    onFocused = { focusedId = n.id },
                                    onClick = { open(n, navigator) }
                                )
                            }
                        }
                    }
                }
            }

            // ---------------------------------------------------------------- details
            Box(Modifier.weight(1f).fillMaxHeight().padding(start = 28.dp, end = Tv.Keyline, top = Tv.NavHeight + 52.dp, bottom = 36.dp)) {
                AnimatedContent(
                    targetState = current,
                    transitionSpec = { fadeIn(tween(220, delayMillis = 60)) togetherWith fadeOut(tween(110)) },
                    contentKey = { it?.id },
                    label = "details"
                ) { n ->
                    if (n != null) Details(n)
                }
            }
        }
    }
}

@Composable
private fun FilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Text(
        label,
        color = when { focused -> Color.Black; selected -> Color.White; else -> Tv.TextSecondary },
        fontSize = 13.sp,
        fontWeight = if (selected || focused) FontWeight.SemiBold else FontWeight.Normal,
        modifier = Modifier
            .onFocusChanged { focused = it.isFocused }
            .clip(RoundedCornerShape(50))
            .background(when { focused -> Color.White; selected -> Color(0x33FFFFFF); else -> Color.Transparent })
            .border(1.dp, if (selected || focused) Color.Transparent else Color(0x33FFFFFF), RoundedCornerShape(50))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp)
    )
}

@Composable
private fun Details(n: Notification) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // Large artwork card with the title logo.
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(14.dp)).background(Tv.Surface)) {
            FallbackImage(n.imageCandidates, 640.dp, 360.dp, Modifier.fillMaxSize())
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.55f to Color.Transparent, 1f to Color(0xCC000000))))
            TitleLogo(
                logoUrl = n.logoUrl,
                title = n.itemTitle ?: n.title,
                maxWidth = 260.dp,
                maxHeight = 80.dp,
                textStyle = TextStyle(color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Black, lineHeight = 29.sp),
                modifier = Modifier.align(Alignment.BottomStart).padding(18.dp)
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            n.category?.takeIf { it.isNotBlank() }?.let {
                Text(it.replace('_', ' ').uppercase(), color = Tv.Red, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp)
                Text("   ·   ", color = Tv.TextMuted, fontSize = 11.sp)
            }
            Text(timeAgo(n.timestampSeconds), color = Tv.TextMuted, fontSize = 12.sp)
        }
        val meta = buildList {
            n.year?.takeIf { it > 0 }?.let { add(it.toString()) }
            n.runtimeMinutes?.takeIf { it > 0 }?.let { add(MediaFormat.duration(it)) }
            n.genres.take(2).forEach(::add)
            if (n.rating > 0) add("★ %.1f".format(n.rating))
        }.joinToString("  •  ")
        Text(n.title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (meta.isNotBlank()) Text(meta, color = Tv.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        (n.overview?.takeIf { it.isNotBlank() } ?: n.message.takeIf { it.isNotBlank() })?.let {
            Text(it, color = Tv.TextSecondary, fontSize = 14.sp, lineHeight = 20.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
        if (n.targetId != null) {
            Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("OK", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(Color.White).padding(horizontal = 6.dp, vertical = 1.dp))
                Text(if (n.targetType == "series") "  Open the show" else "  Open the movie", color = Tv.TextSecondary, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun NotificationCard(n: Notification, modifier: Modifier, onFocused: () -> Unit, onClick: () -> Unit) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by androidx.compose.animation.core.animateFloatAsState(if (isFocused) 1.02f else 1f, tween(Tv.FocusMotionMs, easing = Tv.Ease), label = "nScale")
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged { isFocused = it.isFocused; if (it.isFocused) onFocused() }
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(10.dp))
            .background(if (isFocused) Color.White else Color(0x0DFFFFFF))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(8.dp)
    ) {
        Box(Modifier.width(150.dp).height(84.dp).clip(RoundedCornerShape(7.dp)).background(Tv.Surface)) {
            FallbackImage(n.imageCandidates, 150.dp, 84.dp, Modifier.fillMaxSize())
            // Movie / show badge.
            Icon(
                if (n.targetType == "series") Icons.Filled.Tv else Icons.Filled.Movie, null, tint = Color.White,
                modifier = Modifier.align(Alignment.BottomEnd).padding(5.dp).size(18.dp).clip(CircleShape)
                    .background(Color(0x99000000)).padding(3.dp)
            )
        }
        Column(Modifier.padding(start = 12.dp).weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!n.read) {
                    Text("NEW", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(end = 6.dp).clip(RoundedCornerShape(3.dp)).background(Tv.Red).padding(horizontal = 4.dp, vertical = 1.dp))
                }
                Text(timeAgo(n.timestampSeconds), color = if (isFocused) Color.DarkGray else Tv.TextMuted, fontSize = 11.sp)
            }
            Text(n.itemTitle ?: n.title, color = if (isFocused) Color.Black else Color.White, fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
            Text(n.title.takeIf { n.itemTitle != null && it != n.itemTitle } ?: n.message.ifBlank { n.itemTitle.orEmpty() },
                color = if (isFocused) Color(0xFF333333) else Tv.TextSecondary,
                fontSize = 12.sp, lineHeight = 16.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 1.dp))
        }
    }
}

/** Tries each URL in turn until one loads (server banners/posters are not always there). */
@Composable
private fun FallbackImage(urls: List<String>, width: Dp, height: Dp, modifier: Modifier) {
    var index by remember(urls) { mutableIntStateOf(0) }
    val url = urls.getOrNull(index)
    if (url == null) {
        Box(modifier.background(Tv.SurfaceHigh), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.NotificationsNone, null, tint = Tv.TextMuted, modifier = Modifier.size(28.dp))
        }
        return
    }
    SizedImage(url, width, height, modifier, onError = { index++ })
}

@Composable
private fun EmptyNotifications() {
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(Icons.Filled.NotificationsNone, null, tint = Tv.TextMuted, modifier = Modifier.size(56.dp))
        Spacer(Modifier.height(14.dp))
        Text("You're all caught up", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        Text("New movies and episodes in your library will show up here.", color = Tv.TextSecondary, fontSize = 14.sp,
            modifier = Modifier.padding(top = 6.dp))
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
