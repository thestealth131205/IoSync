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
 * Datentyp-Info: Name, Berechtigung und Record-Klasse
 */
data class HealthDataTypeInfo(
    val displayName: String,
    val permission: String,
    val recordClass: KClass<*>,
    val available: Boolean = false,
    val sources: List<String> = emptyList()
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
    // Alle unterstützten Datentypen
    private val supportedTypes = listOf(
        Triple("Herzfrequenz", HealthPermission.getReadPermission(HeartRateRecord::class), HeartRateRecord::class),
        Triple("Schritte", HealthPermission.getReadPermission(StepsRecord::class), StepsRecord::class),
        Triple("Distanz", HealthPermission.getReadPermission(DistanceRecord::class), DistanceRecord::class),
        Triple("Kalorien (aktiv)", HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class), ActiveCaloriesBurnedRecord::class),
        Triple("Kalorien (gesamt)", HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class), TotalCaloriesBurnedRecord::class),
        Triple("Sauerstoffsättigung", HealthPermission.getReadPermission(OxygenSaturationRecord::class), OxygenSaturationRecord::class),
        Triple("Körpertemperatur", HealthPermission.getReadPermission(BodyTemperatureRecord::class), BodyTemperatureRecord::class),
        Triple("Blutdruck", HealthPermission.getReadPermission(BloodPressureRecord::class), BloodPressureRecord::class),
        Triple("Schlaf", HealthPermission.getReadPermission(SleepSessionRecord::class), SleepSessionRecord::class),
        Triple("Training", HealthPermission.getReadPermission(ExerciseSessionRecord::class), ExerciseSessionRecord::class)
    )

    /** Alle benötigten Berechtigungen */
    val allPermissions: Set<String> = supportedTypes.map { it.second }.toSet()

    /** Prüft ob Health Connect auf dem Gerät verfügbar ist */
    fun isAvailable(): Boolean {
        return HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE
    }

    /** SDK-Status als lesbaren String */
    fun getSdkStatusText(): String {
        return when (HealthConnectClient.getSdkStatus(context)) {
            HealthConnectClient.SDK_AVAILABLE -> "Verfügbar"
            HealthConnectClient.SDK_UNAVAILABLE -> "Nicht verfügbar"
            HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> "Update erforderlich"
            else -> "Unbekannt"
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

            val dataTypes = supportedTypes.map { (name, permission, recordClass) ->
                val granted = grantedPermissions.contains(permission)
                val sources = if (granted) {
                    querySourcesForType(client, recordClass)
                } else {
                    emptyList()
                }
                HealthDataTypeInfo(
                    displayName = name,
                    permission = permission,
                    recordClass = recordClass,
                    available = granted,
                    sources = sources
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
    ): List<String> {
        return try {
            val now = Instant.now()
            val dayAgo = now.minus(1, ChronoUnit.DAYS)
            val timeRange = TimeRangeFilter.between(dayAgo, now)

            // Quellen über die letzten 24h ermitteln
            val sources = when (recordClass) {
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
            // Package-Namen in lesbaren App-Namen umwandeln
            sources.map { packageName -> getAppLabel(packageName) }
        } catch (_: Exception) {
            emptyList()
        }
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
