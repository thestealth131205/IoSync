package com.iosync.watchface.health

import android.content.Context
import android.util.Log
import androidx.health.services.client.HealthServices
import androidx.health.services.client.PassiveListenerConfig
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
class HealthSensorManager(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val passiveMonitoringClient =
        HealthServices.getClient(context).passiveMonitoringClient

    // Lesezugriff auf den gemeinsamen Cache (für den Renderer)
    val heartRate: Int get() = HealthDataCache.heartRate
    val calories: Int get() = HealthDataCache.calories
    val steps: Int get() = HealthDataCache.steps
    val spO2: Int = 0  // SpO2 nicht über Passive API verfügbar

    fun start() {
        scope.launch {
            try {
                val config = PassiveListenerConfig.builder()
                    .setDataTypes(
                        setOf(
                            DataType.HEART_RATE_BPM,
                            DataType.DAILY_CALORIES,
                            DataType.DAILY_STEPS
                        )
                    )
                    .build()

                passiveMonitoringClient.setPassiveListenerService(
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
        scope.launch {
            try {
                passiveMonitoringClient.clearPassiveListenerService()
                Log.d(TAG, "Health Services Passive Listener deregistriert")
            } catch (e: Exception) {
                Log.w(TAG, "Deregistrierung fehlgeschlagen: ${e.message}")
            }
        }
        scope.cancel()
    }
}
