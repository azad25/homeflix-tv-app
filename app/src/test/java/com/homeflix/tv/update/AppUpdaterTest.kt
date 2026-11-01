package com.homeflix.tv.update

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.security.MessageDigest

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class AppUpdaterTest {

    private lateinit var server: MockWebServer
    private lateinit var updater: AppUpdater
    private val apk = ByteArray(300_000) { (it % 251).toByte() }
    private val sha = MessageDigest.getInstance("SHA-256").digest(apk).joinToString("") { "%02x".format(it) }
    @Volatile private var latest: MockResponse = MockResponse().setResponseCode(404)
    @Volatile private var apkResponse: () -> MockResponse = { MockResponse().setBody(Buffer().write(apk)) }

    @Before
    fun setUp() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when (request.path) {
                "/api/app/tv/latest" -> latest
                "/api/app/tv/download/7" -> apkResponse()
                else -> MockResponse().setResponseCode(404)
            }
        }
        server.start()
        updater = AppUpdater(ApplicationProvider.getApplicationContext(), CoroutineScope(SupervisorJob() + Dispatchers.IO))
        updater.apiBase = { server.url("/api").toString().trimEnd('/') }
        updater.currentVersionCode = 6
        updater.archiveVerifier = { _, _ -> null }
    }

    @After
    fun tearDown() { server.shutdown() }

    private fun publish(code: Long, sha256: String = sha, size: Long = apk.size.toLong(), url: String = "/api/app/tv/download/7") {
        latest = MockResponse().setBody(
            """{"version_code":$code,"version_name":"5.2","url":"$url","sha256":"$sha256","size":$size,"notes":"Faster trailers"}"""
        )
    }

    @Test
    fun nothingPublished_isUpToDate() = runBlocking {
        updater.check()
        assertTrue(updater.state.value is UpdateState.UpToDate)
    }

    @Test
    fun sameOrOlderVersion_isUpToDate() = runBlocking {
        publish(6)
        updater.check()
        assertTrue(updater.state.value is UpdateState.UpToDate)
    }

    @Test
    fun newerVersion_downloadsVerifiesAndIsReady() = runBlocking {
        publish(7)
        updater.check()
        val s = updater.state.value
        assertTrue("state was $s", s is UpdateState.Ready)
        s as UpdateState.Ready
        assertEquals(7L, s.info.versionCode)
        assertEquals("Faster trailers", s.info.notes)
        assertTrue(s.file.readBytes().contentEquals(apk))
        // A second check doesn't download again.
        val requestsBefore = server.requestCount
        updater.check()
        assertTrue(updater.state.value is UpdateState.Ready)
        assertEquals(requestsBefore + 1, server.requestCount) // only the /latest call
    }

    @Test
    fun corruptedDownload_isRejected() = runBlocking {
        publish(7, sha256 = "00".repeat(32))
        updater.check()
        val s = updater.state.value
        assertTrue("state was $s", s is UpdateState.Failed)
        assertTrue((s as UpdateState.Failed).message.contains("checksum"))
        val leftovers = java.io.File(ApplicationProvider.getApplicationContext<android.content.Context>().filesDir, "updates")
            .listFiles().orEmpty()
        assertFalse("no partial/bad APK left behind", leftovers.any { it.name.contains("-7") })
    }

    @Test
    fun truncatedDownload_isRejected() = runBlocking {
        publish(7, size = apk.size + 10L)
        updater.check()
        assertTrue((updater.state.value as UpdateState.Failed).message.contains("incomplete"))
    }

    @Test
    fun wrongApp_isRejected() = runBlocking {
        publish(7)
        updater.archiveVerifier = { _, _ -> "The update is for a different app" }
        updater.check()
        assertEquals("The update is for a different app", (updater.state.value as UpdateState.Failed).message)
    }
}
