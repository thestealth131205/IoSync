package com.iosync.watchface.health

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log

private const val TAG = "HealthSensorManager"

// Geschätzte Kalorien pro Schritt (Durchschnitt)
private const val KCAL_PER_STEP = 0.04f

/**
 * Liest Gesundheitsdaten direkt von den Sensoren der Wear OS Uhr.
 * - Puls (TYPE_HEART_RATE)
 * - SpO2 (vendor-spezifisch, falls verfügbar)
 * - Schritte (TYPE_STEP_COUNTER) → Kalorien-Berechnung
 */
class HealthSensorManager(context: Context) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    @Volatile var heartRate: Int = 0
        private set
    @Volatile var spO2: Int = 0
        private set
    @Volatile var calories: Int = 0
        private set
    @Volatile var steps: Int = 0
        private set

    private var initialSteps: Int = -1

    private val heartRateSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE)
    private val stepSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)

    fun start() {
        heartRateSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
            Log.d(TAG, "Pulssensor registriert")
        } ?: Log.w(TAG, "Kein Pulssensor verfügbar")

        stepSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
            Log.d(TAG, "Schrittzähler registriert")
        } ?: Log.w(TAG, "Kein Schrittzähler verfügbar")
    }

    fun stop() {
        sensorManager.unregisterListener(this)
        Log.d(TAG, "Sensoren deregistriert")
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_HEART_RATE -> {
                val hr = event.values[0].toInt()
                if (hr > 0) heartRate = hr
            }
            Sensor.TYPE_STEP_COUNTER -> {
                val totalSteps = event.values[0].toInt()
                if (initialSteps < 0) initialSteps = totalSteps
                steps = totalSteps - initialSteps
                calories = (steps * KCAL_PER_STEP).toInt()
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
