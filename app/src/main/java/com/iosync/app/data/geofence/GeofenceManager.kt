package com.iosync.app.data.geofence

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "GeofenceManager"

@Singleton
class GeofenceManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val geofencingClient = LocationServices.getGeofencingClient(context)

    private val pendingIntent: PendingIntent by lazy {
        val intent = Intent(context, GeofenceTransitionReceiver::class.java)
        PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
    }

    /**
     * Registriert die übergebene Standort-Liste als System-Geofences (je Standort
     * ein Geofence mit der Standort-ID als RequestId). Eine leere Liste entfernt
     * alle bestehenden Geofences.
     */
    suspend fun setGeofences(
        locations: List<GeofenceLocation>,
        responsivenessMs: Int = 60_000
    ): Result<Unit> {
        if (locations.isEmpty()) return removeGeofence()

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return Result.failure(SecurityException("Standort-Berechtigung fehlt"))
        }

        val geofences = locations.map { loc ->
            Geofence.Builder()
                .setRequestId(loc.id)
                .setCircularRegion(loc.lat, loc.lon, loc.radiusMeters.toFloat())
                .setExpirationDuration(Geofence.NEVER_EXPIRE)
                // Steuert, in welchen zeitlichen Intervallen das System prüft, ob sich
                // das Gerät innerhalb/außerhalb des Bereichs befindet. Größere Werte
                // sparen Akku (System darf Prüfungen bündeln), kleinere reagieren schneller.
                .setNotificationResponsiveness(responsivenessMs)
                .setTransitionTypes(
                    Geofence.GEOFENCE_TRANSITION_ENTER or Geofence.GEOFENCE_TRANSITION_EXIT
                )
                .build()
        }

        val request = GeofencingRequest.Builder()
            .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
            .addGeofences(geofences)
            .build()

        return try {
            // Alle vorherigen Geofences zuerst entfernen
            runCatching { geofencingClient.removeGeofences(pendingIntent).await() }
            geofencingClient.addGeofences(request, pendingIntent).await()
            Log.d(TAG, "${geofences.size} Geofence(s) registriert")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Geofences konnten nicht hinzugefügt werden", e)
            Result.failure(e)
        }
    }

    suspend fun removeGeofence(): Result<Unit> {
        return try {
            geofencingClient.removeGeofences(pendingIntent).await()
            Log.d(TAG, "Geofence entfernt")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Geofence konnte nicht entfernt werden", e)
            Result.failure(e)
        }
    }
}
