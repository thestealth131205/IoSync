package com.iosync.app.data.geofence

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log

/**
 * Foreground-Service, der die Standort-Vibration (Geofence) am Laufen hält.
 *
 * Android löscht registrierte Geofences beim Geräte-Neustart oder wenn der
 * App-Prozess vom System beendet wird. Dieser Service zeigt eine persistente
 * Notification und hält damit einen lebenden Prozess, solange die Funktion aktiv
 * ist – die eigentlichen Übergänge liefert weiterhin die System-Geofencing-API an
 * den [GeofenceTransitionReceiver].
 */
class GeofenceService : Service() {

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            else -> {
                val address = intent?.getStringExtra(EXTRA_ADDRESS) ?: ""
                try {
                    startForegroundCompat(address)
                } catch (e: Exception) {
                    Log.w(TAG, "Foreground-Start fehlgeschlagen: ${e.message}")
                    stopSelf()
                }
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

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

        fun start(context: Context, address: String) {
            context.startForegroundService(Intent(context, GeofenceService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_ADDRESS, address)
            })
        }

        fun stop(context: Context) {
            context.startService(Intent(context, GeofenceService::class.java).apply {
                action = ACTION_STOP
            })
        }
    }
}
