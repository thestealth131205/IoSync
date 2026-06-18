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
const val GEOFENCE_ID = "iosync_standort_geofence"

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

    suspend fun addGeofence(
        lat: Double,
        lon: Double,
        radiusMeters: Float,
        responsivenessMs: Int = 60_000
    ): Result<Unit> {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return Result.failure(SecurityException("Standort-Berechtigung fehlt"))
        }

        val geofence = Geofence.Builder()
            .setRequestId(GEOFENCE_ID)
            .setCircularRegion(lat, lon, radiusMeters)
            .setExpirationDuration(Geofence.NEVER_EXPIRE)
            // Steuert, in welchen zeitlichen Intervallen das System prüft, ob sich
            // das Gerät innerhalb/außerhalb des Bereichs befindet. Größere Werte
            // sparen Akku (System darf Prüfungen bündeln), kleinere reagieren schneller.
            .setNotificationResponsiveness(responsivenessMs)
            .setTransitionTypes(
                Geofence.GEOFENCE_TRANSITION_ENTER or Geofence.GEOFENCE_TRANSITION_EXIT
            )
            .build()

        val request = GeofencingRequest.Builder()
            .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
            .addGeofence(geofence)
            .build()

        return try {
            // Vorherigen Geofence zuerst entfernen (falls vorhanden)
            runCatching { geofencingClient.removeGeofences(listOf(GEOFENCE_ID)).await() }
            geofencingClient.addGeofences(request, pendingIntent).await()
            Log.d(TAG, "Geofence registriert: lat=$lat, lon=$lon, radius=${radiusMeters}m")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Geofence konnte nicht hinzugefügt werden", e)
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
