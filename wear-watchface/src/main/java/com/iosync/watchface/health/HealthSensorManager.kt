package com.iosync.watchface.health

import android.content.Context
import android.content.SharedPreferences
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.time.LocalDate

private const val TAG = "HealthSensorManager"

// Geschätzte Kalorien pro Schritt (Durchschnitt)
private const val KCAL_PER_STEP = 0.04f

// Puls-Messintervall: 15 Minuten
private const val HR_INTERVAL_MS = 15L * 60 * 1000
// Wie lange der Sensor aktiv bleibt pro Messung (30 Sekunden für stabilen Wert)
private const val HR_SAMPLE_DURATION_MS = 30_000L

private const val PREFS_NAME = "health_sensor_prefs"
private const val KEY_INITIAL_STEPS = "initial_steps"
private const val KEY_STEPS_DATE = "steps_date"

/**
 * Liest Gesundheitsdaten direkt von den Sensoren der Wear OS Uhr.
 * - Puls (TYPE_HEART_RATE) — alle 15 Minuten gemessen
 * - SpO2 (vendor-spezifisch, falls verfügbar)
 * - Schritte (TYPE_STEP_COUNTER) → Kalorien-Berechnung (täglicher Reset)
 */
class HealthSensorManager(private val context: Context) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val handler = Handler(Looper.getMainLooper())

    @Volatile var heartRate: Int = 0
        private set
    @Volatile var spO2: Int = 0
        private set
    @Volatile var calories: Int = 0
        private set
    @Volatile var steps: Int = 0
        private set

    private var initialSteps: Int = -1
    private var isRunning = false

    private val heartRateSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE)
    private val stepSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)

    // Runnable zum periodischen Puls-Messen
    private val hrMeasureRunnable = object : Runnable {
        override fun run() {
            if (!isRunning) return
            startHeartRateSample()
            handler.postDelayed(this, HR_INTERVAL_MS)
        }
    }

    // Runnable zum Stoppen der Puls-Messung nach Sample-Dauer
    private val hrStopRunnable = Runnable {
        heartRateSensor?.let { sensorManager.unregisterListener(this, it) }
        Log.d(TAG, "Puls-Messung pausiert (nächste in 15 Min)")
    }

    fun start() {
        isRunning = true
        loadDailySteps()

        // Schrittzähler: dauerhaft aktiv
        stepSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
            Log.d(TAG, "Schrittzähler registriert")
        } ?: Log.w(TAG, "Kein Schrittzähler verfügbar")

        // Puls: sofort erste Messung, dann alle 15 Min
        heartRateSensor?.let {
            startHeartRateSample()
            handler.postDelayed(hrMeasureRunnable, HR_INTERVAL_MS)
            Log.d(TAG, "Pulssensor im 15-Min-Intervall gestartet")
        } ?: Log.w(TAG, "Kein Pulssensor verfügbar")
    }

    fun stop() {
        isRunning = false
        handler.removeCallbacks(hrMeasureRunnable)
        handler.removeCallbacks(hrStopRunnable)
        sensorManager.unregisterListener(this)
        Log.d(TAG, "Sensoren deregistriert")
    }

    private fun startHeartRateSample() {
        heartRateSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
            handler.postDelayed(hrStopRunnable, HR_SAMPLE_DURATION_MS)
            Log.d(TAG, "Puls-Messung gestartet (30s Sample)")
        }
    }

    /** Lädt den gespeicherten Tages-Baseline oder setzt ihn zurück bei neuem Tag. */
    private fun loadDailySteps() {
        val today = LocalDate.now().toString()
        val savedDate = prefs.getString(KEY_STEPS_DATE, null)
        if (savedDate == today) {
            initialSteps = prefs.getInt(KEY_INITIAL_STEPS, -1)
            Log.d(TAG, "Tages-Schritte geladen: initialSteps=$initialSteps")
        } else {
            // Neuer Tag → Reset
            initialSteps = -1
            prefs.edit().putString(KEY_STEPS_DATE, today).putInt(KEY_INITIAL_STEPS, -1).apply()
            Log.d(TAG, "Neuer Tag — Schritte-Baseline wird beim ersten Sensor-Event gesetzt")
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_HEART_RATE -> {
                val hr = event.values[0].toInt()
                if (hr > 0) heartRate = hr
            }
            Sensor.TYPE_STEP_COUNTER -> {
                val totalSteps = event.values[0].toInt()
                if (initialSteps < 0) {
                    initialSteps = totalSteps
                    prefs.edit()
                        .putInt(KEY_INITIAL_STEPS, initialSteps)
                        .putString(KEY_STEPS_DATE, LocalDate.now().toString())
                        .apply()
                }
                steps = totalSteps - initialSteps
                calories = (steps * KCAL_PER_STEP).toInt()
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
