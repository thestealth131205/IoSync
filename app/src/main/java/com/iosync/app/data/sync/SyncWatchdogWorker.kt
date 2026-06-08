package com.iosync.app.data.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/**
 * WorkManager-Watchdog: läuft alle 15 Minuten und stellt sicher, dass der
 * Hintergrund-Sync-Service noch aktiv ist. Falls Android den Service gekillt hat
 * (Akku-Optimierung, App-Standby-Bucket o.ä.), wird er hier neu gestartet.
 *
 * WorkManager überlebt Geräteneumstarts und aggressive Hersteller-Optimierungen
 * besser als ein reiner Foreground-Service.
 */
class SyncWatchdogWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        IoSyncSyncService.start(applicationContext)
        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "iosync_sync_watchdog"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<SyncWatchdogWorker>(15, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}
