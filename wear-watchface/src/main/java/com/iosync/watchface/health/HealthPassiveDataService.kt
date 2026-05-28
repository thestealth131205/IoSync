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
    }
}
