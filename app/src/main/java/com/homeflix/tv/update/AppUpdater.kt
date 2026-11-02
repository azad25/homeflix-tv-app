package com.homeflix.tv.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.homeflix.tv.BuildConfig
import com.homeflix.tv.core.ApplicationScope
import com.homeflix.tv.util.ServerConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** What the server publishes at `GET /api/app/tv/latest`. */
data class UpdateInfo(
    @SerializedName("version_code") val versionCode: Long = 0,
    @SerializedName("version_name") val versionName: String? = null,
    val url: String? = null,
    val sha256: String? = null,
    val size: Long = 0,
    val notes: String? = null
)

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data class UpToDate(val checkedAtMs: Long) : UpdateState
    data class Downloading(val info: UpdateInfo, val progress: Float) : UpdateState
    /** Downloaded and verified; waiting for the user's OK. */
    data class Ready(val info: UpdateInfo, val file: File) : UpdateState
    data class Installing(val info: UpdateInfo) : UpdateState
    data class Failed(val message: String, val info: UpdateInfo? = null) : UpdateState
}

/**
 * Self-update from the user's own HomeFlix server (no Play Store).
 *
 * Checks shortly after launch, every 6 h while running, when the app returns to
 * the foreground (at most hourly) and immediately when the server pushes
 * `update_available` over the cast socket. A newer build is downloaded in the
 * background, verified (size, SHA-256, package name, version, signing key) and
 * then installed through PackageInstaller — Android shows one "Update"
 * confirmation; data and settings are kept because every build shares a key.
 */
@Singleton
class AppUpdater @Inject constructor(
    @ApplicationContext private val context: Context,
    @ApplicationScope private val scope: CoroutineScope
) {
    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    /** Tests only: show the UI for a given state without a server. */
    internal fun setStateForTest(s: UpdateState) { _state.value = s }

    private val gson = Gson()
    private val mutex = Mutex()
    private var loopJob: Job? = null
    private var lastCheck = 0L

    /** Overridable for tests. */
    internal var apiBase: () -> String = { ServerConfig.activeApiBase }
    internal var currentVersionCode: Long = BuildConfig.VERSION_CODE.toLong()
    internal var archiveVerifier: (File, UpdateInfo) -> String? = ::verifyArchive

    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val dir: File get() = File(context.filesDir, "updates").apply { mkdirs() }

    /** Called once from Application.onCreate. */
    fun start() {
        if (loopJob != null) return
        cleanUpInstalled()
        loopJob = scope.launch {
            delay(8_000) // let the UI and first feeds load first
            while (isActive) {
                check()
                delay(6 * 60 * 60 * 1000L)
            }
        }
    }

    /**
     * App came to the foreground. Android TV keeps the process alive for days,
     * so "launch" rarely means a fresh start: re-check whenever the last check
     * is more than a few minutes old.
     */
    fun onForeground() {
        if (lastCheck != 0L && SystemClock.elapsedRealtime() - lastCheck > 5 * 60 * 1000L) checkNow()
    }

    /** Server push or the Settings button. */
    fun checkNow() { scope.launch { check() } }

    internal suspend fun check() = mutex.withLock {
        val current = _state.value
        if (current is UpdateState.Downloading || current is UpdateState.Installing) return@withLock
        lastCheck = SystemClock.elapsedRealtime()
        _state.value = UpdateState.Checking
        val info = runCatching { fetchLatest() }.getOrElse { e ->
            Log.w(TAG, "update check failed: ${e.message}")
            // A failed check isn't worth bothering the user about; keep a ready update.
            _state.value = if (current is UpdateState.Ready) current else UpdateState.Failed("Couldn't reach the server")
            return@withLock
        }
        if (info == null || info.versionCode <= currentVersionCode) {
            _state.value = UpdateState.UpToDate(System.currentTimeMillis())
            return@withLock
        }
        if (current is UpdateState.Ready && current.info.versionCode == info.versionCode && current.file.exists()) {
            _state.value = current
            return@withLock
        }
        download(info)
    }

    private suspend fun fetchLatest(): UpdateInfo? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(apiBase().trimEnd('/') + "/app/tv/latest")
            .header("User-Agent", "homeflix-tv/${BuildConfig.VERSION_NAME}")
            .header("X-App-Version-Code", currentVersionCode.toString())
            .build()
        client.newCall(request).execute().use { r ->
            when {
                r.code == 404 || r.code == 204 -> null
                !r.isSuccessful -> error("HTTP ${r.code}")
                else -> gson.fromJson(r.body?.string(), UpdateInfo::class.java)
            }
        }
    }

    private suspend fun download(info: UpdateInfo) {
        val url = resolve(info.url) ?: run {
            _state.value = UpdateState.Failed("The server published an update without a download link", info); return
        }
        val target = File(dir, "homeflix-tv-${info.versionCode}.apk")
        val partial = File(dir, "homeflix-tv-${info.versionCode}.part")
        _state.value = UpdateState.Downloading(info, 0f)
        val error: String? = withContext(Dispatchers.IO) {
            runCatching {
                val digest = MessageDigest.getInstance("SHA-256")
                val request = Request.Builder().url(url)
                    .header("User-Agent", "homeflix-tv/${BuildConfig.VERSION_NAME}").build()
                client.newCall(request).execute().use { r ->
                    if (!r.isSuccessful) return@runCatching "Download failed (HTTP ${r.code})"
                    val body = r.body ?: return@runCatching "Empty download"
                    val total = info.size.takeIf { it > 0 } ?: body.contentLength()
                    var read = 0L
                    var lastEmit = 0L
                    partial.outputStream().use { out ->
                        body.byteStream().use { input ->
                            val buf = ByteArray(64 * 1024)
                            while (true) {
                                val n = input.read(buf)
                                if (n < 0) break
                                out.write(buf, 0, n)
                                digest.update(buf, 0, n)
                                read += n
                                if (total > 0 && read - lastEmit > 256 * 1024) {
                                    lastEmit = read
                                    _state.value = UpdateState.Downloading(info, (read.toFloat() / total).coerceIn(0f, 1f))
                                }
                            }
                        }
                    }
                    if (info.size > 0 && read != info.size) return@runCatching "Download was incomplete"
                    val sha = digest.digest().joinToString("") { "%02x".format(it) }
                    if (!info.sha256.isNullOrBlank() && !sha.equals(info.sha256.trim(), ignoreCase = true)) {
                        return@runCatching "Download was corrupted (checksum mismatch)"
                    }
                    archiveVerifier(partial, info)?.let { return@runCatching it }
                    target.delete()
                    if (!partial.renameTo(target)) return@runCatching "Couldn't save the update"
                    null
                }
            }.getOrElse { it.message ?: "Download failed" }
        }
        if (error != null) {
            partial.delete()
            _state.value = UpdateState.Failed(error, info)
        } else {
            dir.listFiles()?.filter { it != target }?.forEach { it.delete() }
            _state.value = UpdateState.Ready(info, target)
        }
    }

    private fun resolve(url: String?): String? {
        if (url.isNullOrBlank()) return null
        if (url.startsWith("http://") || url.startsWith("https://")) return url
        val root = apiBase().trimEnd('/').removeSuffix("/api")
        return root + (if (url.startsWith("/")) url else "/$url")
    }

    /** Returns an error message, or null when the file is a valid update for this app. */
    private fun verifyArchive(file: File, info: UpdateInfo): String? {
        val pm = context.packageManager
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) PackageManager.GET_SIGNING_CERTIFICATES
        else @Suppress("DEPRECATION") PackageManager.GET_SIGNATURES
        val archive = pm.getPackageArchiveInfo(file.absolutePath, flags)
            ?: return "The downloaded file isn't a valid app"
        if (archive.packageName != context.packageName) return "The update is for a different app"
        if (archive.longVersion() != info.versionCode) return "The update's version doesn't match the server"
        val installed = runCatching { pm.getPackageInfo(context.packageName, flags) }.getOrNull()
        val newSigs = archive.signatureSet(file)
        val oldSigs = installed?.signatureSet(null)
        // Android would refuse a different signing key anyway; catch it early with a clear message.
        if (!newSigs.isNullOrEmpty() && !oldSigs.isNullOrEmpty() && newSigs != oldSigs) {
            return "The update is signed with a different key"
        }
        return null
    }

    @Suppress("DEPRECATION")
    private fun PackageInfo.longVersion(): Long =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) longVersionCode else versionCode.toLong()

    @Suppress("DEPRECATION")
    private fun PackageInfo.signatureSet(archive: File?): Set<String>? {
        if (archive != null) applicationInfo?.let { it.sourceDir = archive.absolutePath; it.publicSourceDir = archive.absolutePath }
        val sigs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) signingInfo?.apkContentsSigners else signatures
        return sigs?.map { it.toCharsString() }?.toSet()
    }

    // ------------------------------------------------------------------ install

    /** True when Android will let HomeFlix install its update (the "Install unknown apps" switch). */
    fun canInstall(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()

    fun openInstallPermissionSettings() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val i = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(i) }.onFailure {
            runCatching {
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
        }
    }

    /** Hand the verified APK to Android; it shows the "Update" confirmation. */
    fun install() {
        val ready = _state.value as? UpdateState.Ready ?: return
        // Even without the "install unknown apps" grant we hand the APK to the
        // system: its installer shows its own (OEM-tested) prompt that leads to
        // the switch — more reliable on TVs than deep-linking to that setting.
        _state.value = UpdateState.Installing(ready.info)
        scope.launch(Dispatchers.IO) {
            runCatching {
                val installer = context.packageManager.packageInstaller
                val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
                    setAppPackageName(context.packageName)
                    setSize(ready.file.length())
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
                    }
                }
                val sessionId = installer.createSession(params)
                installer.openSession(sessionId).use { session ->
                    session.openWrite("homeflix-tv.apk", 0, ready.file.length()).use { out ->
                        ready.file.inputStream().use { it.copyTo(out) }
                        session.fsync(out)
                    }
                    val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                        (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)
                    val status = PendingIntent.getBroadcast(
                        context, sessionId,
                        Intent(context, InstallResultReceiver::class.java).setPackage(context.packageName),
                        flags
                    )
                    session.commit(status.intentSender)
                }
            }.onFailure { e ->
                Log.w(TAG, "install failed", e)
                _state.value = UpdateState.Failed("Couldn't start the update: ${e.message}", ready.info)
            }
        }
    }

    /** Result from PackageInstaller (see [InstallResultReceiver]). */
    internal fun onInstallResult(status: Int, message: String?, confirm: Intent?) {
        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> confirm?.let {
                runCatching { context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            }
            PackageInstaller.STATUS_SUCCESS -> Unit // the system restarts us on the new version
            else -> {
                val info = (state.value as? UpdateState.Installing)?.info
                val file = info?.let { File(dir, "homeflix-tv-${it.versionCode}.apk") }
                _state.value = if (status == PackageInstaller.STATUS_FAILURE_ABORTED && file?.exists() == true)
                    UpdateState.Ready(info, file) // user pressed Cancel: offer it again later
                else UpdateState.Failed(message ?: "The update couldn't be installed", info)
            }
        }
    }

    /** Delete downloads that are already installed (or older). */
    private fun cleanUpInstalled() {
        dir.listFiles()?.forEach { f ->
            val code = f.name.removePrefix("homeflix-tv-").substringBefore('.').toLongOrNull()
            if (code == null || code <= currentVersionCode || f.name.endsWith(".part")) f.delete()
        }
    }

    private companion object { const val TAG = "AppUpdater" }
}
