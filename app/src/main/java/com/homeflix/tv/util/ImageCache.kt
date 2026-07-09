package com.homeflix.tv.util

import android.content.Context
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.CachePolicy
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * IMAGE CACHING — tuned to be light on low-RAM TVs (2 GB / 8 GB boxes).
 * Memory cache scales down and disk cache is capped small on low-RAM devices
 * so posters/backdrops don't pressure the heap or fill limited storage.
 */
object ImageCache {

    private var imageLoader: ImageLoader? = null

    fun getImageLoader(context: Context): ImageLoader {
        return imageLoader ?: createImageLoader(context).also { imageLoader = it }
    }

    private fun createImageLoader(context: Context): ImageLoader {
        val lowRam = DeviceCapabilities.isLowRam(context)
        val memPercent = if (lowRam) 0.12 else 0.20
        val diskBytes = if (lowRam) 96L * 1024 * 1024 else 256L * 1024 * 1024
        return ImageLoader.Builder(context)
            .memoryCache {
                MemoryCache.Builder(context)
                    .maxSizePercent(memPercent)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(context.cacheDir.resolve("image_cache"))
                    .maxSizeBytes(diskBytes)
                    .build()
            }
            .okHttpClient {
                OkHttpClient.Builder()
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .readTimeout(30, TimeUnit.SECONDS)
                    .writeTimeout(30, TimeUnit.SECONDS)
                    .build()
            }
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .networkCachePolicy(CachePolicy.ENABLED)
            // Perf on low-RAM TVs: RGB_565 halves bitmap memory (less GC → less
            // D-pad jank) and no global crossfade avoids an extra draw pass.
            .allowRgb565(true)
            .crossfade(false)
            .build()
    }
    
    fun clearCache(context: Context) {
        imageLoader?.memoryCache?.clear()
        imageLoader?.diskCache?.clear()
    }
}
