package com.iosync.watchface.health

/**
 * Gemeinsamer In-Memory-Cache für Gesundheitsdaten.
 * Wird vom HealthPassiveDataService geschrieben und vom
 * HealthSensorManager / Renderer gelesen.
 */
object HealthDataCache {
    @Volatile var heartRate: Int = 0
    /** Zeitstempel (System.currentTimeMillis) des letzten neuen HR-Werts vom Passive Listener. */
    @Volatile var lastHeartRateTimestamp: Long = 0L
    @Volatile var calories: Int = 0
    @Volatile var steps: Int = 0
    @Volatile var spO2: Int = 0
    @Volatile var sleepMinutes: Int = 0
}
