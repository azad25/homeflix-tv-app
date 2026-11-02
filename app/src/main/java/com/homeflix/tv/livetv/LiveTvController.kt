package com.homeflix.tv.livetv

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What the controller asks of the video player (ExoPlayer in the app, a fake in tests). */
interface LivePlayback {
    /** Replace whatever is playing with this HLS stream (same player instance, no recreate). */
    fun play(url: String)
    /** Behind the live window: jump to the live edge and prepare again. */
    fun restartAtLiveEdge()
    fun stop()
    fun setMuted(muted: Boolean)
}

enum class LivePhase { IDLE, TUNING, PLAYING, UNAVAILABLE, YOUTUBE }

enum class LiveOverlay { NONE, MINI_GUIDE, GUIDE, INFO }

enum class GuideColumn { GROUPS, CHANNELS }

/** A group in the full guide: Favorites, All, a country or a category. */
data class LiveGroup(val id: String, val title: String, val channels: List<LiveChannel>)

data class LiveState(
    val loading: Boolean = true,
    val all: List<LiveChannel> = emptyList(),
    /** The list ▲▼ walks (the guide group the channel was picked from, or All). */
    val list: List<LiveChannel> = emptyList(),
    val current: LiveChannel? = null,
    /** What the banner shows: the channel being tuned, or the one ▲▼ landed on before the tune. */
    val shown: LiveChannel? = null,
    /** The channel actually in the player (differs from [current] while the guide previews). */
    val playing: LiveChannel? = null,
    val phase: LivePhase = LivePhase.IDLE,
    val youtubeId: String? = null,
    /** The YouTube embed is showing a picture. */
    val youtubePlaying: Boolean = false,
    /** The embed failed (embedding blocked / WebView can't play): offer the YouTube app. */
    val youtubeFailed: Boolean = false,
    val bannerVisible: Boolean = false,
    val digits: String = "",
    val message: String? = null,
    val exitHint: Boolean = false,
    val overlay: LiveOverlay = LiveOverlay.NONE,
    val miniIndex: Int = 0,
    val groups: List<LiveGroup> = emptyList(),
    val groupIndex: Int = 0,
    val guideIndex: Int = 0,
    val guideColumn: GuideColumn = GuideColumn.CHANNELS,
    val favorites: Set<String> = emptySet(),
    /** The player is muted (full-guide preview). */
    val muted: Boolean = false
) {
    val guideChannels: List<LiveChannel> get() = groups.getOrNull(groupIndex)?.channels.orEmpty()
    val isFavorite: Boolean get() = current?.id in favorites
}

/**
 * Live TV behaviour, cable-box style (homeflix-wifi docs/LIVE_TV.md):
 * instant banner on every key, debounced tuning, URL fallback, number entry,
 * mini guide (OK), full guide with preview (◀), info/favorite (▶), and Back
 * to the last channel / twice to leave. Pure Kotlin: the screen forwards keys,
 * the player reports first frames and errors.
 */
class LiveTvController(
    private val scope: CoroutineScope,
    private val source: LiveSource,
    private val memory: LiveMemory,
    private val playback: LivePlayback,
    private val now: () -> Long = System::currentTimeMillis,
    /** Hand a YouTube live stream to the YouTube app; false if there's none. */
    private val openYouTubeApp: (String) -> Boolean = { false }
) {
    private val _state = MutableStateFlow(LiveState())
    val state: StateFlow<LiveState> = _state.asStateFlow()
    private val s get() = _state.value

    private var tuneJob: Job? = null
    private var zapJob: Job? = null
    private var bannerJob: Job? = null
    private var digitJob: Job? = null
    private var previewJob: Job? = null
    private var messageJob: Job? = null
    private var hintJob: Job? = null
    private var urls: List<String> = emptyList()
    private var urlIndex = 0
    private var youtube: String? = null
    private var lastBackAt = 0L
    private var previousId: String? = null

    /** Load the lineup and tune [startId], else the last channel, else the first. */
    fun start(lineup: List<LiveChannel>, startId: String?) {
        scope.launch {
            val favorites = memory.favoritesNow()
            val (last, previous) = memory.lastAndPrevious()
            previousId = previous
            _state.update { it.copy(loading = false, all = lineup, list = lineup, favorites = favorites, groups = groups(lineup, favorites)) }
            val first = lineup.firstOrNull { it.id == startId } ?: lineup.firstOrNull { it.id == last }
                ?: lineup.minByOrNull { it.number }
            if (first != null) tune(first, lineup)
        }
    }

    // ------------------------------------------------------------------ tuning

    /** Tune now and make it the current channel. */
    fun tune(channel: LiveChannel, list: List<LiveChannel>? = null) {
        zapJob?.cancel()
        val prev = s.current
        if (prev != null && prev.id != channel.id) previousId = prev.id
        _state.update { it.copy(current = channel, shown = channel, list = list?.takeIf { l -> l.any { c -> c.id == channel.id } } ?: it.list) }
        showBanner()
        scope.launch { memory.recordTune(channel.id) }
        load(channel)
    }

    /** Point the player at [channel]: fresh URLs, best first, then YouTube, then "not available". */
    private fun load(channel: LiveChannel) {
        tuneJob?.cancel()
        playback.stop()
        _state.update { it.copy(playing = channel, phase = LivePhase.TUNING, youtubeId = null, youtubePlaying = false, youtubeFailed = false) }
        tuneJob = scope.launch {
            val play = source.play(channel.id)
            if (s.playing?.id != channel.id) return@launch
            urls = play?.urls.orEmpty()
            urlIndex = 0
            youtube = play?.youtubeId?.takeIf { it.isNotBlank() }
            when {
                urls.isNotEmpty() -> playback.play(urls[0])
                youtube != null -> { showYouTube(); return@launch }
                else -> { unavailable(); return@launch }
            }
            // Neighbours' URLs, so the next zap skips a round trip.
            val l = s.list
            val i = l.indexOfFirst { it.id == channel.id }
            if (i >= 0 && l.size > 1) source.prefetch(listOf(l[(i + 1) % l.size].id, l[(i - 1 + l.size) % l.size].id))
            // No picture in 10 s: say so (never skip to another channel by ourselves).
            delay(NO_SIGNAL_MS)
            if (s.playing?.id == channel.id && s.phase == LivePhase.TUNING) {
                if (youtube != null) showYouTube() else unavailable()
            }
        }
    }

    fun onFirstFrame() {
        if (s.phase == LivePhase.TUNING) _state.update { it.copy(phase = LivePhase.PLAYING) }
    }

    /** A fatal player error. Behind the live window is not a dead channel. */
    fun onPlayerError(behindLiveWindow: Boolean) {
        if (s.phase != LivePhase.TUNING && s.phase != LivePhase.PLAYING) return
        if (behindLiveWindow) { playback.restartAtLiveEdge(); return }
        urlIndex++
        when {
            urlIndex < urls.size -> { _state.update { it.copy(phase = LivePhase.TUNING) }; playback.play(urls[urlIndex]) }
            youtube != null -> showYouTube()
            else -> unavailable()
        }
    }

    private var youtubeJob: Job? = null

    private fun showYouTube() {
        playback.stop()
        _state.update { it.copy(phase = LivePhase.YOUTUBE, youtubeId = youtube, youtubePlaying = false, youtubeFailed = false) }
        val id = youtube
        youtubeJob?.cancel()
        youtubeJob = scope.launch {
            delay(YOUTUBE_START_MS)
            if (s.phase == LivePhase.YOUTUBE && s.youtubeId == id && !s.youtubePlaying) onYouTubeError()
        }
    }

    /** The embed reports a picture. */
    fun onYouTubePlaying() {
        if (s.phase == LivePhase.YOUTUBE) _state.update { it.copy(youtubePlaying = true, youtubeFailed = false) }
    }

    /** The embed can't play (e.g. the broadcaster blocks embedding): offer the YouTube app instead. */
    fun onYouTubeError() {
        if (s.phase == LivePhase.YOUTUBE) _state.update { it.copy(youtubePlaying = false, youtubeFailed = true) }
    }

    /** Hand this channel's YouTube live stream to the YouTube app (its own Back returns here). */
    fun openInYouTube() {
        val id = s.youtubeId ?: return
        if (!openYouTubeApp(id)) flash("The YouTube app isn't installed")
    }

    private fun unavailable() {
        tuneJob?.cancel()
        playback.stop()
        _state.update { it.copy(phase = LivePhase.UNAVAILABLE) }
    }

    /** ▲ (-1) / ▼ (+1): the banner moves at once, the tune waits for the keys to settle. */
    fun zap(delta: Int) {
        val l = s.list.ifEmpty { s.all }
        if (l.isEmpty()) return
        val from = s.shown ?: s.current ?: return
        val i = l.indexOfFirst { it.id == from.id }.let { if (it < 0) 0 else it }
        val next = l[((i + delta) % l.size + l.size) % l.size]
        tuneJob?.cancel()
        playback.stop()
        // Never show the old picture under the new name.
        _state.update { it.copy(shown = next, playing = next, phase = LivePhase.TUNING, digits = "") }
        showBanner()
        zapJob?.cancel()
        zapJob = scope.launch {
            delay(ZAP_DEBOUNCE_MS)
            tune(next)
        }
    }

    // ------------------------------------------------------------- banner etc.

    fun showBanner(ms: Long = BANNER_MS) {
        _state.update { it.copy(bannerVisible = true) }
        bannerJob?.cancel()
        bannerJob = scope.launch {
            delay(ms)
            if (s.overlay != LiveOverlay.INFO) _state.update { it.copy(bannerVisible = false) }
        }
    }

    private fun flash(message: String) {
        _state.update { it.copy(message = message) }
        messageJob?.cancel()
        messageJob = scope.launch { delay(2_000); _state.update { it.copy(message = null) } }
    }

    // ----------------------------------------------------------- number entry

    fun digit(d: Int) {
        if (s.overlay != LiveOverlay.NONE) closeOverlay()
        val digits = (s.digits + d).takeLast(MAX_DIGITS)
        _state.update { it.copy(digits = digits) }
        digitJob?.cancel()
        digitJob = scope.launch {
            delay(DIGIT_COMMIT_MS)
            commitDigits()
        }
    }

    private fun commitDigits() {
        digitJob?.cancel()
        val n = s.digits.toIntOrNull()
        _state.update { it.copy(digits = "") }
        if (n == null) return
        val ch = s.all.firstOrNull { it.number == n }
        if (ch == null) { flash("No channel $n"); return }
        if (ch.id == s.current?.id && s.phase != LivePhase.UNAVAILABLE) { showBanner(); return }
        tune(ch, if (s.list.any { it.id == ch.id }) null else s.all)
    }

    // ------------------------------------------------------------------- keys

    /** OK: number entry → tune now; otherwise the mini guide (or pick in an open guide). */
    fun ok() {
        when {
            s.digits.isNotEmpty() -> commitDigits()
            s.overlay == LiveOverlay.MINI_GUIDE -> {
                val ch = s.list.getOrNull(s.miniIndex)
                closeOverlay()
                if (ch != null && ch.id != s.current?.id) tune(ch) else showBanner()
            }
            s.overlay == LiveOverlay.GUIDE -> guideOk()
            s.overlay == LiveOverlay.INFO -> toggleFavorite()
            // The embed failed: OK hands the stream to the YouTube app.
            s.phase == LivePhase.YOUTUBE && s.youtubeFailed -> openInYouTube()
            else -> {
                val l = s.list
                val i = l.indexOfFirst { it.id == (s.shown ?: s.current)?.id }.coerceAtLeast(0)
                _state.update { it.copy(overlay = LiveOverlay.MINI_GUIDE, miniIndex = i, bannerVisible = false) }
            }
        }
    }

    fun up() = move(-1)
    fun down() = move(1)

    private fun move(delta: Int) {
        when (s.overlay) {
            LiveOverlay.NONE -> zap(delta)
            LiveOverlay.INFO -> { closeOverlay(); zap(delta) }
            LiveOverlay.MINI_GUIDE -> {
                val n = s.list.size
                if (n > 0) _state.update { it.copy(miniIndex = ((it.miniIndex + delta) % n + n) % n) }
            }
            LiveOverlay.GUIDE -> guideMove(delta)
        }
    }

    /** ◀: the full guide (in the guide: to the groups column). */
    fun left() {
        when (s.overlay) {
            LiveOverlay.GUIDE -> _state.update { it.copy(guideColumn = GuideColumn.GROUPS) }
            LiveOverlay.NONE, LiveOverlay.INFO -> openGuide()
            LiveOverlay.MINI_GUIDE -> { closeOverlay(); openGuide() }
        }
    }

    /** ▶: channel info with the favorite toggle (in the guide: to the channels column). */
    fun right() {
        when (s.overlay) {
            LiveOverlay.GUIDE -> if (s.guideColumn == GuideColumn.GROUPS) {
                _state.update { it.copy(guideColumn = GuideColumn.CHANNELS) }
                schedulePreview()
            }
            // On a YouTube channel the info panel's ▶ opens the YouTube app.
            LiveOverlay.INFO -> if (s.phase == LivePhase.YOUTUBE) { closeOverlay(); openInYouTube() } else closeOverlay()
            else -> info()
        }
    }

    /** INFO / Menu key, or ▶. */
    fun info() {
        if (s.overlay != LiveOverlay.NONE && s.overlay != LiveOverlay.INFO) closeOverlay()
        bannerJob?.cancel()
        _state.update { it.copy(overlay = LiveOverlay.INFO, bannerVisible = true) }
    }

    fun toggleFavorite() {
        val ch = s.current ?: return
        val now = if (ch.id in s.favorites) s.favorites - ch.id else s.favorites + ch.id
        _state.update { it.copy(favorites = now, groups = groups(it.all, now), groupIndex = 0) }
        scope.launch { memory.toggleFavorite(ch.id) }
    }

    /**
     * Back: close an overlay; else return to the last channel. Twice quickly
     * leaves Live TV. Returns true when the screen should close.
     */
    fun back(): Boolean {
        if (s.overlay != LiveOverlay.NONE) { closeOverlay(); return false }
        if (s.digits.isNotEmpty()) { digitJob?.cancel(); _state.update { it.copy(digits = "") }; return false }
        val t = now()
        if (t - lastBackAt < DOUBLE_BACK_MS) return true
        lastBackAt = t
        val prev = previousId?.let { id -> s.all.firstOrNull { it.id == id } }
        if (prev != null && prev.id != s.current?.id) tune(prev, if (s.list.any { it.id == prev.id }) null else s.all)
        _state.update { it.copy(exitHint = true) }
        hintJob?.cancel()
        hintJob = scope.launch { delay(DOUBLE_BACK_MS); _state.update { it.copy(exitHint = false) } }
        return false
    }

    fun closeOverlay() {
        val was = s.overlay
        previewJob?.cancel()
        _state.update { it.copy(overlay = LiveOverlay.NONE, bannerVisible = false) }
        if (was == LiveOverlay.GUIDE) {
            // Leaving the guide without picking: back to what was on, with sound.
            playback.setMuted(false)
            _state.update { it.copy(muted = false) }
            val cur = s.current
            if (cur != null && s.playing?.id != cur.id) load(cur)
        }
    }

    // --------------------------------------------------------------- full guide

    private fun openGuide() {
        val cur = s.current
        var gi = s.groups.indexOfFirst { g -> g.channels == s.list }.takeIf { it >= 0 }
            ?: s.groups.indexOfFirst { it.id == "all" }.coerceAtLeast(0)
        if (s.groups.getOrNull(gi)?.channels?.none { it.id == cur?.id } == true) gi = s.groups.indexOfFirst { it.id == "all" }.coerceAtLeast(0)
        val ci = s.groups.getOrNull(gi)?.channels?.indexOfFirst { it.id == cur?.id }?.coerceAtLeast(0) ?: 0
        _state.update { it.copy(overlay = LiveOverlay.GUIDE, groupIndex = gi, guideIndex = ci, guideColumn = GuideColumn.CHANNELS, bannerVisible = false) }
    }

    private fun guideMove(delta: Int) {
        if (s.guideColumn == GuideColumn.GROUPS) {
            val n = s.groups.size
            if (n == 0) return
            _state.update { it.copy(groupIndex = ((it.groupIndex + delta) % n + n) % n, guideIndex = 0) }
        } else {
            val n = s.guideChannels.size
            if (n == 0) return
            _state.update { it.copy(guideIndex = ((it.guideIndex + delta) % n + n) % n) }
            schedulePreview()
        }
    }

    /** Preview the focused channel, muted, once focus rests on it. One player, no second decoder. */
    private fun schedulePreview() {
        previewJob?.cancel()
        val ch = s.guideChannels.getOrNull(s.guideIndex) ?: return
        previewJob = scope.launch {
            delay(PREVIEW_DELAY_MS)
            if (s.overlay != LiveOverlay.GUIDE || s.playing?.id == ch.id) return@launch
            playback.setMuted(true)
            _state.update { it.copy(muted = true) }
            load(ch)
        }
    }

    private fun guideOk() {
        if (s.guideColumn == GuideColumn.GROUPS) {
            _state.update { it.copy(guideColumn = GuideColumn.CHANNELS, guideIndex = 0) }
            schedulePreview()
            return
        }
        val group = s.groups.getOrNull(s.groupIndex) ?: return
        val ch = group.channels.getOrNull(s.guideIndex) ?: return
        previewJob?.cancel()
        playback.setMuted(false)
        val alreadyOn = s.playing?.id == ch.id && (s.phase == LivePhase.PLAYING || s.phase == LivePhase.TUNING || s.phase == LivePhase.YOUTUBE)
        _state.update { it.copy(overlay = LiveOverlay.NONE, muted = false) }
        if (alreadyOn) {
            // The preview is already this channel: keep it, just make it current.
            val prev = s.current
            if (prev != null && prev.id != ch.id) previousId = prev.id
            _state.update { it.copy(current = ch, shown = ch, list = group.channels) }
            showBanner()
            scope.launch { memory.recordTune(ch.id) }
        } else {
            tune(ch, group.channels)
        }
    }

    /** Leaving the screen (Home button): free the decoder. */
    fun pause() {
        zapJob?.cancel(); tuneJob?.cancel()
        playback.stop()
        _state.update { it.copy(phase = LivePhase.IDLE) }
    }

    /** Back on screen: tune the current channel again (live streams can't resume). */
    fun resume() {
        val cur = s.current ?: return
        if (s.phase == LivePhase.IDLE) load(cur)
    }

    companion object {
        const val ZAP_DEBOUNCE_MS = 400L
        const val BANNER_MS = 3_000L
        const val DIGIT_COMMIT_MS = 1_200L
        const val PREVIEW_DELAY_MS = 600L
        const val NO_SIGNAL_MS = 10_000L
        /** A YouTube embed that shows nothing for this long counts as failed. */
        const val YOUTUBE_START_MS = 12_000L
        const val DOUBLE_BACK_MS = 1_500L
        const val MAX_DIGITS = 4

        /** Guide groups: Favorites, All, each country (most channels first), each category. */
        fun groups(all: List<LiveChannel>, favorites: Set<String>): List<LiveGroup> = buildList {
            val favs = all.filter { it.id in favorites }
            if (favs.isNotEmpty()) add(LiveGroup("fav", "★  Favorites", favs))
            add(LiveGroup("all", "All channels", all))
            all.groupBy { it.country?.uppercase().orEmpty() }.filterKeys { it.isNotBlank() }
                .entries.sortedWith(compareByDescending<Map.Entry<String, List<LiveChannel>>> { it.value.size }.thenBy { LiveCountries.name(it.key) })
                .forEach { (code, list) -> add(LiveGroup("c:$code", "${LiveCountries.flag(code)}  ${LiveCountries.name(code)}", list)) }
            all.flatMap { ch -> ch.categories.orEmpty().map { it to ch } }
                .filter { (cat, _) -> cat.isNotBlank() && !cat.equals("xxx", true) && !cat.equals("adult", true) }
                .groupBy({ it.first.lowercase() }, { it.second })
                .entries.sortedWith(compareByDescending<Map.Entry<String, List<LiveChannel>>> { it.value.size }.thenBy { it.key })
                .forEach { (cat, list) -> add(LiveGroup("k:$cat", cat.replaceFirstChar { it.uppercase() }, list.distinctBy { it.id })) }
        }
    }
}
