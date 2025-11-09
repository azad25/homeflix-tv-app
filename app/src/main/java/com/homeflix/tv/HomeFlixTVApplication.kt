package com.homeflix.tv

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class HomeFlixTVApplication : Application() {
    
    override fun onCreate() {
        super.onCreate()
    }
}