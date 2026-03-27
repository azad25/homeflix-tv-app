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
    }
    
    override fun newImageLoader(): ImageLoader {
        return com.homeflix.tv.util.ImageCache.getImageLoader(this)
    }
}