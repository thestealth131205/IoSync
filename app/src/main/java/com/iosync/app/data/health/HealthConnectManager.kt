package com.iosync.app.data.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.BloodPressureRecord
import androidx.health.connect.client.records.BodyTemperatureRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.OxygenSaturationRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.reflect.KClass

/**
 * Datentyp-Info: eindeutiger Key, Name, Berechtigung und Record-Klasse
 */
data class HealthDataTypeInfo(
    val key: String,
    val displayName: String,
    val permission: String,
    val recordClass: KClass<*>,
    val available: Boolean = false,
    val sources: List<String> = emptyList(),        // App-Labels (für Anzeige)
    val sourcePackages: List<String> = emptyList()  // Package-Namen (für Filterung)
)

/**
 * Gesamtstatus der Health-Connect-Verfügbarkeit
 */
data class HealthConnectStatus(
    val sdkAvailable: Boolean = false,
    val clientAvailable: Boolean = false,
    val dataTypes: List<HealthDataTypeInfo> = emptyList()
)

@Singleton
class HealthConnectManager @Inject constructor(
    private val context: Context
) {
    private data class HealthTypeDef(
        val key: String,
        val displayName: String,
        val permission: String,
        val recordClass: KClass<*>
    )

    // Alle unterstützten Datentypen mit eindeutigem Key
    private val supportedTypes = listOf(
        HealthTypeDef("heart_rate",        "Herzfrequenz",        HealthPermission.getReadPermission(HeartRateRecord::class),             HeartRateRecord::class),
        HealthTypeDef("steps",             "Schritte",            HealthPermission.getReadPermission(StepsRecord::class),                 StepsRecord::class),
        HealthTypeDef("distance",          "Distanz (m)",         HealthPermission.getReadPermission(DistanceRecord::class),              DistanceRecord::class),
        HealthTypeDef("active_calories",   "Kalorien (aktiv)",    HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class),  ActiveCaloriesBurnedRecord::class),
        HealthTypeDef("total_calories",    "Kalorien (gesamt)",   HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class),   TotalCaloriesBurnedRecord::class),
        HealthTypeDef("oxygen_saturation", "Sauerstoffsättigung", HealthPermission.getReadPermission(OxygenSaturationRecord::class),      OxygenSaturationRecord::class),
        HealthTypeDef("body_temperature",  "Körpertemperatur",    HealthPermission.getReadPermission(BodyTemperatureRecord::class),       BodyTemperatureRecord::class),
        HealthTypeDef("blood_pressure",    "Blutdruck (syst.)",   HealthPermission.getReadPermission(BloodPressureRecord::class),         BloodPressureRecord::class),
        HealthTypeDef("sleep",             "Schlaf (Min.)",       HealthPermission.getReadPermission(SleepSessionRecord::class),          SleepSessionRecord::class),
        HealthTypeDef("exercise",          "Training (Min.)",     HealthPermission.getReadPermission(ExerciseSessionRecord::class),       ExerciseSessionRecord::class)
    )

    /** Alle benötigten Berechtigungen (inkl. Hintergrund-Lesezugriff für Watchface-Sync) */
    val allPermissions: Set<String> = supportedTypes.map { it.permission }.toSet() +
        setOf("android.permission.health.READ_HEALTH_DATA_IN_BACKGROUND")

    /** Prüft ob Health Connect auf dem Gerät verfügbar ist */
    fun isAvailable(): Boolean {
        val status = HealthConnectClient.getSdkStatus(context)
        if (status == HealthConnectClient.SDK_AVAILABLE) return true

        // Fallback: Auf Android 14+ ist Health Connect im System integriert
        // und getSdkStatus erkennt es manchmal nicht korrekt
        return try {
            HealthConnectClient.getOrCreate(context)
            true
        } catch (_: Exception) {
            false
        }
    }

    /** SDK-Status als lesbaren String */
    fun getSdkStatusText(): String {
        val status = HealthConnectClient.getSdkStatus(context)
        if (status == HealthConnectClient.SDK_AVAILABLE) return "Verfügbar"

        // Fallback-Prüfung für Android 14+ (System-integriert)
        return try {
            HealthConnectClient.getOrCreate(context)
            "Verfügbar"
        } catch (_: Exception) {
            when (status) {
                HealthConnectClient.SDK_UNAVAILABLE -> "Nicht verfügbar"
                HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> "Update erforderlich"
                else -> "Unbekannt"
            }
        }
    }

    /**
     * Fragt den vollständigen Health-Connect-Status ab:
     * - Welche Berechtigungen sind erteilt
     * - Welche Datentypen haben Daten (= Quellenapps vorhanden)
     */
    suspend fun queryStatus(): HealthConnectStatus = withContext(Dispatchers.IO) {
        val sdkAvailable = isAvailable()
        if (!sdkAvailable) {
            return@withContext HealthConnectStatus(sdkAvailable = false)
        }

        try {
            val client = HealthConnectClient.getOrCreate(context)
            val grantedPermissions = client.permissionController.getGrantedPermissions()

            val dataTypes = supportedTypes.map { def ->
                val granted = grantedPermissions.contains(def.permission)
                val (labels, packages) = if (granted) querySourcesForType(client, def.recordClass) else Pair(emptyList(), emptyList())
                HealthDataTypeInfo(
                    key = def.key,
                    displayName = def.displayName,
                    permission = def.permission,
                    recordClass = def.recordClass,
                    available = granted,
                    sources = labels,
                    sourcePackages = packages
                )
            }

            HealthConnectStatus(
                sdkAvailable = true,
                clientAvailable = true,
                dataTypes = dataTypes
            )
        } catch (e: Exception) {
            HealthConnectStatus(sdkAvailable = true, clientAvailable = false)
        }
    }

    @Suppress("UNCHECKED_CAST")
    private suspend fun querySourcesForType(
        client: HealthConnectClient,
        recordClass: KClass<*>
    ): Pair<List<String>, List<String>> {
        return try {
            val now = Instant.now()
            val dayAgo = now.minus(1, ChronoUnit.DAYS)
            val timeRange = TimeRangeFilter.between(dayAgo, now)

            // Package-Namen über die letzten 24h ermitteln
            val packages = when (recordClass) {
                HeartRateRecord::class -> {
                    val resp = client.readRecords(ReadRecordsRequest(HeartRateRecord::class, timeRange))
                    resp.records.mapNotNull { it.metadata.dataOrigin.packageName }.distinct()
                }
                StepsRecord::class -> {
                    val resp = client.readRecords(ReadRecordsRequest(StepsRecord::class, timeRange))
                    resp.records.mapNotNull { it.metadata.dataOrigin.packageName }.distinct()
                }
                DistanceRecord::class -> {
                    val resp = client.readRecords(ReadRecordsRequest(DistanceRecord::class, timeRange))
                    resp.records.mapNotNull { it.metadata.dataOrigin.packageName }.distinct()
                }
                ActiveCaloriesBurnedRecord::class -> {
                    val resp = client.readRecords(ReadRecordsRequest(ActiveCaloriesBurnedRecord::class, timeRange))
                    resp.records.mapNotNull { it.metadata.dataOrigin.packageName }.distinct()
                }
                TotalCaloriesBurnedRecord::class -> {
                    val resp = client.readRecords(ReadRecordsRequest(TotalCaloriesBurnedRecord::class, timeRange))
                    resp.records.mapNotNull { it.metadata.dataOrigin.packageName }.distinct()
                }
                OxygenSaturationRecord::class -> {
                    val resp = client.readRecords(ReadRecordsRequest(OxygenSaturationRecord::class, timeRange))
                    resp.records.mapNotNull { it.metadata.dataOrigin.packageName }.distinct()
                }
                BodyTemperatureRecord::class -> {
                    val resp = client.readRecords(ReadRecordsRequest(BodyTemperatureRecord::class, timeRange))
                    resp.records.mapNotNull { it.metadata.dataOrigin.packageName }.distinct()
                }
                BloodPressureRecord::class -> {
                    val resp = client.readRecords(ReadRecordsRequest(BloodPressureRecord::class, timeRange))
                    resp.records.mapNotNull { it.metadata.dataOrigin.packageName }.distinct()
                }
                SleepSessionRecord::class -> {
                    val resp = client.readRecords(ReadRecordsRequest(SleepSessionRecord::class, timeRange))
                    resp.records.mapNotNull { it.metadata.dataOrigin.packageName }.distinct()
                }
                ExerciseSessionRecord::class -> {
                    val resp = client.readRecords(ReadRecordsRequest(ExerciseSessionRecord::class, timeRange))
                    resp.records.mapNotNull { it.metadata.dataOrigin.packageName }.distinct()
                }
                else -> emptyList()
            }
            val labels = packages.map { getAppLabel(it) }
            Pair(labels, packages)
        } catch (_: Exception) {
            Pair(emptyList(), emptyList())
        }
    }

    /**
     * Liest den letzten Wert eines HC-Typs anhand seines Keys.
     * Gibt null zurück, wenn der Key unbekannt oder kein Wert vorhanden ist.
     * sourceFilter: optionaler Package-Name der Quell-App (leer = alle Quellen)
     */
    suspend fun readLatestValueByKey(key: String, sourceFilter: String = ""): Int? = when (key) {
        "heart_rate"        -> readLatestHeartRate(sourceFilter)
        "steps"             -> readTodaySteps(sourceFilter)
        "distance"          -> readTodayDistanceMeters(sourceFilter)
        "active_calories"   -> readTodayActiveCalories(sourceFilter)
        "total_calories"    -> readTodayCalories(sourceFilter)
        "oxygen_saturation" -> readLatestOxygenSaturation(sourceFilter)
        "body_temperature"  -> readLatestBodyTemperatureTenths(sourceFilter)
        "blood_pressure"    -> readLatestBloodPressureSystolic(sourceFilter)
        "sleep"             -> readTodaySleepMinutes(sourceFilter)
        "exercise"          -> readTodayExerciseMinutes(sourceFilter)
        else                -> null
    }

    /** Liest den letzten Herzfrequenz-Wert aus Health Connect (letzte 24h).
     *  sourceFilter: optionaler Package-Name der Quell-App (leer = alle Quellen) */
    suspend fun readLatestHeartRate(sourceFilter: String = ""): Int? = withContext(Dispatchers.IO) {
        try {
            val client = HealthConnectClient.getOrCreate(context)
            val now = Instant.now()
            val timeRange = TimeRangeFilter.between(now.minus(24, ChronoUnit.HOURS), now)
            val resp = client.readRecords(ReadRecordsRequest(HeartRateRecord::class, timeRange))
            val records = if (sourceFilter.isNotEmpty()) resp.records.filter { it.metadata.dataOrigin.packageName == sourceFilter } else resp.records
            records.lastOrNull()?.samples?.lastOrNull()?.beatsPerMinute?.toInt()
        } catch (_: Exception) { null }
    }

    /** Liest die Gesamtkalorien der letzten 24h aus Health Connect.
     *  sourceFilter: optionaler Package-Name der Quell-App (leer = alle Quellen) */
    suspend fun readTodayCalories(sourceFilter: String = ""): Int? = withContext(Dispatchers.IO) {
        try {
            val client = HealthConnectClient.getOrCreate(context)
            val now = Instant.now()
            val timeRange = TimeRangeFilter.between(now.minus(24, ChronoUnit.HOURS), now)
            val resp = client.readRecords(ReadRecordsRequest(TotalCaloriesBurnedRecord::class, timeRange))
            val records = if (sourceFilter.isNotEmpty()) resp.records.filter { it.metadata.dataOrigin.packageName == sourceFilter } else resp.records
            records.sumOf { it.energy.inKilocalories }.toInt().takeIf { it > 0 }
        } catch (_: Exception) { null }
    }

    /** Liest den letzten SpO2-Wert aus Health Connect (letzte 24h).
     *  sourceFilter: optionaler Package-Name der Quell-App (leer = alle Quellen) */
    suspend fun readLatestOxygenSaturation(sourceFilter: String = ""): Int? = withContext(Dispatchers.IO) {
        try {
            val client = HealthConnectClient.getOrCreate(context)
            val now = Instant.now()
            val timeRange = TimeRangeFilter.between(now.minus(24, ChronoUnit.HOURS), now)
            val resp = client.readRecords(ReadRecordsRequest(OxygenSaturationRecord::class, timeRange))
            val records = if (sourceFilter.isNotEmpty()) resp.records.filter { it.metadata.dataOrigin.packageName == sourceFilter } else resp.records
            records.lastOrNull()?.percentage?.value?.toInt()
        } catch (_: Exception) { null }
    }

    /**
     * Liest die Schlafdauer der letzten ~24h aus Health Connect in Minuten.
     * sourceFilter: optionaler Package-Name der Quell-App (leer = alle Quellen)
     */
    suspend fun readTodaySleepMinutes(sourceFilter: String = ""): Int? = withContext(Dispatchers.IO) {
        try {
            val client = HealthConnectClient.getOrCreate(context)
            val now = Instant.now()
            val timeRange = TimeRangeFilter.between(now.minus(24, ChronoUnit.HOURS), now)
            val resp = client.readRecords(ReadRecordsRequest(SleepSessionRecord::class, timeRange))
            val records = if (sourceFilter.isNotEmpty()) resp.records.filter { it.metadata.dataOrigin.packageName == sourceFilter } else resp.records
            records.sumOf { ChronoUnit.MINUTES.between(it.startTime, it.endTime) }
                .toInt()
                .takeIf { it > 0 }
        } catch (_: Exception) { null }
    }

    /** Liest die heutigen Schritte aus Health Connect.
     *  sourceFilter: optionaler Package-Name der Quell-App (leer = alle Quellen) */
    suspend fun readTodaySteps(sourceFilter: String = ""): Int? = withContext(Dispatchers.IO) {
        try {
            val client = HealthConnectClient.getOrCreate(context)
            val now = Instant.now()
            val timeRange = TimeRangeFilter.between(now.minus(24, ChronoUnit.HOURS), now)
            val resp = client.readRecords(ReadRecordsRequest(StepsRecord::class, timeRange))
            val records = if (sourceFilter.isNotEmpty()) resp.records.filter { it.metadata.dataOrigin.packageName == sourceFilter } else resp.records
            records.sumOf { it.count }.toInt().takeIf { it > 0 }
        } catch (_: Exception) { null }
    }

    /** Liest die heutige zurückgelegte Distanz in Metern aus Health Connect.
     *  sourceFilter: optionaler Package-Name der Quell-App (leer = alle Quellen) */
    suspend fun readTodayDistanceMeters(sourceFilter: String = ""): Int? = withContext(Dispatchers.IO) {
        try {
            val client = HealthConnectClient.getOrCreate(context)
            val now = Instant.now()
            val timeRange = TimeRangeFilter.between(now.minus(24, ChronoUnit.HOURS), now)
            val resp = client.readRecords(ReadRecordsRequest(DistanceRecord::class, timeRange))
            val records = if (sourceFilter.isNotEmpty()) resp.records.filter { it.metadata.dataOrigin.packageName == sourceFilter } else resp.records
            records.sumOf { it.distance.inMeters }.toInt().takeIf { it > 0 }
        } catch (_: Exception) { null }
    }

    /** Liest die aktiven Kalorien (ohne Grundumsatz) der letzten 24h aus Health Connect.
     *  sourceFilter: optionaler Package-Name der Quell-App (leer = alle Quellen) */
    suspend fun readTodayActiveCalories(sourceFilter: String = ""): Int? = withContext(Dispatchers.IO) {
        try {
            val client = HealthConnectClient.getOrCreate(context)
            val now = Instant.now()
            val timeRange = TimeRangeFilter.between(now.minus(24, ChronoUnit.HOURS), now)
            val resp = client.readRecords(ReadRecordsRequest(ActiveCaloriesBurnedRecord::class, timeRange))
            val records = if (sourceFilter.isNotEmpty()) resp.records.filter { it.metadata.dataOrigin.packageName == sourceFilter } else resp.records
            records.sumOf { it.energy.inKilocalories }.toInt().takeIf { it > 0 }
        } catch (_: Exception) { null }
    }

    /**
     * Liest die letzte Körpertemperatur aus Health Connect (letzte 24h).
     * Rückgabe in Zehntel-Grad (z. B. 366 = 36,6°C), damit kein Float-Verlust entsteht.
     * sourceFilter: optionaler Package-Name der Quell-App (leer = alle Quellen)
     */
    suspend fun readLatestBodyTemperatureTenths(sourceFilter: String = ""): Int? = withContext(Dispatchers.IO) {
        try {
            val client = HealthConnectClient.getOrCreate(context)
            val now = Instant.now()
            val timeRange = TimeRangeFilter.between(now.minus(24, ChronoUnit.HOURS), now)
            val resp = client.readRecords(ReadRecordsRequest(BodyTemperatureRecord::class, timeRange))
            val records = if (sourceFilter.isNotEmpty()) resp.records.filter { it.metadata.dataOrigin.packageName == sourceFilter } else resp.records
            records.lastOrNull()?.temperature?.inCelsius?.let { (it * 10).toInt() }
        } catch (_: Exception) { null }
    }

    /** Liest den letzten systolischen Blutdruckwert aus Health Connect (letzte 24h).
     *  sourceFilter: optionaler Package-Name der Quell-App (leer = alle Quellen) */
    suspend fun readLatestBloodPressureSystolic(sourceFilter: String = ""): Int? = withContext(Dispatchers.IO) {
        try {
            val client = HealthConnectClient.getOrCreate(context)
            val now = Instant.now()
            val timeRange = TimeRangeFilter.between(now.minus(24, ChronoUnit.HOURS), now)
            val resp = client.readRecords(ReadRecordsRequest(BloodPressureRecord::class, timeRange))
            val records = if (sourceFilter.isNotEmpty()) resp.records.filter { it.metadata.dataOrigin.packageName == sourceFilter } else resp.records
            records.lastOrNull()?.systolic?.inMillimetersOfMercury?.toInt()
        } catch (_: Exception) { null }
    }

    /** Liest die heutige Trainingsdauer in Minuten aus Health Connect.
     *  sourceFilter: optionaler Package-Name der Quell-App (leer = alle Quellen) */
    suspend fun readTodayExerciseMinutes(sourceFilter: String = ""): Int? = withContext(Dispatchers.IO) {
        try {
            val client = HealthConnectClient.getOrCreate(context)
            val now = Instant.now()
            val timeRange = TimeRangeFilter.between(now.minus(24, ChronoUnit.HOURS), now)
            val resp = client.readRecords(ReadRecordsRequest(ExerciseSessionRecord::class, timeRange))
            val records = if (sourceFilter.isNotEmpty()) resp.records.filter { it.metadata.dataOrigin.packageName == sourceFilter } else resp.records
            records.sumOf { ChronoUnit.MINUTES.between(it.startTime, it.endTime) }
                .toInt().takeIf { it > 0 }
        } catch (_: Exception) { null }
    }

    /** Package-Name in lesbaren App-Namen umwandeln */
    private fun getAppLabel(packageName: String): String {
        return try {
            val pm = context.packageManager
            val appInfo = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(appInfo).toString()
        } catch (_: Exception) {
            packageName.substringAfterLast(".")
        }
    }
}
