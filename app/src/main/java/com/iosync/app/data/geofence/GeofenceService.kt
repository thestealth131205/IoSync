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
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority

/**
 * Foreground-Service, der die Standort-Vibration (Geofence) am Laufen hält.
 *
 * Nutzt [com.google.android.gms.location.FusedLocationProviderClient.requestLocationUpdates]
 * mit einem [LocationCallback] – zuverlässiger als einzelne getCurrentLocation()-Aufrufe,
 * die im Doze-Modus oder bei inaktivem GPS häufig null liefern.
 *
 * Die Config (lat/lon/radius/interval/address) wird in SharedPreferences gespeichert, damit
 * der Service nach einem System-Neustart via START_STICKY die Parameter wiederherstellen kann
 * (intent ist dann null und die Extras fehlen sonst).
 */
class GeofenceService : Service() {

    private val fusedClient by lazy { LocationServices.getFusedLocationProviderClient(this) }

    private var targetLat = Double.NaN
    private var targetLon = Double.NaN
    private var targetRadius = 0f
    private var targetAddress = ""

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val location = result.lastLocation ?: return
            handleLocation(location)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            fusedClient.removeLocationUpdates(locationCallback)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        // Intent-Extras auslesen (null bei START_STICKY-Neustart durch das System)
        val intentLat = intent?.getDoubleExtra(EXTRA_LAT, Double.NaN) ?: Double.NaN
        val intentLon = intent?.getDoubleExtra(EXTRA_LON, Double.NaN) ?: Double.NaN
        val intentRadius = intent?.getFloatExtra(EXTRA_RADIUS, 0f) ?: 0f
        val intentInterval = intent?.getIntExtra(EXTRA_INTERVAL_SEC, 0) ?: 0
        val intentAddress = intent?.getStringExtra(EXTRA_ADDRESS) ?: ""

        val hasIntentConfig = !intentLat.isNaN() && !intentLon.isNaN() && intentRadius > 0f

        if (hasIntentConfig) {
            // Neue Config aus Intent → in Prefs persistieren damit START_STICKY-Neustart klappt
            prefs.edit()
                .putLong(PREF_LAT, intentLat.toBits())
                .putLong(PREF_LON, intentLon.toBits())
                .putFloat(PREF_RADIUS, intentRadius)
                .putInt(PREF_INTERVAL, intentInterval.coerceAtLeast(15))
                .putString(PREF_ADDRESS, intentAddress)
                .apply()
            targetLat = intentLat
            targetLon = intentLon
            targetRadius = intentRadius
            targetAddress = intentAddress
            startForegroundCompat(intentAddress)
            startLocationUpdates(intentInterval)
        } else {
            // START_STICKY-Neustart: Config aus Prefs wiederherstellen
            val savedLat = Double.fromBits(prefs.getLong(PREF_LAT, Double.NaN.toBits()))
            val savedLon = Double.fromBits(prefs.getLong(PREF_LON, Double.NaN.toBits()))
            val savedRadius = prefs.getFloat(PREF_RADIUS, 0f)
            val savedInterval = prefs.getInt(PREF_INTERVAL, 60)
            val savedAddress = prefs.getString(PREF_ADDRESS, "") ?: ""

            if (!savedLat.isNaN() && !savedLon.isNaN() && savedRadius > 0f) {
                targetLat = savedLat
                targetLon = savedLon
                targetRadius = savedRadius
                targetAddress = savedAddress
                startForegroundCompat(savedAddress)
                startLocationUpdates(savedInterval)
                Log.d(TAG, "Service nach Neustart wiederhergestellt: lat=$savedLat, lon=$savedLon, r=${savedRadius}m, intervall=${savedInterval}s")
            } else {
                // Keine gespeicherte Config – Service beenden
                Log.w(TAG, "Kein gespeicherter Geofence – Service beendet")
                stopSelf()
                return START_NOT_STICKY
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        fusedClient.removeLocationUpdates(locationCallback)
        super.onDestroy()
    }

    /**
     * Registriert wiederkehrende Standort-Updates über den FusedLocationProviderClient.
     * Im Gegensatz zu einzelnen getCurrentLocation()-Aufrufen liefert requestLocationUpdates
     * auch im Hintergrund und bei inaktivem GPS-Chip zuverlässig Ergebnisse (Netz-/Wifi-Fix).
     */
    private fun startLocationUpdates(intervalSec: Int) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Log.w(TAG, "Standort-Berechtigung fehlt – Service beendet")
            stopSelf()
            return
        }
        val intervalMs = intervalSec.coerceAtLeast(15) * 1000L
        val request = LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, intervalMs)
            .setMinUpdateIntervalMillis(intervalMs / 2)
            .setMaxUpdateDelayMillis(intervalMs * 2)
            .build()
        try {
            fusedClient.removeLocationUpdates(locationCallback)
            fusedClient.requestLocationUpdates(request, locationCallback, Looper.getMainLooper())
            Log.d(TAG, "LocationUpdates registriert: Intervall=${intervalSec}s")
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException bei requestLocationUpdates: ${e.message}")
            stopSelf()
        }
    }

    private fun handleLocation(location: Location) {
        GeofenceNotifications.notifyLocationUpdated(this, targetAddress)
        val results = FloatArray(1)
        Location.distanceBetween(location.latitude, location.longitude, targetLat, targetLon, results)
        val inside = results[0] <= targetRadius
        Log.d(TAG, "Standort: Distanz=${results[0].toInt()}m, Radius=${targetRadius.toInt()}m, drin=$inside")
        GeofenceVibration.applyState(this, inside, targetAddress)
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
        private const val PREFS_NAME = "geofence_service_prefs"
        private const val PREF_LAT = "lat_bits"
        private const val PREF_LON = "lon_bits"
        private const val PREF_RADIUS = "radius"
        private const val PREF_INTERVAL = "interval_sec"
        private const val PREF_ADDRESS = "address"

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
