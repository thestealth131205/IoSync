package com.iosync.watchface.health

import android.content.Context
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.OxygenSaturationRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

private const val TAG = "WearHealthConnect"
private const val POLL_INTERVAL_MS = 5 * 60 * 1_000L // 5 Minuten

/**
 * Liest Gesundheitsdaten direkt aus Health Connect auf der Uhr (Wear OS 4).
 *
 * Hintergrund: Der Health Services Passive Listener liefert CALORIES_DAILY auf
 * dem Mobvoi Atlas nur sehr selten (teils nur einmal täglich). SpO2 und Schlaf
 * sind gar nicht über den Passive Listener verfügbar. TicHealth schreibt diese
 * Daten jedoch direkt in Health Connect auf der Uhr – daher ist dieser direkte
 * Weg deutlich zuverlässiger.
 *
 * Berechtigungen müssen über [WatchFaceConfigActivity] einmalig erteilt werden.
 */
class WearHealthConnectManager(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var pollJob: Job? = null

    /** Berechtigungen, die in der Config-Activity angefragt werden. */
    val requiredPermissions: Set<String> = setOf(
        HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class),
        HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class),
        HealthPermission.getReadPermission(OxygenSaturationRecord::class),
        HealthPermission.getReadPermission(SleepSessionRecord::class)
    )

    fun isAvailable(): Boolean = try {
        HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE
    } catch (_: Exception) { false }

    fun start() {
        if (!isAvailable()) {
            Log.d(TAG, "Health Connect nicht verfügbar – Polling nicht gestartet")
            return
        }
        pollJob?.cancel()
        pollJob = scope.launch {
            // Sofort beim Start laden
            refreshHealthData()
            while (true) {
                delay(POLL_INTERVAL_MS)
                refreshHealthData()
            }
        }
        Log.d(TAG, "Health Connect Polling gestartet (alle 5 min)")
    }

    fun stop() {
        pollJob?.cancel()
        scope.cancel()
    }

    private suspend fun refreshHealthData() {
        try {
            val client = HealthConnectClient.getOrCreate(context)
            val granted = client.permissionController.getGrantedPermissions()
            val now = Instant.now()
            val startOfToday = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant()
            val timeRange = TimeRangeFilter.between(startOfToday, now)

            // Kalorien (Tagessumme: erst TotalCaloriesBurned, Fallback auf ActiveCaloriesBurned)
            // TicHealth auf dem Mobvoi Atlas schreibt je nach Version TotalCaloriesBurnedRecord
            // oder ActiveCaloriesBurnedRecord – daher beide prüfen.
            var kcalFound = false
            if (granted.contains(HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class))) {
                try {
                    val resp = client.readRecords(ReadRecordsRequest(TotalCaloriesBurnedRecord::class, timeRange))
                    val kcal = resp.records.sumOf { it.energy.inKilocalories }.toInt()
                    if (kcal > 0) {
                        HealthDataCache.calories = kcal
                        kcalFound = true
                        Log.d(TAG, "Kalorien (Total): $kcal kcal")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "TotalCaloriesBurned lesen fehlgeschlagen: ${e.message}")
                }
            }
            if (!kcalFound && granted.contains(HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class))) {
                try {
                    val resp = client.readRecords(ReadRecordsRequest(ActiveCaloriesBurnedRecord::class, timeRange))
                    val kcal = resp.records.sumOf { it.energy.inKilocalories }.toInt()
                    if (kcal > 0) {
                        HealthDataCache.calories = kcal
                        Log.d(TAG, "Kalorien (Active, Fallback): $kcal kcal")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "ActiveCaloriesBurned lesen fehlgeschlagen: ${e.message}")
                }
            }

            // SpO2 (letzter gemessener Wert der letzten 24h)
            if (granted.contains(HealthPermission.getReadPermission(OxygenSaturationRecord::class))) {
                try {
                    val resp = client.readRecords(ReadRecordsRequest(OxygenSaturationRecord::class, timeRange))
                    resp.records.lastOrNull()?.percentage?.value?.toInt()?.let { o2 ->
                        if (o2 > 0) {
                            HealthDataCache.spO2 = o2
                            Log.d(TAG, "SpO2: $o2%")
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "SpO2 lesen fehlgeschlagen: ${e.message}")
                }
            }

            // Schlafdauer: 48h-Fenster, damit Nachtschlaf (Start vor Mitternacht) gefunden wird.
            // Cache IMMER aktualisieren (auch auf 0), damit veraltete Werte nicht eingefroren bleiben.
            if (granted.contains(HealthPermission.getReadPermission(SleepSessionRecord::class))) {
                try {
                    val sleepTimeRange = TimeRangeFilter.between(now.minus(48, ChronoUnit.HOURS), now)
                    val sleepResp = client.readRecords(ReadRecordsRequest(SleepSessionRecord::class, sleepTimeRange))
                    val todayMidnight = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant()
                    val latestEnd = sleepResp.records.maxOfOrNull { it.endTime }
                    val sleepMin = if (latestEnd != null && latestEnd.isAfter(todayMidnight)) {
                        val nightStart = latestEnd.minus(18, ChronoUnit.HOURS)
                        sleepResp.records.filter { it.endTime.isAfter(nightStart) }
                            .sumOf { ChronoUnit.MINUTES.between(it.startTime, it.endTime) }
                            .toInt()
                    } else 0
                    HealthDataCache.sleepMinutes = sleepMin
                    Log.d(TAG, "Schlaf: $sleepMin min (latestEnd=$latestEnd, todayMidnight=$todayMidnight)")
                } catch (e: Exception) {
                    Log.w(TAG, "Schlaf lesen fehlgeschlagen: ${e.message}")
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Health Connect Fehler: ${e.message}")
        }
    }
}
