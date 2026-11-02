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
 * For plain lists, grids and chip rows (Search, Notifications, Settings,
 * seasons): keep the focused item in view with a margin so the next one peeks
 * in, and move there with a critically-damped spring. A spring keeps its
 * velocity when the target changes, so holding the D-pad is one continuous
 * glide instead of a stop-start per item.
 */
@OptIn(ExperimentalFoundationApi::class)
object SmoothBringIntoView : BringIntoViewSpec {
    @Suppress("OVERRIDE_DEPRECATION")
    override val scrollAnimationSpec: androidx.compose.animation.core.AnimationSpec<Float> =
        androidx.compose.animation.core.spring(dampingRatio = 1f, stiffness = 420f)

    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
        val margin = containerSize * 0.12f
        val trailing = offset + size - (containerSize - margin)
        return when {
            size + 2 * margin > containerSize -> offset - margin   // too big: align its start
            offset < margin -> offset - margin                     // above / left of the margin
            trailing > 0 -> trailing                               // below / right of the margin
            else -> 0f
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SmoothFocusScroll(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalBringIntoViewSpec provides SmoothBringIntoView, content = content)
}

/** Ongoing smooth scroll per list: new D-pad presses retarget it instead of restarting it. */
private class Follower {
    var remaining = 0f
    var running = false
}

private val followers = java.util.WeakHashMap<LazyListState, Follower>()

/** Time constant of the follow motion: ~95% of the way in 3 × this. */
private const val FollowTauMs = 68f

/**
 * Scrolls by [delta] px with an ease-out follow. If this list is already
 * moving, the running motion is simply retargeted (no stop, no restart from
 * zero speed), which is what makes rapid or held presses glide.
 */
suspend fun LazyListState.followBy(delta: Float) {
    val f = followers.getOrPut(this) { Follower() }
    f.remaining = delta
    if (f.running) return
    if (kotlin.math.abs(delta) <= 0.5f) return
    f.running = true
    try {
        scroll {
            var last = androidx.compose.runtime.withFrameNanos { it }
            while (kotlin.math.abs(f.remaining) > 0.5f) {
                val now = androidx.compose.runtime.withFrameNanos { it }
                val dt = ((now - last) / 1_000_000f).coerceIn(1f, 48f)
                last = now
                var step = f.remaining * (1f - kotlin.math.exp(-dt / FollowTauMs))
                // A minimum speed so the very end doesn't crawl.
                if (kotlin.math.abs(step) < 1.5f) step = kotlin.math.sign(f.remaining) * minOf(1.5f, kotlin.math.abs(f.remaining))
                val used = scrollBy(step)
                f.remaining -= step
                if (kotlin.math.abs(used - step) > 0.5f) break // hit the end of the list
            }
        }
    } finally {
        f.remaining = 0f
        f.running = false
    }
}

/**
 * Smoothly bring item [index] to the list's start keyline (its content padding
 * start, minus [offsetPx]). Uses the laid-out position when visible so the
 * motion is a single eased glide; falls back to an index jump otherwise.
 */
suspend fun LazyListState.glideTo(index: Int, offsetPx: Int = 0, durationMs: Int = Tv.ScrollMotionMs) {
    val info = layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }
    if (info != null) {
        followBy((info.offset - offsetPx).toFloat())
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
    followBy((target.offset - shrink).toFloat())
}

/** Scroll just enough to show item [index] whole (no-op when it already fits). */
suspend fun LazyListState.revealItem(index: Int, durationMs: Int = Tv.FocusMotionMs) {
    val info = layoutInfo.visibleItemsInfo.firstOrNull { it.index == index } ?: return
    val end = layoutInfo.viewportEndOffset - layoutInfo.afterContentPadding / 4
    val overflow = info.offset + info.size - end
    if (overflow > 1) followBy(overflow.toFloat())
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
