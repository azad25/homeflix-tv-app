package com.homeflix.tv

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import coil.ImageLoader
import coil.ImageLoaderFactory

@HiltAndroidApp
class HomeFlixTVApplication : Application(), ImageLoaderFactory {
    
    override fun onCreate() {
        super.onCreate()

        // Initialize image cache on app startup
        com.homeflix.tv.util.ImageCache.getImageLoader(this)

        // Pick the server before the first screen loads: LAN IP when
        // reachable, public domain otherwise (2.5s probe, off main thread)
        Thread { com.homeflix.tv.util.ServerConfig.probe() }.start()
    }
    
    override fun newImageLoader(): ImageLoader {
        return com.homeflix.tv.util.ImageCache.getImageLoader(this)
    }
}