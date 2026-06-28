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
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "WearDataLayerService"

// ── Data Layer Pfade ──────────────────────────────────────────────────────────
private const val PATH_STATES         = "/iosync/smarthome/states"
private const val PATH_SINGLE_STATE   = "/iosync/smarthome/state"
private const val PATH_SETTINGS       = "/iosync/smarthome/settings"
private const val PATH_WATCHFACE_CONFIG = "/iosync/watchface/config"
private const val PATH_PHONE_BATTERY  = "/iosync/phone/battery"

// ── Verbindungs-Konfig (ab v5: Uhr fragt ioBroker + Wetter direkt selbst ab) ─
private const val PATH_CONNECTION_CONFIG = "/iosync/watchface/connection"
private const val KEY_IO_USE_ADAPTER     = "io_use_adapter"
private const val KEY_IO_HOST            = "io_host"
private const val KEY_IO_PORT            = "io_port"
private const val KEY_IO_USE_HTTPS       = "io_use_https"
private const val KEY_IO_USERNAME        = "io_username"
private const val KEY_IO_PASSWORD        = "io_password"
private const val KEY_IO_USE_PUSH        = "io_use_push"
private const val KEY_CON_SLOT1_ID       = "con_slot1_id"
private const val KEY_CON_SLOT2_ID       = "con_slot2_id"
private const val KEY_CON_SLOT3_ID       = "con_slot3_id"
private const val KEY_CON_SLOT4_ID       = "con_slot4_id"
private const val KEY_CON_P2_SLOT1_ID    = "con_p2_slot1_id"
private const val KEY_CON_P2_SLOT2_ID    = "con_p2_slot2_id"
private const val KEY_CON_P2_SLOT3_ID    = "con_p2_slot3_id"
private const val KEY_CON_P2_SLOT4_ID    = "con_p2_slot4_id"
private const val KEY_CON_P2_BAR_ID      = "con_p2_bar_id"
private const val KEY_CON_P2_COLOR_ID    = "con_p2_color_id"
private const val KEY_CON_P2_COLOR_TEMP_ID      = "con_p2_color_temp_id"
private const val KEY_CON_P2_COLOR_TEMP_WARM    = "con_p2_color_temp_warm"
private const val KEY_CON_P2_COLOR_TEMP_NEUTRAL = "con_p2_color_temp_neutral"
private const val KEY_CON_P2_COLOR_TEMP_COLD    = "con_p2_color_temp_cold"
private const val KEY_CON_SLEEP_ID       = "con_sleep_id"
private const val KEY_CON_WEATHER_USE_FIXED = "con_weather_use_fixed"
private const val KEY_CON_WEATHER_LAT       = "con_weather_lat"
private const val KEY_CON_WEATHER_LON       = "con_weather_lon"
private const val KEY_CON_SLOT_INTERVAL     = "con_slot_interval"
private const val KEY_CON_PAGE2_INTERVAL    = "con_page2_interval"
private const val KEY_CON_WEATHER_INTERVAL  = "con_weather_interval"

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
private const val KEY_WF_SHOW_SUNRISE     = "wf_show_sunrise"
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
private const val KEY_WF_CUSTOM_SLOT4_BAR_IS_SLIDER  = "wf_custom_slot4_bar_is_slider"
private const val KEY_WF_CUSTOM_SLOT4_USE_KLIPPER       = "wf_custom_slot4_use_klipper"
private const val KEY_WF_CUSTOM_SLOT4_KLIPPER_SOURCE    = "wf_custom_slot4_klipper_source"
private const val KEY_WF_CUSTOM_SLOT4_KLIPPER_COLOR_ACT = "wf_custom_slot4_klipper_color_active"
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
private const val KEY_WF_KCAL_LABEL              = "wf_kcal_label"
private const val KEY_WF_KCAL_UNIT               = "wf_kcal_unit"
private const val KEY_WF_OXYGEN_LABEL            = "wf_oxygen_label"
private const val KEY_WF_OXYGEN_UNIT             = "wf_oxygen_unit"
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

// ── NTP-Zeitkorrektur ─────────────────────────────────────────────────────────
private const val KEY_WF_NTP_ENABLED = "wf_ntp_enabled"
private const val KEY_WF_NTP_SERVER  = "wf_ntp_server"

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

// ── Boden-Komplikationen ──────────────────────────────────────────────────────
private const val KEY_WF_SHOW_BOTTOM_COMP   = "wf_show_bottom_comp"
private const val KEY_WF_BC1_USE_IOBROKER   = "wf_bc1_use_iobroker"
private const val KEY_WF_BC1_LABEL          = "wf_bc1_label"
private const val KEY_WF_BC1_COLOR          = "wf_bc1_color"
private const val KEY_WF_BC1_RING_ENABLED   = "wf_bc1_ring_enabled"
private const val KEY_WF_BC1_RING_COLOR1    = "wf_bc1_ring_color1"
private const val KEY_WF_BC1_RING_COLOR2    = "wf_bc1_ring_color2"
private const val KEY_WF_BC1_RING_MIN       = "wf_bc1_ring_min"
private const val KEY_WF_BC1_RING_MAX       = "wf_bc1_ring_max"
private const val KEY_WF_BC1_RING_WIDTH     = "wf_bc1_ring_width"
private const val KEY_WF_BC1_RING_TH_EN     = "wf_bc1_ring_th_en"
private const val KEY_WF_BC1_RING_TH_VAL    = "wf_bc1_ring_th_val"
private const val KEY_WF_BC1_RING_TH_DIR    = "wf_bc1_ring_th_dir"
private const val KEY_WF_BC1_RING_TH_TARGET = "wf_bc1_ring_th_target"
private const val KEY_WF_BC1_RING_TH_COLOR  = "wf_bc1_ring_th_color"
private const val KEY_WF_BC1_TEXT_SCALE     = "wf_bc1_text_scale"
private const val KEY_WF_BC2_METRIC         = "wf_bc2_metric"
private const val KEY_WF_BC2_USE_IOBROKER   = "wf_bc2_use_iobroker"
private const val KEY_WF_BC2_LABEL          = "wf_bc2_label"
private const val KEY_WF_BC2_COLOR          = "wf_bc2_color"
private const val KEY_WF_BC2_RING_ENABLED   = "wf_bc2_ring_enabled"
private const val KEY_WF_BC2_RING_COLOR1    = "wf_bc2_ring_color1"
private const val KEY_WF_BC2_RING_COLOR2    = "wf_bc2_ring_color2"
private const val KEY_WF_BC2_RING_MIN       = "wf_bc2_ring_min"
private const val KEY_WF_BC2_RING_MAX       = "wf_bc2_ring_max"
private const val KEY_WF_BC2_RING_WIDTH     = "wf_bc2_ring_width"
private const val KEY_WF_BC2_RING_TH_EN     = "wf_bc2_ring_th_en"
private const val KEY_WF_BC2_RING_TH_VAL    = "wf_bc2_ring_th_val"
private const val KEY_WF_BC2_RING_TH_DIR    = "wf_bc2_ring_th_dir"
private const val KEY_WF_BC2_RING_TH_TARGET = "wf_bc2_ring_th_target"
private const val KEY_WF_BC2_RING_TH_COLOR  = "wf_bc2_ring_th_color"
private const val KEY_WF_BC2_TEXT_SCALE     = "wf_bc2_text_scale"
private const val KEY_CON_BC1_ID            = "con_bc1_id"
private const val KEY_CON_BC2_ID            = "con_bc2_id"

// ── Seite-2-Balken ────────────────────────────────────────────────────────────
private const val KEY_WF_P2_BAR_LABEL       = "wf_p2_bar_label"
private const val KEY_WF_P2_BAR_VALUE       = "wf_p2_bar_value"
private const val KEY_WF_P2_BAR_COLOR       = "wf_p2_bar_color"
private const val KEY_WF_P2_BAR_MIN         = "wf_p2_bar_min"
private const val KEY_WF_P2_BAR_MAX         = "wf_p2_bar_max"
private const val KEY_WF_P2_BAR_SHOW_LABEL  = "wf_p2_bar_show_label"
private const val KEY_WF_P2_BAR_IS_SLIDER   = "wf_p2_bar_is_slider"
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
        showSunrise: Boolean = true,
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
        customSlot4BarIsSlider: Boolean = false,
        customSlot4UseKlipper: Boolean = false,
        customSlot4KlipperSource: String = "progress",
        customSlot4KlipperColorActive: String = "neon_yellow",
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
        weatherIoBrokerId: String = "",
        kcalLabel: String = "KCAL",
        kcalUnit: String = "kcal",
        oxygenLabel: String = "OXYGEN",
        oxygenUnit: String = "%",
        ntpEnabled: Boolean = false,
        ntpServer: String = "pool.ntp.org",
        // Boden-Komplikationen
        showBottomComp: Boolean = true,
        bc1UseIoBroker: Boolean = false,
        bc1Label: String = "BPM",
        bc1Color: String = "red",
        bc1RingEnabled: Boolean = true,
        bc1RingColor1: String = "red",
        bc1RingColor2: String = "orange",
        bc1RingMin: Float = 0f,
        bc1RingMax: Float = 140f,
        bc1RingWidth: Int = 6,
        bc1RingThreshEnabled: Boolean = false,
        bc1RingThreshValue: Float = 0f,
        bc1RingThreshDir: String = "above",
        bc1RingThreshTarget: String = "color2",
        bc1RingThreshColor: String = "red",
        bc1TextScale: Int = 100,
        bc2Metric: String = "kcal",
        bc2UseIoBroker: Boolean = false,
        bc2Label: String = "KCAL",
        bc2Color: String = "orange",
        bc2RingEnabled: Boolean = true,
        bc2RingColor1: String = "orange",
        bc2RingColor2: String = "neon_yellow",
        bc2RingMin: Float = 0f,
        bc2RingMax: Float = 1000f,
        bc2RingWidth: Int = 6,
        bc2RingThreshEnabled: Boolean = false,
        bc2RingThreshValue: Float = 0f,
        bc2RingThreshDir: String = "above",
        bc2RingThreshTarget: String = "color2",
        bc2RingThreshColor: String = "red",
        bc2TextScale: Int = 100
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
                    dataMap.putBoolean(KEY_WF_SHOW_SUNRISE, showSunrise)
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
                    dataMap.putBoolean(KEY_WF_CUSTOM_SLOT4_BAR_IS_SLIDER, customSlot4BarIsSlider)
                    dataMap.putBoolean(KEY_WF_CUSTOM_SLOT4_USE_KLIPPER, customSlot4UseKlipper)
                    dataMap.putString(KEY_WF_CUSTOM_SLOT4_KLIPPER_SOURCE, customSlot4KlipperSource)
                    dataMap.putString(KEY_WF_CUSTOM_SLOT4_KLIPPER_COLOR_ACT, customSlot4KlipperColorActive)
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
                    dataMap.putString(KEY_WF_KCAL_LABEL, kcalLabel)
                    dataMap.putString(KEY_WF_KCAL_UNIT, kcalUnit)
                    dataMap.putString(KEY_WF_OXYGEN_LABEL, oxygenLabel)
                    dataMap.putString(KEY_WF_OXYGEN_UNIT, oxygenUnit)
                    dataMap.putBoolean(KEY_WF_NTP_ENABLED, ntpEnabled)
                    dataMap.putString(KEY_WF_NTP_SERVER, ntpServer)
                    // Boden-Komplikationen
                    dataMap.putBoolean(KEY_WF_SHOW_BOTTOM_COMP, showBottomComp)
                    dataMap.putBoolean(KEY_WF_BC1_USE_IOBROKER, bc1UseIoBroker)
                    dataMap.putString(KEY_WF_BC1_LABEL,       bc1Label)
                    dataMap.putString(KEY_WF_BC1_COLOR,       bc1Color)
                    dataMap.putBoolean(KEY_WF_BC1_RING_ENABLED, bc1RingEnabled)
                    dataMap.putString(KEY_WF_BC1_RING_COLOR1,  bc1RingColor1)
                    dataMap.putString(KEY_WF_BC1_RING_COLOR2,  bc1RingColor2)
                    dataMap.putFloat(KEY_WF_BC1_RING_MIN,      bc1RingMin)
                    dataMap.putFloat(KEY_WF_BC1_RING_MAX,      bc1RingMax)
                    dataMap.putInt(KEY_WF_BC1_RING_WIDTH,      bc1RingWidth)
                    dataMap.putBoolean(KEY_WF_BC1_RING_TH_EN,  bc1RingThreshEnabled)
                    dataMap.putFloat(KEY_WF_BC1_RING_TH_VAL,   bc1RingThreshValue)
                    dataMap.putString(KEY_WF_BC1_RING_TH_DIR,  bc1RingThreshDir)
                    dataMap.putString(KEY_WF_BC1_RING_TH_TARGET, bc1RingThreshTarget)
                    dataMap.putString(KEY_WF_BC1_RING_TH_COLOR, bc1RingThreshColor)
                    dataMap.putInt(KEY_WF_BC1_TEXT_SCALE,     bc1TextScale)
                    dataMap.putString(KEY_WF_BC2_METRIC,       bc2Metric)
                    dataMap.putBoolean(KEY_WF_BC2_USE_IOBROKER, bc2UseIoBroker)
                    dataMap.putString(KEY_WF_BC2_LABEL,       bc2Label)
                    dataMap.putString(KEY_WF_BC2_COLOR,       bc2Color)
                    dataMap.putBoolean(KEY_WF_BC2_RING_ENABLED, bc2RingEnabled)
                    dataMap.putString(KEY_WF_BC2_RING_COLOR1,  bc2RingColor1)
                    dataMap.putString(KEY_WF_BC2_RING_COLOR2,  bc2RingColor2)
                    dataMap.putFloat(KEY_WF_BC2_RING_MIN,      bc2RingMin)
                    dataMap.putFloat(KEY_WF_BC2_RING_MAX,      bc2RingMax)
                    dataMap.putInt(KEY_WF_BC2_RING_WIDTH,      bc2RingWidth)
                    dataMap.putBoolean(KEY_WF_BC2_RING_TH_EN,  bc2RingThreshEnabled)
                    dataMap.putFloat(KEY_WF_BC2_RING_TH_VAL,   bc2RingThreshValue)
                    dataMap.putString(KEY_WF_BC2_RING_TH_DIR,  bc2RingThreshDir)
                    dataMap.putString(KEY_WF_BC2_RING_TH_TARGET, bc2RingThreshTarget)
                    dataMap.putString(KEY_WF_BC2_RING_TH_COLOR, bc2RingThreshColor)
                    dataMap.putInt(KEY_WF_BC2_TEXT_SCALE,     bc2TextScale)
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
     * Überträgt die Verbindungs-/Datenpunkt-Konfiguration ans Watchface (ab v5).
     *
     * Ab v5 fragt die Uhr die ioBroker-Datenpunkte und das Wetter selbst ab. Das
     * Handy schickt nur noch diese Einstellungen (Adapter-Zugang, Datenpunkt-IDs,
     * Wetter-Standort, Abruf-Intervalle), nicht mehr die Werte.
     */
    suspend fun syncConnectionConfigToWear(
        useAdapter: Boolean,
        host: String,
        port: Int,
        useHttps: Boolean,
        username: String,
        password: String,
        usePush: Boolean,
        slot1Id: String, slot2Id: String, slot3Id: String, slot4Id: String,
        p2Slot1Id: String, p2Slot2Id: String, p2Slot3Id: String, p2Slot4Id: String,
        p2BarId: String,
        p2ColorId: String = "",
        p2ColorTempId: String = "",
        p2ColorTempWarm: String = "3300",
        p2ColorTempNeutral: String = "4500",
        p2ColorTempCold: String = "6500",
        sleepId: String,
        weatherUseFixed: Boolean,
        weatherLat: Double,
        weatherLon: Double,
        slotIntervalSec: Int,
        page2IntervalSec: Int,
        weatherIntervalSec: Int,
        heartRateIntervalSec: Int = 600,
        bc1Id: String = "",
        bc2Id: String = "",
        klipperEnabled: Boolean = false,
        klipperHost: String = "",
        klipperPort: Int = 7125,
        klipperApiKey: String = "",
        klipperChamberObject: String = "heater_generic chamber",
        klipperIntervalSec: Int = 15,
        p3PillEnabled: Boolean = false,
        p3PillColorTrue: String = "cyan",
        p3PillColorFalse: String = "red",
        p3PillObject: String = "",
        p3PillField: String = "value",
        p3PillGcodeOn: String = "",
        p3PillGcodeOff: String = "",
        klipperLedType: String = "gcode",
        klipperLedObject: String = "",
        klipperLedField: String = "value",
        klipperLedGcodeOn: String = "",
        klipperLedGcodeOff: String = "",
        klipperLedPowerDevice: String = "",
        klipperHeatType: String = "gcode",
        klipperHeatHeaterName: String = "chamber",
        klipperHeatTargetTemp: Int = 50,
        klipperHeatGcodeOn: String = "",
        klipperHeatGcodeOff: String = "",
        klipperLedLabel: String = "Led",
        klipperHeatLabel: String = "Heater",
        p3FontScale: Int = 100
    ) {
        withContext(Dispatchers.IO) {
            try {
                val request = PutDataMapRequest.create(PATH_CONNECTION_CONFIG).apply {
                    dataMap.putBoolean(KEY_IO_USE_ADAPTER, useAdapter)
                    dataMap.putString(KEY_IO_HOST, host)
                    dataMap.putInt(KEY_IO_PORT, port)
                    dataMap.putBoolean(KEY_IO_USE_HTTPS, useHttps)
                    dataMap.putString(KEY_IO_USERNAME, username)
                    dataMap.putString(KEY_IO_PASSWORD, password)
                    dataMap.putBoolean(KEY_IO_USE_PUSH, usePush)
                    dataMap.putString(KEY_CON_SLOT1_ID, slot1Id)
                    dataMap.putString(KEY_CON_SLOT2_ID, slot2Id)
                    dataMap.putString(KEY_CON_SLOT3_ID, slot3Id)
                    dataMap.putString(KEY_CON_SLOT4_ID, slot4Id)
                    dataMap.putString(KEY_CON_P2_SLOT1_ID, p2Slot1Id)
                    dataMap.putString(KEY_CON_P2_SLOT2_ID, p2Slot2Id)
                    dataMap.putString(KEY_CON_P2_SLOT3_ID, p2Slot3Id)
                    dataMap.putString(KEY_CON_P2_SLOT4_ID, p2Slot4Id)
                    dataMap.putString(KEY_CON_P2_BAR_ID, p2BarId)
                    dataMap.putString(KEY_CON_P2_COLOR_ID, p2ColorId)
                    dataMap.putString(KEY_CON_P2_COLOR_TEMP_ID, p2ColorTempId)
                    dataMap.putString(KEY_CON_P2_COLOR_TEMP_WARM, p2ColorTempWarm)
                    dataMap.putString(KEY_CON_P2_COLOR_TEMP_NEUTRAL, p2ColorTempNeutral)
                    dataMap.putString(KEY_CON_P2_COLOR_TEMP_COLD, p2ColorTempCold)
                    dataMap.putString(KEY_CON_SLEEP_ID, sleepId)
                    dataMap.putBoolean(KEY_CON_WEATHER_USE_FIXED, weatherUseFixed)
                    dataMap.putDouble(KEY_CON_WEATHER_LAT, weatherLat)
                    dataMap.putDouble(KEY_CON_WEATHER_LON, weatherLon)
                    dataMap.putInt(KEY_CON_SLOT_INTERVAL, slotIntervalSec)
                    dataMap.putInt(KEY_CON_PAGE2_INTERVAL, page2IntervalSec)
                    dataMap.putInt(KEY_CON_WEATHER_INTERVAL, weatherIntervalSec)
                    dataMap.putInt("con_hr_interval", heartRateIntervalSec)
                    dataMap.putString(KEY_CON_BC1_ID, bc1Id)
                    dataMap.putString(KEY_CON_BC2_ID, bc2Id)
                    // Klipper + Seite 3 Pille
                    dataMap.putBoolean("con_klipper_enabled", klipperEnabled)
                    dataMap.putString("con_klipper_host", klipperHost)
                    dataMap.putInt("con_klipper_port", klipperPort)
                    dataMap.putString("con_klipper_api_key", klipperApiKey)
                    dataMap.putString("con_klipper_chamber_obj", klipperChamberObject)
                    dataMap.putInt("con_klipper_interval", klipperIntervalSec)
                    dataMap.putBoolean("con_p3_pill_enabled", p3PillEnabled)
                    dataMap.putString("con_p3_pill_color_true", p3PillColorTrue)
                    dataMap.putString("con_p3_pill_color_false", p3PillColorFalse)
                    dataMap.putString("con_p3_pill_object", p3PillObject)
                    dataMap.putString("con_p3_pill_field", p3PillField)
                    dataMap.putString("con_p3_pill_gcode_on", p3PillGcodeOn)
                    dataMap.putString("con_p3_pill_gcode_off", p3PillGcodeOff)
                    // Seite 3 – LED-Button + Chamber-Heater-Button
                    dataMap.putString("con_klipper_led_type", klipperLedType)
                    dataMap.putString("con_klipper_led_object", klipperLedObject)
                    dataMap.putString("con_klipper_led_field", klipperLedField)
                    dataMap.putString("con_klipper_led_gcode_on", klipperLedGcodeOn)
                    dataMap.putString("con_klipper_led_gcode_off", klipperLedGcodeOff)
                    dataMap.putString("con_klipper_led_power_device", klipperLedPowerDevice)
                    dataMap.putString("con_klipper_heat_type", klipperHeatType)
                    dataMap.putString("con_klipper_heat_heater_name", klipperHeatHeaterName)
                    dataMap.putInt("con_klipper_heat_target_temp", klipperHeatTargetTemp)
                    dataMap.putString("con_klipper_heat_gcode_on", klipperHeatGcodeOn)
                    dataMap.putString("con_klipper_heat_gcode_off", klipperHeatGcodeOff)
                    dataMap.putString("con_klipper_led_label", klipperLedLabel)
                    dataMap.putString("con_klipper_heat_label", klipperHeatLabel)
                    dataMap.putInt("con_p3_font_scale", p3FontScale)
                    dataMap.putLong(KEY_TIMESTAMP, System.currentTimeMillis())
                }.asPutDataRequest().setUrgent()
                dataClient.putDataItem(request).await()
                Log.d(TAG, "Verbindungs-Konfig an Wear OS übertragen (Uhr fragt selbst ab)")
            } catch (e: Exception) {
                Log.e(TAG, "syncConnectionConfigToWear fehlgeschlagen", e)
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
        slot4BarIsSlider: Boolean = false,
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
                    dataMap.putBoolean(KEY_WF_CUSTOM_SLOT4_BAR_IS_SLIDER, slot4BarIsSlider)
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
        p2BarIsSlider: Boolean = false,
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
                    dataMap.putBoolean(KEY_WF_P2_BAR_IS_SLIDER,   p2BarIsSlider)
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
                val result = withTimeoutOrNull(10_000L) { dataClient.putDataItem(request).await() }
                if (result != null) {
                    Log.d(TAG, "Handy-Akku ($level %, lädt=$isCharging, zeigen=$showInWatchface) an Wear OS übertragen")
                } else {
                    Log.w(TAG, "syncPhoneBatteryToWear Timeout – Uhr vermutlich getrennt")
                }
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
