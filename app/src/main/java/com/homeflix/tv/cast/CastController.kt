package com.homeflix.tv.cast

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import com.google.gson.Gson
import com.homeflix.tv.R
import com.homeflix.tv.data.remote.api.HomeFlixApiService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

/** The full-screen player, as seen by casting. Implemented by the player ViewModel. */
interface CastTarget {
    val castMediaId: Int
    fun castSetPlaying(playing: Boolean)
    fun castSeek(positionMs: Long)
    /** Close the player and return to the previous screen. */
    fun castStop()
    fun castVolume(volume: Float?, muted: Boolean?)
    /** Track id from the browser, or "" for off. Returns false if unsupported. */
    fun castSubtitle(id: String): Boolean
    fun castStatus(): CastStatus
}

/** A cast "play" the UI should open. [positionMs] < 0 means "use the normal resume rules". */
data class CastPlayRequest(val mediaId: Int, val positionMs: Long, val from: String?, val token: Long = System.nanoTime())

/**
 * Process-wide bridge between the cast WebSocket and the player.
 *
 * Commands arrive on the connection thread, are executed on the main thread,
 * and are acknowledged right away (for `play`, once the player is opening —
 * load progress is reported through status messages).
 */
@Singleton
class CastController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val api: HomeFlixApiService
) {
    private val gson = Gson()
    private val main = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** Set by [CastConnection]; returns false when the socket is down. */
    @Volatile var sender: ((String) -> Boolean)? = null

    private var target: CastTarget? = null
    private var pendingPlay: CastPlayRequest? = null
    private var pendingTimeout: Job? = null

    private val _playRequests = MutableStateFlow<CastPlayRequest?>(null)
    /** Collected by the navigation graph, which opens the player. */
    val playRequests: StateFlow<CastPlayRequest?> = _playRequests.asStateFlow()

    private val _notice = MutableStateFlow<String?>(null)
    /** "Casting from Chrome on Mac" — shown briefly by the player. */
    val notice: StateFlow<String?> = _notice.asStateFlow()

    private var lastStatus: CastStatus = CastStatus.Idle

    // ------------------------------------------------------------------ inbound

    /** Called from the socket thread with a parsed command; acks via [sender]. */
    fun onCommand(cmd: CastInbound) {
        val id = cmd.commandId ?: return
        main.launch {
            val ack = runCatching { execute(cmd) }.getOrElse { e ->
                Log.w(TAG, "command ${cmd.action} failed", e)
                CastAck(id, ok = false, error = e.message ?: "Command failed")
            }
            send(ack.copy(commandId = id))
        }
    }

    private suspend fun execute(cmd: CastInbound): CastAck {
        val id = cmd.commandId.orEmpty()
        val posMs = cmd.position?.let { (it * 1000).toLong().coerceAtLeast(0) }
        return when (cmd.action) {
            "play" -> play(id, cmd, posMs)
            "pause" -> withTarget(id) { it.castSetPlaying(false) }
            "resume" -> withTarget(id) { it.castSetPlaying(true) }
            "seek" -> if (posMs == null) CastAck(id, false, "Missing position") else withTarget(id) { it.castSeek(posMs) }
            "stop" -> withTarget(id) { it.castStop() }
            "volume" -> withTarget(id) { it.castVolume(cmd.volume?.toFloat()?.coerceIn(0f, 1f), cmd.muted) }
            "subtitle" -> {
                val t = target ?: return CastAck(id, false, "Nothing is playing")
                if (t.castSubtitle(cmd.subtitle.orEmpty())) CastAck(id, true) else CastAck(id, false, "unsupported action")
            }
            else -> CastAck(id, false, "unsupported action")
        }
    }

    private inline fun withTarget(id: String, block: (CastTarget) -> Unit): CastAck {
        val t = target ?: return CastAck(id, false, "Nothing is playing")
        block(t)
        return CastAck(id, true)
    }

    private suspend fun play(id: String, cmd: CastInbound, posMs: Long?): CastAck {
        val media = cmd.media ?: return CastAck(id, false, "Missing media")
        if (media.id <= 0) return CastAck(id, false, "Media not found")
        cmd.from?.takeIf { it.isNotBlank() }?.let { showNotice("Casting from $it") }

        // Same title already open: just seek and play.
        val current = target
        if (current != null && current.castMediaId == media.id) {
            posMs?.let(current::castSeek)
            current.castSetPlaying(true)
            return CastAck(id, true)
        }

        // Validate the id so the browser gets a clear error instead of a stuck TV.
        // (A network hiccup is not a reason to refuse: the player will retry and report.)
        val code = runCatching { withTimeoutOrNull(4_000) { api.getMediaById(media.id.toString()).code() } }.getOrNull()
        if (code == 404) return CastAck(id, false, "Media not found")

        val request = CastPlayRequest(media.id, posMs ?: 0L, cmd.from)
        pendingPlay = request
        report(CastStatus(CastState.LOADING.wire, media.id, media.type, media.title))
        _playRequests.value = request
        bringToFront(request, media.title)
        pendingTimeout?.cancel()
        pendingTimeout = main.launch {
            delay(10_000)
            if (pendingPlay == request && target?.castMediaId != media.id) {
                report(CastStatus(CastState.ERROR.wire, media.id, media.type, media.title,
                    error = "Open HomeFlix on the TV to start playback"))
            }
        }
        return CastAck(id, true)
    }

    /** The navigation graph opened the player for [request]. */
    fun consumePlayRequest(request: CastPlayRequest) {
        if (_playRequests.value == request) _playRequests.value = null
        cancelFullScreenNotification()
    }

    // ------------------------------------------------------------------ player registration

    fun register(t: CastTarget) {
        target = t
        if (pendingPlay?.mediaId == t.castMediaId) {
            pendingPlay = null
            pendingTimeout?.cancel()
        }
        report(t.castStatus())
    }

    fun unregister(t: CastTarget) {
        if (target !== t) return
        target = null
        // A new cast is replacing this player: don't tell the browser we're idle.
        if (pendingPlay == null) report(CastStatus.Idle)
    }

    /** Player state changed or a periodic position update. */
    fun report(status: CastStatus) {
        lastStatus = status
        send(status)
    }

    /** After (re)connecting: give watching browsers the current state. */
    fun sendCurrentStatus() {
        main.launch { send(target?.castStatus() ?: lastStatus.takeIf { pendingPlay != null } ?: CastStatus.Idle) }
    }

    private fun send(message: Any) {
        sender?.invoke(gson.toJson(message))
    }

    private fun showNotice(text: String) {
        _notice.value = text
        main.launch {
            delay(4_000)
            if (_notice.value == text) _notice.value = null
        }
    }

    // ------------------------------------------------------------------ bring the app to the front

    private val isForeground: Boolean
        get() = ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)

    private fun launchIntent(): Intent =
        Intent(context, com.homeflix.tv.presentation.MainActivity::class.java).apply {
            action = ACTION_CAST_PLAY
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        }

    /**
     * Android 10+ blocks activity starts from the background unless the app may
     * draw over other apps. Without that, post a full-screen notification.
     */
    private fun bringToFront(request: CastPlayRequest, title: String?) {
        if (isForeground) return
        val canStart = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || Settings.canDrawOverlays(context)
        val started = canStart && runCatching { context.startActivity(launchIntent()); true }.getOrDefault(false)
        if (!started) postFullScreenNotification(title ?: "HomeFlix")
    }

    @SuppressLint("MissingPermission")
    private fun postFullScreenNotification(title: String) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(PLAY_CHANNEL, "Cast requests", NotificationManager.IMPORTANCE_HIGH)
            )
        }
        val pi = PendingIntent.getActivity(
            context, 1, launchIntent(),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val n = NotificationCompat.Builder(context, PLAY_CHANNEL)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText("Tap to play on this TV")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .setFullScreenIntent(pi, true)
            .build()
        runCatching { nm.notify(PLAY_NOTIFICATION_ID, n) }
    }

    private fun cancelFullScreenNotification() {
        context.getSystemService(NotificationManager::class.java)?.cancel(PLAY_NOTIFICATION_ID)
    }

    companion object {
        const val ACTION_CAST_PLAY = "com.homeflix.tv.CAST_PLAY"
        private const val TAG = "CastController"
        private const val PLAY_CHANNEL = "cast_play"
        private const val PLAY_NOTIFICATION_ID = 4201
    }
}
