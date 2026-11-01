package com.homeflix.tv

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import coil.ImageLoader
import coil.ImageLoaderFactory

@HiltAndroidApp
class HomeFlixTVApplication : Application(), ImageLoaderFactory {

    @javax.inject.Inject lateinit var previewPlayer: com.homeflix.tv.core.PreviewPlayer

    /**
     * Memory pressure: Coil trims its own bitmap cache via component callbacks;
     * here we additionally free the preview decoder + buffers, and drop all
     * cached bitmaps once the UI is hidden and the system is running low.
     */
    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    @Suppress("DEPRECATION")
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (::previewPlayer.isInitialized && level >= android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) {
            previewPlayer.onTrimMemory()
        }
        if (level >= android.content.ComponentCallbacks2.TRIM_MEMORY_BACKGROUND) {
            com.homeflix.tv.util.ImageCache.getImageLoader(this).memoryCache?.clear()
        }
    }
    
    override fun onCreate() {
        super.onCreate()
        com.homeflix.tv.core.CrashReporter.install(this)

        // Initialize image cache on app startup
        com.homeflix.tv.util.ImageCache.getImageLoader(this)

        // Pick the server before the first screen loads: LAN IP when
        // reachable, public domain otherwise (2.5s probe, off main thread)
        com.homeflix.tv.util.ServerConfig.init(this)
        Thread { com.homeflix.tv.util.ServerConfig.probe() }.start()

        // Be castable from the HomeFlix web app as soon as the app process starts.
        com.homeflix.tv.cast.CastReceiverService.start(this)

        // Self-updates from the HomeFlix server (also triggered by a server push).
        val updater = dagger.hilt.android.EntryPointAccessors
            .fromApplication(this, com.homeflix.tv.update.UpdaterEntryPoint::class.java).appUpdater()
        updater.start()
        dagger.hilt.android.EntryPointAccessors
            .fromApplication(this, com.homeflix.tv.cast.CastEntryPoint::class.java)
            .castConnection().onUpdateAvailable = updater::checkNow
    }
    
    override fun newImageLoader(): ImageLoader {
        return com.homeflix.tv.util.ImageCache.getImageLoader(this)
    }
}