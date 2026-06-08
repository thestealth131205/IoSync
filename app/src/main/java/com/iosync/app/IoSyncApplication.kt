package com.iosync.app

import android.app.Application
import com.iosync.app.data.crash.CrashLogManager
import com.iosync.app.data.sync.SyncWatchdogWorker
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class IoSyncApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        installCrashHandler()
        SyncWatchdogWorker.schedule(this)
    }

    private fun installCrashHandler() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                CrashLogManager.writeCrashLog(applicationContext, thread, throwable)
            } finally {
                defaultHandler?.uncaughtException(thread, throwable)
            }
        }
    }
}
