package com.iosync.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.iosync.app.data.sync.IoSyncSyncService

/**
 * Startet den Hintergrund-Sync-Service nach einem Geräteneustart automatisch.
 * Erfordert RECEIVE_BOOT_COMPLETED-Permission (bereits im Manifest deklariert).
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            "android.intent.action.QUICKBOOT_POWERON",
            "com.htc.intent.action.QUICKBOOT_POWERON" -> {
                IoSyncSyncService.start(context)
            }
        }
    }
}
