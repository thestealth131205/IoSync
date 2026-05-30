package com.iosync.app.data.health

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.iosync.app.MainActivity
import com.iosync.app.R
import com.iosync.app.ui.viewmodel.MainViewModel
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
 * Foreground-Service, der die Health-Connect-Werte periodisch ausliest und an
 * das Watchface überträgt – unabhängig davon, ob die App-UI geöffnet ist.
 *
 * Vorher lief das Polling im [MainViewModel] und stoppte, sobald der App-Prozess
 * beendet wurde (App geschlossen / weggeswipet). Dieser Service hält den Sync
 * im Hintergrund aktiv.
 */
@AndroidEntryPoint
class HealthSyncService : Service() {

    @Inject lateinit var healthConnectManager: HealthConnectManager
    @Inject lateinit var wearDataLayerService: WearDataLayerService
    @Inject lateinit var dataStore: DataStore<Preferences>

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var pollJob: Job? = null

    // Letzte bekannte Werte – werden gesendet, wenn Health Connect keinen neuen Wert liefert
    private var lastKnownHr: Int = 0
    private var lastKnownKcal: Int = 0
    private var lastKnownO2: Int = 0

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                startForeground(NOTIFICATION_ID, buildNotification())
                startPolling()
            }
            ACTION_STOP -> {
                pollJob?.cancel()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        pollJob?.cancel()
        super.onDestroy()
    }

    private fun startPolling() {
        pollJob?.cancel()
        pollJob = scope.launch {
            while (true) {
                val prefs = dataStore.data.first()
                val hrSource     = prefs[MainViewModel.KEY_WF_HR_SOURCE]     ?: "local"
                val kcalSource   = prefs[MainViewModel.KEY_WF_KCAL_SOURCE]   ?: "local"
                val oxygenSource = prefs[MainViewModel.KEY_WF_OXYGEN_SOURCE] ?: "local"
                val intervalSec  = prefs[MainViewModel.KEY_HEALTH_POLL_INTERVAL] ?: 60

                var anyHealthConnect = false

                if (hrSource == "healthconnect") {
                    anyHealthConnect = true
                    healthConnectManager.readLatestHeartRate()?.let { if (it > 0) lastKnownHr = it }
                }
                if (kcalSource == "healthconnect") {
                    anyHealthConnect = true
                    healthConnectManager.readTodayCalories()?.let { if (it > 0) lastKnownKcal = it }
                }
                if (oxygenSource == "healthconnect") {
                    anyHealthConnect = true
                    healthConnectManager.readLatestOxygenSaturation()?.let { if (it > 0) lastKnownO2 = it }
                }

                // Keine Quelle mehr auf Health Connect → Service beenden
                if (!anyHealthConnect) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                    return@launch
                }

                if (lastKnownHr > 0 || lastKnownO2 > 0 || lastKnownKcal > 0) {
                    wearDataLayerService.syncPhoneHealthToWear(lastKnownHr, lastKnownO2, lastKnownKcal)
                }

                delay(intervalSec * 1_000L)
            }
        }
    }

    private fun buildNotification(): Notification {
        val channelId = "iosync_health_channel"
        val manager = getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(channelId) == null) {
            val channel = NotificationChannel(
                channelId,
                "IoSync Health-Sync",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Überträgt Health-Connect-Daten ans Watchface"
                setShowBadge(false)
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
            .setContentText("Health-Daten-Sync aktiv")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    companion object {
        const val ACTION_START = "com.iosync.app.START_HEALTH_SYNC"
        const val ACTION_STOP = "com.iosync.app.STOP_HEALTH_SYNC"
        private const val NOTIFICATION_ID = 1002

        fun start(context: Context) {
            context.startForegroundService(Intent(context, HealthSyncService::class.java).apply {
                action = ACTION_START
            })
        }

        fun stop(context: Context) {
            context.startService(Intent(context, HealthSyncService::class.java).apply {
                action = ACTION_STOP
            })
        }
    }
}
