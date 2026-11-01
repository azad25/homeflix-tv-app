package com.homeflix.tv.cast

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.os.Build
import android.util.Log
import com.google.gson.Gson
import com.homeflix.tv.BuildConfig
import com.homeflix.tv.util.ServerConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

/**
 * Keeps the TV registered with the HomeFlix server as a cast target:
 * `ws://<server>/api/cast/tv/ws`, with keep-alive and reconnect-forever
 * (1 s → 30 s backoff with jitter, immediate retry when the network returns).
 */
@Singleton
class CastConnection @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: CastSettings,
    private val controller: CastController
) {
    enum class State { DISABLED, CONNECTING, CONNECTED, DISCONNECTED }

    private val _state = MutableStateFlow(State.DISABLED)
    val state: StateFlow<State> = _state.asStateFlow()

    private val gson = Gson()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Any()

    /** Base used for the WebSocket URL; tests override it. */
    internal var baseUrlProvider: (attempt: Int) -> String = { attempt ->
        val active = ServerConfig.activeApiBase
        // Every 4th failed attempt, try the other configured server (LAN vs. remote).
        if (attempt % 4 == 3) ServerConfig.other(active) else active
    }

    private val client: OkHttpClient = OkHttpClient.Builder()
        .pingInterval(20, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .connectTimeout(8, TimeUnit.SECONDS)
        .build()

    private var socket: WebSocket? = null
    private var generation = 0
    private var attempt = 0
    private var running = false
    private var reconnectJob: Job? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    init {
        controller.sender = { text -> socket?.send(text) ?: false }
        // Server switched (LAN ↔ remote): close and reconnect to the new one.
        ServerConfig.listeners += { reconnectNow() }
        // Rename without reconnecting.
        scope.launch {
            settings.name.drop(1).collect { name ->
                socket?.send(gson.toJson(CastRename(name)))
            }
        }
    }

    fun start() {
        synchronized(lock) { startLocked() }
    }

    private fun startLocked() {
        if (running) return
        running = true
        registerNetworkCallback()
        connectLocked()
    }

    fun stop() {
        synchronized(lock) { stopLocked() }
    }

    private fun stopLocked() {
        running = false
        generation++
        reconnectJob?.cancel()
        socket?.close(1000, "casting disabled")
        socket = null
        unregisterNetworkCallback()
        _state.value = State.DISABLED
    }

    /** Server URL changed (or a manual retry): drop the socket and dial again now. */
    fun reconnectNow() {
        synchronized(lock) { reconnectLocked() }
    }

    private fun reconnectLocked() {
        if (!running) return
        attempt = 0
        reconnectJob?.cancel()
        socket?.cancel()
        connectLocked()
    }

    private fun connectLocked() {
        val gen = ++generation
        val url = buildUrl(baseUrlProvider(attempt)) ?: run { scheduleReconnectLocked(gen); return }
        _state.value = State.CONNECTING
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "homeflix-tv/${BuildConfig.VERSION_NAME}")
            .build()
        socket = client.newWebSocket(request, Listener(gen))
    }

    private fun buildUrl(apiBase: String): String? {
        val base = apiBase.trimEnd('/').let { if (it.endsWith("/api")) it else "$it/api" }
        val http = "$base/cast/tv/ws".toHttpUrlOrNull() ?: return null
        val url = http.newBuilder()
            .addQueryParameter("device_id", settings.deviceId)
            .addQueryParameter("name", settings.name.value)
            .addQueryParameter("platform", "androidtv")
            .addQueryParameter("app_version", BuildConfig.VERSION_NAME)
            .build()
            .toString()
        return url // OkHttp upgrades an http(s) URL to ws(s)
    }

    private fun scheduleReconnectLocked(gen: Int) {
        if (!running || gen != generation) return
        _state.value = State.DISCONNECTED
        val backoffSec = (1L shl attempt.coerceAtMost(5)).coerceAtMost(30L)
        val delayMs = backoffSec * 1000 + Random.nextLong(0, 1000)
        attempt++
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            delay(delayMs)
            synchronized(lock) { if (running && gen == generation) connectLocked() }
        }
    }

    private inner class Listener(private val gen: Int) : WebSocketListener() {
        override fun onMessage(webSocket: WebSocket, text: String) {
            if (gen != generation) return
            val msg = runCatching { gson.fromJson(text, CastInbound::class.java) }.getOrNull() ?: return
            when (msg.type) {
                "hello" -> {
                    synchronized(lock) { attempt = 0 }
                    _state.value = State.CONNECTED
                    controller.sendCurrentStatus()
                }
                "ping" -> webSocket.send(gson.toJson(CastPong()))
                "command" -> controller.onCommand(msg)
            }
        }

        // Server is closing (restart, timeout): finish the handshake and dial again.
        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            webSocket.close(1000, null)
            synchronized(lock) { if (gen == generation) { socket = null; scheduleReconnectLocked(gen) } }
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            synchronized(lock) { if (gen == generation) { socket = null; scheduleReconnectLocked(gen) } }
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            Log.d(TAG, "cast socket failed: ${t.message}")
            synchronized(lock) { if (gen == generation) { socket = null; scheduleReconnectLocked(gen) } }
        }
    }

    private fun registerNetworkCallback() {
        if (networkCallback != null || Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return
        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                if (_state.value != State.CONNECTED) reconnectNow()
            }
        }
        runCatching { cm.registerDefaultNetworkCallback(cb); networkCallback = cb }
    }

    private fun unregisterNetworkCallback() {
        val cb = networkCallback ?: return
        networkCallback = null
        runCatching { context.getSystemService(ConnectivityManager::class.java)?.unregisterNetworkCallback(cb) }
    }

    private companion object { const val TAG = "CastConnection" }
}
