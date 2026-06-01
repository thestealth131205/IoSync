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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

private const val TAG = "HealthSensorManager"

/**
 * Verwaltet die Registrierung des Health Services Passive Listeners.
 * Daten werden asynchron vom HealthPassiveDataService in den HealthDataCache geschrieben.
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

    // Aktive Puls-Messung (MeasureClient) – liefert kontinuierliche Samples,
    // im Gegensatz zum Passive Listener (nur sporadisch bei Aktivität).
    @Volatile private var heartRateMeasureActive = false
    private val heartRateCallback = object : MeasureCallback {
        override fun onRegistered() {
            Log.d(TAG, "HR-Measure-Callback registriert")
        }

        override fun onRegistrationFailed(throwable: Throwable) {
            Log.e(TAG, "HR-Measure-Registrierung fehlgeschlagen: ${throwable.message}")
        }

        override fun onAvailabilityChanged(dataType: DeltaDataType<*, *>, availability: Availability) {
            Log.d(TAG, "HR-Verfügbarkeit: $availability")
        }

        override fun onDataReceived(data: DataPointContainer) {
            data.getData(DataType.HEART_RATE_BPM).lastOrNull()?.let { point ->
                val hr = point.value.toInt()
                if (hr > 0) {
                    HealthDataCache.heartRate = hr
                    Log.d(TAG, "Puls (Measure): $hr bpm")
                }
            }
        }
    }

    // Lesezugriff auf den gemeinsamen Cache (für den Renderer)
    val heartRate: Int get() = HealthDataCache.heartRate
    val calories: Int get() = HealthDataCache.calories
    val steps: Int get() = HealthDataCache.steps
    val spO2: Int get() = HealthDataCache.spO2

    fun start() {
        if (isNoop || passiveMonitoringClient == null || scope == null || context == null) return

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
                val dataTypes = mutableSetOf(
                    DataType.HEART_RATE_BPM,
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
     * Startet die kontinuierliche Puls-Messung über den MeasureClient.
     * Sollte nur bei sichtbarem, nicht-ambientem Watchface laufen (Akku schonen).
     */
    fun startHeartRate() {
        if (isNoop || measureClient == null || context == null || heartRateMeasureActive) return
        val hasBodySensors = ContextCompat.checkSelfPermission(
            context, Manifest.permission.BODY_SENSORS
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasBodySensors) {
            Log.w(TAG, "BODY_SENSORS-Berechtigung fehlt – HR-Messung wird nicht gestartet")
            return
        }
        try {
            measureClient.registerMeasureCallback(DataType.HEART_RATE_BPM, heartRateCallback)
            heartRateMeasureActive = true
            Log.d(TAG, "HR-Messung (MeasureClient) registriert")
        } catch (e: Exception) {
            Log.e(TAG, "HR-Messung-Registrierung fehlgeschlagen: ${e.message}")
        }
    }

    /** Stoppt die kontinuierliche Puls-Messung. */
    fun stopHeartRate() {
        if (isNoop || measureClient == null || !heartRateMeasureActive) return
        try {
            measureClient.unregisterMeasureCallbackAsync(DataType.HEART_RATE_BPM, heartRateCallback)
            Log.d(TAG, "HR-Messung (MeasureClient) deregistriert")
        } catch (e: Exception) {
            Log.w(TAG, "HR-Messung-Deregistrierung fehlgeschlagen: ${e.message}")
        }
        heartRateMeasureActive = false
    }

    fun stop() {
        stopHeartRate()
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
