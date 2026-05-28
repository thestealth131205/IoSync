package com.iosync.app.wear

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.NodeClient
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.iosync.app.data.model.SmartHomeState
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "WearDataLayerService"

// ── Data Layer Pfade ──────────────────────────────────────────────────────────
private const val PATH_STATES         = "/iosync/smarthome/states"
private const val PATH_SINGLE_STATE   = "/iosync/smarthome/state"
private const val PATH_SETTINGS       = "/iosync/smarthome/settings"
private const val PATH_WATCHFACE_CONFIG = "/iosync/watchface/config"
private const val PATH_PHONE_BATTERY  = "/iosync/phone/battery"

// ── Allgemeine Keys ───────────────────────────────────────────────────────────
private const val KEY_STATES_JSON    = "states_json"
private const val KEY_STATE_ID       = "state_id"
private const val KEY_STATE_JSON     = "state_json"
private const val KEY_TIMESTAMP      = "timestamp"
private const val KEY_HOST           = "host"
private const val KEY_PORT           = "port"

// ── Watchface-Konfigurationsschlüssel ─────────────────────────────────────────
private const val KEY_WF_TIME_COLOR          = "wf_time_color"
private const val KEY_WF_DATE_COLOR          = "wf_date_color"
private const val KEY_WF_SHOW_SECONDS        = "wf_show_seconds"
private const val KEY_WF_SHOW_TICKS          = "wf_show_ticks"
private const val KEY_WF_SHOW_WEEKDAY        = "wf_show_weekday"
private const val KEY_WF_SHOW_PHONE_BATTERY  = "wf_show_phone_battery"
private const val KEY_WF_SHOW_IOBROKER_DATA  = "wf_show_iobroker_data"
private const val KEY_WF_SHOW_SECONDS_RING   = "wf_show_seconds_ring"
private const val KEY_WF_SECONDS_RING_COLOR  = "wf_seconds_ring_color"
private const val KEY_WF_SECONDS_RING_WIDTH   = "wf_seconds_ring_width"
private const val KEY_WF_SECONDS_GLOW_WIDTH   = "wf_seconds_glow_width"
private const val KEY_WF_SECONDS_NUMBER_COLOR = "wf_seconds_number_color"

// ── Gesundheits-/Wetter-Konfigurationsschlüssel ──────────────────────────────
private const val KEY_WF_SHOW_WEATHER     = "wf_show_weather"
private const val KEY_WF_SHOW_HEART_RATE  = "wf_show_heart_rate"
private const val KEY_WF_SHOW_OXYGEN      = "wf_show_oxygen"
private const val KEY_WF_SHOW_CALORIES    = "wf_show_calories"

// ── Wetter-Daten-Pfad ─────────────────────────────────────────────────────────
private const val PATH_WEATHER            = "/iosync/watchface/weather"
private const val KEY_WEATHER_TEMP        = "weather_temp"
private const val KEY_WEATHER_CONDITION   = "weather_condition"

// ── Custom ioBroker-Slots ─────────────────────────────────────────────────────
private const val KEY_WF_CUSTOM_SLOT1_LABEL = "wf_custom_slot1_label"
private const val KEY_WF_CUSTOM_SLOT1_VALUE = "wf_custom_slot1_value"
private const val KEY_WF_CUSTOM_SLOT2_LABEL = "wf_custom_slot2_label"
private const val KEY_WF_CUSTOM_SLOT2_VALUE = "wf_custom_slot2_value"
private const val KEY_WF_CUSTOM_SLOT3_LABEL = "wf_custom_slot3_label"
private const val KEY_WF_CUSTOM_SLOT3_VALUE = "wf_custom_slot3_value"
private const val KEY_WF_CUSTOM_SLOT4_LABEL = "wf_custom_slot4_label"
private const val KEY_WF_CUSTOM_SLOT4_VALUE = "wf_custom_slot4_value"
private const val KEY_WF_CUSTOM_SLOT4_BAR_COLOR = "wf_custom_slot4_bar_color"
private const val KEY_WF_CUSTOM_SLOT4_BAR_MIN        = "wf_custom_slot4_bar_min"
private const val KEY_WF_CUSTOM_SLOT4_BAR_MAX        = "wf_custom_slot4_bar_max"
private const val KEY_WF_CUSTOM_SLOT4_BAR_SHOW_LABEL = "wf_custom_slot4_bar_show_label"
private const val KEY_WF_SHOW_CUSTOM_SLOTS  = "wf_show_custom_slots"
private const val KEY_WF_HR_TEXT_SCALE        = "wf_hr_text_scale"
private const val KEY_WF_KCAL_TEXT_SCALE      = "wf_kcal_text_scale"
private const val KEY_WF_SLOT1_TEXT_SCALE     = "wf_slot1_text_scale"
private const val KEY_WF_SLOT2_TEXT_SCALE     = "wf_slot2_text_scale"
private const val KEY_WF_SLOT3_TEXT_SCALE     = "wf_slot3_text_scale"
private const val KEY_WF_SLOT4_TEXT_SCALE     = "wf_slot4_text_scale"
private const val KEY_WF_WEATHER_TEXT_SCALE   = "wf_weather_text_scale"
private const val KEY_WF_SUNRISE_TEXT_SCALE   = "wf_sunrise_text_scale"
private const val PATH_CUSTOM_SLOTS           = "/iosync/watchface/custom_slots"

// ── Aktions-Pille-Konfigurationsschlüssel ─────────────────────────────────────
private const val KEY_WF_ACTION_PILL_ENABLED     = "wf_action_pill_enabled"
private const val KEY_WF_ACTION_PILL_COLOR_TRUE  = "wf_action_pill_color_true"
private const val KEY_WF_ACTION_PILL_COLOR_FALSE = "wf_action_pill_color_false"
private const val KEY_WF_ACTION_PILL_IOBROKER_ID = "wf_action_pill_iobroker_id"
private const val KEY_WF_ACTION_PILL_VALUE_MODE  = "wf_action_pill_value_mode"
private const val KEY_WF_ACTION_PILL_FIXED_VALUE = "wf_action_pill_fixed_value"
private const val KEY_WF_ACTION_PILL_STATE       = "wf_action_pill_state"

// ── Aktions-Pille Status-Pfad ─────────────────────────────────────────────────
private const val PATH_ACTION_PILL_STATE = "/iosync/watchface/action_pill_state"
private const val KEY_PILL_STATE         = "pill_state"

// ── Akku-Keys ─────────────────────────────────────────────────────────────────
private const val KEY_BATTERY_LEVEL   = "battery_level"
private const val KEY_IS_CHARGING     = "is_charging"

@Singleton
class WearDataLayerService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val moshi: Moshi
) {

    private val dataClient: DataClient by lazy { Wearable.getDataClient(context) }
    private val nodeClient: NodeClient by lazy { Wearable.getNodeClient(context) }

    /**
     * Prüft ob mindestens eine Wear OS Uhr per Bluetooth verbunden ist.
     */
    suspend fun isWatchConnected(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val nodes = nodeClient.connectedNodes.await()
                nodes.isNotEmpty()
            } catch (e: Exception) {
                Log.e(TAG, "isWatchConnected fehlgeschlagen", e)
                false
            }
        }
    }

    private val statesListAdapter by lazy {
        moshi.adapter<List<SmartHomeState>>(
            Types.newParameterizedType(List::class.java, SmartHomeState::class.java)
        )
    }

    private val stateAdapter by lazy {
        moshi.adapter(SmartHomeState::class.java)
    }

    /**
     * Serialisiert die vollständige SmartHomeState-Liste und schreibt sie in den
     * Wearable Data Layer. Das Watchface und die Wear-App abonnieren diesen Pfad.
     */
    suspend fun syncStatesToWear(states: List<SmartHomeState>) {
        withContext(Dispatchers.IO) {
            try {
                val json = statesListAdapter.toJson(states)
                val request = PutDataMapRequest.create(PATH_STATES).apply {
                    dataMap.putString(KEY_STATES_JSON, json)
                    dataMap.putLong(KEY_TIMESTAMP, System.currentTimeMillis())
                }.asPutDataRequest().setUrgent()
                dataClient.putDataItem(request).await()
                Log.d(TAG, "${states.size} States an Wear OS übertragen")
            } catch (e: Exception) {
                Log.e(TAG, "syncStatesToWear fehlgeschlagen", e)
            }
        }
    }

    /**
     * Schreibt ein einzelnes State-Update in den Data Layer.
     * Nützlich für hochfrequente Updates eines bestimmten Datenpunkts.
     */
    suspend fun syncSingleStateToWear(state: SmartHomeState) {
        withContext(Dispatchers.IO) {
            try {
                val json = stateAdapter.toJson(state)
                val request = PutDataMapRequest.create("$PATH_SINGLE_STATE/${state.id}").apply {
                    dataMap.putString(KEY_STATE_ID, state.id)
                    dataMap.putString(KEY_STATE_JSON, json)
                    dataMap.putLong(KEY_TIMESTAMP, System.currentTimeMillis())
                }.asPutDataRequest().setUrgent()
                dataClient.putDataItem(request).await()
                Log.d(TAG, "Einzelner State '${state.id}' an Wear OS übertragen")
            } catch (e: Exception) {
                Log.e(TAG, "syncSingleStateToWear fehlgeschlagen", e)
            }
        }
    }

    /**
     * Überträgt Verbindungseinstellungen (Host, Port) an die Wear-App.
     */
    suspend fun syncSettingsToWear(host: String, port: Int) {
        withContext(Dispatchers.IO) {
            try {
                val request = PutDataMapRequest.create(PATH_SETTINGS).apply {
                    dataMap.putString(KEY_HOST, host)
                    dataMap.putInt(KEY_PORT, port)
                    dataMap.putLong(KEY_TIMESTAMP, System.currentTimeMillis())
                }.asPutDataRequest().setUrgent()
                dataClient.putDataItem(request).await()
                Log.d(TAG, "Einstellungen (host=$host, port=$port) an Wear OS übertragen")
            } catch (e: Exception) {
                Log.e(TAG, "syncSettingsToWear fehlgeschlagen", e)
            }
        }
    }

    /**
     * Überträgt die vollständige Watchface-Konfiguration an die Uhr.
     * Enthält Darstellungsoptionen sowie Schalter für Akkuanzeige und ioBroker-Daten.
     */
    suspend fun syncWatchFaceConfigToWear(
        timeColor: String,
        dateColor: String,
        showSeconds: Boolean,
        showTicks: Boolean,
        showWeekday: Boolean,
        showPhoneBattery: Boolean = false,
        showIoBrokerData: Boolean = false,
        showSecondsRing: Boolean = false,
        secondsRingColor: String = "neon_yellow",
        secondsRingWidth: Int = 5,
        secondsGlowWidth: Int = 100,
        secondsNumberColor: String = "dim_time",
        actionPillEnabled: Boolean = false,
        actionPillColorTrue: String = "cyan",
        actionPillColorFalse: String = "red",
        actionPillIoBrokerId: String = "",
        actionPillValueMode: String = "toggle",
        actionPillFixedValue: String = "",
        actionPillState: Boolean = false,
        showWeather: Boolean = true,
        showHeartRate: Boolean = true,
        showOxygen: Boolean = false,
        showCalories: Boolean = true,
        showCustomSlots: Boolean = false,
        customSlot1Label: String = "",
        customSlot2Label: String = "",
        customSlot3Label: String = "",
        customSlot4Label: String = "",
        customSlot4BarColor: String = "neon_yellow",
        customSlot4BarMin: Float = 0f,
        customSlot4BarMax: Float = 100f,
        customSlot4BarShowLabel: Boolean = true,
        hrTextScale: Int = 100,
        kcalTextScale: Int = 100,
        slot1TextScale: Int = 100,
        slot2TextScale: Int = 100,
        slot3TextScale: Int = 100,
        slot4TextScale: Int = 100,
        weatherTextScale: Int = 100,
        sunriseTextScale: Int = 100
    ) {
        withContext(Dispatchers.IO) {
            try {
                val request = PutDataMapRequest.create(PATH_WATCHFACE_CONFIG).apply {
                    dataMap.putString(KEY_WF_TIME_COLOR, timeColor)
                    dataMap.putString(KEY_WF_DATE_COLOR, dateColor)
                    dataMap.putBoolean(KEY_WF_SHOW_SECONDS, showSeconds)
                    dataMap.putBoolean(KEY_WF_SHOW_TICKS, showTicks)
                    dataMap.putBoolean(KEY_WF_SHOW_WEEKDAY, showWeekday)
                    dataMap.putBoolean(KEY_WF_SHOW_PHONE_BATTERY, showPhoneBattery)
                    dataMap.putBoolean(KEY_WF_SHOW_IOBROKER_DATA, showIoBrokerData)
                    dataMap.putBoolean(KEY_WF_SHOW_SECONDS_RING, showSecondsRing)
                    dataMap.putString(KEY_WF_SECONDS_RING_COLOR, secondsRingColor)
                    dataMap.putInt(KEY_WF_SECONDS_RING_WIDTH, secondsRingWidth)
                    dataMap.putInt(KEY_WF_SECONDS_GLOW_WIDTH, secondsGlowWidth)
                    dataMap.putString(KEY_WF_SECONDS_NUMBER_COLOR, secondsNumberColor)
                    dataMap.putBoolean(KEY_WF_SHOW_WEATHER, showWeather)
                    dataMap.putBoolean(KEY_WF_SHOW_HEART_RATE, showHeartRate)
                    dataMap.putBoolean(KEY_WF_SHOW_OXYGEN, showOxygen)
                    dataMap.putBoolean(KEY_WF_SHOW_CALORIES, showCalories)
                    dataMap.putBoolean(KEY_WF_ACTION_PILL_ENABLED, actionPillEnabled)
                    dataMap.putString(KEY_WF_ACTION_PILL_COLOR_TRUE, actionPillColorTrue)
                    dataMap.putString(KEY_WF_ACTION_PILL_COLOR_FALSE, actionPillColorFalse)
                    dataMap.putString(KEY_WF_ACTION_PILL_IOBROKER_ID, actionPillIoBrokerId)
                    dataMap.putString(KEY_WF_ACTION_PILL_VALUE_MODE, actionPillValueMode)
                    dataMap.putString(KEY_WF_ACTION_PILL_FIXED_VALUE, actionPillFixedValue)
                    dataMap.putBoolean(KEY_WF_ACTION_PILL_STATE, actionPillState)
                    dataMap.putBoolean(KEY_WF_SHOW_CUSTOM_SLOTS, showCustomSlots)
                    dataMap.putString(KEY_WF_CUSTOM_SLOT1_LABEL, customSlot1Label)
                    dataMap.putString(KEY_WF_CUSTOM_SLOT2_LABEL, customSlot2Label)
                    dataMap.putString(KEY_WF_CUSTOM_SLOT3_LABEL, customSlot3Label)
                    dataMap.putString(KEY_WF_CUSTOM_SLOT4_LABEL, customSlot4Label)
                    dataMap.putString(KEY_WF_CUSTOM_SLOT4_BAR_COLOR, customSlot4BarColor)
                    dataMap.putFloat(KEY_WF_CUSTOM_SLOT4_BAR_MIN, customSlot4BarMin)
                    dataMap.putFloat(KEY_WF_CUSTOM_SLOT4_BAR_MAX, customSlot4BarMax)
                    dataMap.putBoolean(KEY_WF_CUSTOM_SLOT4_BAR_SHOW_LABEL, customSlot4BarShowLabel)
                    dataMap.putInt(KEY_WF_HR_TEXT_SCALE,    hrTextScale)
                    dataMap.putInt(KEY_WF_KCAL_TEXT_SCALE,  kcalTextScale)
                    dataMap.putInt(KEY_WF_SLOT1_TEXT_SCALE, slot1TextScale)
                    dataMap.putInt(KEY_WF_SLOT2_TEXT_SCALE, slot2TextScale)
                    dataMap.putInt(KEY_WF_SLOT3_TEXT_SCALE, slot3TextScale)
                    dataMap.putInt(KEY_WF_SLOT4_TEXT_SCALE,    slot4TextScale)
                    dataMap.putInt(KEY_WF_WEATHER_TEXT_SCALE, weatherTextScale)
                    dataMap.putInt(KEY_WF_SUNRISE_TEXT_SCALE, sunriseTextScale)
                    dataMap.putLong(KEY_TIMESTAMP, System.currentTimeMillis())
                }.asPutDataRequest().setUrgent()
                dataClient.putDataItem(request).await()
                Log.d(TAG, "Watchface-Konfiguration an Wear OS übertragen")
            } catch (e: Exception) {
                Log.e(TAG, "syncWatchFaceConfigToWear fehlgeschlagen", e)
                throw e
            }
        }
    }

    /**
     * Überträgt aktuelle Wetterdaten an das Watchface.
     */
    suspend fun syncWeatherToWear(temperature: Int, condition: String) {
        withContext(Dispatchers.IO) {
            try {
                val request = PutDataMapRequest.create(PATH_WEATHER).apply {
                    dataMap.putInt(KEY_WEATHER_TEMP, temperature)
                    dataMap.putString(KEY_WEATHER_CONDITION, condition)
                    dataMap.putLong(KEY_TIMESTAMP, System.currentTimeMillis())
                }.asPutDataRequest().setUrgent()
                dataClient.putDataItem(request).await()
                Log.d(TAG, "Wetter (${temperature}°C, $condition) an Wear OS übertragen")
            } catch (e: Exception) {
                Log.e(TAG, "syncWeatherToWear fehlgeschlagen", e)
            }
        }
    }

    /**
     * Überträgt nur den aktuellen Aktions-Pille-Status an das Watchface.
     * Wird nach einem Trigger-Befehl aufgerufen, um den neuen Zustand anzuzeigen.
     */
    suspend fun syncActionPillStateToWear(state: Boolean) {
        withContext(Dispatchers.IO) {
            try {
                val request = PutDataMapRequest.create(PATH_ACTION_PILL_STATE).apply {
                    dataMap.putBoolean(KEY_PILL_STATE, state)
                    dataMap.putLong(KEY_TIMESTAMP, System.currentTimeMillis())
                }.asPutDataRequest().setUrgent()
                dataClient.putDataItem(request).await()
                Log.d(TAG, "Aktions-Pille Status ($state) an Wear OS übertragen")
            } catch (e: Exception) {
                Log.e(TAG, "syncActionPillStateToWear fehlgeschlagen", e)
            }
        }
    }

    /**
     * Überträgt die aktuellen Werte der Custom ioBroker-Slots ans Watchface.
     */
    suspend fun syncCustomSlotsToWear(
        slot1Label: String, slot1Value: String,
        slot2Label: String, slot2Value: String,
        slot3Label: String = "", slot3Value: String = "--",
        slot4Label: String = "", slot4Value: String = "--",
        slot4BarColor: String = "neon_yellow",
        slot4BarMin: Float = 0f, slot4BarMax: Float = 100f,
        slot4BarShowLabel: Boolean = true
    ) {
        withContext(Dispatchers.IO) {
            try {
                val request = PutDataMapRequest.create(PATH_CUSTOM_SLOTS).apply {
                    dataMap.putString(KEY_WF_CUSTOM_SLOT1_LABEL, slot1Label)
                    dataMap.putString(KEY_WF_CUSTOM_SLOT1_VALUE, slot1Value)
                    dataMap.putString(KEY_WF_CUSTOM_SLOT2_LABEL, slot2Label)
                    dataMap.putString(KEY_WF_CUSTOM_SLOT2_VALUE, slot2Value)
                    dataMap.putString(KEY_WF_CUSTOM_SLOT3_LABEL, slot3Label)
                    dataMap.putString(KEY_WF_CUSTOM_SLOT3_VALUE, slot3Value)
                    dataMap.putString(KEY_WF_CUSTOM_SLOT4_LABEL, slot4Label)
                    dataMap.putString(KEY_WF_CUSTOM_SLOT4_VALUE, slot4Value)
                    dataMap.putString(KEY_WF_CUSTOM_SLOT4_BAR_COLOR, slot4BarColor)
                    dataMap.putFloat(KEY_WF_CUSTOM_SLOT4_BAR_MIN, slot4BarMin)
                    dataMap.putFloat(KEY_WF_CUSTOM_SLOT4_BAR_MAX, slot4BarMax)
                    dataMap.putBoolean(KEY_WF_CUSTOM_SLOT4_BAR_SHOW_LABEL, slot4BarShowLabel)
                    dataMap.putLong(KEY_TIMESTAMP, System.currentTimeMillis())
                }.asPutDataRequest().setUrgent()
                dataClient.putDataItem(request).await()
                Log.d(TAG, "Custom-Slot-Daten an Wear OS übertragen")
            } catch (e: Exception) {
                Log.e(TAG, "syncCustomSlotsToWear fehlgeschlagen", e)
            }
        }
    }

    /**
     * Überträgt den aktuellen Handy-Akkustand ans Watchface.
     * @param level      Akkustand in Prozent (0–100)
     * @param isCharging true wenn das Gerät gerade geladen wird
     */
    suspend fun syncPhoneBatteryToWear(level: Int, isCharging: Boolean) {
        withContext(Dispatchers.IO) {
            try {
                val request = PutDataMapRequest.create(PATH_PHONE_BATTERY).apply {
                    dataMap.putInt(KEY_BATTERY_LEVEL, level)
                    dataMap.putBoolean(KEY_IS_CHARGING, isCharging)
                    dataMap.putLong(KEY_TIMESTAMP, System.currentTimeMillis())
                }.asPutDataRequest().setUrgent()
                dataClient.putDataItem(request).await()
                Log.d(TAG, "Handy-Akku ($level %, lädt=$isCharging) an Wear OS übertragen")
            } catch (e: Exception) {
                Log.e(TAG, "syncPhoneBatteryToWear fehlgeschlagen", e)
            }
        }
    }

    /**
     * Entfernt alle IoSync-Daten aus dem Data Layer (z.B. bei Verbindungstrennung).
     */
    suspend fun clearWearData() {
        withContext(Dispatchers.IO) {
            try {
                dataClient.deleteDataItems(
                    android.net.Uri.parse("wear://*$PATH_STATES")
                ).await()
                Log.d(TAG, "Wear OS Data Layer geleert")
            } catch (e: Exception) {
                Log.e(TAG, "clearWearData fehlgeschlagen", e)
            }
        }
    }
}
