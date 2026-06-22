package com.iosync.app.data.geofence

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.location.CancellationToken
import com.google.android.gms.location.CancellationTokenSource
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority

/**
 * Foreground-Service, der die Standort-Vibration (Geofence) am Laufen hält.
 *
 * Nutzt [AlarmManager.setExactAndAllowWhileIdle] um das Gerät auch im Doze-Modus
 * zuverlässig in den konfigurierten Intervallen aufzuwecken. Passive
 * requestLocationUpdates()-Ansätze funktionieren im Doze nicht, da der FusedLocation-
 * Provider ohne aktive GPS-Nutzung anderer Apps keine Events liefert.
 *
 * Ablauf:
 * 1. Service startet → liest Config, zeigt persistente Notification, plant ersten Alarm
 * 2. Alarm feuert → GeofenceAlarmReceiver → ACTION_CHECK_LOCATION an diesen Service
 * 3. Service holt einmalig aktuellen Standort (HIGH_ACCURACY), prüft Geofence, plant nächsten Alarm
 */
class GeofenceService : Service() {

    private val fusedClient by lazy { LocationServices.getFusedLocationProviderClient(this) }
    private var cancellationTokenSource: CancellationTokenSource? = null

    private var targetLat = Double.NaN
    private var targetLon = Double.NaN
    private var targetRadius = 0f
    private var targetAddress = ""
    private var intervalSec = 60

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            cancelAlarm()
            cancellationTokenSource?.cancel()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        if (intent?.action == ACTION_CHECK_LOCATION) {
            // Alarm ausgelöst → Config aus Prefs laden und Standort prüfen
            val savedLat = Double.fromBits(prefs.getLong(PREF_LAT, Double.NaN.toBits()))
            val savedLon = Double.fromBits(prefs.getLong(PREF_LON, Double.NaN.toBits()))
            val savedRadius = prefs.getFloat(PREF_RADIUS, 0f)
            val savedInterval = prefs.getInt(PREF_INTERVAL, 60)
            val savedAddress = prefs.getString(PREF_ADDRESS, "") ?: ""

            if (!savedLat.isNaN() && savedRadius > 0f) {
                targetLat = savedLat
                targetLon = savedLon
                targetRadius = savedRadius
                intervalSec = savedInterval
                targetAddress = savedAddress
                // Sicherstellen dass Foreground läuft (bei START_STICKY-Neustart wichtig)
                startForegroundCompat(savedAddress)
                checkLocationOnce()
                scheduleNextAlarm(savedInterval)
            } else {
                Log.w(TAG, "Keine gespeicherte Config bei Alarm → Service beendet")
                stopSelf()
            }
            return START_STICKY
        }

        // Normaler Start (Intent mit Config-Extras oder START_STICKY-Neustart)
        val intentLat = intent?.getDoubleExtra(EXTRA_LAT, Double.NaN) ?: Double.NaN
        val intentLon = intent?.getDoubleExtra(EXTRA_LON, Double.NaN) ?: Double.NaN
        val intentRadius = intent?.getFloatExtra(EXTRA_RADIUS, 0f) ?: 0f
        val intentInterval = intent?.getIntExtra(EXTRA_INTERVAL_SEC, 0) ?: 0
        val intentAddress = intent?.getStringExtra(EXTRA_ADDRESS) ?: ""

        val hasIntentConfig = !intentLat.isNaN() && !intentLon.isNaN() && intentRadius > 0f

        if (hasIntentConfig) {
            val clampedInterval = intentInterval.coerceAtLeast(15)
            prefs.edit()
                .putLong(PREF_LAT, intentLat.toBits())
                .putLong(PREF_LON, intentLon.toBits())
                .putFloat(PREF_RADIUS, intentRadius)
                .putInt(PREF_INTERVAL, clampedInterval)
                .putString(PREF_ADDRESS, intentAddress)
                .apply()
            targetLat = intentLat
            targetLon = intentLon
            targetRadius = intentRadius
            intervalSec = clampedInterval
            targetAddress = intentAddress
            startForegroundCompat(intentAddress)
            // Sofort ersten Check + ersten Alarm starten
            checkLocationOnce()
            scheduleNextAlarm(clampedInterval)
        } else {
            // START_STICKY-Neustart: Config aus Prefs
            val savedLat = Double.fromBits(prefs.getLong(PREF_LAT, Double.NaN.toBits()))
            val savedLon = Double.fromBits(prefs.getLong(PREF_LON, Double.NaN.toBits()))
            val savedRadius = prefs.getFloat(PREF_RADIUS, 0f)
            val savedInterval = prefs.getInt(PREF_INTERVAL, 60)
            val savedAddress = prefs.getString(PREF_ADDRESS, "") ?: ""

            if (!savedLat.isNaN() && savedRadius > 0f) {
                targetLat = savedLat
                targetLon = savedLon
                targetRadius = savedRadius
                intervalSec = savedInterval
                targetAddress = savedAddress
                startForegroundCompat(savedAddress)
                checkLocationOnce()
                scheduleNextAlarm(savedInterval)
                Log.d(TAG, "Service nach Neustart wiederhergestellt: lat=$savedLat, lon=$savedLon, r=${savedRadius}m, intervall=${savedInterval}s")
            } else {
                Log.w(TAG, "Kein gespeicherter Geofence – Service beendet")
                stopSelf()
                return START_NOT_STICKY
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        cancelAlarm()
        cancellationTokenSource?.cancel()
        super.onDestroy()
    }

    /**
     * Holt einmalig den aktuellen Standort mit HIGH_ACCURACY (GPS + Netz).
     * Im Gegensatz zu requestLocationUpdates wartet getCurrentLocation() aktiv auf
     * einen frischen Fix und funktioniert auch ohne laufende GPS-Sessions anderer Apps.
     */
    private fun checkLocationOnce() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Log.w(TAG, "Standort-Berechtigung fehlt – Check übersprungen")
            return
        }
        cancellationTokenSource?.cancel()
        val cts = CancellationTokenSource()
        cancellationTokenSource = cts

        fusedClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.token)
            .addOnSuccessListener { location: Location? ->
                if (location != null) {
                    handleLocation(location)
                } else {
                    Log.w(TAG, "getCurrentLocation lieferte null (kein Fix)")
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "getCurrentLocation fehlgeschlagen: ${e.message}")
            }
    }

    /**
     * Plant den nächsten Alarm via AlarmManager.setExactAndAllowWhileIdle().
     * Diese Variante wacht das Gerät auch im Doze-Modus auf (im Gegensatz zu setRepeating
     * oder setInexactRepeating, die im Doze gebündelt/verschoben werden).
     */
    private fun scheduleNextAlarm(intervalSec: Int) {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val triggerAt = SystemClock.elapsedRealtime() + intervalSec * 1000L
        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.ELAPSED_REALTIME_WAKEUP,
            triggerAt,
            getAlarmPendingIntent()
        )
        Log.d(TAG, "Nächster Alarm in ${intervalSec}s geplant")
    }

    private fun cancelAlarm() {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(getAlarmPendingIntent())
        Log.d(TAG, "Alarm abgebrochen")
    }

    private fun getAlarmPendingIntent(): PendingIntent {
        val intent = Intent(this, GeofenceAlarmReceiver::class.java).apply {
            action = ACTION_CHECK_LOCATION
        }
        return PendingIntent.getBroadcast(
            this,
            ALARM_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
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
        private const val ALARM_REQUEST_CODE = 7421

        const val ACTION_START = "com.iosync.app.START_GEOFENCE"
        const val ACTION_STOP = "com.iosync.app.STOP_GEOFENCE"
        const val ACTION_CHECK_LOCATION = "com.iosync.app.CHECK_GEOFENCE_LOCATION"
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
