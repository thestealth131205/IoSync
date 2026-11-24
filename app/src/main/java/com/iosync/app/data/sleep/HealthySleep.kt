package com.iosync.app.data.sleep

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.iosync.app.BuildConfig
import com.iosync.app.R
import com.iosync.app.data.network.HomeAssistantClient
import com.iosync.app.data.network.MobileAppGoneException
import com.iosync.app.ui.viewmodel.MainViewModel
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Schlaf-/Wach-Status der Healthy-Uhr-App für Home Assistant.
 *
 * Weg: Healthy (Uhr) → IoSync-Watchface (HealthySleepStateProvider) → Data Layer →
 * [com.iosync.app.wear.WatchFaceTriggerListenerService] → [onStateFromWatch] → [HealthySleepHaWorker].
 *
 * In Home Assistant entsteht ein eigenes Gerät „Healthy“ mit echten Entitäten — bleiben nach einem
 * HA-Neustart erhalten und lassen sich in Automationen nutzen (z.B. Heizung absenken, solange man schläft).
 * Bevorzugt über die IoSync-Integration (HACS, github.com/thestealth131205/HA-IoSync); ist sie nicht
 * installiert, meldet sich IoSync wie die HA-Companion-App über die mobile_app-Integration an.
 */
object HealthySleep {

    // Letzter Stand von der Uhr
    val KEY_SLEEPING     = booleanPreferencesKey("healthy_sleep_sleeping")
    val KEY_CHANGED_AT   = longPreferencesKey("healthy_sleep_changed_at")
    val KEY_SLEEP_START  = longPreferencesKey("healthy_sleep_start")
    val KEY_SLEEP_END    = longPreferencesKey("healthy_sleep_end")

    // Home Assistant
    val KEY_HA_ENABLED   = booleanPreferencesKey("healthy_sleep_ha_enabled")
    val KEY_HA_DEVICE_ID = stringPreferencesKey("healthy_sleep_ha_device_id")
    /** Gilt auch nach einer Adressänderung weiter; kennt HA ihn nicht, wird neu angemeldet. */
    val KEY_HA_WEBHOOK   = stringPreferencesKey("healthy_sleep_ha_webhook")
    val KEY_HA_SENSORS   = intPreferencesKey("healthy_sleep_ha_sensors_version")
    val KEY_HA_STATUS    = stringPreferencesKey("healthy_sleep_ha_status")
    val KEY_HA_SYNCED_AT = longPreferencesKey("healthy_sleep_ha_synced_at")
    /** Weg der letzten erfolgreichen Übertragung: [VIA_INTEGRATION] oder [VIA_MOBILE_APP]. */
    val KEY_HA_VIA       = stringPreferencesKey("healthy_sleep_ha_via")

    const val VIA_INTEGRATION = "integration"
    const val VIA_MOBILE_APP = "mobile_app"

    /** Erhöhen, wenn sich Name/Typ/Icon einer Entität ändert → wird neu registriert. */
    const val SENSORS_VERSION = 1

    /** Entitäts-IDs, wie HA sie aus Gerätename „Healthy“ + Entitätsname bildet. */
    const val ENTITY_SLEEPING = "binary_sensor.healthy_schlaf"
    const val ENTITY_SLEEP_START = "sensor.healthy_eingeschlafen"
    const val ENTITY_SLEEP_END = "sensor.healthy_aufgewacht"

    /** Neue Meldung der Uhr speichern und sofort an Home Assistant weitergeben. */
    suspend fun onStateFromWatch(
        context: Context,
        dataStore: DataStore<Preferences>,
        sleeping: Boolean,
        changedAt: Long,
        sleepStart: Long?,
        sleepEnd: Long?
    ) {
        dataStore.edit { prefs ->
            // Verspätet zugestellte ältere Meldung nicht über eine neuere schreiben.
            if ((prefs[KEY_CHANGED_AT] ?: 0L) > changedAt) return@edit
            prefs[KEY_SLEEPING] = sleeping
            prefs[KEY_CHANGED_AT] = changedAt
            // Beim Einschlafen bleibt die letzte Aufwachzeit stehen (und umgekehrt).
            sleepStart?.let { prefs[KEY_SLEEP_START] = it }
            sleepEnd?.let { prefs[KEY_SLEEP_END] = it }
        }
        HealthySleepHaWorker.enqueue(context)
    }

    /** Eine mobile_app-Entität; [update] enthält nur, was update_sensor_states erlaubt. */
    internal class Sensor(
        val uniqueId: String,
        private val type: String,
        private val name: String,
        private val icon: String,
        private val deviceClass: String?,
        private val state: Any?,
        private val attributes: JSONObject = JSONObject()
    ) {
        fun update(): JSONObject = JSONObject()
            .put("unique_id", uniqueId)
            .put("type", type)
            .put("state", state ?: JSONObject.NULL)
            .put("icon", icon)
            .put("attributes", attributes)

        fun registration(): JSONObject = update()
            .put("name", name)
            .apply { deviceClass?.let { put("device_class", it) } }

        /** Format der IoSync-Integration (POST /api/iosync/update). */
        fun iosync(): JSONObject = JSONObject()
            .put("key", uniqueId)
            .put("type", type)
            .put("name", name)
            .put("state", state ?: JSONObject.NULL)
            .put("icon", icon)
            .put("device_class", deviceClass ?: JSONObject.NULL)
            .put("attributes", attributes)
    }

    internal fun sensors(prefs: Preferences): List<Sensor> {
        val sleeping = prefs[KEY_SLEEPING] ?: false
        val start = iso(prefs[KEY_SLEEP_START])
        val end = iso(prefs[KEY_SLEEP_END])
        return listOf(
            Sensor(
                uniqueId = "healthy_sleeping", type = "binary_sensor", name = "Schlaf",
                icon = if (sleeping) "mdi:sleep" else "mdi:sleep-off", deviceClass = null, state = sleeping,
                attributes = JSONObject()
                    .put("status", if (sleeping) "schläft" else "wach")
                    .put("geaendert", iso(prefs[KEY_CHANGED_AT]) ?: JSONObject.NULL)
                    .put("eingeschlafen", start ?: JSONObject.NULL)
                    .put("aufgewacht", end ?: JSONObject.NULL)
            ),
            Sensor("healthy_sleep_start", "sensor", "Eingeschlafen", "mdi:bed-clock", "timestamp", start),
            Sensor("healthy_sleep_end", "sensor", "Aufgewacht", "mdi:weather-sunset-up", "timestamp", end)
        )
    }

    internal fun registration(deviceId: String): JSONObject = JSONObject()
        .put("device_id", deviceId)
        .put("app_id", BuildConfig.APPLICATION_ID)
        .put("app_name", "IoSync")
        .put("app_version", BuildConfig.VERSION_NAME)
        .put("device_name", "Healthy")
        .put("manufacturer", "letheapp")
        .put("model", "Schlafstatus Uhr")
        .put("os_name", "Wear OS")
        .put("supports_encryption", false)

    /** Nutzt dieselbe Geräte-ID wie die mobile_app-Anmeldung. */
    internal fun iosyncPayload(deviceId: String, prefs: Preferences): JSONObject = JSONObject()
        .put("device", JSONObject()
            .put("id", deviceId)
            .put("name", "Healthy")
            .put("manufacturer", "letheapp")
            .put("model", "Schlafstatus Uhr")
            .put("sw_version", BuildConfig.VERSION_NAME))
        .put("sensors", JSONArray(sensors(prefs).map { it.iosync() }))

    private fun iso(millis: Long?): String? =
        millis?.takeIf { it > 0 }?.let { Instant.ofEpochMilli(it).truncatedTo(ChronoUnit.SECONDS).toString() }
}

/**
 * Überträgt den letzten Schlafstatus an Home Assistant. Liest den Stand erst beim Ausführen —
 * mehrere Meldungen kurz nacheinander ergeben eine Übertragung mit dem neuesten Stand.
 * Expedited, damit es auch nachts im Doze-Modus sofort rausgeht; ohne Netz/HA wird wiederholt.
 */
class HealthySleepHaWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Dependencies {
        fun dataStore(): DataStore<Preferences>
        fun homeAssistantClient(): HomeAssistantClient
    }

    override suspend fun doWork(): Result {
        val deps = EntryPointAccessors.fromApplication(applicationContext, Dependencies::class.java)
        val dataStore = deps.dataStore()
        val ha = deps.homeAssistantClient()
        val prefs = dataStore.data.first()

        if (prefs[HealthySleep.KEY_HA_ENABLED] == false) return Result.success()
        if (prefs[HealthySleep.KEY_CHANGED_AT] == null) return Result.success()   // noch keine Meldung der Uhr
        val host  = prefs[MainViewModel.KEY_HA_HOST].orEmpty()
        val port  = prefs[MainViewModel.KEY_HA_PORT] ?: 8123
        val https = prefs[MainViewModel.KEY_HA_USE_HTTPS] ?: false
        val token = prefs[MainViewModel.KEY_HA_TOKEN].orEmpty()
        if (host.isBlank() || token.isBlank()) {
            setStatus(dataStore, "Home Assistant nicht eingerichtet (Host/Token fehlt)")
            return Result.success()
        }

        val sensors = HealthySleep.sensors(prefs)
        var webhookId = prefs[HealthySleep.KEY_HA_WEBHOOK]
        var registered = webhookId != null && prefs[HealthySleep.KEY_HA_SENSORS] == HealthySleep.SENSORS_VERSION

        return try {
            val deviceId = prefs[HealthySleep.KEY_HA_DEVICE_ID] ?: UUID.randomUUID().toString().also { id ->
                dataStore.edit { it[HealthySleep.KEY_HA_DEVICE_ID] = id }
            }
            // Bevorzugt über die IoSync-Integration; ohne sie (HTTP 404) wie bisher über mobile_app.
            if (ha.iosyncUpdate(host, port, https, token, HealthySleep.iosyncPayload(deviceId, prefs)).getOrThrow()) {
                setStatus(dataStore, null, HealthySleep.VIA_INTEGRATION)
                return Result.success()
            }
            // Zweite Runde nur, wenn HA das Gerät nicht (mehr) kennt: gelöscht oder andere HA-Instanz.
            for (round in 0..1) {
                val id = webhookId ?: run {
                    val newId = ha.registerMobileApp(host, port, https, token, HealthySleep.registration(deviceId)).getOrThrow()
                    dataStore.edit {
                        it[HealthySleep.KEY_HA_WEBHOOK] = newId
                        it.remove(HealthySleep.KEY_HA_SENSORS)
                    }
                    registered = false
                    newId
                }
                webhookId = id
                try {
                    if (registered) {
                        val result = ha.mobileAppWebhook(host, port, https, id, "update_sensor_states",
                            JSONArray(sensors.map { it.update() })).getOrThrow()
                        if (result == null) throw MobileAppGoneException()
                        // In HA gelöschte Entität → "not_registered" → neu registrieren
                        registered = result is JSONObject && sensors.all { s ->
                            result.optJSONObject(s.uniqueId)?.optBoolean("success") == true
                        }
                    }
                    if (!registered) {
                        sensors.forEach { s ->
                            val result = ha.mobileAppWebhook(host, port, https, id, "register_sensor", s.registration()).getOrThrow()
                            if (result == null) throw MobileAppGoneException()
                            check(result is JSONObject && result.optBoolean("success")) { "register_sensor abgelehnt: $result" }
                        }
                        dataStore.edit { it[HealthySleep.KEY_HA_SENSORS] = HealthySleep.SENSORS_VERSION }
                    }
                    setStatus(dataStore, null, HealthySleep.VIA_MOBILE_APP)
                    return Result.success()
                } catch (e: MobileAppGoneException) {
                    if (round == 1) throw e
                    webhookId = null
                }
            }
            Result.success()
        } catch (e: CancellationException) {
            throw e   // durch neueren Stand ersetzt (REPLACE)
        } catch (e: Exception) {
            val retry = runAttemptCount < MAX_ATTEMPTS
            setStatus(dataStore, "Fehler: ${e.message ?: e.javaClass.simpleName}" + if (retry) " – neuer Versuch folgt" else "")
            if (retry) Result.retry() else Result.failure()
        }
    }

    /** [error] null = erfolgreich übertragen, [via] = genutzter Weg. */
    private suspend fun setStatus(dataStore: DataStore<Preferences>, error: String?, via: String? = null) {
        dataStore.edit {
            if (error == null) {
                it.remove(HealthySleep.KEY_HA_STATUS)
                it[HealthySleep.KEY_HA_SYNCED_AT] = System.currentTimeMillis()
                via?.let { v -> it[HealthySleep.KEY_HA_VIA] = v }
            } else {
                it[HealthySleep.KEY_HA_STATUS] = error
            }
        }
    }

    /** Nur bis Android 11 nötig: dort läuft Expedited-Arbeit als Vordergrunddienst. */
    override suspend fun getForegroundInfo(): ForegroundInfo {
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "IoSync Hintergrund-Sync", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setContentTitle("IoSync")
            .setContentText("Schlafstatus an Home Assistant")
            .setSmallIcon(R.drawable.ic_notification)
            .setSilent(true)
            .build()
        return ForegroundInfo(NOTIFICATION_ID, notification)
    }

    companion object {
        private const val WORK_NAME = "healthy_sleep_ha"
        private const val CHANNEL_ID = "iosync_channel"
        private const val NOTIFICATION_ID = 1007
        private const val MAX_ATTEMPTS = 12

        fun enqueue(context: Context) {
            val request = OneTimeWorkRequestBuilder<HealthySleepHaWorker>()
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            // Neuer Stand ersetzt einen wartenden Wiederholversuch → geht sofort raus.
            WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request)
        }
    }
}
