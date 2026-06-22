package com.iosync.app.data.geofence

import android.Manifest
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Foreground-Service, der die Standort-Vibration (Geofence) am Laufen hält.
 *
 * Zwei Mechanismen greifen ineinander:
 *  1. Die System-Geofencing-API ([GeofenceManager]) liefert sofortige Übergänge an
 *     den [GeofenceTransitionReceiver] – ist aber stark akku-optimiert und fragt den
 *     Standort NICHT zuverlässig im eingestellten Intervall ab.
 *  2. Dieser Service fragt den GPS-Standort daher zusätzlich AKTIV im konfigurierten
 *     Intervall via [com.google.android.gms.location.FusedLocationProviderClient] ab,
 *     vergleicht ihn koordinatenbasiert mit dem Zielbereich und stößt selbst die
 *     Notifications + den Klingelmodus-Wechsel an. So wird der Standort garantiert im
 *     gewählten Intervall geprüft.
 */
class GeofenceService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var pollJob: Job? = null
    private val fusedClient by lazy { LocationServices.getFusedLocationProviderClient(this) }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                pollJob?.cancel()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            else -> {
                val address = intent?.getStringExtra(EXTRA_ADDRESS) ?: ""
                val lat = intent?.getDoubleExtra(EXTRA_LAT, Double.NaN) ?: Double.NaN
                val lon = intent?.getDoubleExtra(EXTRA_LON, Double.NaN) ?: Double.NaN
                val radius = intent?.getFloatExtra(EXTRA_RADIUS, 0f) ?: 0f
                val intervalSec = intent?.getIntExtra(EXTRA_INTERVAL_SEC, 60) ?: 60
                try {
                    startForegroundCompat(address)
                } catch (e: Exception) {
                    Log.w(TAG, "Foreground-Start fehlgeschlagen: ${e.message}")
                    stopSelf()
                    return START_STICKY
                }
                if (!lat.isNaN() && !lon.isNaN() && radius > 0f) {
                    startPolling(lat, lon, radius, intervalSec, address)
                }
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        pollJob?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    /** Startet die aktive, intervallbasierte Standortabfrage. */
    private fun startPolling(
        lat: Double,
        lon: Double,
        radius: Float,
        intervalSec: Int,
        address: String
    ) {
        pollJob?.cancel()
        val intervalMs = intervalSec.coerceAtLeast(15) * 1000L
        pollJob = scope.launch {
            while (isActive) {
                pollOnce(lat, lon, radius, address)
                delay(intervalMs)
            }
        }
    }

    /** Fordert EINEN frischen GPS-Fix an und gleicht ihn mit dem Zielbereich ab. */
    private suspend fun pollOnce(lat: Double, lon: Double, radius: Float, address: String) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Log.w(TAG, "Standort-Berechtigung fehlt – Polling übersprungen")
            return
        }
        val location: Location? = try {
            fusedClient.getCurrentLocation(
                Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                CancellationTokenSource().token
            ).await()
        } catch (e: SecurityException) {
            Log.w(TAG, "Standortabfrage ohne Berechtigung: ${e.message}")
            null
        } catch (e: Exception) {
            Log.w(TAG, "Standortabfrage fehlgeschlagen: ${e.message}")
            null
        }
        if (location == null) return

        // Notification 2: der GPS-Standort wurde gerade neu abgeglichen.
        GeofenceNotifications.notifyLocationUpdated(this, address)

        val results = FloatArray(1)
        Location.distanceBetween(location.latitude, location.longitude, lat, lon, results)
        val inside = results[0] <= radius
        Log.d(TAG, "Polling: Distanz=${results[0].toInt()}m, Radius=${radius.toInt()}m, drin=$inside")
        GeofenceVibration.applyState(this, inside, address)
    }

    private fun startForegroundCompat(address: String) {
        val notification = GeofenceNotifications.buildPersistent(this, address)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                GeofenceNotifications.NOTIFICATION_ID_PERSISTENT,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            )
        } else {
            startForeground(GeofenceNotifications.NOTIFICATION_ID_PERSISTENT, notification)
        }
    }

    companion object {
        private const val TAG = "GeofenceService"
        const val ACTION_START = "com.iosync.app.START_GEOFENCE"
        const val ACTION_STOP = "com.iosync.app.STOP_GEOFENCE"
        const val EXTRA_ADDRESS = "geofence_address"
        const val EXTRA_LAT = "geofence_lat"
        const val EXTRA_LON = "geofence_lon"
        const val EXTRA_RADIUS = "geofence_radius"
        const val EXTRA_INTERVAL_SEC = "geofence_interval_sec"

        fun start(
            context: Context,
            address: String,
            lat: Double,
            lon: Double,
            radiusMeters: Float,
            intervalSec: Int
        ) {
            context.startForegroundService(Intent(context, GeofenceService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_ADDRESS, address)
                putExtra(EXTRA_LAT, lat)
                putExtra(EXTRA_LON, lon)
                putExtra(EXTRA_RADIUS, radiusMeters)
                putExtra(EXTRA_INTERVAL_SEC, intervalSec)
            })
        }

        fun stop(context: Context) {
            context.startService(Intent(context, GeofenceService::class.java).apply {
                action = ACTION_STOP
            })
        }
    }
}
