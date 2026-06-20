package com.iosync.app.data.geofence

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.util.Log
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent

private const val TAG = "GeofenceReceiver"
const val GEOFENCE_PREFS_NAME = "iosync_geofence_prefs"
const val KEY_PREVIOUS_RINGER_MODE = "previous_ringer_mode"
const val KEY_INSIDE_GEOFENCE = "inside_geofence"

class GeofenceTransitionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val geofencingEvent = GeofencingEvent.fromIntent(intent) ?: return
        if (geofencingEvent.hasError()) {
            Log.e(TAG, "GeofencingEvent Fehler: ${geofencingEvent.errorCode}")
            return
        }

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (!notificationManager.isNotificationPolicyAccessGranted) {
            Log.w(TAG, "Kein DND-Zugriff gewährt – Klingelmodus wird nicht geändert")
            return
        }

        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val prefs = context.getSharedPreferences(GEOFENCE_PREFS_NAME, Context.MODE_PRIVATE)

        when (geofencingEvent.geofenceTransition) {
            Geofence.GEOFENCE_TRANSITION_ENTER -> {
                val currentMode = audioManager.ringerMode
                prefs.edit()
                    .putInt(KEY_PREVIOUS_RINGER_MODE, currentMode)
                    .putBoolean(KEY_INSIDE_GEOFENCE, true)
                    .apply()
                audioManager.ringerMode = AudioManager.RINGER_MODE_VIBRATE
                Log.d(TAG, "Geofence betreten – Klingelmodus auf Vibration (vorher: $currentMode)")
            }
            Geofence.GEOFENCE_TRANSITION_EXIT -> {
                val previousMode = prefs.getInt(KEY_PREVIOUS_RINGER_MODE, AudioManager.RINGER_MODE_NORMAL)
                prefs.edit().putBoolean(KEY_INSIDE_GEOFENCE, false).apply()
                audioManager.ringerMode = previousMode
                Log.d(TAG, "Geofence verlassen – Klingelmodus wiederhergestellt: $previousMode")
            }
        }
    }
}
