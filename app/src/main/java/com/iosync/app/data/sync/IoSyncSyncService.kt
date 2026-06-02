package com.iosync.app.data.sync

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.iosync.app.MainActivity
import com.iosync.app.R
import com.iosync.app.data.health.HealthConnectManager
import com.iosync.app.data.model.SmartHomeState
import com.iosync.app.data.network.IoSyncClient
import com.iosync.app.data.network.WeatherService
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
 * Zentraler Foreground-Service für den gesamten Hintergrund-Datenfluss ans Watchface.
 *
 * Vorher lief das Polling für Wetter, Handy-Akku und ioBroker-Slots im
 * [MainViewModel] (`viewModelScope`) und stoppte, sobald die App geschlossen /
 * weggewischt oder vom System beendet wurde – die Uhr zeigte dann stundenlang
 * eingefrorene Werte (Wetter, kcal, Slots). Dieser Service bündelt alle
 * Sync-Schleifen und hält sie unabhängig von der App-UI am Leben.
 *
 * Alle Konfiguration wird pro Zyklus frisch aus dem [DataStore] gelesen, damit
 * der Service auch ohne laufendes ViewModel korrekt arbeitet.
 */
@AndroidEntryPoint
class IoSyncSyncService : Service() {

    @Inject lateinit var ioSyncClient: IoSyncClient
    @Inject lateinit var weatherService: WeatherService
    @Inject lateinit var wearDataLayerService: WearDataLayerService
    @Inject lateinit var healthConnectManager: HealthConnectManager
    @Inject lateinit var dataStore: DataStore<Preferences>

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var dataJob: Job? = null
    private var batteryJob: Job? = null
    private var weatherJob: Job? = null
    private var healthJob: Job? = null

    // Letzte bekannte Health-Werte (Health Connect liefert nicht immer einen neuen Wert)
    private var lastKnownHr: Int = 0
    private var lastKnownKcal: Int = 0
    private var lastKnownO2: Int = 0
    private var lastKnownSleep: Int = 0

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> stopEverything()
            else -> {
                // ACTION_START oder null (Android-Neustart nach Kill via START_STICKY)
                try {
                    startForeground(NOTIFICATION_ID, buildNotification())
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
        dataJob?.cancel(); batteryJob?.cancel(); weatherJob?.cancel(); healthJob?.cancel()
    }

    private fun startLoops() {
        cancelLoops()
        dataJob = scope.launch { dataLoop() }
        batteryJob = scope.launch { batteryLoop() }
        weatherJob = scope.launch { weatherLoop() }
        healthJob = scope.launch { healthLoop() }
    }

    // ── ioBroker-Slots / States ───────────────────────────────────────────────

    private suspend fun dataLoop() {
        while (true) {
            try {
                val prefs = dataStore.data.first()
                val useAdapter = prefs[MainViewModel.KEY_USE_IOSYNC_ADAPTER] ?: true
                val host       = prefs[MainViewModel.KEY_IOSYNC_HOST] ?: ""
                val intervalSec = prefs[MainViewModel.KEY_SLOT_POLL_INTERVAL] ?: 300

                if (useAdapter && host.isNotBlank()) {
                    val port     = prefs[MainViewModel.KEY_IOSYNC_PORT] ?: 7443
                    val useHttps = prefs[MainViewModel.KEY_IOSYNC_USE_HTTPS] ?: false
                    val username = prefs[MainViewModel.KEY_IOSYNC_USERNAME] ?: ""
                    val password = prefs[MainViewModel.KEY_IOSYNC_PASSWORD] ?: ""

                    ioSyncClient.fetchDataPoints(host, port, useHttps, username, password)
                        .onSuccess { states ->
                            if (prefs[MainViewModel.KEY_WF_SHOW_IOBROKER_DATA] == true) {
                                wearDataLayerService.syncStatesToWear(states)
                            }
                            if (prefs[MainViewModel.KEY_SHOW_CUSTOM_SLOTS] == true) {
                                syncCustomSlots(prefs, states)
                            }
                            if (hasPage2Slots(prefs)) {
                                syncPage2Slots(prefs, states)
                            }
                            syncPage2PillStates(prefs, states)
                            // ioBroker als Wetter-Temperatur-Quelle
                            if (prefs[MainViewModel.KEY_WF_SHOW_WEATHER] != false &&
                                prefs[MainViewModel.KEY_WF_WEATHER_TEMP_SOURCE] == "iobroker"
                            ) {
                                val id = prefs[MainViewModel.KEY_WF_WEATHER_IOBROKER_ID] ?: ""
                                states.firstOrNull { it.id == id }?.value?.toDoubleOrNull()?.let { temp ->
                                    wearDataLayerService.syncWeatherToWear(temp.toInt(), "clear")
                                }
                            }
                            // ioBroker als Schlaf-Quelle
                            if (prefs[MainViewModel.KEY_WF_SLEEP_SOURCE] == "iobroker") {
                                val id = prefs[MainViewModel.KEY_WF_SLEEP_IOBROKER_ID] ?: ""
                                if (id.isNotBlank()) {
                                    states.firstOrNull { it.id == id }?.value?.toDoubleOrNull()?.toInt()?.let { mins ->
                                        if (mins > 0) {
                                            lastKnownSleep = mins
                                            wearDataLayerService.syncPhoneHealthToWear(lastKnownHr, lastKnownO2, lastKnownKcal, mins)
                                        }
                                    }
                                }
                            }
                        }
                }
                delay(intervalSec * 1_000L)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "dataLoop Ausnahme, Neuversuch in 30s: ${e.message}", e)
                delay(30_000L)
            }
        }
    }

    private fun hasPage2Slots(prefs: Preferences): Boolean =
        !prefs[MainViewModel.KEY_P2_SLOT1_ID].isNullOrBlank() ||
        !prefs[MainViewModel.KEY_P2_SLOT2_ID].isNullOrBlank() ||
        !prefs[MainViewModel.KEY_P2_SLOT3_ID].isNullOrBlank() ||
        !prefs[MainViewModel.KEY_P2_SLOT4_ID].isNullOrBlank() ||
        !prefs[MainViewModel.KEY_P2_BAR_ID].isNullOrBlank()

    private suspend fun syncCustomSlots(prefs: Preferences, states: List<SmartHomeState>) {
        fun valOf(key: androidx.datastore.preferences.core.Preferences.Key<String>) =
            states.firstOrNull { it.id == prefs[key] }?.value
        wearDataLayerService.syncCustomSlotsToWear(
            prefs[MainViewModel.KEY_CUSTOM_SLOT1_LABEL] ?: "", formatSlotValue(valOf(MainViewModel.KEY_CUSTOM_SLOT1_ID)),
            prefs[MainViewModel.KEY_CUSTOM_SLOT2_LABEL] ?: "", formatSlotValue(valOf(MainViewModel.KEY_CUSTOM_SLOT2_ID)),
            prefs[MainViewModel.KEY_CUSTOM_SLOT3_LABEL] ?: "", formatSlotValue(valOf(MainViewModel.KEY_CUSTOM_SLOT3_ID)),
            prefs[MainViewModel.KEY_CUSTOM_SLOT4_LABEL] ?: "", formatSlotValue(valOf(MainViewModel.KEY_CUSTOM_SLOT4_ID)),
            prefs[MainViewModel.KEY_CUSTOM_SLOT4_BAR_COLOR] ?: "neon_yellow",
            prefs[MainViewModel.KEY_CUSTOM_SLOT4_BAR_MIN]?.toFloatOrNull() ?: 0f,
            prefs[MainViewModel.KEY_CUSTOM_SLOT4_BAR_MAX]?.toFloatOrNull() ?: 100f,
            prefs[MainViewModel.KEY_CUSTOM_SLOT4_BAR_SHOW_LABEL] ?: true,
            prefs[MainViewModel.KEY_CUSTOM_SLOT4_WARN1_COLOR] ?: "orange",
            prefs[MainViewModel.KEY_CUSTOM_SLOT4_WARN1_VALUE]?.toFloatOrNull() ?: Float.NaN,
            prefs[MainViewModel.KEY_CUSTOM_SLOT4_WARN2_COLOR] ?: "red",
            prefs[MainViewModel.KEY_CUSTOM_SLOT4_WARN2_VALUE]?.toFloatOrNull() ?: Float.NaN
        )
    }

    private suspend fun syncPage2Slots(prefs: Preferences, states: List<SmartHomeState>) {
        fun valOf(key: androidx.datastore.preferences.core.Preferences.Key<String>) =
            states.firstOrNull { it.id == prefs[key] }?.value
        val barId = prefs[MainViewModel.KEY_P2_BAR_ID] ?: ""
        wearDataLayerService.syncPage2SlotsToWear(
            prefs[MainViewModel.KEY_P2_SLOT1_LABEL] ?: "", formatSlotValue(valOf(MainViewModel.KEY_P2_SLOT1_ID)),
            prefs[MainViewModel.KEY_P2_SLOT2_LABEL] ?: "", formatSlotValue(valOf(MainViewModel.KEY_P2_SLOT2_ID)),
            prefs[MainViewModel.KEY_P2_SLOT3_LABEL] ?: "", formatSlotValue(valOf(MainViewModel.KEY_P2_SLOT3_ID)),
            prefs[MainViewModel.KEY_P2_SLOT4_LABEL] ?: "", formatSlotValue(valOf(MainViewModel.KEY_P2_SLOT4_ID)),
            p2BarValue = if (barId.isNotBlank()) formatSlotValue(valOf(MainViewModel.KEY_P2_BAR_ID)) else "--"
        )
    }

    private suspend fun syncPage2PillStates(prefs: Preferences, states: List<SmartHomeState>) {
        val pill1Enabled = prefs[MainViewModel.KEY_P2_PILL_ENABLED] ?: false
        val pill2Enabled = prefs[MainViewModel.KEY_P2_PILL2_ENABLED] ?: false
        if (!pill1Enabled && !pill2Enabled) return
        val pill1Id = if (pill1Enabled) prefs[MainViewModel.KEY_P2_PILL_IOBROKER_ID] ?: "" else ""
        val pill2Id = if (pill2Enabled) prefs[MainViewModel.KEY_P2_PILL2_IOBROKER_ID] ?: "" else ""
        if (pill1Id.isBlank() && pill2Id.isBlank()) return
        fun boolOf(id: String, fallbackKey: androidx.datastore.preferences.core.Preferences.Key<Boolean>): Boolean {
            val v = states.firstOrNull { it.id == id }?.value ?: return prefs[fallbackKey] ?: false
            return v == "true" || v == "1"
        }
        val s1 = if (pill1Id.isNotBlank()) boolOf(pill1Id, MainViewModel.KEY_P2_PILL1_STATE) else prefs[MainViewModel.KEY_P2_PILL1_STATE] ?: false
        val s2 = if (pill2Id.isNotBlank()) boolOf(pill2Id, MainViewModel.KEY_P2_PILL2_STATE) else prefs[MainViewModel.KEY_P2_PILL2_STATE] ?: false
        wearDataLayerService.syncP2PillStatesToWear(s1, s2)
    }

    private fun formatSlotValue(value: String?): String {
        if (value == null) return "--"
        val num = value.toDoubleOrNull() ?: return value.take(6)
        return String.format(java.util.Locale.US, "%.1f", num)
    }

    // ── Handy-Akku ────────────────────────────────────────────────────────────

    private suspend fun batteryLoop() {
        while (true) {
            try {
                val prefs = dataStore.data.first()
                val intervalSec = prefs[MainViewModel.KEY_BATTERY_POLL_INTERVAL] ?: 60
                if (prefs[MainViewModel.KEY_WF_SHOW_PHONE_BATTERY] == true) {
                    val batteryIntent = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                    if (batteryIntent != null) {
                        val level = batteryIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                        val scale = batteryIntent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
                        val status = batteryIntent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                                status == BatteryManager.BATTERY_STATUS_FULL
                        val percent = if (level >= 0 && scale > 0) (level * 100 / scale) else -1
                        if (percent >= 0) wearDataLayerService.syncPhoneBatteryToWear(percent, isCharging)
                    }
                }
                delay(intervalSec * 1_000L)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "batteryLoop Ausnahme, Neuversuch in 30s: ${e.message}", e)
                delay(30_000L)
            }
        }
    }

    // ── Wetter ────────────────────────────────────────────────────────────────

    private suspend fun weatherLoop() {
        while (true) {
            try {
                val prefs = dataStore.data.first()
                val showWeather = prefs[MainViewModel.KEY_WF_SHOW_WEATHER] ?: true
                val tempSource  = prefs[MainViewModel.KEY_WF_WEATHER_TEMP_SOURCE] ?: "openweather"
                // ioBroker-Temperaturquelle wird im dataLoop behandelt
                if (showWeather && tempSource == "openweather") {
                    // Standort-Konfiguration aus DataStore in den Singleton spiegeln,
                    // damit der Service auch ohne laufendes ViewModel korrekt abfragt.
                    val useFixed = prefs[MainViewModel.KEY_WEATHER_USE_FIXED] ?: false
                    weatherService.useFixedLocation = useFixed
                    weatherService.fixedLat = if (useFixed) prefs[MainViewModel.KEY_WEATHER_FIXED_LAT]?.toDoubleOrNull() else null
                    weatherService.fixedLon = if (useFixed) prefs[MainViewModel.KEY_WEATHER_FIXED_LON]?.toDoubleOrNull() else null

                    weatherService.fetchWeather()
                        .onSuccess { wearDataLayerService.syncWeatherToWear(it.temperature, it.condition) }
                }
                delay(WEATHER_INTERVAL_MS)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "weatherLoop Ausnahme, Neuversuch in 60s: ${e.message}", e)
                delay(60_000L)
            }
        }
    }

    // ── Health Connect ──────────────────────────────────────────────────────────

    private suspend fun healthLoop() {
        while (true) {
            try {
                val prefs = dataStore.data.first()
                val hrSource     = prefs[MainViewModel.KEY_WF_HR_SOURCE]      ?: "local"
                val kcalSource   = prefs[MainViewModel.KEY_WF_KCAL_SOURCE]    ?: "local"
                val oxygenSource = prefs[MainViewModel.KEY_WF_OXYGEN_SOURCE]  ?: "local"
                val sleepSource  = prefs[MainViewModel.KEY_WF_SLEEP_SOURCE]   ?: "healthconnect"
                val intervalSec  = prefs[MainViewModel.KEY_HEALTH_POLL_INTERVAL] ?: 60

                val anyHealthConnect = hrSource == "healthconnect" || kcalSource == "healthconnect" ||
                    oxygenSource == "healthconnect" || sleepSource == "healthconnect"
                if (anyHealthConnect) {
                    if (hrSource == "healthconnect")     healthConnectManager.readLatestHeartRate()?.let { if (it > 0) lastKnownHr = it }
                    if (kcalSource == "healthconnect")   healthConnectManager.readTodayCalories()?.let { if (it > 0) lastKnownKcal = it }
                    if (oxygenSource == "healthconnect") healthConnectManager.readLatestOxygenSaturation()?.let { if (it > 0) lastKnownO2 = it }
                    if (sleepSource == "healthconnect")  healthConnectManager.readTodaySleepMinutes()?.let { if (it > 0) lastKnownSleep = it }

                    if (lastKnownHr > 0 || lastKnownO2 > 0 || lastKnownKcal > 0 || lastKnownSleep > 0) {
                        wearDataLayerService.syncPhoneHealthToWear(lastKnownHr, lastKnownO2, lastKnownKcal, lastKnownSleep)
                    }
                }
                delay(intervalSec * 1_000L)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "healthLoop Ausnahme, Neuversuch in 30s: ${e.message}", e)
                delay(30_000L)
            }
        }
    }

    // ── Notification ────────────────────────────────────────────────────────────

    private fun buildNotification(): Notification {
        val channelId = "iosync_sync_channel"
        val manager = getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(channelId) == null) {
            val channel = NotificationChannel(
                channelId,
                "IoSync Hintergrund-Sync",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Überträgt Wetter-, Akku-, ioBroker- und Health-Daten ans Watchface"
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
            .setContentText("Daten-Sync zum Watchface aktiv")
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
        private const val NOTIFICATION_ID = 1003
        private const val WEATHER_INTERVAL_MS = 900_000L // 15 Minuten

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
    }
}
