package com.iosync.watchface.health

/**
 * Gemeinsamer In-Memory-Cache für Gesundheitsdaten.
 * Wird vom HealthPassiveDataService geschrieben und vom
 * HealthSensorManager / Renderer gelesen.
 */
object HealthDataCache {
    @Volatile var heartRate: Int = 0
    @Volatile var calories: Int = 0
    @Volatile var steps: Int = 0
    @Volatile var spO2: Int = 0
}
