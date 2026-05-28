package com.iosync.watchface.health

import android.util.Log
import androidx.health.services.client.PassiveListenerService
import androidx.health.services.client.data.DataPointContainer
import androidx.health.services.client.data.DataType

private const val TAG = "HealthPassiveService"

/**
 * Empfängt Gesundheitsdaten im Hintergrund über die Health Services API.
 * Schreibt Herzfrequenz, Kalorien und Schritte in den HealthDataCache.
 *
 * Muss in AndroidManifest.xml als Service registriert sein.
 */
class HealthPassiveDataService : PassiveListenerService() {

    override fun onNewDataPointsReceived(dataPoints: DataPointContainer) {
        // Herzfrequenz (SampleDataType → SampleDataPoint<Double>)
        dataPoints.getData(DataType.HEART_RATE_BPM).lastOrNull()?.let { point ->
            val hr = point.value.toInt()
            if (hr > 0) {
                HealthDataCache.heartRate = hr
                Log.d(TAG, "Puls: $hr bpm")
            }
        }

        // Tageskalorien (DeltaDataType → IntervalDataPoint<Double>)
        dataPoints.getData(DataType.CALORIES_DAILY).lastOrNull()?.let { point ->
            val kcal = point.value.toInt()
            HealthDataCache.calories = kcal
            Log.d(TAG, "Kalorien: $kcal kcal")
        }

        // Tagesschritte (DeltaDataType → IntervalDataPoint<Long>)
        dataPoints.getData(DataType.STEPS_DAILY).lastOrNull()?.let { point ->
            val s = point.value.toInt()
            HealthDataCache.steps = s
            Log.d(TAG, "Schritte: $s")
        }

        // SpO2 (Sauerstoffsättigung)
        try {
            dataPoints.getData(DataType.OXYGEN_SATURATION).lastOrNull()?.let { point ->
                val o2 = point.value.toInt()
                if (o2 > 0) {
                    HealthDataCache.spO2 = o2
                    Log.d(TAG, "SpO2: $o2%")
                }
            }
        } catch (_: Exception) {
            // SpO2 nicht über Passive API verfügbar auf diesem Gerät
        }
    }
}
