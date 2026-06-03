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
private const val KEY_WF_SHOW_STEPS       = "wf_show_steps"

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
private const val KEY_WF_SUNRISE_TEXT_SCALE        = "wf_sunrise_text_scale"
private const val KEY_WF_WATCH_BATTERY_TEXT_SCALE = "wf_watch_battery_text_scale"
private const val KEY_WF_STEPS_TEXT_SCALE         = "wf_steps_text_scale"
private const val KEY_WF_HEALTH_DATA_SOURCE      = "wf_health_data_source"
private const val KEY_WF_HR_SOURCE               = "wf_hr_source"
private const val KEY_WF_KCAL_SOURCE             = "wf_kcal_source"
private const val KEY_WF_OXYGEN_SOURCE           = "wf_oxygen_source"
private const val KEY_WF_HR_COMPLICATION         = "wf_hr_complication"
private const val KEY_WF_KCAL_COMPLICATION       = "wf_kcal_complication"
private const val KEY_WF_OXYGEN_COMPLICATION     = "wf_oxygen_complication"
private const val PATH_CUSTOM_SLOTS           = "/iosync/watchface/custom_slots"
private const val PATH_CUSTOM_SLOTS_P2        = "/iosync/watchface/custom_slots_p2"
private const val PATH_CONFIG_P2              = "/iosync/watchface/config_p2"
private const val PATH_PHONE_HEALTH           = "/iosync/watchface/phone_health"
private const val KEY_PHONE_HEART_RATE        = "phone_heart_rate"
private const val KEY_PHONE_SPO2              = "phone_spo2"
private const val KEY_PHONE_CALORIES          = "phone_calories"

// ── Aktions-Pille-Konfigurationsschlüssel ─────────────────────────────────────
private const val KEY_WF_ACTION_PILL_ENABLED     = "wf_action_pill_enabled"
private const val KEY_WF_ACTION_PILL_COLOR_TRUE  = "wf_action_pill_color_true"
private const val KEY_WF_ACTION_PILL_COLOR_FALSE = "wf_action_pill_color_false"
private const val KEY_WF_ACTION_PILL_IOBROKER_ID = "wf_action_pill_iobroker_id"
private const val KEY_WF_ACTION_PILL_VALUE_MODE  = "wf_action_pill_value_mode"
private const val KEY_WF_ACTION_PILL_FIXED_VALUE = "wf_action_pill_fixed_value"
private const val KEY_WF_ACTION_PILL_STATE       = "wf_action_pill_state"

// ── Aktions-Pille Status-Pfad ─────────────────────────────────────────────────
private const val PATH_ACTION_PILL_STATE  = "/iosync/watchface/action_pill_state"
private const val KEY_PILL_STATE          = "pill_state"
private const val PATH_P2_PILL_STATES     = "/iosync/watchface/p2_pill_states"

// ── Akku-Keys ─────────────────────────────────────────────────────────────────
private const val KEY_BATTERY_LEVEL   = "battery_level"
private const val KEY_IS_CHARGING     = "is_charging"

// ── Gesundheitsdaten-Farben ───────────────────────────────────────────────────
private const val KEY_WF_HR_COLOR      = "wf_hr_color"
private const val KEY_WF_KCAL_COLOR    = "wf_kcal_color"
private const val KEY_WF_OXYGEN_COLOR  = "wf_oxygen_color"
private const val KEY_WF_STEPS_COLOR   = "wf_steps_color"
private const val KEY_WF_SLEEP_COLOR   = "wf_sleep_color"
private const val KEY_WF_SUNRISE_COLOR = "wf_sunrise_color"
private const val KEY_WF_SLOT_COLOR    = "wf_slot_color"

// ── Akku-Ring-Farben ──────────────────────────────────────────────────────────
private const val KEY_WF_SHOW_BACKGROUND           = "wf_show_background"
private const val KEY_WF_BATTERY_RING_COLOR1       = "wf_battery_ring_color1"
private const val KEY_WF_BATTERY_RING_COLOR2       = "wf_battery_ring_color2"
private const val KEY_WF_BATTERY_RING_STROKE_SCALE = "wf_battery_ring_stroke_scale"

// ── Akku-Ring Warnstufen (Schwelle in %, 0 = deaktiviert) ─────────────────────
private const val KEY_WF_BATTERY_WARN1_COLOR     = "wf_battery_warn1_color"
private const val KEY_WF_BATTERY_WARN1_THRESHOLD = "wf_battery_warn1_threshold"
private const val KEY_WF_BATTERY_WARN2_COLOR     = "wf_battery_warn2_color"
private const val KEY_WF_BATTERY_WARN2_THRESHOLD = "wf_battery_warn2_threshold"

// ── Balken (Slot 4) Warnstufen (absoluter Wert, NaN = deaktiviert) ────────────
private const val KEY_WF_SLOT4_WARN1_COLOR = "wf_slot4_warn1_color"
private const val KEY_WF_SLOT4_WARN1_VALUE = "wf_slot4_warn1_value"
private const val KEY_WF_SLOT4_WARN2_COLOR = "wf_slot4_warn2_color"
private const val KEY_WF_SLOT4_WARN2_VALUE = "wf_slot4_warn2_value"

// ── Wetter-Temperatur-Quelle ───────────────────────────────────────────────────
private const val KEY_WF_WEATHER_TEMP_SOURCE  = "wf_weather_temp_source"
private const val KEY_WF_WEATHER_IOBROKER_ID  = "wf_weather_iobroker_id"

// ── Seite-2-Balken ────────────────────────────────────────────────────────────
private const val KEY_WF_P2_BAR_LABEL       = "wf_p2_bar_label"
private const val KEY_WF_P2_BAR_VALUE       = "wf_p2_bar_value"
private const val KEY_WF_P2_BAR_COLOR       = "wf_p2_bar_color"
private const val KEY_WF_P2_BAR_MIN         = "wf_p2_bar_min"
private const val KEY_WF_P2_BAR_MAX         = "wf_p2_bar_max"
private const val KEY_WF_P2_BAR_SHOW_LABEL  = "wf_p2_bar_show_label"
private const val KEY_WF_P2_BAR_TEXT_SCALE  = "wf_p2_bar_text_scale"
private const val KEY_WF_P2_BAR_WARN1_COLOR = "wf_p2_bar_warn1_color"
private const val KEY_WF_P2_BAR_WARN1_VALUE = "wf_p2_bar_warn1_value"
private const val KEY_WF_P2_BAR_WARN2_COLOR = "wf_p2_bar_warn2_color"
private const val KEY_WF_P2_BAR_WARN2_VALUE = "wf_p2_bar_warn2_value"

// ── Schlafdauer (Minuten, via Health Connect) ─────────────────────────────────
private const val KEY_PHONE_SLEEP_MINUTES = "phone_sleep_minutes"

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
        showSteps: Boolean = true,
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
        stepsTextScale: Int = 100,
        slot1TextScale: Int = 100,
        slot2TextScale: Int = 100,
        slot3TextScale: Int = 100,
        slot4TextScale: Int = 100,
        weatherTextScale: Int = 100,
        sunriseTextScale: Int = 100,
        watchBatteryTextScale: Int = 100,
        batteryRingColor1: String = "cyan",
        batteryRingColor2: String = "neon_yellow",
        batteryRingStrokeScale: Int = 100,
        healthDataSource: String = "local",
        hrSource: String = "local",
        kcalSource: String = "local",
        oxygenSource: String = "local",
        hrComplication: String = "",
        kcalComplication: String = "",
        oxygenComplication: String = "",
        batteryWarn1Color: String = "orange",
        batteryWarn1Threshold: Int = 0,
        batteryWarn2Color: String = "red",
        batteryWarn2Threshold: Int = 0,
        showBackground: Boolean = false,
        hrColor: String = "red",
        kcalColor: String = "orange",
        oxygenColor: String = "cyan",
        stepsColor: String = "neon_yellow",
        sleepColor: String = "purple",
        sunriseColor: String = "neon_yellow",
        slotColor: String = "neon_yellow",
        weatherTempSource: String = "openweather",
        weatherIoBrokerId: String = ""
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
                    dataMap.putBoolean(KEY_WF_SHOW_STEPS, showSteps)
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
                    dataMap.putInt(KEY_WF_STEPS_TEXT_SCALE, stepsTextScale)
                    dataMap.putInt(KEY_WF_SLOT1_TEXT_SCALE, slot1TextScale)
                    dataMap.putInt(KEY_WF_SLOT2_TEXT_SCALE, slot2TextScale)
                    dataMap.putInt(KEY_WF_SLOT3_TEXT_SCALE, slot3TextScale)
                    dataMap.putInt(KEY_WF_SLOT4_TEXT_SCALE,    slot4TextScale)
                    dataMap.putInt(KEY_WF_WEATHER_TEXT_SCALE, weatherTextScale)
                    dataMap.putInt(KEY_WF_SUNRISE_TEXT_SCALE, sunriseTextScale)
                    dataMap.putInt(KEY_WF_WATCH_BATTERY_TEXT_SCALE, watchBatteryTextScale)
                    dataMap.putString(KEY_WF_BATTERY_RING_COLOR1, batteryRingColor1)
                    dataMap.putString(KEY_WF_BATTERY_RING_COLOR2, batteryRingColor2)
                    dataMap.putInt(KEY_WF_BATTERY_RING_STROKE_SCALE, batteryRingStrokeScale)
                    dataMap.putString(KEY_WF_BATTERY_WARN1_COLOR, batteryWarn1Color)
                    dataMap.putInt(KEY_WF_BATTERY_WARN1_THRESHOLD, batteryWarn1Threshold)
                    dataMap.putString(KEY_WF_BATTERY_WARN2_COLOR, batteryWarn2Color)
                    dataMap.putInt(KEY_WF_BATTERY_WARN2_THRESHOLD, batteryWarn2Threshold)
                    dataMap.putBoolean(KEY_WF_SHOW_BACKGROUND, showBackground)
                    dataMap.putString(KEY_WF_HR_COLOR,      hrColor)
                    dataMap.putString(KEY_WF_KCAL_COLOR,    kcalColor)
                    dataMap.putString(KEY_WF_OXYGEN_COLOR,  oxygenColor)
                    dataMap.putString(KEY_WF_STEPS_COLOR,   stepsColor)
                    dataMap.putString(KEY_WF_SLEEP_COLOR,   sleepColor)
                    dataMap.putString(KEY_WF_SUNRISE_COLOR, sunriseColor)
                    dataMap.putString(KEY_WF_SLOT_COLOR,    slotColor)
                    dataMap.putString(KEY_WF_WEATHER_TEMP_SOURCE, weatherTempSource)
                    dataMap.putString(KEY_WF_WEATHER_IOBROKER_ID, weatherIoBrokerId)
                    dataMap.putString(KEY_WF_HEALTH_DATA_SOURCE, healthDataSource)
                    dataMap.putString(KEY_WF_HR_SOURCE, hrSource)
                    dataMap.putString(KEY_WF_KCAL_SOURCE, kcalSource)
                    dataMap.putString(KEY_WF_OXYGEN_SOURCE, oxygenSource)
                    dataMap.putString(KEY_WF_HR_COMPLICATION, hrComplication)
                    dataMap.putString(KEY_WF_KCAL_COMPLICATION, kcalComplication)
                    dataMap.putString(KEY_WF_OXYGEN_COMPLICATION, oxygenComplication)
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
     * Überträgt den Status der beiden Seite-2-Pillen ans Watchface.
     */
    suspend fun syncP2PillStatesToWear(pill1State: Boolean, pill2State: Boolean) {
        withContext(Dispatchers.IO) {
            try {
                val request = PutDataMapRequest.create(PATH_P2_PILL_STATES).apply {
                    dataMap.putBoolean("wf_p2_pill1_state", pill1State)
                    dataMap.putBoolean("wf_p2_pill2_state", pill2State)
                    dataMap.putLong(KEY_TIMESTAMP, System.currentTimeMillis())
                }.asPutDataRequest().setUrgent()
                dataClient.putDataItem(request).await()
                Log.d(TAG, "Seite-2-Pillen-Status ($pill1State/$pill2State) an Wear OS übertragen")
            } catch (e: Exception) {
                Log.e(TAG, "syncP2PillStatesToWear fehlgeschlagen", e)
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
        slot4BarShowLabel: Boolean = true,
        slot4Warn1Color: String = "orange", slot4Warn1Value: Float = Float.NaN,
        slot4Warn2Color: String = "red", slot4Warn2Value: Float = Float.NaN
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
                    dataMap.putString(KEY_WF_SLOT4_WARN1_COLOR, slot4Warn1Color)
                    dataMap.putFloat(KEY_WF_SLOT4_WARN1_VALUE, slot4Warn1Value)
                    dataMap.putString(KEY_WF_SLOT4_WARN2_COLOR, slot4Warn2Color)
                    dataMap.putFloat(KEY_WF_SLOT4_WARN2_VALUE, slot4Warn2Value)
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
     * Überträgt Seite-2-Slot-Daten (Labels + Werte) ans Watchface.
     */
    suspend fun syncPage2SlotsToWear(
        slot1Label: String, slot1Value: String,
        slot2Label: String, slot2Value: String,
        slot3Label: String, slot3Value: String,
        slot4Label: String, slot4Value: String,
        p2BarValue: String = "--"
    ) {
        withContext(Dispatchers.IO) {
            try {
                val request = PutDataMapRequest.create(PATH_CUSTOM_SLOTS_P2).apply {
                    dataMap.putString("wf_p2_slot1_label", slot1Label)
                    dataMap.putString("wf_p2_slot1_value", slot1Value)
                    dataMap.putString("wf_p2_slot2_label", slot2Label)
                    dataMap.putString("wf_p2_slot2_value", slot2Value)
                    dataMap.putString("wf_p2_slot3_label", slot3Label)
                    dataMap.putString("wf_p2_slot3_value", slot3Value)
                    dataMap.putString("wf_p2_slot4_label", slot4Label)
                    dataMap.putString("wf_p2_slot4_value", slot4Value)
                    dataMap.putString(KEY_WF_P2_BAR_VALUE, p2BarValue)
                    dataMap.putLong(KEY_TIMESTAMP, System.currentTimeMillis())
                }.asPutDataRequest().setUrgent()
                dataClient.putDataItem(request).await()
                Log.d(TAG, "Seite-2-Slot-Daten an Wear OS übertragen")
            } catch (e: Exception) {
                Log.e(TAG, "syncPage2SlotsToWear fehlgeschlagen", e)
            }
        }
    }

    /**
     * Überträgt die Seite-2-Konfiguration (Pillen + Textgrößen) ans Watchface.
     */
    suspend fun syncPage2ConfigToWear(
        p2PillEnabled: Boolean,
        p2PillColorTrue: String,
        p2PillColorFalse: String,
        p2PillIoBrokerId: String,
        p2PillValueMode: String,
        p2PillFixedValue: String,
        p2Pill2Enabled: Boolean,
        p2Pill2ColorTrue: String,
        p2Pill2ColorFalse: String,
        p2Pill2IoBrokerId: String,
        p2Pill2ValueMode: String,
        p2Pill2FixedValue: String,
        p2Slot1TextScale: Int,
        p2Slot2TextScale: Int,
        p2Slot3TextScale: Int,
        p2Slot4TextScale: Int,
        sleepTextScale: Int,
        sleepSource: String = "healthconnect",
        sleepComplication: String = "",
        p2BarLabel: String = "",
        p2BarColor: String = "neon_yellow",
        p2BarMin: Float = 0f,
        p2BarMax: Float = 100f,
        p2BarShowLabel: Boolean = true,
        p2BarTextScale: Int = 100,
        p2BarWarn1Color: String = "orange",
        p2BarWarn1Value: Float = Float.NaN,
        p2BarWarn2Color: String = "red",
        p2BarWarn2Value: Float = Float.NaN,
        p2ShowBackground: Boolean = false
    ) {
        withContext(Dispatchers.IO) {
            try {
                val request = PutDataMapRequest.create(PATH_CONFIG_P2).apply {
                    dataMap.putBoolean("wf_p2_pill_enabled",       p2PillEnabled)
                    dataMap.putString("wf_p2_pill_color_true",     p2PillColorTrue)
                    dataMap.putString("wf_p2_pill_color_false",    p2PillColorFalse)
                    dataMap.putString("wf_p2_pill_iobroker_id",    p2PillIoBrokerId)
                    dataMap.putString("wf_p2_pill_value_mode",     p2PillValueMode)
                    dataMap.putString("wf_p2_pill_fixed_value",    p2PillFixedValue)
                    dataMap.putBoolean("wf_p2_pill2_enabled",      p2Pill2Enabled)
                    dataMap.putString("wf_p2_pill2_color_true",    p2Pill2ColorTrue)
                    dataMap.putString("wf_p2_pill2_color_false",   p2Pill2ColorFalse)
                    dataMap.putString("wf_p2_pill2_iobroker_id",   p2Pill2IoBrokerId)
                    dataMap.putString("wf_p2_pill2_value_mode",    p2Pill2ValueMode)
                    dataMap.putString("wf_p2_pill2_fixed_value",   p2Pill2FixedValue)
                    dataMap.putInt("wf_p2_slot1_text_scale",       p2Slot1TextScale)
                    dataMap.putInt("wf_p2_slot2_text_scale",       p2Slot2TextScale)
                    dataMap.putInt("wf_p2_slot3_text_scale",       p2Slot3TextScale)
                    dataMap.putInt("wf_p2_slot4_text_scale",       p2Slot4TextScale)
                    dataMap.putInt("wf_sleep_text_scale",          sleepTextScale)
                    dataMap.putString("wf_sleep_source",           sleepSource)
                    dataMap.putString("wf_sleep_complication",     sleepComplication)
                    dataMap.putString(KEY_WF_P2_BAR_LABEL,        p2BarLabel)
                    dataMap.putString(KEY_WF_P2_BAR_COLOR,        p2BarColor)
                    dataMap.putFloat(KEY_WF_P2_BAR_MIN,           p2BarMin)
                    dataMap.putFloat(KEY_WF_P2_BAR_MAX,           p2BarMax)
                    dataMap.putBoolean(KEY_WF_P2_BAR_SHOW_LABEL,  p2BarShowLabel)
                    dataMap.putInt(KEY_WF_P2_BAR_TEXT_SCALE,      p2BarTextScale)
                    dataMap.putString(KEY_WF_P2_BAR_WARN1_COLOR,  p2BarWarn1Color)
                    dataMap.putFloat(KEY_WF_P2_BAR_WARN1_VALUE,   p2BarWarn1Value)
                    dataMap.putString(KEY_WF_P2_BAR_WARN2_COLOR,  p2BarWarn2Color)
                    dataMap.putFloat(KEY_WF_P2_BAR_WARN2_VALUE,   p2BarWarn2Value)
                    dataMap.putBoolean("wf_p2_show_background",   p2ShowBackground)
                    dataMap.putLong(KEY_TIMESTAMP, System.currentTimeMillis())
                }.asPutDataRequest().setUrgent()
                dataClient.putDataItem(request).await()
                Log.d(TAG, "Seite-2-Konfig an Wear OS übertragen")
            } catch (e: Exception) {
                Log.e(TAG, "syncPage2ConfigToWear fehlgeschlagen", e)
            }
        }
    }

    /**
     * Überträgt Gesundheitsdaten (vom Smartphone / ioBroker) an das Watchface.
     */
    suspend fun syncPhoneHealthToWear(heartRate: Int, spO2: Int, calories: Int, sleepMinutes: Int = 0) {
        withContext(Dispatchers.IO) {
            try {
                val request = PutDataMapRequest.create(PATH_PHONE_HEALTH).apply {
                    dataMap.putInt(KEY_PHONE_HEART_RATE, heartRate)
                    dataMap.putInt(KEY_PHONE_SPO2, spO2)
                    dataMap.putInt(KEY_PHONE_CALORIES, calories)
                    dataMap.putInt(KEY_PHONE_SLEEP_MINUTES, sleepMinutes)
                    dataMap.putLong(KEY_TIMESTAMP, System.currentTimeMillis())
                }.asPutDataRequest().setUrgent()
                dataClient.putDataItem(request).await()
                Log.d(TAG, "Phone-Health-Daten an Wear OS übertragen: HR=$heartRate, SpO2=$spO2, kcal=$calories, sleep=$sleepMinutes")
            } catch (e: Exception) {
                Log.e(TAG, "syncPhoneHealthToWear fehlgeschlagen", e)
            }
        }
    }

    /**
     * Überträgt den aktuellen Handy-Akkustand ans Watchface.
     * @param level      Akkustand in Prozent (0–100)
     * @param isCharging true wenn das Gerät gerade geladen wird
     */
    suspend fun syncPhoneBatteryToWear(level: Int, isCharging: Boolean, showInWatchface: Boolean = true) {
        withContext(Dispatchers.IO) {
            try {
                val request = PutDataMapRequest.create(PATH_PHONE_BATTERY).apply {
                    dataMap.putInt(KEY_BATTERY_LEVEL, level)
                    dataMap.putBoolean(KEY_IS_CHARGING, isCharging)
                    dataMap.putBoolean("show_phone_battery", showInWatchface)
                    dataMap.putLong(KEY_TIMESTAMP, System.currentTimeMillis())
                }.asPutDataRequest().setUrgent()
                dataClient.putDataItem(request).await()
                Log.d(TAG, "Handy-Akku ($level %, lädt=$isCharging, zeigen=$showInWatchface) an Wear OS übertragen")
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
