package com.iosync.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.iosync.app.data.geofence.GeofenceService
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
                // GeofenceService neu starten – lädt gespeicherte Config aus eigenen Prefs.
                // System-Geofences werden bei Reboot vom OS gelöscht; der GeofenceService
                // übernimmt die Überwachung per AlarmManager-Polling (kein Neustart der App nötig).
                // Gibt es keine gespeicherte Config, beendet sich der Service selbst.
                context.startForegroundService(Intent(context, GeofenceService::class.java))
            }
        }
    }
}
