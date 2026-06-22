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
const val KEY_INSIDE_GEOFENCE = "inside_geofence"
const val KEY_GEOFENCE_ADDRESS_DISPLAY = "geofence_address_display"

/**
 * Gemeinsame Logik für das Umschalten des Klingelmodus + Innerhalb/Außerhalb-
 * Notification. Wird sowohl vom [GeofenceTransitionReceiver] (System-Geofence-
 * Übergänge) als auch vom aktiven Standort-Polling im [GeofenceService] genutzt,
 * damit beide Pfade denselben Zustand führen.
 */
object GeofenceVibration {

    /**
     * Wendet den Bereichs-Status an. Tut nur etwas, wenn sich der Zustand
     * gegenüber dem zuletzt gespeicherten Wert ([KEY_INSIDE_GEOFENCE]) geändert
     * hat – so feuern Polling und System-Geofence nicht doppelt.
     */
    fun applyState(context: Context, inside: Boolean, address: String) {
        val prefs = context.getSharedPreferences(GEOFENCE_PREFS_NAME, Context.MODE_PRIVATE)
        val wasInside = if (prefs.contains(KEY_INSIDE_GEOFENCE))
            prefs.getBoolean(KEY_INSIDE_GEOFENCE, false) else null
        if (wasInside == inside) return

        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val hasDndAccess = notificationManager.isNotificationPolicyAccessGranted
        if (!hasDndAccess) {
            Log.w(TAG, "Kein DND-Zugriff gewährt – Klingelmodus wird nicht geändert")
        }
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

        if (inside) {
            if (hasDndAccess) {
                prefs.edit().putBoolean(KEY_INSIDE_GEOFENCE, true).apply()
                audioManager.ringerMode = AudioManager.RINGER_MODE_VIBRATE
                Log.d(TAG, "Geofence betreten – Klingelmodus auf Vibration")
            } else {
                prefs.edit().putBoolean(KEY_INSIDE_GEOFENCE, true).apply()
            }
            GeofenceNotifications.notifyRegionStatus(context, inside = true, address = address)
        } else {
            if (hasDndAccess) {
                prefs.edit().putBoolean(KEY_INSIDE_GEOFENCE, false).apply()
                audioManager.ringerMode = AudioManager.RINGER_MODE_NORMAL
                Log.d(TAG, "Geofence verlassen – Klingelmodus auf Normal")
            } else {
                prefs.edit().putBoolean(KEY_INSIDE_GEOFENCE, false).apply()
            }
            GeofenceNotifications.notifyRegionStatus(context, inside = false, address = address)
        }
    }
}

class GeofenceTransitionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val geofencingEvent = GeofencingEvent.fromIntent(intent) ?: return
        if (geofencingEvent.hasError()) {
            Log.e(TAG, "GeofencingEvent Fehler: ${geofencingEvent.errorCode}")
            return
        }

        val prefs = context.getSharedPreferences(GEOFENCE_PREFS_NAME, Context.MODE_PRIVATE)
        val address = prefs.getString(KEY_GEOFENCE_ADDRESS_DISPLAY, "") ?: ""

        // Notification 2: der GPS-Standort wurde gerade neu abgeglichen.
        GeofenceNotifications.notifyLocationUpdated(context, address)

        when (geofencingEvent.geofenceTransition) {
            Geofence.GEOFENCE_TRANSITION_ENTER ->
                GeofenceVibration.applyState(context, inside = true, address = address)
            Geofence.GEOFENCE_TRANSITION_EXIT ->
                GeofenceVibration.applyState(context, inside = false, address = address)
        }
    }
}
