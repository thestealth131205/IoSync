package com.iosync.watchface.health

import android.content.Context
import android.util.Log
import androidx.health.services.client.HealthServices
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

    // Lesezugriff auf den gemeinsamen Cache (für den Renderer)
    val heartRate: Int get() = HealthDataCache.heartRate
    val calories: Int get() = HealthDataCache.calories
    val steps: Int get() = HealthDataCache.steps
    val spO2: Int get() = HealthDataCache.spO2

    fun start() {
        if (isNoop || passiveMonitoringClient == null || scope == null) return
        scope.launch {
            try {
                val dataTypes = mutableSetOf(
                    DataType.HEART_RATE_BPM,
                    DataType.CALORIES_DAILY,
                    DataType.STEPS_DAILY
                )
                try { dataTypes.add(DataType.SPO2) } catch (_: Exception) { /* nicht auf jedem Gerät verfügbar */ }
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

    fun stop() {
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
