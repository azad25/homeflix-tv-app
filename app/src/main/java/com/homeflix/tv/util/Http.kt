package com.homeflix.tv.util

import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * One HTTP stack for the whole app. Every client (API, artwork, cast socket,
 * updater, probes) is derived from [shared] with `newBuilder()`, so they share
 * one connection pool and one thread pool instead of each keeping its own.
 *
 * Kept open connections are reused across clients: the boot-time server probe
 * leaves a warm connection that the first Home request picks up, which saves
 * a TCP (and on the domain, TLS) handshake on the critical path.
 */
object Http {
    val shared: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectionPool(ConnectionPool(8, 5, TimeUnit.MINUTES))
            .build()
    }

    /**
     * Artwork gets its own request queue (still the same connections), so a
     * screenful of posters never holds up an API call: OkHttp allows only
     * a few requests per host at a time, and the API and images share a host.
     */
    fun images(): OkHttpClient = shared.newBuilder()
        .dispatcher(Dispatcher().apply { maxRequests = 16; maxRequestsPerHost = 6 })
        .build()
}
