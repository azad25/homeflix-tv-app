package com.homeflix.tv.presentation.ui

import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import android.os.SystemClock
import android.view.KeyEvent

/**
 * Focus-driven scrolling, Netflix style.
 *
 * Compose's default TV behaviour scrolls the focused item to 30% of the
 * viewport using pixel offsets that go stale while tiles resize. Instead every
 * lazy list here opts out of automatic bring-into-view and scrolls itself by
 * *item index* when focus lands, so the focused tile/row is pinned to the
 * keyline exactly, even while the expanding tile animates.
 */
@OptIn(ExperimentalFoundationApi::class)
private object NoAutoScroll : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float = 0f
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ManualFocusScroll(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalBringIntoViewSpec provides NoAutoScroll, content = content)
}

/**
 * Smoothly bring item [index] to the list's start keyline (its content padding
 * start, minus [offsetPx]). Uses the laid-out position when visible so the
 * motion is a single eased glide; falls back to an index jump otherwise.
 */
suspend fun LazyListState.glideTo(index: Int, offsetPx: Int = 0, durationMs: Int = Tv.ScrollMotionMs) {
    val info = layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }
    if (info != null) {
        val delta = (info.offset - offsetPx).toFloat()
        if (kotlin.math.abs(delta) > 0.5f) animateScrollBy(delta, tween(durationMs, easing = Tv.Ease))
    } else {
        animateScrollToItem(index, offsetPx)
    }
}

/**
 * One D-pad step along a row: slide so tile [index] lands on the keyline in a
 * single eased motion. The tile we're leaving ([from]) may be expanded and
 * collapses back to [basePx] over the same duration; if it sits before the
 * target that collapse already moves the target left, so it's subtracted from
 * the scroll. Without this the row overshoots and then swings back.
 */
suspend fun LazyListState.slideTo(index: Int, from: Int, basePx: Int, durationMs: Int = Tv.FocusMotionMs) {
    val items = layoutInfo.visibleItemsInfo
    val target = items.firstOrNull { it.index == index } ?: run { animateScrollToItem(index); return }
    val shrink = if (from < index) items.firstOrNull { it.index == from }?.let { (it.size - basePx).coerceAtLeast(0) } ?: 0 else 0
    val delta = (target.offset - shrink).toFloat()
    if (kotlin.math.abs(delta) > 0.5f) animateScrollBy(delta, tween(durationMs, easing = Tv.Ease))
}

/** Scroll just enough to show item [index] whole (no-op when it already fits). */
suspend fun LazyListState.revealItem(index: Int, durationMs: Int = Tv.FocusMotionMs) {
    val info = layoutInfo.visibleItemsInfo.firstOrNull { it.index == index } ?: return
    val end = layoutInfo.viewportEndOffset - layoutInfo.afterContentPadding / 4
    val overflow = info.offset + info.size - end
    if (overflow > 1) animateScrollBy(overflow.toFloat(), tween(durationMs, easing = Tv.Ease))
}

/** Remembers FocusRequesters by key so a screen can restore focus to the exact tile it left. */
@Stable
class FocusRegistry {
    private val requesters = HashMap<String, FocusRequester>()
    fun requesterFor(key: String): FocusRequester = requesters.getOrPut(key) { FocusRequester() }
    fun tryFocus(key: String?): Boolean {
        if (key == null) return false
        val r = requesters[key] ?: return false
        return runCatching { r.requestFocus(); true }.getOrDefault(false)
    }
    internal fun forget(key: String) { requesters.remove(key) }
}

@Composable
fun rememberFocusRegistry(): FocusRegistry = remember { FocusRegistry() }

/** Attach a registry-owned FocusRequester to this node under [key]. */
@Composable
fun Modifier.registerFocus(registry: FocusRegistry, key: String): Modifier {
    val requester = remember(registry, key) { registry.requesterFor(key) }
    DisposableEffect(registry, key) { onDispose { registry.forget(key) } }
    return this.focusRequester(requester)
}

/**
 * Throttles held-down D-pad repeats so focus moves at a steady, animatable
 * cadence instead of outrunning layout (the source of "stutter" when a key is
 * held). Single presses are never delayed.
 */
fun Modifier.throttleKeyRepeat(minIntervalMs: Long = 85L): Modifier {
    var last = 0L
    return onPreviewKeyEvent { e ->
        if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
        val code = e.key.nativeKeyCode
        val isDpad = code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_RIGHT ||
            code == KeyEvent.KEYCODE_DPAD_UP || code == KeyEvent.KEYCODE_DPAD_DOWN
        if (!isDpad) return@onPreviewKeyEvent false
        val now = SystemClock.uptimeMillis()
        val repeat = e.nativeKeyEvent.repeatCount > 0
        if (repeat && now - last < minIntervalMs) return@onPreviewKeyEvent true
        last = now
        false
    }
}
