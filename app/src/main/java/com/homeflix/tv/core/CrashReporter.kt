package com.homeflix.tv.core

import android.content.Context
import android.os.Build
import com.homeflix.tv.BuildConfig
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Keeps the last crash on disk so the next launch can show it on screen
 * (TVs rarely have adb/logcat handy; a photo of the screen is enough to fix it).
 */
object CrashReporter {
    private const val FILE = "last_crash.txt"

    fun install(context: Context) {
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                val sw = StringWriter()
                error.printStackTrace(PrintWriter(sw))
                val header = "HomeFlix ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) · " +
                    "${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE} · " +
                    SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date()) +
                    " · thread ${thread.name}"
                File(app.filesDir, FILE).writeText(header + "\n" + sw.toString().take(12_000))
            }
            previous?.uncaughtException(thread, error)
        }
    }

    fun lastCrash(context: Context): String? =
        runCatching { File(context.filesDir, FILE).takeIf { it.exists() }?.readText() }.getOrNull()

    fun clear(context: Context) {
        runCatching { File(context.filesDir, FILE).delete() }
    }

    /** Header + the first app frames: what fits on a TV screen. */
    fun summary(report: String, maxLines: Int = 14): String {
        val lines = report.lines()
        val app = lines.filter { it.contains("com.homeflix") }.take(6)
        val head = lines.take(4)
        return (head + listOf("…") + app).distinct().take(maxLines).joinToString("\n")
    }
}
