package com.homeflix.tv.cast

import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.homeflix.tv.data.remote.api.HomeFlixApiService
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

/**
 * Plays the HomeFlix server's side of docs/TV_CAST_PROTOCOL.md against the
 * real CastConnection + CastController.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class CastProtocolTest {

    private lateinit var server: MockWebServer
    private val fromTv = CopyOnWriteArrayList<JsonObject>()
    private val connections = AtomicInteger(0)
    @Volatile private var tvSocket: WebSocket? = null
    private lateinit var connection: CastConnection
    private lateinit var controller: CastController

    private class FakeTarget(override val castMediaId: Int) : CastTarget {
        var playing = true
        var seekMs = -1L
        var stopped = false
        override fun castSetPlaying(playing: Boolean) { this.playing = playing }
        override fun castSeek(positionMs: Long) { seekMs = positionMs }
        override fun castStop() { stopped = true }
        override fun castVolume(volume: Float?, muted: Boolean?) {}
        override fun castSubtitle(id: String) = true
        override fun castStatus() = CastStatus(
            state = if (playing) "playing" else "paused", mediaId = castMediaId, mediaType = "movie",
            title = "Fake", position = 12.0, duration = 100.0
        )
    }

    @Before
    fun setUp() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                return when {
                    path.startsWith("/api/cast/tv/ws") -> {
                        connections.incrementAndGet()
                        assertTrue(path.contains("device_id="))
                        assertTrue(path.contains("platform=androidtv"))
                        MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
                            override fun onOpen(webSocket: WebSocket, response: Response) {
                                tvSocket = webSocket
                                webSocket.send("""{"type":"hello","device_id":"x","server_time":"2026-11-01T18:58:39Z"}""")
                            }
                            override fun onMessage(webSocket: WebSocket, text: String) {
                                fromTv += JsonParser.parseString(text).asJsonObject
                            }
                        })
                    }
                    path.startsWith("/api/media/99") -> MockResponse().setResponseCode(404)
                    path.startsWith("/api/media/") -> MockResponse().setBody("""{"id":42,"title":"Inception","type":"movie"}""")
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        server.start()
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val api = Retrofit.Builder()
            .baseUrl(server.url("/api/"))
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(HomeFlixApiService::class.java)
        controller = CastController(ctx, api)
        connection = CastConnection(ctx, CastSettings(ctx), controller)
        connection.baseUrlProvider = { server.url("/api").toString().trimEnd('/') }
    }

    @After
    fun tearDown() {
        connection.stop()
        runCatching { tvSocket?.close(1000, null) }
        runCatching { server.shutdown() }
    }

    private fun await(what: String, timeoutMs: Long = 8_000, cond: () -> Boolean) {
        val end = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < end) {
            shadowOf(Looper.getMainLooper()).idle()
            if (cond()) return
            Thread.sleep(20)
        }
        throw AssertionError("timed out waiting for: $what")
    }

    private fun ack(id: String): JsonObject {
        await("ack $id") { fromTv.any { it.str("type") == "ack" && it.str("command_id") == id } }
        return fromTv.last { it.str("type") == "ack" && it.str("command_id") == id }
    }

    private fun JsonObject.str(k: String) = get(k)?.takeIf { !it.isJsonNull }?.asString
    private fun command(id: String, body: String) =
        tvSocket!!.send("""{"type":"command","command_id":"$id",$body,"from":"Chrome on Mac","sent_at":"2026-11-01T18:58:42Z"}""")

    @Test
    fun fullProtocol() {
        connection.start()
        await("connected") { connection.state.value == CastConnection.State.CONNECTED }
        // After hello the TV reports its state (idle: nothing playing yet).
        await("initial status") { fromTv.any { it.str("type") == "status" && it.str("state") == "idle" } }

        // Keep-alive.
        tvSocket!!.send("""{"type":"ping"}""")
        await("pong") { fromTv.any { it.str("type") == "pong" } }

        // No player yet → refuse transport commands with a readable error.
        command("c0", """"action":"pause"""")
        assertEquals("Nothing is playing", ack("c0").str("error"))

        val target = FakeTarget(7)
        shadowOf(Looper.getMainLooper()).idle()
        controller.register(target)

        command("c1", """"action":"pause"""")
        assertEquals("true", ack("c1").get("ok").asString)
        assertFalse(target.playing)

        command("c2", """"action":"seek","position":120.5""")
        ack("c2")
        assertEquals(120_500L, target.seekMs)

        command("c3", """"action":"resume"""")
        ack("c3")
        assertTrue(target.playing)

        command("c4", """"action":"teleport"""")
        val unknown = ack("c4")
        assertFalse(unknown.get("ok").asBoolean)
        assertEquals("unsupported action", unknown.str("error"))

        // Same title again: seek + play in place, no navigation.
        command("c5", """"action":"play","media":{"id":7,"type":"movie","title":"Fake"},"position":30""")
        assertTrue(ack("c5").get("ok").asBoolean)
        assertEquals(30_000L, target.seekMs)
        assertEquals(null, controller.playRequests.value)

        command("c6", """"action":"stop"""")
        ack("c6")
        assertTrue(target.stopped)
        controller.unregister(target)
        await("idle after player closed") { fromTv.last { it.str("type") == "status" }.str("state") == "idle" }

        // Unknown media → refused.
        command("c7", """"action":"play","media":{"id":99,"type":"movie"}""")
        val missing = ack("c7")
        assertFalse(missing.get("ok").asBoolean)
        assertEquals("Media not found", missing.str("error"))

        // New title → accepted immediately, player opening is requested, status "loading".
        command("c8", """"action":"play","media":{"id":42,"type":"movie","title":"Inception"},"position":600""")
        assertTrue(ack("c8").get("ok").asBoolean)
        val req = controller.playRequests.value
        assertNotNull(req)
        assertEquals(42, req!!.mediaId)
        assertEquals(600_000L, req.positionMs)
        await("loading status") { fromTv.any { it.str("state") == "loading" && it.get("media_id")?.asInt == 42 } }
        assertEquals("Casting from Chrome on Mac", controller.notice.value)

        // Server drops the socket → the TV reconnects on its own and says hello again.
        tvSocket!!.close(1001, "server restart")
        await("reconnected", timeoutMs = 10_000) { connections.get() >= 2 && connection.state.value == CastConnection.State.CONNECTED }
    }
}
