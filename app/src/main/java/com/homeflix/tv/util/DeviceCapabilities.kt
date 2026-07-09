package com.homeflix.tv.util

import android.app.ActivityManager
import android.content.Context

/**
 * Runtime device capability checks so the app can lighten itself on low-RAM
 * TVs (e.g. 2 GB Sony Android TV): smaller image caches and NO auto-playing
 * background preview videos (the single biggest ongoing cost).
 */
object DeviceCapabilities {

    @Volatile private var cachedLowRam: Boolean? = null

    /** True on constrained TVs — total RAM under ~2.5 GB or OS low-RAM flag. */
    fun isLowRam(context: Context): Boolean {
        cachedLowRam?.let { return it }
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val result = try {
            val mem = ActivityManager.MemoryInfo()
            am?.getMemoryInfo(mem)
            val totalGb = (mem.totalMem.toDouble()) / (1024.0 * 1024.0 * 1024.0)
            (am?.isLowRamDevice == true) || totalGb < 2.5
        } catch (e: Exception) {
            false
        }
        cachedLowRam = result
        return result
    }

    /** Whether ambient background preview videos should auto-play. */
    fun allowBackgroundVideo(context: Context): Boolean = !isLowRam(context)
}
