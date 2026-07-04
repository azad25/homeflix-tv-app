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
    val LOCAL: String = BuildConfig.BASE_URL.removeSuffix("/")
    val REMOTE: String = BuildConfig.FALLBACK_BASE_URL.removeSuffix("/")

    @Volatile
    var activeApiBase: String = LOCAL
        private set

    fun activate(base: String) {
        if (activeApiBase != base) {
            activeApiBase = base
            Log.i("ServerConfig", "Active server -> $base")
        }
    }

    fun other(base: String): String = if (base == LOCAL) REMOTE else LOCAL

    /**
     * Boot-time probe: quick reachability check against the LAN server; any
     * HTTP response counts as reachable. Falls back to the domain otherwise.
     * Call from Application.onCreate on a background thread.
     */
    fun probe() {
        val client = OkHttpClient.Builder()
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
