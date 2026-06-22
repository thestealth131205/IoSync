package com.iosync.app.data.geofence

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Empfängt den AlarmManager-Alarm und delegiert den Standort-Check an den GeofenceService.
 * Der AlarmManager nutzt setExactAndAllowWhileIdle() → wacht auch im Doze-Modus auf.
 */
class GeofenceAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == GeofenceService.ACTION_CHECK_LOCATION) {
            Log.d("GeofenceAlarmReceiver", "Alarm ausgelöst → Standort-Check starten")
            val serviceIntent = Intent(context, GeofenceService::class.java).apply {
                action = GeofenceService.ACTION_CHECK_LOCATION
            }
            context.startForegroundService(serviceIntent)
        }
    }
}
