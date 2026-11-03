package com.homeflix.tv.comingsoon

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What the controller needs from the server (the repository in the app, a fake in tests). */
interface ComingSoonSource {
    suspend fun items(): List<ComingSoonItem>
    suspend fun title(mediaType: String, tmdbId: Int): ComingSoonItem?
    suspend fun setNotify(item: ComingSoonItem, on: Boolean): Result<NotifyResult>
    fun trailerUrl(item: ComingSoonItem): String?
}

/** The trailer player (one ExoPlayer in the app). */
interface TrailerPlayback {
    fun play(url: String)
    fun stop()
    fun pause()
    fun resume()
}

enum class CsZone { NOTIFY, DETAILS, STRIP }

data class ComingSoonState(
    val loading: Boolean = true,
    val items: List<ComingSoonItem> = emptyList(),
    val index: Int = 0,
    /** A picture is on screen (until then the backdrop shows). */
    val videoVisible: Boolean = false,
    /** This title has no downloaded trailer: the backdrop stays, then the next title comes. */
    val noTrailer: Boolean = false,
    val overlayVisible: Boolean = true,
    val zone: CsZone = CsZone.NOTIFY,
    /** "Details" expanded: the full overview and cast. */
    val details: Boolean = false,
    val paused: Boolean = false,
    val message: String? = null
) {
    val current: ComingSoonItem? get() = items.getOrNull(index)
}

/**
 * Coming Soon, full screen (homeflix-wifi docs/TV_APP_COMING_SOON_PROMPT.md):
 * trailers with sound, one after another like channels; an info overlay that
 * fades after a few seconds and returns on any key; ◀ ▶ change title, ▲ ▼
 * move between Notify Me, Details and the strip of titles, OK presses, Back
 * closes the overlay and then the screen.
 */
class ComingSoonController(
    private val scope: CoroutineScope,
    private val source: ComingSoonSource,
    private val playback: TrailerPlayback
) {
    private val _state = MutableStateFlow(ComingSoonState())
    val state: StateFlow<ComingSoonState> = _state.asStateFlow()
    private val s get() = _state.value

    private var hideJob: Job? = null
    private var advanceJob: Job? = null
    private var messageJob: Job? = null

    fun start(startKey: String?) {
        scope.launch {
            val items = source.items()
            val i = items.indexOfFirst { it.key == startKey }.coerceAtLeast(0)
            _state.update { it.copy(loading = false, items = items, index = i) }
            if (items.isNotEmpty()) show(i)
        }
    }

    // ---------------------------------------------------------------- titles

    private fun show(i: Int) {
        advanceJob?.cancel()
        playback.stop()
        val item = s.items.getOrNull(i) ?: return
        _state.update { it.copy(index = i, videoVisible = false, noTrailer = false, paused = false, details = false) }
        wake()
        val url = source.trailerUrl(item)
        if (url != null) playback.play(url) else noTrailer()
    }

    /** No MP4 yet (or it failed): keep the backdrop up for a while, then move on. */
    private fun noTrailer() {
        _state.update { it.copy(noTrailer = true, videoVisible = false) }
        val at = s.index
        advanceJob = scope.launch {
            delay(NO_TRAILER_MS)
            if (s.index == at && s.items.size > 1) next()
        }
    }

    fun next() { if (s.items.isNotEmpty()) show((s.index + 1).mod(s.items.size)) }
    fun previous() { if (s.items.isNotEmpty()) show((s.index - 1).mod(s.items.size)) }

    fun onFirstFrame() { _state.update { it.copy(videoVisible = true, noTrailer = false) } }

    /** The trailer finished: on to the next title, like changing channel. */
    fun onEnded() { if (s.items.size > 1) next() else { playback.stop(); _state.update { it.copy(videoVisible = false) }; wake() } }

    fun onError() {
        playback.stop()
        noTrailer()
    }

    // ------------------------------------------------------------------ overlay

    /** Show the overlay and restart its fade timer (it stays while Details is open). */
    fun wake() {
        _state.update { it.copy(overlayVisible = true) }
        hideJob?.cancel()
        hideJob = scope.launch {
            delay(OVERLAY_MS)
            if (!s.details) _state.update { it.copy(overlayVisible = false) }
        }
    }

    // --------------------------------------------------------------------- keys

    fun left() { previous() }
    fun right() { next() }

    fun up() {
        if (!s.overlayVisible) { wake(); return }
        _state.update { it.copy(zone = CsZone.entries[(it.zone.ordinal - 1).coerceAtLeast(0)]) }
        wake()
    }

    fun down() {
        if (!s.overlayVisible) { wake(); return }
        _state.update { it.copy(zone = CsZone.entries[(it.zone.ordinal + 1).coerceAtMost(CsZone.entries.lastIndex)]) }
        wake()
    }

    fun ok() {
        if (!s.overlayVisible) { wake(); return }
        when (s.zone) {
            CsZone.NOTIFY -> toggleNotify()
            CsZone.DETAILS -> _state.update { it.copy(details = !it.details) }
            CsZone.STRIP -> _state.update { it.copy(zone = CsZone.NOTIFY) }
        }
        wake()
    }

    fun playPause() {
        if (!s.videoVisible) return
        if (s.paused) playback.resume() else playback.pause()
        _state.update { it.copy(paused = !it.paused) }
        wake()
    }

    /** Back: close Details, then the overlay, then the screen (returns true to leave). */
    fun back(): Boolean {
        when {
            s.details -> { _state.update { it.copy(details = false) }; wake() }
            s.overlayVisible -> { hideJob?.cancel(); _state.update { it.copy(overlayVisible = false) } }
            else -> return true
        }
        return false
    }

    // ---------------------------------------------------------------- Notify Me

    fun toggleNotify() {
        val item = s.current ?: return
        val turnOn = !item.notifyOn
        val optimistic = item.copy(notify = ComingSoonNotify(on = turnOn, status = if (turnOn) "queued" else ""))
        replace(item.key, optimistic)
        scope.launch {
            val r = source.setNotify(item, turnOn)
            if (r.isFailure) {
                replace(item.key, item) // roll back
                flash("Couldn't reach the server")
                return@launch
            }
            // The server's view (e.g. "scheduled") replaces the guess.
            val fresh = source.title(item.mediaType, item.tmdbId)
                ?: optimistic.copy(notify = ComingSoonNotify(on = r.getOrNull()?.on ?: turnOn, status = r.getOrNull()?.status))
            replace(item.key, fresh)
        }
    }

    private fun replace(key: String, item: ComingSoonItem) {
        _state.update { st -> st.copy(items = st.items.map { if (it.key == key) item else it }) }
    }

    private fun flash(text: String) {
        _state.update { it.copy(message = text) }
        messageJob?.cancel()
        messageJob = scope.launch { delay(2_500); _state.update { it.copy(message = null) } }
    }

    fun pause() { playback.stop(); _state.update { it.copy(videoVisible = false) } }
    fun resume() { if (s.items.isNotEmpty()) show(s.index) }

    companion object {
        const val OVERLAY_MS = 4_000L
        const val NO_TRAILER_MS = 10_000L
    }
}
