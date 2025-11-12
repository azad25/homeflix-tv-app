package com.homeflix.tv.util

import android.content.Context
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.CachePolicy
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * NETFLIX-STYLE IMAGE CACHING
 * - Aggressive memory cache (256MB)
 * - Large disk cache (512MB)
 * - Instant image loading
 */
object ImageCache {
    
    private var imageLoader: ImageLoader? = null
    
    fun getImageLoader(context: Context): ImageLoader {
        return imageLoader ?: createImageLoader(context).also { imageLoader = it }
    }
    
    private fun createImageLoader(context: Context): ImageLoader {
        return ImageLoader.Builder(context)
            .memoryCache {
                MemoryCache.Builder(context)
                    .maxSizePercent(0.25) // Use 25% of app memory
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(context.cacheDir.resolve("image_cache"))
                    .maxSizeBytes(512 * 1024 * 1024) // 512MB disk cache
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
            .crossfade(true)
            .crossfade(300)
            .build()
    }
    
    fun clearCache(context: Context) {
        imageLoader?.memoryCache?.clear()
        imageLoader?.diskCache?.clear()
    }
}
