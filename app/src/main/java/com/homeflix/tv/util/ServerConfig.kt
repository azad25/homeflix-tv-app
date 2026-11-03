package com.homeflix.tv.util

import android.util.Log
import com.homeflix.tv.BuildConfig
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Runtime server selection: LOCAL (LAN IP, fastest) is preferred; the public
 * DOMAIN is the fallback when the TV is not on the home network.
 *
 * All URL construction (API, images, streams) goes through [activeApiBase],
 * and [ServerFailoverInterceptor] transparently retries a failed request
 * against the other server and sticks with whichever one works.
 */
object ServerConfig {
    /** LAN server. Built-in default, overridable in Settings → Server address. */
    @Volatile
    var LOCAL: String = BuildConfig.BASE_URL.removeSuffix("/")
        private set
    val REMOTE: String = BuildConfig.FALLBACK_BASE_URL.removeSuffix("/")
    val DEFAULT_LOCAL: String = BuildConfig.BASE_URL.removeSuffix("/")

    private const val PREFS = "server_config"
    private const val KEY_LOCAL = "local_api_base"

    /** Load a user-set server address. Call first thing in Application.onCreate. */
    fun init(context: android.content.Context) {
        context.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)
            .getString(KEY_LOCAL, null)?.let { LOCAL = it; activeApiBase = it }
    }

    /**
     * Accepts "192.168.1.50", "192.168.1.50:8252", "http://host:port" or a full
     * ".../api" URL and stores it as "http(s)://host:port/api". Returns the
     * normalized base, or null if the input isn't a usable address.
     */
    fun setLocal(context: android.content.Context, input: String): String? {
        val normalized = normalize(input) ?: return null
        context.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)
            .edit().putString(KEY_LOCAL, normalized).apply()
        LOCAL = normalized
        activate(normalized)
        return normalized
    }

    fun normalize(input: String): String? {
        var s = input.trim()
        val rest = s.removePrefix("http://").removePrefix("https://").trim('/')
        if (rest.isEmpty() || rest.startsWith(":")) return null
        s = s.trimEnd('/')
        if (!s.startsWith("http://") && !s.startsWith("https://")) s = "http://$s"
        val url = runCatching { s.toHttpUrl() }.getOrNull() ?: return null
        val hasPort = Regex("^https?://[^/]+:\\d+").containsMatchIn(s)
        val port = if (hasPort) url.port else if (url.scheme == "https") 443 else 8252
        val defaultPort = (url.scheme == "http" && port == 80) || (url.scheme == "https" && port == 443)
        return "${url.scheme}://${url.host}${if (defaultPort) "" else ":$port"}/api"
    }

    @Volatile
    var activeApiBase: String = BuildConfig.BASE_URL.removeSuffix("/")
        private set

    fun activate(base: String) {
        if (activeApiBase != base) {
            activeApiBase = base
            Log.i("ServerConfig", "Active server -> $base")
            listeners.forEach { runCatching { it(base) } }
        }
    }

    /** Notified when the active server changes (e.g. the cast socket re-dials). */
    val listeners = java.util.concurrent.CopyOnWriteArrayList<(String) -> Unit>()

    fun other(base: String): String = if (base == LOCAL) REMOTE else LOCAL

    /**
     * Boot-time probe: quick reachability check against the LAN server; any
     * HTTP response counts as reachable. Falls back to the domain otherwise.
     * Call from Application.onCreate on a background thread.
     */
    fun probe() {
        val client = Http.shared.newBuilder()
            .connectTimeout(2500, TimeUnit.MILLISECONDS)
            .readTimeout(2500, TimeUnit.MILLISECONDS)
            .build()
        try {
            client.newCall(
                Request.Builder().url("$LOCAL/genres").head().build()
            ).execute().use { /* reachable - keep LOCAL */ }
            activate(LOCAL)
        } catch (e: IOException) {
            Log.i("ServerConfig", "LAN server unreachable, using domain fallback")
            activate(REMOTE)
        }
    }
}

/**
 * Rewrites every request to the currently active server and fails over to
 * the other one on connection errors.
 */
class ServerFailoverInterceptor : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val active = ServerConfig.activeApiBase
        val first = rewrite(chain.request(), active)
        return try {
            chain.proceed(first)
        } catch (e: IOException) {
            val fallback = ServerConfig.other(active)
            val retry = rewrite(chain.request(), fallback)
            val response = chain.proceed(retry) // throws if this one fails too
            ServerConfig.activate(fallback)     // stick with what works
            response
        }
    }

    private fun rewrite(request: Request, apiBase: String): Request {
        val base = apiBase.toHttpUrl()
        val newUrl = request.url.newBuilder()
            .scheme(base.scheme)
            .host(base.host)
            .port(base.port)
            .build()
        return request.newBuilder().url(newUrl).build()
    }
}
