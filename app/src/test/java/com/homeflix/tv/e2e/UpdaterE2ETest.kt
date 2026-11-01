package com.homeflix.tv.e2e

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.homeflix.tv.update.AppUpdater
import com.homeflix.tv.update.UpdateState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File
import java.security.MessageDigest

/**
 * The real self-update path against a real HomeFlix backend:
 * publish an APK via the server's upload endpoint, then let AppUpdater find,
 * download and verify it. Needs -Phomeflix.e2e.server and a built release APK.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class UpdaterE2ETest {
    @Test
    fun publishedBuild_isFoundDownloadedAndVerified() = runBlocking {
        val server = System.getProperty("homeflix.e2e.server")
        val apk = File("build/outputs/apk/release/app-release.apk")
        assumeTrue(server != null && apk.exists())

        // Publish through the server, exactly like the docs describe.
        val body = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("apk", "homeflix-tv.apk", apk.asRequestBody("application/vnd.android.package-archive".toMediaType()))
            .addFormDataPart("notes", "E2E test build")
            .build()
        val code = OkHttpClient().newCall(
            Request.Builder().url("http://$server/api/admin/app/tv/upload").post(body).build()
        ).execute().use { it.code }
        assertEquals(200, code)

        val updater = AppUpdater(ApplicationProvider.getApplicationContext(), CoroutineScope(SupervisorJob() + Dispatchers.IO))
        updater.apiBase = { "http://$server/api" }
        updater.currentVersionCode = 1 // pretend an old build is installed
        updater.archiveVerifier = { _, _ -> null } // PackageManager archive parsing isn't available off-device
        updater.check()

        val s = updater.state.value
        assertTrue("state was $s", s is UpdateState.Ready)
        s as UpdateState.Ready
        assertEquals("E2E test build", s.info.notes)
        assertEquals(apk.length(), s.file.length())
        val sha = MessageDigest.getInstance("SHA-256").digest(s.file.readBytes()).joinToString("") { "%02x".format(it) }
        assertEquals(sha, s.info.sha256)
        println("UPDATER-E2E ready: ${s.info.versionName} (${s.info.versionCode}) ${s.file.length()} bytes")
    }
}
