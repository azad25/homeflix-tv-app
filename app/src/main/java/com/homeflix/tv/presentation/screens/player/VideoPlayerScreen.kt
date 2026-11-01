package com.homeflix.tv.presentation.screens.player

import android.view.KeyEvent
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import com.homeflix.tv.presentation.ui.MediaFormat
import com.homeflix.tv.presentation.ui.PillButton
import com.homeflix.tv.presentation.ui.SizedImage
import com.homeflix.tv.presentation.ui.TitleLogo
import com.homeflix.tv.presentation.ui.Tv
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class Panel { NONE, TRACKS, SPEED }

/**
 * Netflix-style full-screen player.
 *
 * Controls hidden: OK = play/pause, ◀ ▶ = seek 10 s (hold to scrub faster),
 * any other key shows the controls. Controls shown: the timeline has focus
 * (◀ ▶ scrub, OK play/pause), ▲ reaches Back/Restart and the pause actions,
 * ▼ reaches Audio & Subtitles / Speed / Next Episode.
 * Paused for a while → "You're watching" pause screen.
 */
@UnstableApi
@Composable
fun VideoPlayerScreen(
    mediaId: Int,
    startTimeMs: Long,
    forceStartFromBeginning: Boolean,
    onClose: () -> Unit,
    onPlayNext: (Int) -> Unit,
    vm: VideoPlayerViewModel = hiltViewModel()
) {
    val ui by vm.ui.collectAsState()
    val player = vm.player
    val scope = rememberCoroutineScope()

    LaunchedEffect(mediaId) { vm.load(mediaId, startTimeMs, forceStartFromBeginning) }
    LaunchedEffect(vm) {
        vm.events.collect { e ->
            when (e) {
                PlayerEvent.Close -> onClose()
                is PlayerEvent.PlayNext -> onPlayNext(e.mediaId)
            }
        }
    }

    // Lifecycle: Home button / screen off → save + release; back → re-prepare.
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_START -> vm.onStart()
                Lifecycle.Event.ON_STOP -> vm.onStop()
                else -> Unit
            }
        }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }

    var controlsVisible by remember { mutableStateOf(false) }
    var pauseScreen by remember { mutableStateOf(false) }
    var panel by remember { mutableStateOf(Panel.NONE) }
    var lastInteraction by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var scrubTarget by remember { mutableStateOf<Long?>(null) }
    var scrubSteps by remember { mutableIntStateOf(0) }
    var focusTarget by remember { mutableIntStateOf(0) } // bumps to (re)focus the timeline

    val rootFocus = remember { FocusRequester() }
    val timelineFocus = remember { FocusRequester() }

    fun touch() { lastInteraction = System.currentTimeMillis() }
    fun showControls(focusTimeline: Boolean = true) {
        touch()
        pauseScreen = false
        if (!controlsVisible) { controlsVisible = true; if (focusTimeline) focusTarget++ }
    }
    fun commitScrub() {
        scrubTarget?.let { vm.seekTo(it) }
        scrubTarget = null
        scrubSteps = 0
    }
    fun scrub(direction: Int, repeat: Int) {
        val base = scrubTarget ?: player.currentPosition
        val step = when {
            repeat > 20 -> 60_000L
            repeat > 6 -> 30_000L
            else -> 10_000L
        }
        val dur = player.duration.takeIf { it > 0 } ?: ui.durationMs
        scrubTarget = (base + direction * step).coerceIn(0, (dur - 1_000).coerceAtLeast(0))
        scrubSteps++
        touch()
    }
    fun exit() { vm.close(); onClose() }

    // Commit a scrub shortly after the last key press (Netflix-style preview-then-seek).
    LaunchedEffect(scrubTarget, scrubSteps) {
        if (scrubTarget != null) { delay(650); commitScrub() }
    }
    // Auto-hide while playing; pause screen after a while paused.
    LaunchedEffect(controlsVisible, ui.playWhenReady, lastInteraction, panel, ui.phase) {
        if (panel != Panel.NONE || ui.phase !is PlayerPhase.Ready) return@LaunchedEffect
        if (ui.playWhenReady) {
            if (controlsVisible) { delay(4_500); if (scrubTarget == null) controlsVisible = false }
        } else {
            delay(9_000)
            controlsVisible = false
            pauseScreen = true
        }
    }
    // Show controls whenever the user pauses or we come back paused, and briefly
    // when playback first starts (so the title is visible, like Netflix).
    val ready = ui.phase is PlayerPhase.Ready
    var shownOnStart by remember { mutableStateOf(false) }
    LaunchedEffect(ui.playWhenReady, ready) {
        if (!ready) return@LaunchedEffect
        if (!ui.playWhenReady) showControls()
        else if (!shownOnStart) { shownOnStart = true; showControls() }
    }
    LaunchedEffect(focusTarget) {
        if (focusTarget == 0) return@LaunchedEffect
        withFrameNanos { }
        runCatching { timelineFocus.requestFocus() }
    }
    LaunchedEffect(controlsVisible, panel, pauseScreen) {
        if (!controlsVisible && panel == Panel.NONE) { withFrameNanos { }; runCatching { rootFocus.requestFocus() } }
    }

    BackHandler {
        when {
            panel != Panel.NONE -> { panel = Panel.NONE; showControls() }
            pauseScreen -> showControls()
            else -> exit()
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (e.key.nativeKeyCode) {
                    KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> { vm.togglePlayPause(); showControls(); true }
                    KeyEvent.KEYCODE_MEDIA_PLAY -> { vm.play(); showControls(); true }
                    KeyEvent.KEYCODE_MEDIA_PAUSE -> { vm.pause(); showControls(); true }
                    KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> { vm.seekTo(player.currentPosition + 30_000); showControls(); true }
                    KeyEvent.KEYCODE_MEDIA_REWIND -> { vm.seekTo(player.currentPosition - 30_000); showControls(); true }
                    KeyEvent.KEYCODE_MEDIA_NEXT -> { ui.nextEpisode?.let { onPlayNext(it.id) }; true }
                    else -> { touch(); false }
                }
            }
    ) {
        PlayerSurface(player, keepOn = ui.playWhenReady)

        // Root key target while controls are hidden.
        Box(
            Modifier
                .fillMaxSize()
                .focusRequester(rootFocus)
                .focusable()
                .onKeyEvent { e ->
                    if (e.type != KeyEventType.KeyDown || ui.phase is PlayerPhase.Error) return@onKeyEvent false
                    when (e.key.nativeKeyCode) {
                        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                            if (pauseScreen) showControls() else { vm.togglePlayPause(); showControls() }
                            true
                        }
                        KeyEvent.KEYCODE_DPAD_LEFT -> { showControls(); scrub(-1, e.nativeKeyEvent.repeatCount); true }
                        KeyEvent.KEYCODE_DPAD_RIGHT -> { showControls(); scrub(+1, e.nativeKeyEvent.repeatCount); true }
                        KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN -> { showControls(); true }
                        else -> false
                    }
                }
        )

        // Buffering mid-playback: a small spinner over the picture.
        if (ui.isBuffering && ui.phase is PlayerPhase.Ready) {
            CircularProgressIndicator(color = Tv.Red, strokeWidth = 4.dp,
                modifier = Modifier.align(Alignment.Center).size(56.dp))
        }

        AnimatedVisibility(
            visible = controlsVisible && ui.phase is PlayerPhase.Ready,
            enter = fadeIn(tween(180)),
            exit = fadeOut(tween(240))
        ) {
            ControlsOverlay(
                ui = ui,
                player = player,
                scrubTarget = scrubTarget,
                timelineFocus = timelineFocus,
                onScrub = { dir, repeat -> scrub(dir, repeat) },
                onToggle = { commitScrub(); vm.togglePlayPause(); touch() },
                onBack = { exit() },
                onRestart = { vm.restart(); touch() },
                onMyList = { vm.toggleMyList(); touch() },
                onTracks = { panel = Panel.TRACKS },
                onSpeed = { panel = Panel.SPEED },
                onNext = { ui.nextEpisode?.let { onPlayNext(it.id) } },
                onInteract = { touch() }
            )
        }

        AnimatedVisibility(visible = pauseScreen, enter = fadeIn(tween(600)), exit = fadeOut(tween(200))) {
            PauseScreen(ui)
        }

        NextEpisodeCard(ui, player, visible = !controlsVisible && panel == Panel.NONE, onPlayNext = onPlayNext)

        AnimatedVisibility(
            visible = panel != Panel.NONE,
            enter = slideInHorizontally(tween(220)) { it / 3 } + fadeIn(tween(220)),
            exit = slideOutHorizontally(tween(160)) { it / 3 } + fadeOut(tween(160)),
            modifier = Modifier.align(Alignment.CenterEnd)
        ) {
            when (panel) {
                Panel.TRACKS -> TracksPanel(ui, onAudio = vm::selectAudio, onText = vm::selectText)
                Panel.SPEED -> SpeedPanel(ui.speed) { vm.setSpeed(it); panel = Panel.NONE; showControls() }
                Panel.NONE -> Unit
            }
        }

        when (val phase = ui.phase) {
            PlayerPhase.Loading -> LoadingCurtain(ui)
            is PlayerPhase.Error -> ErrorCurtain(phase.message, onRetry = { vm.retry() }, onBack = { exit() })
            PlayerPhase.Ready -> Unit
        }
    }
}

// ----------------------------------------------------------------------------- surface

@UnstableApi
@Composable
private fun PlayerSurface(player: Player, keepOn: Boolean) {
    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                useController = false
                setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                setShutterBackgroundColor(android.graphics.Color.BLACK)
                setBackgroundColor(android.graphics.Color.BLACK)
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                isFocusable = false
                isFocusableInTouchMode = false
                subtitleView?.apply {
                    setStyle(
                        CaptionStyleCompat(
                            android.graphics.Color.WHITE,
                            android.graphics.Color.TRANSPARENT,
                            android.graphics.Color.TRANSPARENT,
                            CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW,
                            android.graphics.Color.BLACK,
                            null
                        )
                    )
                    setFractionalTextSize(0.052f)
                    setBottomPaddingFraction(0.08f)
                    setApplyEmbeddedStyles(false)
                }
                this.player = player
            }
        },
        update = { it.keepScreenOn = keepOn },
        onRelease = { it.player = null },
        modifier = Modifier.fillMaxSize()
    )
}

// ----------------------------------------------------------------------------- controls

@UnstableApi
@Composable
private fun ControlsOverlay(
    ui: PlayerUiState,
    player: Player,
    scrubTarget: Long?,
    timelineFocus: FocusRequester,
    onScrub: (Int, Int) -> Unit,
    onToggle: () -> Unit,
    onBack: () -> Unit,
    onRestart: () -> Unit,
    onMyList: () -> Unit,
    onTracks: () -> Unit,
    onSpeed: () -> Unit,
    onNext: () -> Unit,
    onInteract: () -> Unit
) {
    val paused = !ui.playWhenReady
    Box(Modifier.fillMaxSize()) {
        // Scrims
        Box(Modifier.fillMaxWidth().height(170.dp).background(
            Brush.verticalGradient(0f to Color(0xCC000000), 1f to Color.Transparent)))
        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(260.dp).background(
            Brush.verticalGradient(0f to Color.Transparent, 1f to Color(0xE6000000))))
        if (paused) Box(Modifier.fillMaxSize().background(
            Brush.horizontalGradient(0f to Color(0xB3000000), 0.6f to Color(0x33000000), 1f to Color.Transparent)))

        // Top bar: back + restart on the left, title on the right.
        Row(
            verticalAlignment = Alignment.Top,
            modifier = Modifier.fillMaxWidth().padding(start = 40.dp, end = 48.dp, top = 28.dp)
        ) {
            RoundIconButton(Icons.AutoMirrored.Filled.ArrowBack, "Back", onBack)
            Spacer(Modifier.width(14.dp))
            RoundIconButton(Icons.Filled.Replay, "Restart", onRestart)
            Spacer(Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.End) {
                Text(ui.title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                ui.subtitle?.takeIf { it.isNotBlank() }?.let {
                    Text(it, color = Tv.TextSecondary, fontSize = 15.sp, maxLines = 1)
                }
            }
        }

        // Paused: big title treatment + actions on the left (Netflix pause layout).
        if (paused) {
            Column(Modifier.align(Alignment.CenterStart).padding(start = 64.dp, bottom = 40.dp).widthIn(max = 520.dp)) {
                TitleLogo(
                    logoUrl = ui.logoUrl,
                    title = ui.title,
                    maxWidth = 400.dp,
                    maxHeight = 140.dp,
                    textStyle = TextStyle(color = Color.White, fontSize = 46.sp, fontWeight = FontWeight.Black, lineHeight = 48.sp)
                )
                ui.subtitle?.takeIf { it.isNotBlank() }?.let {
                    Text(it, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 10.dp))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 20.dp)) {
                    PillButton("Play from beginning", onRestart, icon = Icons.Filled.Replay, compact = true)
                    ui.inMyList?.let { inList ->
                        PillButton(if (inList) "In My List" else "My List", onMyList,
                            icon = if (inList) Icons.Filled.Check else Icons.Filled.Add, compact = true)
                    }
                }
                Text("Paused", color = Tv.TextSecondary, fontSize = 15.sp, modifier = Modifier.padding(top = 14.dp))
            }
        }

        // Bottom: play state, elapsed, timeline, remaining; then the chips.
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 56.dp, vertical = 30.dp)) {
            Timeline(ui, player, scrubTarget, timelineFocus, onScrub, onToggle)
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                modifier = Modifier.fillMaxWidth().padding(top = 18.dp)
            ) {
                Chip(Icons.Filled.Subtitles, ui.textSummary, onTracks, onInteract)
                Chip(Icons.Filled.Speed, if (ui.speed == 1f) "Speed" else "Speed (${speedLabel(ui.speed)})", onSpeed, onInteract)
                if (ui.nextEpisode != null) Chip(Icons.Filled.SkipNext, "Next Episode", onNext, onInteract)
            }
        }
    }
}

@Composable
private fun rememberPosition(player: Player, intervalMs: Long = 250): Long {
    val position by produceState(player.currentPosition, player) {
        while (true) { value = player.currentPosition.coerceAtLeast(0); delay(intervalMs) }
    }
    return position
}

@Composable
private fun Timeline(
    ui: PlayerUiState,
    player: Player,
    scrubTarget: Long?,
    focus: FocusRequester,
    onScrub: (Int, Int) -> Unit,
    onToggle: () -> Unit
) {
    val live = rememberPosition(player)
    val position = scrubTarget ?: live
    val duration = (player.duration.takeIf { it > 0 } ?: ui.durationMs).coerceAtLeast(1)
    val buffered = player.bufferedPosition
    var focused by remember { mutableStateOf(false) }
    val barHeight by animateDpAsState(if (focused) 6.dp else 4.dp, tween(150), label = "bar")
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(52.dp).clip(CircleShape).background(Color.White)
        ) {
            Icon(if (ui.playWhenReady) Icons.Filled.Pause else Icons.Filled.PlayArrow, null,
                tint = Color.Black, modifier = Modifier.size(32.dp))
        }
        Text(MediaFormat.clock(position), color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(start = 18.dp, end = 16.dp))
        BoxWithConstraints(
            contentAlignment = Alignment.CenterStart,
            modifier = Modifier
                .weight(1f)
                .height(40.dp)
                .focusRequester(focus)
                .onFocusChanged { focused = it.isFocused }
                .focusable()
                .onKeyEvent { e ->
                    if (e.type != KeyEventType.KeyDown) return@onKeyEvent false
                    when (e.key.nativeKeyCode) {
                        KeyEvent.KEYCODE_DPAD_LEFT -> { onScrub(-1, e.nativeKeyEvent.repeatCount); true }
                        KeyEvent.KEYCODE_DPAD_RIGHT -> { onScrub(+1, e.nativeKeyEvent.repeatCount); true }
                        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> { onToggle(); true }
                        else -> false
                    }
                }
        ) {
            val frac = (position.toFloat() / duration).coerceIn(0f, 1f)
            val bufFrac = (buffered.toFloat() / duration).coerceIn(0f, 1f)
            Box(Modifier.fillMaxWidth().height(barHeight).clip(RoundedCornerShape(3.dp)).background(Color(0x55FFFFFF)))
            Box(Modifier.fillMaxWidth(bufFrac).height(barHeight).clip(RoundedCornerShape(3.dp)).background(Color(0x88FFFFFF)))
            Box(Modifier.fillMaxWidth(frac).height(barHeight).clip(RoundedCornerShape(3.dp)).background(Tv.Red))
            val thumb: Dp = if (focused) 18.dp else 0.dp
            if (focused) {
                Box(Modifier.offset(x = maxWidth * frac - thumb / 2).size(thumb).clip(CircleShape).background(Tv.Red))
            }
            if (scrubTarget != null) {
                Text(
                    MediaFormat.clock(scrubTarget),
                    color = Color.Black,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .offset(x = (maxWidth * frac - 36.dp).coerceAtLeast(0.dp), y = (-30).dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.White)
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
        }
        Text(MediaFormat.clock(duration - position), color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(start = 16.dp))
    }
}

@Composable
private fun RoundIconButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(48.dp)
                .onFocusChanged { focused = it.isFocused }
                .clip(CircleShape)
                .background(if (focused) Color.White else Color.Transparent)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
        ) {
            Icon(icon, contentDescription = label, tint = if (focused) Color.Black else Color.White, modifier = Modifier.size(30.dp))
        }
        Text(
            label.uppercase(),
            color = if (focused) Color.White else Color.Transparent,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

@Composable
private fun Chip(icon: ImageVector, label: String, onClick: () -> Unit, onFocus: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .onFocusChanged { focused = it.isFocused; if (it.isFocused) onFocus() }
            .clip(RoundedCornerShape(50))
            .background(if (focused) Color.White else Color(0x40FFFFFF))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 9.dp)
    ) {
        Icon(icon, null, tint = if (focused) Color.Black else Color.White, modifier = Modifier.size(20.dp))
        Text(label, color = if (focused) Color.Black else Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 360.dp))
    }
}

private fun speedLabel(s: Float) = if (s % 1f == 0f) "${s.toInt()}x" else "${s}x"

// ----------------------------------------------------------------------------- pause screen

@Composable
private fun PauseScreen(ui: PlayerUiState) {
    Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0f to Color(0xEB000000), 0.7f to Color(0x99000000), 1f to Color(0x66000000)))) {
        Column(Modifier.align(Alignment.CenterStart).padding(start = 72.dp).widthIn(max = 560.dp)) {
            Text("You're watching", color = Tv.TextSecondary, fontSize = 18.sp)
            TitleLogo(
                logoUrl = ui.logoUrl,
                title = ui.title,
                maxWidth = 420.dp,
                maxHeight = 150.dp,
                textStyle = TextStyle(color = Color.White, fontSize = 50.sp, fontWeight = FontWeight.Black, lineHeight = 52.sp),
                modifier = Modifier.padding(top = 12.dp)
            )
            ui.subtitle?.takeIf { it.isNotBlank() }?.let {
                Text(it, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 14.dp))
            }
            if (ui.meta.isNotBlank()) {
                Text(ui.meta, color = Tv.TextSecondary, fontSize = 16.sp, modifier = Modifier.padding(top = 10.dp))
            }
            ui.description?.let {
                Text(it, color = Color(0xFFDDDDDD), fontSize = 17.sp, lineHeight = 24.sp, maxLines = 4,
                    overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 12.dp))
            }
        }
        Text("Paused", color = Tv.TextSecondary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.align(Alignment.BottomEnd).padding(48.dp))
    }
}

// ----------------------------------------------------------------------------- next episode

@Composable
private fun NextEpisodeCard(ui: PlayerUiState, player: Player, visible: Boolean, onPlayNext: (Int) -> Unit) {
    val next = ui.nextEpisode ?: return
    val position = rememberPosition(player, 1_000)
    val duration = player.duration.takeIf { it > 0 } ?: ui.durationMs
    val show = visible && ui.phase is PlayerPhase.Ready && duration > 60_000 && duration - position in 1..25_000
    val focus = remember { FocusRequester() }
    LaunchedEffect(show) { if (show) { withFrameNanos { }; runCatching { focus.requestFocus() } } }
    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = show,
            enter = fadeIn(tween(300)),
            exit = fadeOut(tween(200)),
            modifier = Modifier.align(Alignment.BottomEnd).padding(48.dp)
        ) {
            Column(horizontalAlignment = Alignment.End) {
                ui.nextEpisodeLabel?.let {
                    Text(it, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(bottom = 10.dp))
                }
                PillButton("Next Episode", { onPlayNext(next.id) }, icon = Icons.Filled.SkipNext, focusRequester = focus)
            }
        }
    }
}

// ----------------------------------------------------------------------------- panels

@Composable
private fun TracksPanel(ui: PlayerUiState, onAudio: (TrackOption) -> Unit, onText: (TrackOption?) -> Unit) {
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { withFrameNanos { }; runCatching { first.requestFocus() } }
    Row(
        Modifier
            .fillMaxHeight()
            .width(640.dp)
            .background(Brush.horizontalGradient(0f to Color(0xCC000000), 0.2f to Color(0xF2141414)))
            .padding(start = 72.dp, top = 64.dp, end = 40.dp)
    ) {
        Column(Modifier.weight(1f)) {
            Text("Audio", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            LazyColumn(Modifier.padding(top = 14.dp)) {
                if (ui.audioTracks.isEmpty()) item { OptionRow("Default", true, {}, Modifier.focusRequester(first)) }
                items(ui.audioTracks, key = { it.id }) { t ->
                    OptionRow(t.label, t.selected, { onAudio(t) },
                        if (t == ui.audioTracks.first()) Modifier.focusRequester(first) else Modifier)
                }
            }
        }
        Spacer(Modifier.width(24.dp))
        Column(Modifier.weight(1f)) {
            Text("Subtitles", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            LazyColumn(Modifier.padding(top = 14.dp)) {
                item { OptionRow("Off", ui.textTracks.none { it.selected }, { onText(null) }) }
                items(ui.textTracks, key = { it.id }) { t -> OptionRow(t.label, t.selected, { onText(t) }) }
            }
        }
    }
}

@Composable
private fun SpeedPanel(current: Float, onSelect: (Float) -> Unit) {
    val speeds = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f)
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { withFrameNanos { }; runCatching { focus.requestFocus() } }
    Column(
        Modifier
            .fillMaxHeight()
            .width(360.dp)
            .background(Brush.horizontalGradient(0f to Color(0xCC000000), 0.25f to Color(0xF2141414)))
            .padding(start = 64.dp, top = 64.dp, end = 32.dp)
    ) {
        Text("Playback Speed", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(14.dp))
        speeds.forEach { s ->
            OptionRow(if (s == 1f) "1x (Normal)" else speedLabel(s), s == current, { onSelect(s) },
                if (s == current) Modifier.focusRequester(focus) else Modifier)
        }
    }
}

@Composable
private fun OptionRow(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    var focused by remember { mutableStateOf(false) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .onFocusChanged { focused = it.isFocused }
            .clip(RoundedCornerShape(6.dp))
            .background(if (focused) Color.White else Color.Transparent)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Box(Modifier.width(28.dp)) {
            if (selected) Icon(Icons.Filled.Check, null, tint = if (focused) Color.Black else Color.White, modifier = Modifier.size(20.dp))
        }
        Text(label, color = if (focused) Color.Black else if (selected) Color.White else Tv.TextSecondary,
            fontSize = 17.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1,
            overflow = TextOverflow.Ellipsis)
    }
}

// ----------------------------------------------------------------------------- curtains

/** Shown until the first video frame is on screen (start, resume, recovery). */
@Composable
private fun LoadingCurtain(ui: PlayerUiState) {
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (ui.backdropUrl != null) {
            SizedImage(ui.backdropUrl, 960.dp, 540.dp, Modifier.fillMaxSize(), crossfadeMs = 250)
            Box(Modifier.fillMaxSize().background(Color(0xD9000000)))
        }
        Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = Tv.Red, strokeWidth = 5.dp, modifier = Modifier.size(64.dp))
            if (ui.title.isNotBlank()) {
                Text(ui.title, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 22.dp))
                ui.subtitle?.takeIf { it.isNotBlank() }?.let {
                    Text(it, color = Tv.TextSecondary, fontSize = 16.sp, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
    }
}

@Composable
private fun ErrorCurtain(message: String, onRetry: () -> Unit, onBack: () -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(message) { withFrameNanos { }; runCatching { focus.requestFocus() } }
    Box(Modifier.fillMaxSize().background(Color(0xF0000000)), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(message, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.padding(top = 24.dp)) {
                PillButton("Try Again", onRetry, icon = Icons.Filled.Replay, focusRequester = focus)
                PillButton("Back", onBack, icon = Icons.AutoMirrored.Filled.ArrowBack)
            }
        }
    }
}
