package com.iosync.watchface.health

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.health.services.client.HealthServices
import androidx.health.services.client.MeasureCallback
import androidx.health.services.client.data.Availability
import androidx.health.services.client.data.DataPointContainer
import androidx.health.services.client.data.DeltaDataType
import androidx.health.services.client.data.PassiveListenerConfig
import androidx.health.services.client.data.DataType
import com.iosync.watchface.datalayer.WatchFaceConfigCache
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

private const val TAG = "HealthSensorManager"

// Maximale Wartezeit pro Einzelmessung auf einen gültigen Puls-Wert, bevor der
// Sensor wieder ausgeschaltet wird.
private const val HR_MEASURE_WINDOW_MS = 30_000L

/**
 * Verwaltet die Registrierung des Health Services Passive Listeners sowie
 * des Health Connect Pollings (Kalorien, SpO2, Schlaf).
 *
 * Passive Monitoring liefert CALORIES_DAILY auf dem Mobvoi Atlas nur sehr
 * selten. SpO2 und Schlaf sind gar nicht über den Passive Listener verfügbar.
 * Der [WearHealthConnectManager] liest diese Werte alle 5 Minuten direkt aus
 * Health Connect, wohin TicHealth die Daten schreibt.
 *
 * Benötigte Berechtigungen (AndroidManifest + Laufzeit):
 *   - android.permission.BODY_SENSORS
 *   - android.permission.ACTIVITY_RECOGNITION
 */
class HealthSensorManager private constructor(
    private val context: Context?,
    private val isNoop: Boolean = false
) {
    // Öffentlicher Konstruktor für normalen Betrieb
    constructor(context: Context) : this(context, false)

    companion object {
        /** Dummy-Instanz falls Health Services nicht verfügbar */
        val NOOP = HealthSensorManager(context = null, isNoop = true)
    }

    private val scope = if (!isNoop) CoroutineScope(SupervisorJob() + Dispatchers.IO) else null
    private val passiveMonitoringClient = if (!isNoop && context != null) {
        try { HealthServices.getClient(context).passiveMonitoringClient } catch (_: Exception) { null }
    } else null
    private val measureClient = if (!isNoop && context != null) {
        try { HealthServices.getClient(context).measureClient } catch (_: Exception) { null }
    } else null

    // Health Connect Polling für Kalorien, SpO2 und Schlaf
    private val wearHealthConnectManager: WearHealthConnectManager? =
        if (!isNoop && context != null) WearHealthConnectManager(context) else null

    // Periodische Puls-Messung (MeasureClient): Statt den optischen Sensor
    // dauerhaft laufen zu lassen (Dauer-Akkuverbrauch), wird er nur alle paar
    // Minuten kurz eingeschaltet, EIN Wert geholt und sofort wieder ausgeschaltet.
    // Das Intervall liefert WatchFaceConfigCache.heartRateIntervalSec (App-Config).
    @Volatile private var heartRateMeasureActive = false
    private var heartRateJob: Job? = null

    // Lesezugriff auf den gemeinsamen Cache (für den Renderer)
    val heartRate: Int get() = HealthDataCache.heartRate
    val calories: Int get() = HealthDataCache.calories
    val steps: Int get() = HealthDataCache.steps
    val spO2: Int get() = HealthDataCache.spO2

    fun start() {
        if (isNoop || context == null) return

        // Health Connect Polling unabhängig von BODY_SENSORS starten —
        // HC braucht nur HC-Berechtigungen, nicht BODY_SENSORS.
        wearHealthConnectManager?.start()

        if (passiveMonitoringClient == null || scope == null) return

        // Runtime-Permission-Prüfung: ohne BODY_SENSORS liefert Health Services keine Daten,
        // und ohne ACTIVITY_RECOGNITION fehlen Schritte/Kalorien.
        val hasBodySensors = ContextCompat.checkSelfPermission(
            context, Manifest.permission.BODY_SENSORS
        ) == PackageManager.PERMISSION_GRANTED
        val hasActivityRecognition = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACTIVITY_RECOGNITION
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasBodySensors) {
            Log.w(TAG, "BODY_SENSORS-Berechtigung fehlt – Passive Listener wird nicht gestartet")
            return
        }
        if (!hasActivityRecognition) {
            Log.w(TAG, "ACTIVITY_RECOGNITION-Berechtigung fehlt – Schritte/Kalorien werden ggf. nicht geliefert")
        }

        scope.launch {
            try {
                // WICHTIG: HEART_RATE_BPM NICHT als passiven DataType registrieren!
                // Passive Monitoring von HEART_RATE_BPM hält den optischen Puls-Sensor
                // DAUERHAFT aktiv → permanenter Akku-Verbrauch, auch wenn TicCare-Echtzeit
                // aus ist. Der Puls wird stattdessen on-demand und nur bei sichtbarem
                // Watchface periodisch über den MeasureClient gemessen (startHeartRate()):
                // Sensor kurz an, EIN Wert, Sensor wieder aus.
                // Kalorien/Schritte sind aggregierte Tages-DataTypes ohne Dauer-Sensor.
                val dataTypes = mutableSetOf(
                    DataType.CALORIES_DAILY,
                    DataType.STEPS_DAILY
                )
                // SpO2: nicht als passiver DataType verfügbar, erfordert MeasureClient
                val config = PassiveListenerConfig.builder()
                    .setDataTypes(dataTypes)
                    .build()

                passiveMonitoringClient.setPassiveListenerServiceAsync(
                    HealthPassiveDataService::class.java,
                    config
                )
                Log.d(TAG, "Health Services Passive Listener registriert")
            } catch (e: Exception) {
                Log.e(TAG, "Registrierung fehlgeschlagen: ${e.message}")
            }
        }
    }

    /**
     * Startet die periodische Puls-Messung. Sollte nur bei sichtbarem,
     * nicht-ambientem Watchface laufen. Misst sofort einmal und danach im
     * Intervall [WatchFaceConfigCache.heartRateIntervalSec]; zwischen den
     * Messungen bleibt der Sensor aus (Akku schonen).
     */
    fun startHeartRate() {
        if (isNoop || measureClient == null || context == null || heartRateMeasureActive) return
        val s = scope ?: return
        val hasBodySensors = ContextCompat.checkSelfPermission(
            context, Manifest.permission.BODY_SENSORS
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasBodySensors) {
            Log.w(TAG, "BODY_SENSORS-Berechtigung fehlt – HR-Messung wird nicht gestartet")
            return
        }
        heartRateMeasureActive = true
        heartRateJob = s.launch {
            while (heartRateMeasureActive) {
                measureHeartRateOnce()
                val intervalSec = WatchFaceConfigCache.heartRateIntervalSec.coerceAtLeast(30)
                delay(intervalSec * 1_000L)
            }
        }
        Log.d(TAG, "Periodische HR-Messung gestartet")
    }

    /**
     * Schaltet den Puls-Sensor kurz ein, wartet auf einen gültigen Wert
     * (max. [HR_MEASURE_WINDOW_MS]) und schaltet ihn danach wieder aus.
     */
    private suspend fun measureHeartRateOnce() {
        val mc = measureClient ?: return
        val received = CompletableDeferred<Unit>()
        val callback = object : MeasureCallback {
            override fun onRegistered() {}

            override fun onRegistrationFailed(throwable: Throwable) {
                Log.e(TAG, "HR-Measure-Registrierung fehlgeschlagen: ${throwable.message}")
                received.complete(Unit)
            }

            override fun onAvailabilityChanged(dataType: DeltaDataType<*, *>, availability: Availability) {}

            override fun onDataReceived(data: DataPointContainer) {
                data.getData(DataType.HEART_RATE_BPM).lastOrNull()?.let { point ->
                    val hr = point.value.toInt()
                    if (hr > 0) {
                        HealthDataCache.heartRate = hr
                        HealthDataCache.lastHeartRateTimestamp = System.currentTimeMillis()
                        Log.d(TAG, "Puls (Measure, periodisch): $hr bpm")
                        received.complete(Unit)
                    }
                }
            }
        }
        try {
            mc.registerMeasureCallback(DataType.HEART_RATE_BPM, callback)
            withTimeoutOrNull(HR_MEASURE_WINDOW_MS) { received.await() }
        } catch (e: Exception) {
            Log.e(TAG, "HR-Einzelmessung fehlgeschlagen: ${e.message}")
        } finally {
            try {
                mc.unregisterMeasureCallbackAsync(DataType.HEART_RATE_BPM, callback)
            } catch (e: Exception) {
                Log.w(TAG, "HR-Messung-Deregistrierung fehlgeschlagen: ${e.message}")
            }
        }
    }

    /** Stoppt die periodische Puls-Messung. */
    fun stopHeartRate() {
        if (!heartRateMeasureActive) return
        heartRateMeasureActive = false
        heartRateJob?.cancel()
        heartRateJob = null
        Log.d(TAG, "Periodische HR-Messung gestoppt")
    }

    fun stop() {
        stopHeartRate()
        wearHealthConnectManager?.stop()
        if (isNoop || passiveMonitoringClient == null || scope == null) return
        scope.launch {
            try {
                passiveMonitoringClient.clearPassiveListenerServiceAsync()
                Log.d(TAG, "Health Services Passive Listener deregistriert")
            } catch (e: Exception) {
                Log.w(TAG, "Deregistrierung fehlgeschlagen: ${e.message}")
            }
        }
        scope.cancel()
    }
}
