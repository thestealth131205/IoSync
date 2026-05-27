package com.iosync.watchface.datalayer

import android.util.Log
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMap
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.WearableListenerService

private const val TAG = "WatchFaceDataListener"

// ── Data Layer Pfade ──────────────────────────────────────────────────────────
private const val PATH_STATES           = "/iosync/smarthome/states"
private const val PATH_WATCHFACE_CONFIG = "/iosync/watchface/config"
private const val PATH_PHONE_BATTERY    = "/iosync/phone/battery"

// ── States-Key ────────────────────────────────────────────────────────────────
private const val KEY_STATES_JSON = "states_json"

// ── Watchface-Config-Keys ─────────────────────────────────────────────────────
private const val KEY_WF_TIME_COLOR           = "wf_time_color"
private const val KEY_WF_DATE_COLOR           = "wf_date_color"
private const val KEY_WF_SHOW_SECONDS         = "wf_show_seconds"
private const val KEY_WF_SHOW_TICKS           = "wf_show_ticks"
private const val KEY_WF_SHOW_WEEKDAY         = "wf_show_weekday"
private const val KEY_WF_SHOW_PHONE_BATTERY   = "wf_show_phone_battery"
private const val KEY_WF_SHOW_IOBROKER_DATA   = "wf_show_iobroker_data"
private const val KEY_WF_SHOW_SECONDS_RING    = "wf_show_seconds_ring"
private const val KEY_WF_SECONDS_RING_COLOR   = "wf_seconds_ring_color"
private const val KEY_WF_SECONDS_RING_WIDTH   = "wf_seconds_ring_width"

// ── Gesundheits-/Wetter-Anzeige-Keys ─────────────────────────────────────────
private const val KEY_WF_SHOW_WEATHER     = "wf_show_weather"
private const val KEY_WF_SHOW_HEART_RATE  = "wf_show_heart_rate"
private const val KEY_WF_SHOW_OXYGEN      = "wf_show_oxygen"
private const val KEY_WF_SHOW_CALORIES    = "wf_show_calories"

// ── Aktions-Pille-Keys ────────────────────────────────────────────────────────
private const val KEY_WF_ACTION_PILL_ENABLED      = "wf_action_pill_enabled"
private const val KEY_WF_ACTION_PILL_COLOR_TRUE   = "wf_action_pill_color_true"
private const val KEY_WF_ACTION_PILL_COLOR_FALSE  = "wf_action_pill_color_false"
private const val KEY_WF_ACTION_PILL_IOBROKER_ID  = "wf_action_pill_iobroker_id"
private const val KEY_WF_ACTION_PILL_VALUE_MODE   = "wf_action_pill_value_mode"
private const val KEY_WF_ACTION_PILL_FIXED_VALUE  = "wf_action_pill_fixed_value"
private const val KEY_WF_ACTION_PILL_STATE        = "wf_action_pill_state"

// ── Akku-Keys ─────────────────────────────────────────────────────────────────
private const val KEY_BATTERY_LEVEL = "battery_level"
private const val KEY_IS_CHARGING   = "is_charging"

// ── Wetter-Daten-Pfad ─────────────────────────────────────────────────────────
private const val PATH_WEATHER            = "/iosync/watchface/weather"
private const val KEY_WEATHER_TEMP        = "weather_temp"
private const val KEY_WEATHER_CONDITION   = "weather_condition"

// ── Custom ioBroker-Slots (2 Datenpunkte unter der Uhrzeit) ─────────────────
private const val KEY_WF_CUSTOM_SLOT1_LABEL = "wf_custom_slot1_label"
private const val KEY_WF_CUSTOM_SLOT1_VALUE = "wf_custom_slot1_value"
private const val KEY_WF_CUSTOM_SLOT2_LABEL = "wf_custom_slot2_label"
private const val KEY_WF_CUSTOM_SLOT2_VALUE = "wf_custom_slot2_value"
private const val KEY_WF_SHOW_CUSTOM_SLOTS  = "wf_show_custom_slots"

// ── Custom-Slot-Daten (Echtzeit-Updates der Werte) ──────────────────────────
private const val PATH_CUSTOM_SLOTS         = "/iosync/watchface/custom_slots"

// ── Aktions-Pille Status-Pfad (separater Pfad für schnelle State-Updates) ────
private const val PATH_ACTION_PILL_STATE = "/iosync/watchface/action_pill_state"
private const val KEY_PILL_STATE         = "pill_state"

/**
 * Data Layer Listener für das Watchface-Modul.
 *
 * Empfängt:
 *  - ioBroker-Zustandsdaten vom IoSync Adapter (PATH_STATES)
 *  - Watchface-Konfiguration vom Smartphone (PATH_WATCHFACE_CONFIG)
 *  - Handy-Akkustand (PATH_PHONE_BATTERY)
 */
class WatchFaceDataListenerService : WearableListenerService() {

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        dataEvents.forEach { event ->
            if (event.type != DataEvent.TYPE_CHANGED) return@forEach
            when (event.dataItem.uri.path) {
                PATH_STATES -> {
                    val dataMap = DataMapItem.fromDataItem(event.dataItem).dataMap
                    val json = dataMap.getString(KEY_STATES_JSON) ?: return@forEach
                    Log.d(TAG, "ioBroker States empfangen")
                    SmartHomeStateCache.updateFromJson(json)
                }
                PATH_WATCHFACE_CONFIG -> {
                    val dataMap = DataMapItem.fromDataItem(event.dataItem).dataMap
                    Log.d(TAG, "Watchface-Konfiguration vom Smartphone empfangen")
                    WatchFaceConfigCache.updateFromDataMap(dataMap)
                }
                PATH_PHONE_BATTERY -> {
                    val dataMap = DataMapItem.fromDataItem(event.dataItem).dataMap
                    val level = dataMap.getInt(KEY_BATTERY_LEVEL, -1)
                    val charging = dataMap.getBoolean(KEY_IS_CHARGING, false)
                    Log.d(TAG, "Handy-Akku empfangen: $level % (lädt=$charging)")
                    WatchFaceConfigCache.phoneBatteryLevel = level
                    WatchFaceConfigCache.phoneBatteryCharging = charging
                }
                PATH_WEATHER -> {
                    val dataMap = DataMapItem.fromDataItem(event.dataItem).dataMap
                    WatchFaceConfigCache.weatherTemp = dataMap.getInt(KEY_WEATHER_TEMP, 0)
                    dataMap.getString(KEY_WEATHER_CONDITION)?.let { WatchFaceConfigCache.weatherCondition = it }
                    Log.d(TAG, "Wetterdaten empfangen: ${WatchFaceConfigCache.weatherTemp}°C, ${WatchFaceConfigCache.weatherCondition}")
                }
                PATH_CUSTOM_SLOTS -> {
                    val dataMap = DataMapItem.fromDataItem(event.dataItem).dataMap
                    dataMap.getString(KEY_WF_CUSTOM_SLOT1_LABEL)?.let { WatchFaceConfigCache.customSlot1Label = it }
                    dataMap.getString(KEY_WF_CUSTOM_SLOT1_VALUE)?.let { WatchFaceConfigCache.customSlot1Value = it }
                    dataMap.getString(KEY_WF_CUSTOM_SLOT2_LABEL)?.let { WatchFaceConfigCache.customSlot2Label = it }
                    dataMap.getString(KEY_WF_CUSTOM_SLOT2_VALUE)?.let { WatchFaceConfigCache.customSlot2Value = it }
                    Log.d(TAG, "Custom-Slot-Daten empfangen: ${WatchFaceConfigCache.customSlot1Label}=${WatchFaceConfigCache.customSlot1Value}, ${WatchFaceConfigCache.customSlot2Label}=${WatchFaceConfigCache.customSlot2Value}")
                }
                PATH_ACTION_PILL_STATE -> {
                    val dataMap = DataMapItem.fromDataItem(event.dataItem).dataMap
                    val state = dataMap.getBoolean(KEY_PILL_STATE, false)
                    Log.d(TAG, "Aktions-Pille Status empfangen: $state")
                    WatchFaceConfigCache.actionPillState = state
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Caches — vom Renderer in jedem Frame gelesen (thread-safe via @Volatile)
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Watchface-Konfiguration, die vom Smartphone per Data Layer übertragen wird.
 */
object WatchFaceConfigCache {

    @Volatile var timeColorId: String = "light_gray"
    @Volatile var dateColorId: String = "cyan"
    @Volatile var showSeconds: Boolean = true
    @Volatile var showTicks: Boolean = true
    @Volatile var showWeekday: Boolean = true
    @Volatile var showPhoneBattery: Boolean = false
    @Volatile var showIoBrokerData: Boolean = false
    @Volatile var phoneBatteryLevel: Int = -1
    @Volatile var phoneBatteryCharging: Boolean = false
    @Volatile var showSecondsRing: Boolean = false
    @Volatile var secondsRingColorId: String = "neon_yellow"
    @Volatile var secondsRingWidth: Int = 5
    // Gesundheits- und Wetter-Anzeige
    @Volatile var showWeather: Boolean = true
    @Volatile var showHeartRate: Boolean = true
    @Volatile var showOxygen: Boolean = false
    @Volatile var showCalories: Boolean = true
    // Wetterdaten (vom Handy empfangen)
    @Volatile var weatherTemp: Int = 0
    @Volatile var weatherCondition: String = "clear"
    // Aktions-Pille
    @Volatile var actionPillEnabled: Boolean = false
    @Volatile var actionPillColorTrue: String = "cyan"
    @Volatile var actionPillColorFalse: String = "red"
    @Volatile var actionPillIoBrokerId: String = ""
    @Volatile var actionPillValueMode: String = "toggle"
    @Volatile var actionPillFixedValue: String = ""
    @Volatile var actionPillState: Boolean = false
    @Volatile var lastConfigReceivedAt: Long = 0L
    // Custom ioBroker-Slots (2 Datenpunkte unter der Uhrzeit)
    @Volatile var showCustomSlots: Boolean = false
    @Volatile var customSlot1Label: String = ""
    @Volatile var customSlot1Value: String = "--"
    @Volatile var customSlot2Label: String = ""
    @Volatile var customSlot2Value: String = "--"

    fun updateFromDataMap(dataMap: DataMap) {
        lastConfigReceivedAt = System.currentTimeMillis()
        dataMap.getString(KEY_WF_TIME_COLOR)?.let { timeColorId = it }
        dataMap.getString(KEY_WF_DATE_COLOR)?.let { dateColorId = it }
        if (dataMap.containsKey(KEY_WF_SHOW_SECONDS))       showSeconds        = dataMap.getBoolean(KEY_WF_SHOW_SECONDS)
        if (dataMap.containsKey(KEY_WF_SHOW_TICKS))         showTicks          = dataMap.getBoolean(KEY_WF_SHOW_TICKS)
        if (dataMap.containsKey(KEY_WF_SHOW_WEEKDAY))       showWeekday        = dataMap.getBoolean(KEY_WF_SHOW_WEEKDAY)
        if (dataMap.containsKey(KEY_WF_SHOW_PHONE_BATTERY)) showPhoneBattery   = dataMap.getBoolean(KEY_WF_SHOW_PHONE_BATTERY)
        if (dataMap.containsKey(KEY_WF_SHOW_IOBROKER_DATA)) showIoBrokerData   = dataMap.getBoolean(KEY_WF_SHOW_IOBROKER_DATA)
        if (dataMap.containsKey(KEY_WF_SHOW_SECONDS_RING))  showSecondsRing    = dataMap.getBoolean(KEY_WF_SHOW_SECONDS_RING)
        dataMap.getString(KEY_WF_SECONDS_RING_COLOR)?.let   { secondsRingColorId = it }
        if (dataMap.containsKey(KEY_WF_SECONDS_RING_WIDTH))  secondsRingWidth  = dataMap.getInt(KEY_WF_SECONDS_RING_WIDTH)
        if (dataMap.containsKey(KEY_WF_SHOW_WEATHER))      showWeather    = dataMap.getBoolean(KEY_WF_SHOW_WEATHER)
        if (dataMap.containsKey(KEY_WF_SHOW_HEART_RATE))   showHeartRate  = dataMap.getBoolean(KEY_WF_SHOW_HEART_RATE)
        if (dataMap.containsKey(KEY_WF_SHOW_OXYGEN))       showOxygen     = dataMap.getBoolean(KEY_WF_SHOW_OXYGEN)
        if (dataMap.containsKey(KEY_WF_SHOW_CALORIES))     showCalories   = dataMap.getBoolean(KEY_WF_SHOW_CALORIES)
        if (dataMap.containsKey(KEY_WF_ACTION_PILL_ENABLED))     actionPillEnabled    = dataMap.getBoolean(KEY_WF_ACTION_PILL_ENABLED)
        dataMap.getString(KEY_WF_ACTION_PILL_COLOR_TRUE)?.let  { actionPillColorTrue  = it }
        dataMap.getString(KEY_WF_ACTION_PILL_COLOR_FALSE)?.let { actionPillColorFalse = it }
        dataMap.getString(KEY_WF_ACTION_PILL_IOBROKER_ID)?.let { actionPillIoBrokerId = it }
        dataMap.getString(KEY_WF_ACTION_PILL_VALUE_MODE)?.let  { actionPillValueMode  = it }
        dataMap.getString(KEY_WF_ACTION_PILL_FIXED_VALUE)?.let { actionPillFixedValue = it }
        if (dataMap.containsKey(KEY_WF_ACTION_PILL_STATE))      actionPillState       = dataMap.getBoolean(KEY_WF_ACTION_PILL_STATE)
        if (dataMap.containsKey(KEY_WF_SHOW_CUSTOM_SLOTS))      showCustomSlots       = dataMap.getBoolean(KEY_WF_SHOW_CUSTOM_SLOTS)
        dataMap.getString(KEY_WF_CUSTOM_SLOT1_LABEL)?.let { customSlot1Label = it }
        dataMap.getString(KEY_WF_CUSTOM_SLOT2_LABEL)?.let { customSlot2Label = it }
    }
}

/**
 * In-memory-Cache für ioBroker-Zustände, zugänglich vom Watchface-Renderer.
 * Nutzt leichtgewichtiges JSON-Parsing ohne externe Bibliotheken.
 */
object SmartHomeStateCache {

    @Volatile var states: List<CachedState> = emptyList()
        private set

    @Volatile var lastUpdated: Long = 0L
        private set

    fun updateFromJson(json: String) {
        lastUpdated = System.currentTimeMillis()
        states = parseStatesSimple(json)
    }

    fun getTopValue(): String? = states.firstOrNull()?.displayValue
    fun getBottomValue(): String? = states.getOrNull(1)?.displayValue

    private fun parseStatesSimple(json: String): List<CachedState> {
        if (json.isBlank() || json == "[]") return emptyList()
        return try {
            val result = mutableListOf<CachedState>()
            var depth = 0
            var start = -1
            for (i in json.indices) {
                when (json[i]) {
                    '{' -> { if (depth == 0) start = i; depth++ }
                    '}' -> {
                        depth--
                        if (depth == 0 && start >= 0) {
                            parseStateObject(json.substring(start, i + 1))?.let { result.add(it) }
                            start = -1
                        }
                    }
                }
            }
            result
        } catch (e: Exception) {
            Log.w(TAG, "JSON-Parse fehlgeschlagen: ${e.message}")
            emptyList()
        }
    }

    private fun parseStateObject(obj: String): CachedState? {
        val id    = extractJsonString(obj, "id")    ?: return null
        val name  = extractJsonString(obj, "name")  ?: id
        val value = extractJsonString(obj, "value")
        val unit  = extractJsonString(obj, "unit")
        val type  = extractJsonString(obj, "type")  ?: "mixed"
        return CachedState(id = id, name = name, value = value, unit = unit, type = type)
    }

    private fun extractJsonString(json: String, key: String): String? =
        Regex("\"$key\"\\s*:\\s*\"([^\"]*)\"").find(json)?.groupValues?.get(1)
}

data class CachedState(
    val id: String,
    val name: String,
    val value: String?,
    val unit: String?,
    val type: String = "mixed"
) {
    val isNumeric: Boolean get() = type == "number"
    val numericValue: Float? get() = value?.toFloatOrNull()
    val isPercent: Boolean get() = unit == "%" || unit == "Prozent"

    val displayValue: String get() = when {
        value == null -> "–"
        unit != null  -> "$value $unit"
        else          -> value
    }

    val displayLabel: String get() = if (name.length > 16) name.take(15) + "…" else name
}
