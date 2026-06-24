package com.iosync.app.data.geofence

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.location.Location
import android.media.AudioManager
import android.util.Log
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent

private const val TAG = "GeofenceReceiver"
const val GEOFENCE_PREFS_NAME = "iosync_geofence_prefs"
const val KEY_INSIDE_GEOFENCE = "inside_geofence"
/** JSON-Liste aller überwachten Standorte (von der App geschrieben). */
const val KEY_GEOFENCE_LOCATIONS_JSON = "geofence_locations_json"

/**
 * Gemeinsame Logik für das Umschalten des Klingelmodus + Innerhalb/Außerhalb-
 * Notification bei mehreren Standorten. Wird sowohl vom [GeofenceTransitionReceiver]
 * (System-Geofence-Übergänge) als auch vom aktiven Standort-Polling im
 * [GeofenceService] genutzt, damit beide Pfade denselben Zustand führen.
 *
 * Pro Standort wird der Innerhalb-Status separat unter [KEY_INSIDE_PREFIX]`<id>`
 * gespeichert (für die UI-Anzeige). Der Klingelmodus wird global geschaltet:
 * Vibration sobald man sich in IRGENDEINEM Bereich befindet, Normal sobald man
 * alle Bereiche verlassen hat.
 */
object GeofenceVibration {

    /** Prefix für den Innerhalb-Status pro Standort-ID. */
    const val KEY_INSIDE_PREFIX = "inside_loc_"

    /**
     * Bewertet alle Standorte gegen die aktuelle Position und wendet das Ergebnis an.
     */
    fun evaluateAll(
        context: Context,
        locations: List<GeofenceLocation>,
        currentLat: Double,
        currentLon: Double
    ) {
        val insideById = locations.associate { loc ->
            val results = FloatArray(1)
            Location.distanceBetween(currentLat, currentLon, loc.lat, loc.lon, results)
            loc.id to (results[0] <= loc.radiusMeters)
        }
        applyInsideMap(context, locations, insideById)
    }

    /**
     * Wendet einen (ggf. teilweisen) Innerhalb-Status pro Standort an. Standorte
     * ohne Eintrag in [insideById] behalten ihren zuletzt gespeicherten Status.
     * Notifications feuern nur bei tatsächlichem Statuswechsel; der Klingelmodus
     * wird nur umgeschaltet, wenn sich der globale Status (in irgendeinem Bereich)
     * geändert hat.
     */
    fun applyInsideMap(
        context: Context,
        locations: List<GeofenceLocation>,
        insideById: Map<String, Boolean>
    ) {
        val prefs = context.getSharedPreferences(GEOFENCE_PREFS_NAME, Context.MODE_PRIVATE)
        val editor = prefs.edit()
        var anyInside = false
        for (loc in locations) {
            val key = KEY_INSIDE_PREFIX + loc.id
            val inside = insideById[loc.id]
                ?: (if (prefs.contains(key)) prefs.getBoolean(key, false) else false)
            if (inside) anyInside = true
            val wasInside = if (prefs.contains(key)) prefs.getBoolean(key, false) else null
            if (wasInside != inside) {
                editor.putBoolean(key, inside)
                GeofenceNotifications.notifyRegionStatus(context, inside = inside, address = loc.address)
            }
        }
        editor.apply()
        applyGlobalRinger(context, anyInside)
    }

    private fun applyGlobalRinger(context: Context, anyInside: Boolean) {
        val prefs = context.getSharedPreferences(GEOFENCE_PREFS_NAME, Context.MODE_PRIVATE)
        val wasInside = if (prefs.contains(KEY_INSIDE_GEOFENCE))
            prefs.getBoolean(KEY_INSIDE_GEOFENCE, false) else null
        if (wasInside == anyInside) return

        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val hasDndAccess = notificationManager.isNotificationPolicyAccessGranted
        if (!hasDndAccess) {
            Log.w(TAG, "Kein DND-Zugriff gewährt – Klingelmodus wird nicht geändert")
        }
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

        prefs.edit().putBoolean(KEY_INSIDE_GEOFENCE, anyInside).apply()
        if (hasDndAccess) {
            audioManager.ringerMode = if (anyInside)
                AudioManager.RINGER_MODE_VIBRATE else AudioManager.RINGER_MODE_NORMAL
            Log.d(TAG, if (anyInside) "In einem Bereich – Klingelmodus auf Vibration"
                       else "Alle Bereiche verlassen – Klingelmodus auf Normal")
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
        val locations = GeofenceLocation.listFromJson(prefs.getString(KEY_GEOFENCE_LOCATIONS_JSON, null))
        if (locations.isEmpty()) return

        // Notification: der GPS-Standort wurde gerade neu abgeglichen.
        GeofenceNotifications.notifyLocationUpdated(context, "")

        val triggerLoc = geofencingEvent.triggeringLocation
        if (triggerLoc != null) {
            // Vollständige Neubewertung aller Standorte gegen die Trigger-Position.
            GeofenceVibration.evaluateAll(context, locations, triggerLoc.latitude, triggerLoc.longitude)
        } else {
            // Fallback ohne Position: nur die ausgelösten Standorte aktualisieren.
            val enter = geofencingEvent.geofenceTransition == Geofence.GEOFENCE_TRANSITION_ENTER
            val triggeredIds = geofencingEvent.triggeringGeofences?.map { it.requestId } ?: emptyList()
            val insideById = triggeredIds.associateWith { enter }
            GeofenceVibration.applyInsideMap(context, locations, insideById)
        }
    }
}
