package com.homeflix.tv.livetv

import android.view.KeyEvent as AKeyEvent
import android.view.SurfaceView
import com.homeflix.tv.presentation.ui.throttleKeyRepeat
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import coil.compose.AsyncImage
import com.homeflix.tv.presentation.ui.Tv
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Safe = 48.dp
private val GuideBackdrop = Color(0xF20B0B0B)
private val LiveRed = Color(0xFFE50914)

/** Full-screen Live TV. [channelId] = channel to start on (else the last one, else channel 1). */
@Composable
fun LiveTvScreen(channelId: String?, onExit: () -> Unit, vm: LiveTvViewModel = hiltViewModel()) {
    LaunchedEffect(Unit) { vm.start(channelId) }
    val failed by vm.loadFailed.collectAsState()
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { vm.controller.pause() }
    LifecycleEventEffect(Lifecycle.Event.ON_START) { vm.controller.resume() }
    if (failed) {
        LiveUnavailableLineup(onExit)
        return
    }
    val state by vm.state.collectAsState()
    LiveTvContent(
        state = state,
        controller = vm.controller,
        onExit = onExit,
        video = { modifier -> LiveVideo(vm, modifier) }
    )
}

/** Stateless Live TV UI; [video] draws the player surface (a fake in tests). */
@Composable
fun LiveTvContent(
    state: LiveState,
    controller: LiveTvController,
    onExit: () -> Unit,
    video: @Composable (Modifier) -> Unit
) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    // Back is also routed here when the system delivers it as a back gesture.
    BackHandler { if (controller.back()) onExit() }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .testTag("livetv")
            .focusRequester(focus)
            .focusable()
            // Held keys move the guides at a steady pace instead of flooding recomposition.
            .throttleKeyRepeat(70L)
            .onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                handleKey(e.key.nativeKeyCode, controller, onExit)
            }
    ) {
        val inGuide = state.overlay == LiveOverlay.GUIDE
        // The guide's backdrop goes *under* the video so its preview window shows through.
        if (inGuide) Box(Modifier.fillMaxSize().background(GuideBackdrop))
        // One surface for everything: full screen, or the guide's preview window.
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val pw = maxWidth * 0.36f
            video(
                if (inGuide) Modifier.padding(top = 56.dp, end = Safe).align(Alignment.TopEnd).width(pw).height(pw * 9f / 16f)
                else Modifier.fillMaxSize()
            )
        }

        // Until the first frame the surface is covered, so the old picture never shows under a new name.
        if (!inGuide) {
            when (state.phase) {
                LivePhase.TUNING, LivePhase.IDLE -> TuningScreen(state.shown ?: state.current, state.loading)
                LivePhase.UNAVAILABLE -> NoSignal(state.shown ?: state.current)
                LivePhase.PLAYING -> Unit
            }
        }

        // Info banner (and the info/favorite panel on ▶ / INFO).
        AnimatedVisibility(
            visible = (state.bannerVisible || state.overlay == LiveOverlay.INFO) && !inGuide && state.overlay != LiveOverlay.MINI_GUIDE,
            enter = fadeIn(tween(150)) + slideInVertically(tween(200)) { it / 3 },
            exit = fadeOut(tween(250)),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            (state.shown ?: state.current)?.let { InfoBanner(it, state.overlay == LiveOverlay.INFO, state.isFavorite) }
        }

        if (state.digits.isNotEmpty()) NumberEntry(state.digits, Modifier.align(Alignment.TopEnd))
        state.message?.let { Pill(it, Modifier.align(Alignment.TopCenter).padding(top = 40.dp)) }
        if (state.exitHint) Pill("Press Back again to leave Live TV", Modifier.align(Alignment.BottomCenter).padding(bottom = 40.dp))

        AnimatedVisibility(
            visible = state.overlay == LiveOverlay.MINI_GUIDE,
            enter = slideInHorizontally(tween(200)) { -it / 4 } + fadeIn(tween(150)),
            exit = slideOutHorizontally(tween(180)) { -it / 4 } + fadeOut(tween(150))
        ) { MiniGuide(state) }

        if (inGuide) FullGuide(state)
    }
}

/** Remote → controller. Returns true if the key was used. */
private fun handleKey(code: Int, c: LiveTvController, onExit: () -> Unit): Boolean {
    when (code) {
        AKeyEvent.KEYCODE_DPAD_UP, AKeyEvent.KEYCODE_CHANNEL_UP, AKeyEvent.KEYCODE_PAGE_UP -> c.up()
        AKeyEvent.KEYCODE_DPAD_DOWN, AKeyEvent.KEYCODE_CHANNEL_DOWN, AKeyEvent.KEYCODE_PAGE_DOWN -> c.down()
        AKeyEvent.KEYCODE_DPAD_CENTER, AKeyEvent.KEYCODE_ENTER, AKeyEvent.KEYCODE_NUMPAD_ENTER -> c.ok()
        AKeyEvent.KEYCODE_DPAD_LEFT -> c.left()
        AKeyEvent.KEYCODE_DPAD_RIGHT -> c.right()
        AKeyEvent.KEYCODE_INFO, AKeyEvent.KEYCODE_MENU, AKeyEvent.KEYCODE_GUIDE ->
            if (code == AKeyEvent.KEYCODE_GUIDE) c.left() else c.info()
        // Esc arrives as Back (MainActivity maps it app-wide).
        AKeyEvent.KEYCODE_BACK -> if (c.back()) onExit()
        in AKeyEvent.KEYCODE_0..AKeyEvent.KEYCODE_9 -> c.digit(code - AKeyEvent.KEYCODE_0)
        in AKeyEvent.KEYCODE_NUMPAD_0..AKeyEvent.KEYCODE_NUMPAD_9 -> c.digit(code - AKeyEvent.KEYCODE_NUMPAD_0)
        AKeyEvent.KEYCODE_LAST_CHANNEL -> if (c.back()) onExit()
        else -> return false
    }
    return true
}

@Composable
private fun LiveVideo(vm: LiveTvViewModel, modifier: Modifier) {
    var view by remember { mutableStateOf<SurfaceView?>(null) }
    AndroidView(
        factory = { ctx -> SurfaceView(ctx).also { view = it; vm.attach(it) } },
        modifier = modifier
    )
    DisposableEffect(Unit) { onDispose { view?.let(vm::detach) } }
}

// ------------------------------------------------------------------ screens

@Composable
private fun TuningScreen(channel: LiveChannel?, loading: Boolean) {
    Box(Modifier.fillMaxSize().background(Color.Black).testTag("live_tuning"), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp)) {
            if (channel != null) LogoTile(channel, 180.dp, 100.dp)
            else if (!loading) Text("Live TV", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
        }
    }
}

@Composable
private fun NoSignal(channel: LiveChannel?) {
    Box(Modifier.fillMaxSize().background(Color.Black).testTag("live_unavailable"), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (channel != null) LogoTile(channel, 140.dp, 78.dp)
            Text("${channel?.name ?: "This channel"} isn't available right now", color = Color.White, fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
            Text("Press ▲ ▼ for another channel", color = Tv.TextSecondary, fontSize = 15.sp)
        }
    }
}

@Composable
private fun LiveUnavailableLineup(onExit: () -> Unit) {
    BackHandler(onBack = onExit)
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    Box(Modifier.fillMaxSize().background(Color.Black).focusRequester(focus).focusable(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Live TV isn't available", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text("Turn it on in the HomeFlix web app: Settings → Live TV.", color = Tv.TextSecondary, fontSize = 15.sp)
        }
    }
}

@Composable
private fun InfoBanner(ch: LiveChannel, infoMode: Boolean, favorite: Boolean) {
    val clock = rememberClock()
    Box(
        Modifier.fillMaxWidth()
            .background(Brush.verticalGradient(0f to Color.Transparent, 0.35f to Color(0xB3000000), 1f to Color(0xF2000000)))
            .padding(start = Safe, end = Safe, top = 70.dp, bottom = 36.dp)
            .testTag("live_banner")
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(ch.number.toString(), color = Color.White, fontSize = 54.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.width(130.dp))
            LogoTile(ch, 132.dp, 74.dp)
            Column(Modifier.padding(start = 22.dp).weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(ch.name, color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold, maxLines = 1,
                        overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    if (favorite) Text("  ★", color = Color(0xFFFFC94D), fontSize = 22.sp)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LiveDot()
                    Text(" LIVE", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    if (ch.details.isNotBlank()) Text("    ${ch.details}", color = Tv.TextSecondary, fontSize = 15.sp, maxLines = 1)
                }
                if (infoMode) {
                    Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(if (favorite) "★  In Favorites" else "☆  Add to Favorites", color = Color.Black, fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.clip(RoundedCornerShape(50)).background(Color.White).padding(horizontal = 16.dp, vertical = 7.dp))
                        Text("OK to ${if (favorite) "remove" else "add"}  ·  ◀ guide  ·  Back to close", color = Tv.TextSecondary, fontSize = 13.sp)
                    }
                }
            }
            Text(clock, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun NumberEntry(digits: String, modifier: Modifier) {
    val shown = digits.map { it.toString() } + if (digits.length < LiveTvController.MAX_DIGITS) listOf("_") else emptyList()
    Text(
        shown.joinToString(" "),
        color = Color.White, fontSize = 46.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp,
        modifier = modifier.padding(top = 36.dp, end = Safe)
            .clip(RoundedCornerShape(10.dp)).background(Color(0x99000000)).padding(horizontal = 20.dp, vertical = 6.dp)
            .testTag("live_digits")
    )
}

@Composable
private fun Pill(text: String, modifier: Modifier) {
    Text(text, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium,
        modifier = modifier.clip(RoundedCornerShape(50)).background(Color(0xCC1F1F1F)).padding(horizontal = 18.dp, vertical = 8.dp))
}

// ------------------------------------------------------------------- guides

@Composable
private fun MiniGuide(state: LiveState) {
    val list = rememberLazyListState()
    KeepInView(list, state.miniIndex)
    Box(
        Modifier.fillMaxHeight().fillMaxWidth(0.35f)
            .background(Brush.horizontalGradient(0f to Color(0xF0000000), 0.85f to Color(0xD9000000), 1f to Color(0xB3000000)))
            .testTag("live_mini_guide")
    ) {
        Column(Modifier.padding(start = Safe, end = 16.dp, top = 36.dp)) {
            Text("Channels", color = Tv.TextSecondary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 10.dp))
            LazyColumn(state = list, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                itemsIndexed(state.list, key = { _, c -> c.id }) { i, ch ->
                    ChannelRow(ch, highlighted = i == state.miniIndex, now = ch.id == state.current?.id,
                        favorite = ch.id in state.favorites)
                }
            }
        }
    }
}

@Composable
private fun FullGuide(state: LiveState) {
    val groupList = rememberLazyListState()
    val channelList = rememberLazyListState()
    KeepInView(groupList, state.groupIndex)
    KeepInView(channelList, state.guideIndex)
    val focused = state.guideChannels.getOrNull(state.guideIndex)
    // The backdrop is drawn by LiveTvContent under the video (see GuideBackdrop).
    Box(Modifier.fillMaxSize().testTag("live_guide")) {
        Row(Modifier.fillMaxSize().padding(start = Safe, top = 36.dp)) {
            Column(Modifier.width(260.dp)) {
                Text("Live TV", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 14.dp))
                LazyColumn(state = groupList, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    itemsIndexed(state.groups, key = { _, g -> g.id }) { i, g ->
                        val selected = i == state.groupIndex
                        val active = selected && state.guideColumn == GuideColumn.GROUPS
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                                .background(when { active -> Color.White; selected -> Color(0x33FFFFFF); else -> Color.Transparent })
                                .padding(horizontal = 14.dp, vertical = 9.dp)
                        ) {
                            Text(g.title, color = if (active) Color.Black else Color.White, fontSize = 16.sp,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1,
                                overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            Text(g.channels.size.toString(), color = if (active) Color.DarkGray else Tv.TextMuted, fontSize = 13.sp)
                        }
                    }
                }
            }
            Spacer(Modifier.width(24.dp))
            Column(Modifier.weight(0.58f).padding(top = 48.dp)) {
                LazyColumn(state = channelList, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    itemsIndexed(state.guideChannels, key = { _, c -> c.id }) { i, ch ->
                        ChannelRow(ch, highlighted = i == state.guideIndex && state.guideColumn == GuideColumn.CHANNELS,
                            now = ch.id == state.current?.id, favorite = ch.id in state.favorites,
                            dim = state.guideColumn == GuideColumn.GROUPS)
                    }
                }
            }
            Spacer(Modifier.weight(0.42f))
        }
        // Under the preview window (the video surface is placed above it by LiveTvContent).
        if (focused != null) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val pw = maxWidth * 0.36f
                Column(Modifier.align(Alignment.TopEnd).padding(end = Safe, top = 56.dp).width(pw)) {
                    Box(Modifier.fillMaxWidth().height(pw * 9f / 16f).clip(RoundedCornerShape(10.dp)).border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(10.dp)))
                    Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("${focused.number}  ${focused.name}", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold,
                            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        LiveDot(); Text(" LIVE", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    if (focused.details.isNotBlank()) Text(focused.details, color = Tv.TextSecondary, fontSize = 14.sp)
                    Text("OK to watch  ·  ◀ ▶ groups / channels  ·  Back to close", color = Tv.TextMuted, fontSize = 13.sp, modifier = Modifier.padding(top = 10.dp))
                }
                // Tuning / unavailable state for the preview window.
                if (state.phase != LivePhase.PLAYING || state.playing?.id != focused.id) {
                    Box(Modifier.align(Alignment.TopEnd).padding(end = Safe, top = 56.dp).width(pw).height(pw * 9f / 16f)
                        .background(Color.Black), contentAlignment = Alignment.Center) {
                        if (state.phase == LivePhase.UNAVAILABLE) Text("Not available right now", color = Tv.TextSecondary, fontSize = 14.sp)
                        else LogoTile(focused, 120.dp, 66.dp)
                    }
                }
            }
        }
    }
}

@Composable
private fun ChannelRow(ch: LiveChannel, highlighted: Boolean, now: Boolean, favorite: Boolean, dim: Boolean = false) {
    val fg = if (highlighted) Color.Black else if (dim) Tv.TextSecondary else Color.White
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
            .background(if (highlighted) Color.White else Color.Transparent)
            .padding(horizontal = 10.dp, vertical = 7.dp)
    ) {
        Text(ch.number.toString(), color = if (highlighted) Color.DarkGray else Tv.TextSecondary, fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold, modifier = Modifier.width(46.dp))
        LogoTile(ch, 54.dp, 32.dp)
        Column(Modifier.padding(start = 12.dp).weight(1f)) {
            Text(ch.name + if (favorite) "  ★" else "", color = fg, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            ch.category?.let {
                Text(it.replaceFirstChar { c -> c.uppercase() }, color = if (highlighted) Color.DarkGray else Tv.TextMuted, fontSize = 12.sp, maxLines = 1)
            }
        }
        if (now) {
            LiveDot()
            Text(" NOW", color = if (highlighted) Color.Black else Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}

/** Channel logo on a white rounded tile (most logos are made for light backgrounds). */
@Composable
fun LogoTile(ch: LiveChannel, width: androidx.compose.ui.unit.Dp, height: androidx.compose.ui.unit.Dp) {
    Box(
        Modifier.width(width).height(height).clip(RoundedCornerShape(8.dp)).background(Color.White).padding(6.dp),
        contentAlignment = Alignment.Center
    ) {
        if (ch.logo.isNullOrBlank()) {
            Text(ch.name.take(14), color = Color.Black, fontSize = (height.value / 4).coerceIn(10f, 18f).sp,
                fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        } else {
            AsyncImage(model = ch.logo, contentDescription = ch.name, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
        }
    }
}

@Composable
fun LiveDot() {
    Box(Modifier.size(9.dp).clip(CircleShape).background(LiveRed))
}

@Composable
private fun KeepInView(list: LazyListState, index: Int) {
    LaunchedEffect(index) {
        val visible = list.layoutInfo.visibleItemsInfo
        val first = visible.firstOrNull()?.index ?: 0
        val last = visible.lastOrNull()?.index ?: 0
        if (visible.isEmpty() || index <= first || index >= last) {
            val target = (index - 3).coerceAtLeast(0)
            // Far jumps (wrap-around, held keys) snap; one-step moves glide.
            if (kotlin.math.abs(target - first) > 6) list.scrollToItem(target) else list.animateScrollToItem(target)
        }
    }
}

@Composable
private fun rememberClock(): String {
    val fmt = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }
    var text by remember { mutableStateOf(fmt.format(Date())) }
    LaunchedEffect(Unit) {
        while (true) {
            text = fmt.format(Date())
            delay(10_000)
        }
    }
    return text
}
