package com.homeflix.tv.comingsoon

import android.view.KeyEvent as AKeyEvent
import android.view.SurfaceView
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.homeflix.tv.presentation.ui.SizedImage
import com.homeflix.tv.presentation.ui.Tv
import com.homeflix.tv.presentation.ui.throttleKeyRepeat

private val Safe = 48.dp

/** Full-screen Coming Soon, starting at [startKey] ("movie:123" / "tv:456"). */
@Composable
fun ComingSoonScreen(startKey: String?, onExit: () -> Unit, vm: ComingSoonViewModel = hiltViewModel()) {
    LaunchedEffect(Unit) { vm.start(startKey) }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { vm.controller.pause() }
    LifecycleEventEffect(Lifecycle.Event.ON_START) { vm.controller.resume() }
    val state by vm.controller.state.collectAsState()
    ComingSoonContent(state, vm.controller, onExit) { m ->
        var view by remember { mutableStateOf<SurfaceView?>(null) }
        AndroidView(factory = { ctx -> SurfaceView(ctx).also { view = it; vm.attach(it) } }, modifier = m)
        DisposableEffect(Unit) { onDispose { view?.let(vm::detach) } }
    }
}

@Composable
fun ComingSoonContent(state: ComingSoonState, c: ComingSoonController, onExit: () -> Unit, video: @Composable (Modifier) -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    BackHandler { if (c.back()) onExit() }
    // Nothing coming soon (or the server can't say): leave quietly.
    LaunchedEffect(state.loading, state.items.isEmpty()) { if (!state.loading && state.items.isEmpty()) onExit() }

    Box(
        Modifier.fillMaxSize().background(Color.Black).testTag("comingsoon")
            .focusRequester(focus).focusable()
            .throttleKeyRepeat(120L)
            .onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (e.key.nativeKeyCode) {
                    AKeyEvent.KEYCODE_DPAD_LEFT -> c.left()
                    AKeyEvent.KEYCODE_DPAD_RIGHT -> c.right()
                    AKeyEvent.KEYCODE_DPAD_UP -> c.up()
                    AKeyEvent.KEYCODE_DPAD_DOWN -> c.down()
                    AKeyEvent.KEYCODE_DPAD_CENTER, AKeyEvent.KEYCODE_ENTER, AKeyEvent.KEYCODE_NUMPAD_ENTER -> c.ok()
                    AKeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, AKeyEvent.KEYCODE_MEDIA_PLAY, AKeyEvent.KEYCODE_MEDIA_PAUSE -> c.playPause()
                    AKeyEvent.KEYCODE_MEDIA_NEXT, AKeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> c.next()
                    AKeyEvent.KEYCODE_MEDIA_PREVIOUS, AKeyEvent.KEYCODE_MEDIA_REWIND -> c.previous()
                    AKeyEvent.KEYCODE_BACK -> if (c.back()) onExit()
                    else -> { c.wake(); return@onPreviewKeyEvent false }
                }
                true
            }
    ) {
        video(Modifier.fillMaxSize())
        // The backdrop covers the video until its first frame (and stays for titles without a trailer).
        val item = state.current
        if (!state.videoVisible) {
            Crossfade(targetState = item?.backdropUrl, animationSpec = tween(400), label = "csBackdrop") { url ->
                SizedImage(url, 960.dp, 540.dp, Modifier.fillMaxSize())
            }
        }
        if (state.paused) {
            Icon(Icons.Filled.Pause, "Paused", tint = Color.White,
                modifier = Modifier.align(Alignment.Center).size(64.dp).clip(RoundedCornerShape(50)).background(Color(0x99000000)).padding(12.dp))
        }
        AnimatedVisibility(
            visible = state.overlayVisible && item != null,
            enter = fadeIn(tween(200)), exit = fadeOut(tween(500)),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) { item?.let { Overlay(state, it) } }
        state.message?.let {
            Text(it, color = Color.White, fontSize = 15.sp, modifier = Modifier.align(Alignment.TopCenter).padding(top = 40.dp)
                .clip(RoundedCornerShape(50)).background(Color(0xCC1F1F1F)).padding(horizontal = 18.dp, vertical = 8.dp))
        }
    }
}

@Composable
private fun Overlay(state: ComingSoonState, item: ComingSoonItem) {
    Box(Modifier.fillMaxSize().testTag("cs_overlay")) {
        // Legibility over any trailer: a wash from the left and from the bottom.
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0f to Color(0xD9000000), 0.55f to Color(0x66000000), 1f to Color.Transparent)))
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.45f to Color.Transparent, 1f to Color(0xF2000000))))
        Box(Modifier.fillMaxWidth().height(120.dp).background(Brush.verticalGradient(0f to Color(0xB3000000), 1f to Color.Transparent)))

        // Top: what this is, where we are, what's next.
        Row(Modifier.fillMaxWidth().padding(start = Safe, end = Safe, top = 30.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(com.homeflix.tv.presentation.ui.HomeFlixMark, null, tint = Color.White, modifier = Modifier.size(14.dp, 16.dp))
            Text("  COMING SOON", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
            if (item.rank > 0) {
                Text("   ·   #${item.rank} in ${if (item.isSeries) "Series" else "Movies"}", color = Tv.TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
            androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
            val next = state.items.getOrNull((state.index + 1) % state.items.size.coerceAtLeast(1))
            if (next != null && state.items.size > 1) {
                Text("Up next  ", color = Tv.TextMuted, fontSize = 13.sp)
                Text(next.title, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 260.dp))
                Text("    ", fontSize = 13.sp)
            }
            Text("${state.index + 1} / ${state.items.size}", color = Tv.TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clip(RoundedCornerShape(50)).background(Color(0x33FFFFFF)).padding(horizontal = 10.dp, vertical = 3.dp))
        }

        Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(start = Safe, end = Safe, bottom = 26.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                DateTile(item)
                Column(Modifier.padding(start = 20.dp).weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Chip(if (item.isSeries) "SERIES" else "MOVIE", Color(0x33FFFFFF), Color.White)
                        item.releaseLabel?.takeIf { it.isNotBlank() }?.let { Chip(it.uppercase(), Color.White, Color.Black) }
                        if (state.noTrailer) Text("Trailer coming soon", color = Tv.TextMuted, fontSize = 12.sp, modifier = Modifier.padding(start = 4.dp))
                    }
                    Text(item.title, color = Color.White, fontSize = 44.sp, fontWeight = FontWeight.Black, lineHeight = 46.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                        Text(ComingSoonDates.countdown(item.daysUntil), color = Color.Black, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.clip(RoundedCornerShape(50)).background(Color(0xFFFFC94D)).padding(horizontal = 10.dp, vertical = 3.dp))
                        Text("   ${ComingSoonDates.fullDate(item)}", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        item.studio?.takeIf { it.isNotBlank() }?.let { Text("   ·   $it", color = Tv.TextSecondary, fontSize = 15.sp) }
                    }
                }
            }
            val genres = item.genres.orEmpty().take(3)
            if (genres.isNotEmpty()) {
                Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    genres.forEach { g ->
                        Text(g, color = Tv.TextPrimary, fontSize = 12.sp, modifier = Modifier.clip(RoundedCornerShape(50))
                            .border(1.dp, Color(0x4DFFFFFF), RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 3.dp))
                    }
                }
            }
            item.overview?.takeIf { it.isNotBlank() }?.let {
                Text(it, color = Tv.TextPrimary, fontSize = 15.sp, lineHeight = 21.sp, maxLines = if (state.details) 8 else 2,
                    overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth(0.58f).padding(top = 10.dp))
            }
            item.cast.orEmpty().takeIf { it.isNotEmpty() }?.let {
                Text("Starring  ${it.take(if (state.details) 4 else 3).joinToString(" · ")}", color = Tv.TextSecondary, fontSize = 13.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
            }
            Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                val on = item.notifyOn
                CsButton(if (on) "We'll Notify You" else "Notify Me", if (on) Icons.Filled.Check else Icons.Filled.NotificationsNone,
                    focused = state.zone == CsZone.NOTIFY)
                item.notify?.status?.takeIf { on && it.isNotBlank() }?.let {
                    Text(it.replaceFirstChar { c -> c.uppercase() }, color = Tv.TextMuted, fontSize = 12.sp)
                }
                CsButton(if (state.details) "Less" else "Details", Icons.Filled.Info, focused = state.zone == CsZone.DETAILS)
                androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
                Text("◀ ▶  browse    ▲ ▼  buttons / titles", color = Tv.TextMuted, fontSize = 12.sp)
            }
            Strip(state)
        }
    }
}

/** Calendar tile: "FEB" over a big "12" (or the year for far-off dates). */
@Composable
private fun DateTile(item: ComingSoonItem) {
    val (month, day) = remember(item.releaseDate) {
        runCatching {
            val d = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).parse(item.releaseDate!!)!!
            java.text.SimpleDateFormat("MMM", java.util.Locale.ENGLISH).format(d).uppercase() to java.text.SimpleDateFormat("d", java.util.Locale.ENGLISH).format(d)
        }.getOrDefault("SOON" to "—")
    }
    Column(
        Modifier.width(86.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xE6FFFFFF)),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(month, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp,
            modifier = Modifier.fillMaxWidth().background(Tv.Red).padding(vertical = 4.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Text(day, color = Color.Black, fontSize = 38.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(vertical = 4.dp))
    }
}

@Composable
private fun Chip(text: String, bg: Color, fg: Color) {
    Text(text, color = fg, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp,
        modifier = Modifier.padding(end = 8.dp).clip(RoundedCornerShape(4.dp)).background(bg).padding(horizontal = 7.dp, vertical = 2.dp))
}

@Composable
private fun CsButton(text: String, icon: ImageVector, focused: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.clip(RoundedCornerShape(50)).background(if (focused) Color.White else Color(0x33FFFFFF))
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Icon(icon, null, tint = if (focused) Color.Black else Color.White, modifier = Modifier.size(18.dp))
        Text("  $text", color = if (focused) Color.Black else Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** The upcoming titles along the bottom; the playing one is lifted and outlined (white when the strip has focus). */
@Composable
private fun Strip(state: ComingSoonState) {
    val list = rememberLazyListState()
    LaunchedEffect(state.index) { list.animateScrollToItem((state.index - 2).coerceAtLeast(0)) }
    LazyRow(state = list, horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(top = 18.dp, start = 6.dp, end = 6.dp)) {
        itemsIndexed(state.items, key = { _, it -> it.key }) { i, it ->
            val current = i == state.index
            Column(Modifier.width(176.dp).graphicsLayer { val sc = if (current) 1.04f else 1f; scaleX = sc; scaleY = sc; alpha = if (current) 1f else 0.72f }) {
                Box(
                    Modifier.fillMaxWidth().height(99.dp).clip(RoundedCornerShape(8.dp)).background(Tv.Surface)
                        .border(if (current) 3.dp else 0.dp,
                            if (current && state.zone == CsZone.STRIP) Color.White else if (current) Color(0xB3FFFFFF) else Color.Transparent,
                            RoundedCornerShape(8.dp))
                ) {
                    SizedImage(it.backdropUrl ?: it.posterUrl, 176.dp, 99.dp, Modifier.fillMaxSize())
                    if (it.notifyOn) Icon(Icons.Filled.Notifications, null, tint = Color.White,
                        modifier = Modifier.align(Alignment.TopEnd).padding(5.dp).size(18.dp).clip(RoundedCornerShape(50)).background(Color(0x99000000)).padding(3.dp))
                }
                Text(it.title, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
                Text(ComingSoonDates.cardLine(it), color = if (current) Color(0xFFFFC94D) else Tv.TextMuted, fontSize = 11.sp, maxLines = 1)
            }
        }
    }
}
