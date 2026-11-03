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
    Column(
        Modifier.fillMaxWidth()
            .background(Brush.verticalGradient(0f to Color.Transparent, 0.3f to Color(0xA6000000), 1f to Color(0xF2000000)))
            .padding(start = Safe, end = Safe, top = 80.dp, bottom = 28.dp)
            .testTag("cs_overlay")
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("COMING SOON", color = Tv.Red, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.6.sp)
            item.releaseLabel?.takeIf { it.isNotBlank() }?.let {
                Text(it, color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 10.dp).clip(RoundedCornerShape(4.dp)).background(Color.White).padding(horizontal = 6.dp, vertical = 1.dp))
            }
            if (state.noTrailer) Text("   Trailer coming soon", color = Tv.TextMuted, fontSize = 12.sp)
        }
        Text(item.title, color = Color.White, fontSize = 38.sp, fontWeight = FontWeight.Black, maxLines = 1,
            overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
            Text(ComingSoonDates.fullDate(item), color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text("   ${ComingSoonDates.countdown(item.daysUntil)}", color = Color(0xFFFFC94D), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        }
        val meta = (listOfNotNull(item.studio?.takeIf { it.isNotBlank() }) + item.genres.orEmpty().take(3)).joinToString("  ·  ")
        if (meta.isNotBlank()) Text(meta, color = Tv.TextSecondary, fontSize = 14.sp, modifier = Modifier.padding(top = 6.dp))
        item.cast.orEmpty().takeIf { it.isNotEmpty() }?.let {
            Text("Starring ${it.take(if (state.details) 4 else 3).joinToString(", ")}", color = Tv.TextSecondary, fontSize = 14.sp, maxLines = 1,
                overflow = TextOverflow.Ellipsis)
        }
        item.overview?.takeIf { it.isNotBlank() }?.let {
            Text(it, color = Tv.TextPrimary, fontSize = 14.sp, lineHeight = 20.sp, maxLines = if (state.details) 8 else 2,
                overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth(0.62f).padding(top = 8.dp))
        }
        Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            val on = item.notifyOn
            CsButton(if (on) "We'll Notify You" else "Notify Me", if (on) Icons.Filled.Check else Icons.Filled.NotificationsNone,
                focused = state.zone == CsZone.NOTIFY)
            item.notify?.status?.takeIf { on && it.isNotBlank() }?.let { Text(it, color = Tv.TextMuted, fontSize = 12.sp) }
            CsButton(if (state.details) "Less" else "Details", Icons.Filled.Info, focused = state.zone == CsZone.DETAILS)
            Text("◀ ▶  previous / next", color = Tv.TextMuted, fontSize = 12.sp, modifier = Modifier.padding(start = 8.dp))
        }
        Strip(state)
    }
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

/** The upcoming titles along the bottom; the playing one is outlined (white when the strip has focus). */
@Composable
private fun Strip(state: ComingSoonState) {
    val list = rememberLazyListState()
    LaunchedEffect(state.index) { list.animateScrollToItem((state.index - 2).coerceAtLeast(0)) }
    LazyRow(state = list, horizontalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(top = 16.dp)) {
        itemsIndexed(state.items, key = { _, it -> it.key }) { i, it ->
            val current = i == state.index
            Box(
                Modifier.width(150.dp).height(84.dp).clip(RoundedCornerShape(8.dp)).background(Tv.Surface)
                    .border(if (current) 3.dp else 0.dp,
                        if (current && state.zone == CsZone.STRIP) Color.White else if (current) Color(0x99FFFFFF) else Color.Transparent,
                        RoundedCornerShape(8.dp))
            ) {
                SizedImage(it.backdropUrl ?: it.posterUrl, 150.dp, 84.dp, Modifier.fillMaxSize())
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.5f to Color.Transparent, 1f to Color(0xCC000000))))
                Text(it.title, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, modifier = Modifier.align(Alignment.BottomStart).padding(6.dp))
                if (it.notifyOn) Icon(Icons.Filled.Notifications, null, tint = Color.White,
                    modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(14.dp))
            }
        }
    }
}
