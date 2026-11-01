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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.homeflix.tv.domain.model.Notification
import com.homeflix.tv.presentation.navigation.AppNavigator
import com.homeflix.tv.presentation.shell.ErrorState
import com.homeflix.tv.presentation.shell.LoadingState
import com.homeflix.tv.presentation.ui.MediaFormat
import com.homeflix.tv.presentation.ui.PillButton
import com.homeflix.tv.presentation.ui.SizedImage
import com.homeflix.tv.presentation.ui.TitleLogo
import com.homeflix.tv.presentation.ui.Tv

/**
 * Notifications, Netflix "New & Hot" style: a list on the left; the focused
 * item's artwork fills the screen behind a details panel on the right.
 */
@Composable
fun NotificationsScreen(navigator: AppNavigator, vm: NotificationsViewModel = hiltViewModel()) {
    val state by vm.uiState.collectAsState()
    Box(Modifier.fillMaxSize().background(Tv.Background)) {
        when (val s = state) {
            NotificationsUiState.Loading -> LoadingState()
            is NotificationsUiState.Error -> ErrorState("Couldn't load notifications", onRetry = vm::loadNotifications)
            is NotificationsUiState.Success ->
                if (s.notifications.isEmpty()) EmptyNotifications()
                else NotificationsContent(s.notifications.distinctBy { it.id }, navigator)
        }
    }
}

private fun open(n: Notification, navigator: AppNavigator) {
    when (n.targetType) {
        "series" -> n.targetId?.let(navigator::series)
        "movie" -> n.targetId?.let(navigator::details)
    }
}

@Composable
private fun NotificationsContent(items: List<Notification>, navigator: AppNavigator) {
    var focused by rememberSaveable { mutableIntStateOf(0) }
    val first = remember { FocusRequester() }
    val listState = rememberLazyListState()
    LaunchedEffect(Unit) { withFrameNanos { }; runCatching { first.requestFocus() } }
    val current = items.getOrNull(focused.coerceIn(0, items.lastIndex))

    Box(Modifier.fillMaxSize()) {
        // Full-bleed artwork of the focused notification.
        Crossfade(targetState = current, animationSpec = tween(400), label = "bg", modifier = Modifier.fillMaxSize()) { n ->
            if (n != null) FallbackImage(n.imageCandidates, 960.dp, 540.dp, Modifier.fillMaxSize())
        }
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(
            0f to Tv.Background, 0.42f to Color(0xF2141414), 0.62f to Color(0x99141414), 1f to Color(0x33141414))))
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(
            0.55f to Color.Transparent, 1f to Color(0xE6141414))))

        Row(Modifier.fillMaxSize()) {
            // ---------------------------------------------------------------- list
            Column(Modifier.width(470.dp).fillMaxHeight().padding(start = Tv.Keyline, top = 28.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Notifications", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    val unread = items.count { !it.read }
                    if (unread > 0) {
                        Text(
                            "$unread new", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 10.dp).clip(RoundedCornerShape(4.dp))
                                .background(Tv.Red).padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(top = 14.dp, bottom = 60.dp, end = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    itemsIndexed(items, key = { _, n -> n.id }) { i, n ->
                        NotificationCard(
                            n = n,
                            modifier = if (i == 0) Modifier.focusRequester(first) else Modifier,
                            onFocused = { focused = i },
                            onClick = { open(n, navigator) }
                        )
                    }
                }
            }

            // ---------------------------------------------------------------- details
            Box(Modifier.weight(1f).fillMaxHeight().padding(start = 32.dp, end = Tv.Keyline, bottom = 40.dp)) {
                AnimatedContent(
                    targetState = current,
                    transitionSpec = { fadeIn(tween(250, delayMillis = 80)) togetherWith fadeOut(tween(120)) },
                    contentKey = { it?.id },
                    label = "details",
                    modifier = Modifier.align(Alignment.BottomStart)
                ) { n ->
                    if (n != null) Details(n) { open(n, navigator) }
                }
            }
        }
    }
}

@Composable
private fun Details(n: Notification, onOpen: () -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        n.category?.takeIf { it.isNotBlank() }?.let {
            Text(it.replace('_', ' ').uppercase(), color = Tv.Red, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
        }
        TitleLogo(
            logoUrl = n.logoUrl,
            title = n.itemTitle ?: n.title,
            maxWidth = 320.dp,
            maxHeight = 96.dp,
            textStyle = TextStyle(color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Black, lineHeight = 33.sp)
        )
        val meta = buildList {
            n.year?.takeIf { it > 0 }?.let { add(it.toString()) }
            n.runtimeMinutes?.takeIf { it > 0 }?.let { add(MediaFormat.duration(it)) }
            n.genres.take(2).forEach(::add)
            if (n.rating > 0) add("★ %.1f".format(n.rating))
        }.joinToString("  •  ")
        if (meta.isNotBlank()) Text(meta, color = Tv.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Text(n.title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 2,
            overflow = TextOverflow.Ellipsis)
        (n.overview?.takeIf { it.isNotBlank() } ?: n.message.takeIf { it.isNotBlank() })?.let {
            Text(it, color = Tv.TextSecondary, fontSize = 14.sp, lineHeight = 20.sp, maxLines = 4, overflow = TextOverflow.Ellipsis)
        }
        if (n.targetId != null) {
            Text("Press OK to open", color = Tv.TextMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
private fun NotificationCard(n: Notification, modifier: Modifier, onFocused: () -> Unit, onClick: () -> Unit) {
    var isFocused by remember { mutableStateOf(false) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged { isFocused = it.isFocused; if (it.isFocused) onFocused() }
            .clip(RoundedCornerShape(8.dp))
            .background(if (isFocused) Color(0x26FFFFFF) else Color.Transparent)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(8.dp)
    ) {
        Box(
            Modifier
                .width(144.dp)
                .height(81.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Tv.Surface)
                .then(if (isFocused) Modifier.border(2.dp, Color.White, RoundedCornerShape(6.dp)) else Modifier)
        ) {
            FallbackImage(n.imageCandidates, 144.dp, 81.dp, Modifier.fillMaxSize())
            if (!n.read) {
                Box(Modifier.align(Alignment.TopStart).padding(6.dp).size(8.dp).clip(CircleShape).background(Tv.Red))
            }
        }
        Column(Modifier.padding(start = 12.dp).weight(1f)) {
            Text(timeAgo(n.timestampSeconds), color = Tv.TextMuted, fontSize = 11.sp)
            Text(n.title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
                overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 1.dp))
            Text(n.message.ifBlank { n.itemTitle.orEmpty() }, color = if (isFocused) Tv.TextPrimary else Tv.TextSecondary,
                fontSize = 12.sp, lineHeight = 16.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 1.dp))
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
