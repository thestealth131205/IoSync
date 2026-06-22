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
const val KEY_GEOFENCE_ADDRESS_DISPLAY = "geofence_address_display"

class GeofenceTransitionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val geofencingEvent = GeofencingEvent.fromIntent(intent) ?: return
        if (geofencingEvent.hasError()) {
            Log.e(TAG, "GeofencingEvent Fehler: ${geofencingEvent.errorCode}")
            return
        }

        val prefs = context.getSharedPreferences(GEOFENCE_PREFS_NAME, Context.MODE_PRIVATE)
        val address = prefs.getString(KEY_GEOFENCE_ADDRESS_DISPLAY, "") ?: ""
        val transition = geofencingEvent.geofenceTransition

        // Notification 2: der GPS-Standort wurde gerade neu abgeglichen.
        GeofenceNotifications.notifyLocationUpdated(context, address)

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val hasDndAccess = notificationManager.isNotificationPolicyAccessGranted
        if (!hasDndAccess) {
            Log.w(TAG, "Kein DND-Zugriff gewährt – Klingelmodus wird nicht geändert")
        }

        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

        when (transition) {
            Geofence.GEOFENCE_TRANSITION_ENTER -> {
                if (hasDndAccess) {
                    val currentMode = audioManager.ringerMode
                    prefs.edit()
                        .putInt(KEY_PREVIOUS_RINGER_MODE, currentMode)
                        .putBoolean(KEY_INSIDE_GEOFENCE, true)
                        .apply()
                    audioManager.ringerMode = AudioManager.RINGER_MODE_VIBRATE
                    Log.d(TAG, "Geofence betreten – Klingelmodus auf Vibration (vorher: $currentMode)")
                } else {
                    prefs.edit().putBoolean(KEY_INSIDE_GEOFENCE, true).apply()
                }
                // Notification 3: man befindet sich innerhalb des Bereichs.
                GeofenceNotifications.notifyRegionStatus(context, inside = true, address = address)
            }
            Geofence.GEOFENCE_TRANSITION_EXIT -> {
                if (hasDndAccess) {
                    val previousMode = prefs.getInt(KEY_PREVIOUS_RINGER_MODE, AudioManager.RINGER_MODE_NORMAL)
                    prefs.edit().putBoolean(KEY_INSIDE_GEOFENCE, false).apply()
                    audioManager.ringerMode = previousMode
                    Log.d(TAG, "Geofence verlassen – Klingelmodus wiederhergestellt: $previousMode")
                } else {
                    prefs.edit().putBoolean(KEY_INSIDE_GEOFENCE, false).apply()
                }
                // Notification 3: man befindet sich außerhalb des Bereichs.
                GeofenceNotifications.notifyRegionStatus(context, inside = false, address = address)
            }
        }
    }
}
