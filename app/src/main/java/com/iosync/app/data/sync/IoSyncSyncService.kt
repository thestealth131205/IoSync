package com.iosync.app.data.sync

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.iosync.app.MainActivity
import com.iosync.app.R
import com.iosync.app.ui.viewmodel.MainViewModel
import com.iosync.app.data.geofence.GeofenceManager
import com.iosync.app.wear.WearDataLayerService
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Foreground-Service für den Handy-Akku-Push ans Watchface.
 *
 * Ab v5 fragt die Uhr alle übrigen Daten selbst ab (ioBroker-Datenpunkte, Wetter,
 * Health – siehe Watchface-Modul `WatchDataSyncManager`). Das Handy überträgt nur
 * noch seinen eigenen Akkustand, den die Uhr naturgemäß nicht selbst kennt.
 *
 * Der Service läuft als Foreground-Service unabhängig von der App-UI weiter, damit
 * der Akku-Ring auf der Uhr auch bei geschlossener App aktuell bleibt.
 */
@AndroidEntryPoint
class IoSyncSyncService : Service() {

    @Inject lateinit var wearDataLayerService: WearDataLayerService
    @Inject lateinit var dataStore: DataStore<Preferences>
    @Inject lateinit var geofenceManager: GeofenceManager

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var batteryJob: Job? = null

    // Event-basierter Akku-Empfänger: pusht den Handy-Akku sofort bei jeder Änderung
    // (Ladezustand / Prozent), unabhängig vom Polling-Intervall.
    private var batteryReceiver: android.content.BroadcastReceiver? = null
    private var lastBatteryPercent: Int = -1
    private var lastBatteryCharging: Boolean? = null

    // Ob die Akku-Loop aktuell läuft.
    @Volatile private var loopsRunning = false

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> stopEverything()
            ACTION_SYNC_NOW -> {
                // Sofortiger Akku-Push (z.B. wenn das Watchface aktiv wird).
                try {
                    startForegroundCompat()
                    if (!loopsRunning) startLoops()
                    scope.launch { pushBatteryNow() }
                } catch (e: Exception) {
                    Log.w(TAG, "Sofort-Akku-Sync fehlgeschlagen: ${e.message}")
                    stopSelf()
                }
            }
            else -> {
                // ACTION_START oder null (Android-Neustart nach Kill via START_STICKY)
                try {
                    startForegroundCompat()
                    startLoops()
                } catch (e: Exception) {
                    Log.w(TAG, "Foreground-Start fehlgeschlagen: ${e.message}")
                    stopSelf()
                }
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        cancelLoops()
        super.onDestroy()
    }

    private fun stopEverything() {
        cancelLoops()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun cancelLoops() {
        loopsRunning = false
        unregisterBatteryReceiver()
        batteryJob?.cancel()
    }

    /**
     * Ab v5 fragt die Uhr ioBroker-Datenpunkte, Wetter und Health selbst ab
     * (siehe Watchface-Modul `WatchDataSyncManager`). Das Handy überträgt nur noch
     * den Handy-Akku ans Watchface – alle anderen Sync-Loops entfallen hier.
     */
    private fun startLoops() {
        cancelLoops()
        loopsRunning = true
        registerBatteryReceiver()
        batteryJob = scope.launch { batteryLoop() }
        // Geofence nach Neustart / Service-Kill erneut registrieren, da Android
        // alle Geofences beim Neustart des Geräts oder nach dem App-Kill löscht.
        scope.launch { reregisterGeofenceIfNeeded() }
    }

    private suspend fun reregisterGeofenceIfNeeded() {
        try {
            val prefs = dataStore.data.first()
            val enabled = prefs[MainViewModel.KEY_GEOFENCE_ENABLED] ?: false
            if (!enabled) return
            val lat = prefs[MainViewModel.KEY_GEOFENCE_LAT]?.toDoubleOrNull() ?: return
            val lon = prefs[MainViewModel.KEY_GEOFENCE_LON]?.toDoubleOrNull() ?: return
            if (lat == 0.0 && lon == 0.0) return
            val radius = (prefs[MainViewModel.KEY_GEOFENCE_RADIUS] ?: 300).toFloat()
            val responsivenessMs = (prefs[MainViewModel.KEY_GEOFENCE_RESPONSIVENESS] ?: 60) * 1000
            geofenceManager.addGeofence(lat, lon, radius, responsivenessMs)
                .onSuccess { Log.d(TAG, "Geofence nach Service-Start reaktiviert: lat=$lat, lon=$lon, r=${radius}m") }
                .onFailure { Log.w(TAG, "Geofence-Reaktivierung fehlgeschlagen: ${it.message}") }
        } catch (e: Exception) {
            Log.e(TAG, "reregisterGeofenceIfNeeded Fehler: ${e.message}", e)
        }
    }

    // ── Handy-Akku ────────────────────────────────────────────────────────────

    /**
     * Registriert einen [android.content.BroadcastReceiver] für
     * [Intent.ACTION_BATTERY_CHANGED]. Damit wird der Handy-Akku bei JEDER Änderung
     * (Prozent / Ladezustand) sofort ans Watchface gepusht, statt nur im Polling-Intervall.
     */
    private fun registerBatteryReceiver() {
        if (batteryReceiver != null) return
        batteryReceiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                intent ?: return
                scope.launch { pushBattery(intent, force = false) }
            }
        }
        registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    }

    private fun unregisterBatteryReceiver() {
        batteryReceiver?.let { runCatching { unregisterReceiver(it) } }
        batteryReceiver = null
    }

    /**
     * Sendet den Akkustand aus dem übergebenen Battery-Intent ans Watchface.
     * Bei [force] = false wird nur gesendet, wenn sich Prozent oder Ladezustand
     * gegenüber dem letzten Push geändert haben (entdoppelt häufige Broadcasts).
     */
    private suspend fun pushBattery(batteryIntent: Intent, force: Boolean) {
        val prefs = dataStore.data.first()
        // Immer senden (auch wenn showBattery=false), damit die Uhr das Flag kennt
        // und den Ring direkt zeigen/verstecken kann — ohne separaten Config-Push.
        val showBattery = prefs[MainViewModel.KEY_WF_SHOW_PHONE_BATTERY] ?: false
        val level = batteryIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = batteryIntent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        val status = batteryIntent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL
        val percent = if (level >= 0 && scale > 0) (level * 100 / scale) else -1
        if (percent < 0) return
        if (!force && percent == lastBatteryPercent && isCharging == lastBatteryCharging) return
        lastBatteryPercent = percent
        lastBatteryCharging = isCharging
        wearDataLayerService.syncPhoneBatteryToWear(percent, isCharging, showBattery)
    }

    /** Fallback-Loop: erzwingt periodisch einen Akku-Push (hält das Watchface-Flag aktuell). */
    private suspend fun batteryLoop() {
        while (true) {
            try {
                val prefs = dataStore.data.first()
                // Akku-Fallback NICHT an ein großes Intervall koppeln:
                // ACTION_BATTERY_CHANGED wird auf manchen Phones an Hintergrund-Receiver
                // gedrosselt, dann ist dieser Loop der einzige Pfad. Auf max. 60 s deckeln,
                // damit der Wert spätestens nach 1 Minute frisch ist.
                val intervalSec = (prefs[MainViewModel.KEY_BATTERY_POLL_INTERVAL] ?: 60).coerceAtMost(60)
                val batteryIntent = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                if (batteryIntent != null) pushBattery(batteryIntent, force = true)
                delay(intervalSec * 1_000L)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "batteryLoop Ausnahme, Neuversuch in 30s: ${e.message}", e)
                delay(30_000L)
            }
        }
    }

    /** Einmaliger erzwungener Akku-Push. */
    private suspend fun pushBatteryNow() {
        try {
            val batteryIntent = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            if (batteryIntent != null) pushBattery(batteryIntent, force = true)
        } catch (e: Exception) {
            Log.e(TAG, "pushBatteryNow fehlgeschlagen: ${e.message}", e)
        }
    }

    // ── Notification ────────────────────────────────────────────────────────────

    private fun startForegroundCompat() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID, notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC or
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(): Notification {
        val channelId = "iosync_channel"
        val manager = getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(channelId) == null) {
            val channel = NotificationChannel(
                channelId,
                "IoSync Hintergrund-Sync",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Überträgt den Handy-Akku ans Watchface"
                setShowBadge(false)
                setSound(null, null) // stumm – kein Ton trotz IMPORTANCE_DEFAULT
                enableVibration(false)
            }
            manager.createNotificationChannel(channel)
        }

        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("IoSync")
            .setContentText("Handy-Akku-Sync zum Watchface aktiv")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    companion object {
        private const val TAG = "IoSyncSyncService"
        const val ACTION_START = "com.iosync.app.START_SYNC"
        const val ACTION_STOP = "com.iosync.app.STOP_SYNC"
        const val ACTION_SYNC_NOW = "com.iosync.app.SYNC_NOW"
        private const val NOTIFICATION_ID = 1003

        fun start(context: Context) {
            context.startForegroundService(Intent(context, IoSyncSyncService::class.java).apply {
                action = ACTION_START
            })
        }

        fun stop(context: Context) {
            context.startService(Intent(context, IoSyncSyncService::class.java).apply {
                action = ACTION_STOP
            })
        }

        /** Einmaliger Sofort-Akku-Push. */
        fun syncNow(context: Context) {
            context.startForegroundService(Intent(context, IoSyncSyncService::class.java).apply {
                action = ACTION_SYNC_NOW
            })
        }
    }
}
