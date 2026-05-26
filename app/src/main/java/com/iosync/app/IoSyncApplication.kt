package com.iosync.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class IoSyncApplication : Application() {

    override fun onCreate() {
        super.onCreate()
    }
}
