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
private const val KEY_WF_SECONDS_GLOW_WIDTH   = "wf_seconds_glow_width"
private const val KEY_WF_SECONDS_NUMBER_COLOR = "wf_seconds_number_color"

// ── Gesundheits-/Wetter-Anzeige-Keys ─────────────────────────────────────────
private const val KEY_WF_SHOW_WEATHER     = "wf_show_weather"
private const val KEY_WF_SHOW_SUNRISE     = "wf_show_sunrise"
private const val KEY_WF_SHOW_HEART_RATE  = "wf_show_heart_rate"
private const val KEY_WF_SHOW_OXYGEN      = "wf_show_oxygen"
private const val KEY_WF_SHOW_CALORIES    = "wf_show_calories"
private const val KEY_WF_SHOW_STEPS       = "wf_show_steps"

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

// ── Custom ioBroker-Slots (4 Datenpunkte unter der Uhrzeit) ─────────────────
private const val KEY_WF_CUSTOM_SLOT1_LABEL = "wf_custom_slot1_label"
private const val KEY_WF_CUSTOM_SLOT1_VALUE = "wf_custom_slot1_value"
private const val KEY_WF_CUSTOM_SLOT2_LABEL = "wf_custom_slot2_label"
private const val KEY_WF_CUSTOM_SLOT2_VALUE = "wf_custom_slot2_value"
private const val KEY_WF_CUSTOM_SLOT3_LABEL = "wf_custom_slot3_label"
private const val KEY_WF_CUSTOM_SLOT3_VALUE = "wf_custom_slot3_value"
private const val KEY_WF_CUSTOM_SLOT4_LABEL = "wf_custom_slot4_label"
private const val KEY_WF_CUSTOM_SLOT4_VALUE = "wf_custom_slot4_value"
private const val KEY_WF_CUSTOM_SLOT4_BAR_COLOR = "wf_custom_slot4_bar_color"
private const val KEY_WF_CUSTOM_SLOT4_BAR_MIN   = "wf_custom_slot4_bar_min"
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
private const val KEY_WF_WATCH_BATTERY_TEXT_SCALE  = "wf_watch_battery_text_scale"
private const val KEY_WF_STEPS_TEXT_SCALE          = "wf_steps_text_scale"
private const val KEY_WF_HEALTH_DATA_SOURCE        = "wf_health_data_source"
private const val KEY_WF_HR_SOURCE                 = "wf_hr_source"
private const val KEY_WF_KCAL_SOURCE               = "wf_kcal_source"
private const val KEY_WF_OXYGEN_SOURCE             = "wf_oxygen_source"
// Pro-Typ gewählte Komplikation (Slot-ID als String, "" = keine)
private const val KEY_WF_HR_COMPLICATION           = "wf_hr_complication"
private const val KEY_WF_KCAL_COMPLICATION         = "wf_kcal_complication"
private const val KEY_WF_OXYGEN_COMPLICATION       = "wf_oxygen_complication"
// Dynamische Überschrift + Format-Einheit der frei wählbaren Health-Slots (Kcal/Oxygen)
private const val KEY_WF_KCAL_LABEL                = "wf_kcal_label"
private const val KEY_WF_KCAL_UNIT                 = "wf_kcal_unit"
private const val KEY_WF_OXYGEN_LABEL              = "wf_oxygen_label"
private const val KEY_WF_OXYGEN_UNIT               = "wf_oxygen_unit"

// ── Boden-Komplikationen (2 Stück, in den unteren Kreistaschen) ──────────────
private const val KEY_WF_SHOW_BOTTOM_COMP   = "wf_show_bottom_comp"
// Komplikation 1 (links) – Puls oder ioBroker-Datenpunkt
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
// Komplikation 2 (rechts) – wählbare Metrik oder ioBroker-Datenpunkt (kcal|oxygen|bloodpressure|training)
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

// ── Phone-Health-Daten (vom Smartphone gesendet) ────────────────────────────
private const val PATH_PHONE_HEALTH          = "/iosync/watchface/phone_health"
private const val KEY_PHONE_HEART_RATE       = "phone_heart_rate"
private const val KEY_PHONE_SPO2             = "phone_spo2"
private const val KEY_PHONE_CALORIES         = "phone_calories"

// ── Hintergrund ───────────────────────────────────────────────────────────────
private const val KEY_WF_SHOW_BACKGROUND    = "wf_show_background"
private const val KEY_WF_P2_SHOW_BACKGROUND = "wf_p2_show_background"

// ── NTP-Zeitkorrektur (Offset wird auf der Uhr per NTP ermittelt) ────────────
private const val KEY_WF_NTP_ENABLED = "wf_ntp_enabled"
private const val KEY_WF_NTP_SERVER  = "wf_ntp_server"

// ── Gesundheitsdaten-Farben ───────────────────────────────────────────────────
private const val KEY_WF_HR_COLOR      = "wf_hr_color"
private const val KEY_WF_KCAL_COLOR    = "wf_kcal_color"
private const val KEY_WF_OXYGEN_COLOR  = "wf_oxygen_color"
private const val KEY_WF_STEPS_COLOR   = "wf_steps_color"
private const val KEY_WF_SLEEP_COLOR   = "wf_sleep_color"
private const val KEY_WF_SUNRISE_COLOR = "wf_sunrise_color"

// ── ioBroker-Slot-Farbe (Wert-Text) ──────────────────────────────────────────
private const val KEY_WF_SLOT_COLOR = "wf_slot_color"

// ── Akku-Ring-Farben ──────────────────────────────────────────────────────────
private const val KEY_WF_BATTERY_RING_COLOR1       = "wf_battery_ring_color1"
private const val KEY_WF_BATTERY_RING_COLOR2       = "wf_battery_ring_color2"
private const val KEY_WF_BATTERY_RING_STROKE_SCALE = "wf_battery_ring_stroke_scale"

// ── Akku-Ring Warnstufen (Schwelle in %, 0 = deaktiviert) ─────────────────────
private const val KEY_WF_BATTERY_WARN1_COLOR     = "wf_battery_warn1_color"
private const val KEY_WF_BATTERY_WARN1_THRESHOLD = "wf_battery_warn1_threshold"
private const val KEY_WF_BATTERY_WARN2_COLOR     = "wf_battery_warn2_color"
private const val KEY_WF_BATTERY_WARN2_THRESHOLD = "wf_battery_warn2_threshold"

// ── Balken (Slot 4) Warnstufen (Schwelle als absoluter Wert, NaN = deaktiviert) ─
private const val KEY_WF_SLOT4_WARN1_COLOR = "wf_slot4_warn1_color"
private const val KEY_WF_SLOT4_WARN1_VALUE = "wf_slot4_warn1_value"
private const val KEY_WF_SLOT4_WARN2_COLOR = "wf_slot4_warn2_color"
private const val KEY_WF_SLOT4_WARN2_VALUE = "wf_slot4_warn2_value"

// ── Schlafdauer (vom Handy via Health Connect, in Minuten) ────────────────────
private const val KEY_PHONE_SLEEP_MINUTES = "phone_sleep_minutes"

// ── Wetter-Quelle: "openweather" oder "iobroker" ─────────────────────────────
private const val KEY_WF_WEATHER_TEMP_SOURCE  = "wf_weather_temp_source"
private const val KEY_WF_WEATHER_IOBROKER_ID  = "wf_weather_iobroker_id"

// ── Custom-Slot-Daten (Echtzeit-Updates der Werte) ──────────────────────────
private const val PATH_CUSTOM_SLOTS         = "/iosync/watchface/custom_slots"

// ── Seite-2 ioBroker-Slots (4 Datenpunkte auf der zweiten Watchface-Seite) ──
private const val PATH_CUSTOM_SLOTS_P2      = "/iosync/watchface/custom_slots_p2"
private const val KEY_WF_P2_SLOT1_LABEL     = "wf_p2_slot1_label"
private const val KEY_WF_P2_SLOT1_VALUE     = "wf_p2_slot1_value"
private const val KEY_WF_P2_SLOT2_LABEL     = "wf_p2_slot2_label"
private const val KEY_WF_P2_SLOT2_VALUE     = "wf_p2_slot2_value"
private const val KEY_WF_P2_SLOT3_LABEL     = "wf_p2_slot3_label"
private const val KEY_WF_P2_SLOT3_VALUE     = "wf_p2_slot3_value"
private const val KEY_WF_P2_SLOT4_LABEL     = "wf_p2_slot4_label"
private const val KEY_WF_P2_SLOT4_VALUE     = "wf_p2_slot4_value"

// ── Seite-2 Konfig (Pillen + Textgrößen) ─────────────────────────────────────
private const val PATH_CONFIG_P2                = "/iosync/watchface/config_p2"
private const val KEY_WF_P2_PILL_ENABLED        = "wf_p2_pill_enabled"
private const val KEY_WF_P2_PILL_COLOR_TRUE     = "wf_p2_pill_color_true"
private const val KEY_WF_P2_PILL_COLOR_FALSE    = "wf_p2_pill_color_false"
private const val KEY_WF_P2_PILL_IOBROKER_ID    = "wf_p2_pill_iobroker_id"
private const val KEY_WF_P2_PILL_VALUE_MODE     = "wf_p2_pill_value_mode"
private const val KEY_WF_P2_PILL_FIXED_VALUE    = "wf_p2_pill_fixed_value"
private const val KEY_WF_P2_PILL2_ENABLED       = "wf_p2_pill2_enabled"
private const val KEY_WF_P2_PILL2_COLOR_TRUE    = "wf_p2_pill2_color_true"
private const val KEY_WF_P2_PILL2_COLOR_FALSE   = "wf_p2_pill2_color_false"
private const val KEY_WF_P2_PILL2_IOBROKER_ID   = "wf_p2_pill2_iobroker_id"
private const val KEY_WF_P2_PILL2_VALUE_MODE    = "wf_p2_pill2_value_mode"
private const val KEY_WF_P2_PILL2_FIXED_VALUE   = "wf_p2_pill2_fixed_value"
private const val PATH_P2_PILL_STATES           = "/iosync/watchface/p2_pill_states"
private const val KEY_WF_P2_SLOT1_TEXT_SCALE    = "wf_p2_slot1_text_scale"
private const val KEY_WF_P2_SLOT2_TEXT_SCALE    = "wf_p2_slot2_text_scale"
private const val KEY_WF_P2_SLOT3_TEXT_SCALE    = "wf_p2_slot3_text_scale"
private const val KEY_WF_P2_SLOT4_TEXT_SCALE    = "wf_p2_slot4_text_scale"
private const val KEY_WF_SLEEP_TEXT_SCALE       = "wf_sleep_text_scale"

// ── Seite 2 – vertikaler Balken (Slot 5) ─────────────────────────────────────
private const val KEY_WF_P2_BAR_LABEL           = "wf_p2_bar_label"
private const val KEY_WF_P2_BAR_VALUE           = "wf_p2_bar_value"
private const val KEY_WF_P2_BAR_COLOR           = "wf_p2_bar_color"
private const val KEY_WF_P2_BAR_MIN             = "wf_p2_bar_min"
private const val KEY_WF_P2_BAR_MAX             = "wf_p2_bar_max"
private const val KEY_WF_P2_BAR_SHOW_LABEL      = "wf_p2_bar_show_label"
private const val KEY_WF_P2_BAR_IS_SLIDER       = "wf_p2_bar_is_slider"
private const val KEY_WF_P2_BAR_TEXT_SCALE      = "wf_p2_bar_text_scale"
private const val KEY_WF_P2_BAR_WARN1_COLOR     = "wf_p2_bar_warn1_color"
private const val KEY_WF_P2_BAR_WARN1_VALUE     = "wf_p2_bar_warn1_value"
private const val KEY_WF_P2_BAR_WARN2_COLOR     = "wf_p2_bar_warn2_color"
private const val KEY_WF_P2_BAR_WARN2_VALUE     = "wf_p2_bar_warn2_value"

// ── Aktions-Pille Status-Pfad (separater Pfad für schnelle State-Updates) ────
private const val PATH_ACTION_PILL_STATE = "/iosync/watchface/action_pill_state"
private const val KEY_PILL_STATE         = "pill_state"

// ── Verbindungs-Konfig (ab v5: Uhr fragt ioBroker + Wetter direkt selbst ab) ─
// Das Handy überträgt nur noch die Verbindungs-/Datenpunkt-Einstellungen, die
// Uhr ruft die Werte eigenständig vom Adapter / OpenWeather ab.
private const val PATH_CONNECTION_CONFIG = "/iosync/watchface/connection"
private const val KEY_IO_USE_ADAPTER     = "io_use_adapter"
private const val KEY_IO_HOST            = "io_host"
private const val KEY_IO_PORT            = "io_port"
private const val KEY_IO_USE_HTTPS       = "io_use_https"
private const val KEY_IO_USERNAME        = "io_username"
private const val KEY_IO_PASSWORD        = "io_password"
private const val KEY_IO_USE_PUSH        = "io_use_push"
// Datenpunkt-IDs der Slots (Werte werden auf der Uhr aufgelöst)
private const val KEY_CON_SLOT1_ID       = "con_slot1_id"
private const val KEY_CON_SLOT2_ID       = "con_slot2_id"
private const val KEY_CON_SLOT3_ID       = "con_slot3_id"
private const val KEY_CON_SLOT4_ID       = "con_slot4_id"
private const val KEY_CON_P2_SLOT1_ID    = "con_p2_slot1_id"
private const val KEY_CON_P2_SLOT2_ID    = "con_p2_slot2_id"
private const val KEY_CON_P2_SLOT3_ID    = "con_p2_slot3_id"
private const val KEY_CON_P2_SLOT4_ID    = "con_p2_slot4_id"
private const val KEY_CON_P2_BAR_ID      = "con_p2_bar_id"
private const val KEY_CON_SLEEP_ID       = "con_sleep_id"
// Wetter-Standort (Uhr hat kein GPS → festes lat/lon vom Handy)
private const val KEY_CON_WEATHER_USE_FIXED = "con_weather_use_fixed"
private const val KEY_CON_WEATHER_LAT       = "con_weather_lat"
private const val KEY_CON_WEATHER_LON       = "con_weather_lon"
// Abruf-Intervalle (Sekunden)
private const val KEY_CON_SLOT_INTERVAL   = "con_slot_interval"
private const val KEY_CON_PAGE2_INTERVAL  = "con_page2_interval"
private const val KEY_CON_WEATHER_INTERVAL = "con_weather_interval"

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
                    if (dataMap.containsKey("show_phone_battery")) {
                        WatchFaceConfigCache.showPhoneBattery = dataMap.getBoolean("show_phone_battery", false)
                    }
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
                    dataMap.getString(KEY_WF_CUSTOM_SLOT3_LABEL)?.let { WatchFaceConfigCache.customSlot3Label = it }
                    dataMap.getString(KEY_WF_CUSTOM_SLOT3_VALUE)?.let { WatchFaceConfigCache.customSlot3Value = it }
                    dataMap.getString(KEY_WF_CUSTOM_SLOT4_LABEL)?.let { WatchFaceConfigCache.customSlot4Label = it }
                    dataMap.getString(KEY_WF_CUSTOM_SLOT4_VALUE)?.let { WatchFaceConfigCache.customSlot4Value = it }
                    dataMap.getString(KEY_WF_CUSTOM_SLOT4_BAR_COLOR)?.let { WatchFaceConfigCache.customSlot4BarColor = it }
                    if (dataMap.containsKey(KEY_WF_CUSTOM_SLOT4_BAR_MIN))        WatchFaceConfigCache.customSlot4BarMin       = dataMap.getFloat(KEY_WF_CUSTOM_SLOT4_BAR_MIN)
                    if (dataMap.containsKey(KEY_WF_CUSTOM_SLOT4_BAR_MAX))        WatchFaceConfigCache.customSlot4BarMax       = dataMap.getFloat(KEY_WF_CUSTOM_SLOT4_BAR_MAX)
                    if (dataMap.containsKey(KEY_WF_CUSTOM_SLOT4_BAR_SHOW_LABEL)) WatchFaceConfigCache.customSlot4BarShowLabel = dataMap.getBoolean(KEY_WF_CUSTOM_SLOT4_BAR_SHOW_LABEL)
                    if (dataMap.containsKey(KEY_WF_CUSTOM_SLOT4_BAR_IS_SLIDER))  WatchFaceConfigCache.customSlot4BarIsSlider  = dataMap.getBoolean(KEY_WF_CUSTOM_SLOT4_BAR_IS_SLIDER)
                    if (dataMap.containsKey(KEY_WF_CUSTOM_SLOT4_USE_KLIPPER))    WatchFaceConfigCache.customSlot4UseKlipper   = dataMap.getBoolean(KEY_WF_CUSTOM_SLOT4_USE_KLIPPER)
                    dataMap.getString(KEY_WF_CUSTOM_SLOT4_KLIPPER_SOURCE)?.let  { WatchFaceConfigCache.customSlot4KlipperSource = it }
                    dataMap.getString(KEY_WF_CUSTOM_SLOT4_KLIPPER_COLOR_ACT)?.let { WatchFaceConfigCache.customSlot4KlipperColorActive = it }
                    dataMap.getString(KEY_WF_SLOT4_WARN1_COLOR)?.let { WatchFaceConfigCache.slot4Warn1Color = it }
                    dataMap.getString(KEY_WF_SLOT4_WARN2_COLOR)?.let { WatchFaceConfigCache.slot4Warn2Color = it }
                    if (dataMap.containsKey(KEY_WF_SLOT4_WARN1_VALUE)) WatchFaceConfigCache.slot4Warn1Value = dataMap.getFloat(KEY_WF_SLOT4_WARN1_VALUE)
                    if (dataMap.containsKey(KEY_WF_SLOT4_WARN2_VALUE)) WatchFaceConfigCache.slot4Warn2Value = dataMap.getFloat(KEY_WF_SLOT4_WARN2_VALUE)
                    Log.d(TAG, "Custom-Slot-Daten empfangen")
                }
                PATH_PHONE_HEALTH -> {
                    val dataMap = DataMapItem.fromDataItem(event.dataItem).dataMap
                    val newHr   = if (dataMap.containsKey(KEY_PHONE_HEART_RATE)) dataMap.getInt(KEY_PHONE_HEART_RATE) else WatchFaceConfigCache.phoneHeartRate
                    val newSpO2 = if (dataMap.containsKey(KEY_PHONE_SPO2))       dataMap.getInt(KEY_PHONE_SPO2)       else WatchFaceConfigCache.phoneSpO2
                    val newKcal = if (dataMap.containsKey(KEY_PHONE_CALORIES))   dataMap.getInt(KEY_PHONE_CALORIES)   else WatchFaceConfigCache.phoneCalories
                    WatchFaceConfigCache.phoneHeartRate = newHr
                    WatchFaceConfigCache.phoneSpO2      = newSpO2
                    WatchFaceConfigCache.phoneCalories  = newKcal
                    if (dataMap.containsKey(KEY_PHONE_SLEEP_MINUTES)) WatchFaceConfigCache.phoneSleepMinutes = dataMap.getInt(KEY_PHONE_SLEEP_MINUTES)
                    // Immer als "frisch" markieren wenn Daten vom Handy empfangen wurden –
                    // Wert 0 bedeutet "kein Messwert verfügbar", nicht "keine Verbindung".
                    // Vorher: nur bei Wert > 0 → nach 30 min stale → kcal zeigt "--" obwohl
                    // das Handy aktiv sendet und Health Connect einfach keine Daten liefert.
                    WatchFaceConfigCache.phoneHealthLastReceived = System.currentTimeMillis()
                    Log.d(TAG, "Phone-Health-Daten empfangen: HR=$newHr, SpO2=$newSpO2, kcal=$newKcal")
                }
                PATH_ACTION_PILL_STATE -> {
                    val dataMap = DataMapItem.fromDataItem(event.dataItem).dataMap
                    val state = dataMap.getBoolean(KEY_PILL_STATE, false)
                    Log.d(TAG, "Aktions-Pille Status empfangen: $state")
                    WatchFaceConfigCache.actionPillState = state
                }
                PATH_P2_PILL_STATES -> {
                    val dataMap = DataMapItem.fromDataItem(event.dataItem).dataMap
                    if (dataMap.containsKey("wf_p2_pill1_state")) WatchFaceConfigCache.p2Pill1State = dataMap.getBoolean("wf_p2_pill1_state")
                    if (dataMap.containsKey("wf_p2_pill2_state")) WatchFaceConfigCache.p2Pill2State = dataMap.getBoolean("wf_p2_pill2_state")
                    Log.d(TAG, "Seite-2-Pillen-Status empfangen: pill1=${WatchFaceConfigCache.p2Pill1State}, pill2=${WatchFaceConfigCache.p2Pill2State}")
                }
                PATH_CUSTOM_SLOTS_P2 -> {
                    val dataMap = DataMapItem.fromDataItem(event.dataItem).dataMap
                    dataMap.getString(KEY_WF_P2_SLOT1_LABEL)?.let { WatchFaceConfigCache.p2Slot1Label = it }
                    dataMap.getString(KEY_WF_P2_SLOT1_VALUE)?.let { WatchFaceConfigCache.p2Slot1Value = it }
                    dataMap.getString(KEY_WF_P2_SLOT2_LABEL)?.let { WatchFaceConfigCache.p2Slot2Label = it }
                    dataMap.getString(KEY_WF_P2_SLOT2_VALUE)?.let { WatchFaceConfigCache.p2Slot2Value = it }
                    dataMap.getString(KEY_WF_P2_SLOT3_LABEL)?.let { WatchFaceConfigCache.p2Slot3Label = it }
                    dataMap.getString(KEY_WF_P2_SLOT3_VALUE)?.let { WatchFaceConfigCache.p2Slot3Value = it }
                    dataMap.getString(KEY_WF_P2_SLOT4_LABEL)?.let { WatchFaceConfigCache.p2Slot4Label = it }
                    dataMap.getString(KEY_WF_P2_SLOT4_VALUE)?.let { WatchFaceConfigCache.p2Slot4Value = it }
                    dataMap.getString(KEY_WF_P2_BAR_VALUE)?.let   { WatchFaceConfigCache.p2BarValue   = it }
                    Log.d(TAG, "Seite-2-Slot-Daten empfangen")
                }
                PATH_CONFIG_P2 -> {
                    val dataMap = DataMapItem.fromDataItem(event.dataItem).dataMap
                    WatchFaceConfigCache.updateP2ConfigFromDataMap(dataMap)
                    Log.d(TAG, "Seite-2-Konfig empfangen")
                }
                PATH_CONNECTION_CONFIG -> {
                    val dataMap = DataMapItem.fromDataItem(event.dataItem).dataMap
                    WatchFaceConfigCache.updateConnectionFromDataMap(dataMap)
                    // Sofort in SharedPreferences sichern, damit die Config nach einem
                    // Watchface-Prozess-Neustart ohne Handy-Verbindung verfügbar ist.
                    WatchConnectionPrefs.save(applicationContext)
                    Log.d(TAG, "Verbindungs-Konfig empfangen und gespeichert (Uhr fragt selbst ab)")
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

    @Volatile var showBackground: Boolean = false
    @Volatile var showBackgroundPage2: Boolean = false
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
    @Volatile var secondsGlowWidth: Int = 100
    @Volatile var secondsNumberColorId: String = "dim_time"
    // Gesundheits- und Wetter-Anzeige
    @Volatile var showWeather: Boolean = true
    @Volatile var showSunrise: Boolean = true
    @Volatile var showHeartRate: Boolean = true
    @Volatile var showOxygen: Boolean = false
    @Volatile var showCalories: Boolean = true
    @Volatile var showSteps: Boolean = true
    // Wetterdaten (vom Handy empfangen)
    @Volatile var weatherTemp: Int = 0
    @Volatile var weatherCondition: String = "clear"
    // Wetter-Temperaturquelle: "openweather" = OpenWeather-API, "iobroker" = ioBroker-Datenpunkt
    @Volatile var weatherTempSource: String = "openweather"
    @Volatile var weatherIoBrokerId: String = ""
    // Aktions-Pille
    @Volatile var actionPillEnabled: Boolean = false
    @Volatile var actionPillColorTrue: String = "cyan"
    @Volatile var actionPillColorFalse: String = "red"
    @Volatile var actionPillIoBrokerId: String = ""
    @Volatile var actionPillValueMode: String = "toggle"
    @Volatile var actionPillFixedValue: String = ""
    @Volatile var actionPillState: Boolean = false
    @Volatile var lastConfigReceivedAt: Long = 0L
    // Custom ioBroker-Slots (4 Datenpunkte unter der Uhrzeit)
    @Volatile var showCustomSlots: Boolean = false
    @Volatile var customSlot1Label: String = ""
    @Volatile var customSlot1Value: String = "--"
    @Volatile var customSlot2Label: String = ""
    @Volatile var customSlot2Value: String = "--"
    @Volatile var customSlot3Label: String = ""
    @Volatile var customSlot3Value: String = "--"
    // Slot 4: Balken-Graph
    @Volatile var customSlot4Label: String = ""
    @Volatile var customSlot4Value: String = "--"
    @Volatile var customSlot4BarColor: String = "neon_yellow"
    @Volatile var customSlot4BarMin: Float = 0f
    @Volatile var customSlot4BarMax: Float = 100f
    @Volatile var customSlot4BarShowLabel: Boolean = true
    @Volatile var customSlot4BarIsSlider: Boolean = false
    // Klipper-Override: Wenn aktiv, zeigt Slot-4-Balken Klipper-Wert statt ioBroker-Wert
    @Volatile var customSlot4UseKlipper: Boolean = false
    @Volatile var customSlot4KlipperSource: String = "progress"   // progress|nozzle_temp|bed_temp|chamber_temp|fan|speed
    @Volatile var customSlot4KlipperColorActive: String = "neon_yellow"
    // Individuelle Schriftgrößen je Wert (70–160, Default 100 = 100 %)
    @Volatile var hrTextScale: Int = 100
    @Volatile var kcalTextScale: Int = 100
    @Volatile var stepsTextScale: Int = 100
    @Volatile var slot1TextScale: Int = 100
    @Volatile var slot2TextScale: Int = 100
    @Volatile var slot3TextScale: Int = 100
    @Volatile var slot4TextScale: Int = 100
    @Volatile var weatherTextScale: Int = 100
    @Volatile var sunriseTextScale: Int = 100
    @Volatile var watchBatteryTextScale: Int = 100
    // Akku-Ring-Farben (Farbverlauf) und Ringbreite
    @Volatile var batteryRingColor1: String = "cyan"
    @Volatile var batteryRingColor2: String = "neon_yellow"
    @Volatile var batteryRingStrokeScale: Int = 100
    // Akku-Ring Warnstufen (Schwelle in %, 0 = deaktiviert)
    @Volatile var batteryWarn1Color: String = "orange"
    @Volatile var batteryWarn1Threshold: Int = 0
    @Volatile var batteryWarn2Color: String = "red"
    @Volatile var batteryWarn2Threshold: Int = 0
    // Balken (Slot 4) Warnstufen (absoluter Wert, NaN = deaktiviert)
    @Volatile var slot4Warn1Color: String = "orange"
    @Volatile var slot4Warn1Value: Float = Float.NaN
    @Volatile var slot4Warn2Color: String = "red"
    @Volatile var slot4Warn2Value: Float = Float.NaN
    // Gesundheitsdaten-Farben (Standard = ursprüngliche Farben)
    @Volatile var hrColor: String      = "red"
    @Volatile var kcalColor: String    = "orange"
    @Volatile var oxygenColor: String  = "cyan"
    @Volatile var stepsColor: String   = "neon_yellow"
    @Volatile var sleepColor: String   = "purple"
    @Volatile var sunriseColor: String = "neon_yellow"
    // ioBroker-Slot-Farbe (Wert-Text)
    @Volatile var slotColor: String    = "neon_yellow"
    // Schlafdauer (vom Handy via Health Connect, in Minuten)
    @Volatile var phoneSleepMinutes: Int = 0
    // Gesundheitsdaten-Quelle: "local" = Uhr-Sensoren, "phone" = vom Smartphone
    @Volatile var healthDataSource: String = "local"
    // Pro-Typ Quelle: "local" = Uhr-Sensoren, "healthconnect" = Health Connect via App
    @Volatile var hrSource: String = "local"
    @Volatile var kcalSource: String = "local"
    @Volatile var oxygenSource: String = "local"
    // Pro-Typ gewählte Komplikation (Slot-ID als String, "" = keine → normale Quelle)
    @Volatile var hrComplication: String = ""
    @Volatile var kcalComplication: String = ""
    @Volatile var oxygenComplication: String = ""
    // Dynamische Überschrift + Format-Einheit der frei wählbaren Health-Slots.
    // Werden vom Handy passend zur gewählten Metrik gesetzt (z. B. "TEMP"/"°C").
    @Volatile var kcalLabel: String = "KCAL"
    @Volatile var kcalUnit: String = "kcal"
    @Volatile var oxygenLabel: String = "OXYGEN"
    @Volatile var oxygenUnit: String = "%"
    // Phone-Health-Daten (wenn Quelle = "healthconnect")
    @Volatile var phoneHeartRate: Int = 0
    @Volatile var phoneSpO2: Int = 0
    @Volatile var phoneCalories: Int = 0
    @Volatile var phoneHealthLastReceived: Long = 0L

    // ── Boden-Komplikationen (2 Kreistaschen unten) ───────────────────────────
    @Volatile var showBottomComp: Boolean = true
    // BC1 (links) – Puls oder ioBroker-Datenpunkt
    @Volatile var bc1UseIoBroker: Boolean = false
    @Volatile var bc1Label: String = "BPM"
    @Volatile var bc1Color: String = "red"
    @Volatile var bc1RingEnabled: Boolean = true
    @Volatile var bc1RingColor1: String = "red"
    @Volatile var bc1RingColor2: String = "orange"
    @Volatile var bc1RingMin: Float = 0f
    @Volatile var bc1RingMax: Float = 140f
    @Volatile var bc1RingWidth: Int = 6
    // Schwellenwert-Farbumschlag für den BC1-Ring
    @Volatile var bc1RingThreshEnabled: Boolean = false
    @Volatile var bc1RingThreshValue: Float = 0f
    @Volatile var bc1RingThreshDir: String = "above"      // above | below
    @Volatile var bc1RingThreshTarget: String = "color2"  // color1 | color2
    @Volatile var bc1RingThreshColor: String = "red"
    @Volatile var bc1TextScale: Int = 100
    // BC2 (rechts) – wählbare Metrik oder ioBroker-Datenpunkt
    @Volatile var bc2Metric: String = "kcal"  // kcal | oxygen | bloodpressure | training
    @Volatile var bc2UseIoBroker: Boolean = false
    @Volatile var bc2Label: String = "KCAL"
    @Volatile var bc2Color: String = "orange"
    @Volatile var bc2RingEnabled: Boolean = true
    @Volatile var bc2RingColor1: String = "orange"
    @Volatile var bc2RingColor2: String = "neon_yellow"
    @Volatile var bc2RingMin: Float = 0f
    @Volatile var bc2RingMax: Float = 1000f
    @Volatile var bc2RingWidth: Int = 6
    // Schwellenwert-Farbumschlag für den BC2-Ring
    @Volatile var bc2RingThreshEnabled: Boolean = false
    @Volatile var bc2RingThreshValue: Float = 0f
    @Volatile var bc2RingThreshDir: String = "above"      // above | below
    @Volatile var bc2RingThreshTarget: String = "color2"  // color1 | color2
    @Volatile var bc2RingThreshColor: String = "red"
    @Volatile var bc2TextScale: Int = 100
    // ioBroker-Datenpunkt-IDs für BC1/BC2 (aus Verbindungs-Konfig)
    @Volatile var conBc1Id: String = ""
    @Volatile var conBc2Id: String = ""
    // Gecachte ioBroker-Werte für BC1/BC2 (wird von WatchDataSyncManager befüllt)
    @Volatile var bc1IoValue: String = "--"
    @Volatile var bc2IoValue: String = "--"

    // ── NTP-Zeitkorrektur ─────────────────────────────────────────────────────
    // ntpEnabled/ntpServer kommen vom Handy; ntpOffsetMs wird auf der Uhr per
    // NTP-Abfrage ermittelt (alle 30 min) und auf die Systemzeit aufaddiert.
    @Volatile var ntpEnabled: Boolean = false
    @Volatile var ntpServer: String = "pool.ntp.org"
    @Volatile var ntpOffsetMs: Long = 0L

    // ── Seite 2 ioBroker-Slots ────────────────────────────────────────────────
    @Volatile var p2Slot1Label: String = ""
    @Volatile var p2Slot1Value: String = "--"
    @Volatile var p2Slot2Label: String = ""
    @Volatile var p2Slot2Value: String = "--"
    @Volatile var p2Slot3Label: String = ""
    @Volatile var p2Slot3Value: String = "--"
    @Volatile var p2Slot4Label: String = ""
    @Volatile var p2Slot4Value: String = "--"

    // ── Seite 2 Textgrößen ────────────────────────────────────────────────────
    @Volatile var p2Slot1TextScale: Int = 100
    @Volatile var p2Slot2TextScale: Int = 100
    @Volatile var p2Slot3TextScale: Int = 100
    @Volatile var p2Slot4TextScale: Int = 100
    @Volatile var sleepTextScale: Int = 100
    @Volatile var sleepSource: String = "healthconnect"
    @Volatile var sleepComplication: String = ""

    // ── Seite 2 – vertikaler Balken ───────────────────────────────────────────
    @Volatile var p2BarLabel: String = ""
    @Volatile var p2BarValue: String = "--"
    @Volatile var p2BarColor: String = "neon_yellow"
    @Volatile var p2BarMin: Float = 0f
    @Volatile var p2BarMax: Float = 100f
    @Volatile var p2BarShowLabel: Boolean = true
    @Volatile var p2BarIsSlider: Boolean = false
    @Volatile var p2BarTextScale: Int = 100
    @Volatile var p2BarWarn1Color: String = "orange"
    @Volatile var p2BarWarn1Value: Float = Float.NaN
    @Volatile var p2BarWarn2Color: String = "red"
    @Volatile var p2BarWarn2Value: Float = Float.NaN

    // ── Seite 2 Pillen – Pille 1 (7 Uhr) ─────────────────────────────────────
    @Volatile var p2PillEnabled: Boolean = false
    @Volatile var p2PillColorTrue: String = "cyan"
    @Volatile var p2PillColorFalse: String = "red"
    @Volatile var p2PillIoBrokerId: String = ""
    @Volatile var p2PillValueMode: String = "toggle"
    @Volatile var p2PillFixedValue: String = ""
    @Volatile var p2Pill1State: Boolean = false
    // ── Seite 2 Pillen – Pille 2 (5 Uhr) ─────────────────────────────────────
    @Volatile var p2Pill2Enabled: Boolean = false
    @Volatile var p2Pill2ColorTrue: String = "cyan"
    @Volatile var p2Pill2ColorFalse: String = "red"
    @Volatile var p2Pill2IoBrokerId: String = ""
    @Volatile var p2Pill2ValueMode: String = "toggle"
    @Volatile var p2Pill2FixedValue: String = ""
    @Volatile var p2Pill2State: Boolean = false

    // ── Klipper-Verbindung (Seite 3 – Moonraker-API) ─────────────────────────
    @Volatile var klipperEnabled: Boolean = false
    @Volatile var klipperHost: String = ""
    @Volatile var klipperPort: Int = 7125
    @Volatile var klipperApiKey: String = ""
    // Objekt-Name für Chamber-Temperatur (default: "heater_generic chamber")
    @Volatile var klipperChamberObject: String = "heater_generic chamber"

    // ── Seite 3 – Pille (6 Uhr) ───────────────────────────────────────────────
    @Volatile var p3PillEnabled: Boolean = false
    @Volatile var p3PillColorTrue: String = "cyan"
    @Volatile var p3PillColorFalse: String = "red"
    @Volatile var p3PillObject: String = ""      // Moonraker-Objekt, z.B. "output_pin my_led"
    @Volatile var p3PillField: String = "value"  // Feld im Objekt
    @Volatile var p3PillGcodeOn: String = ""     // G-Code zum Einschalten
    @Volatile var p3PillGcodeOff: String = ""    // G-Code zum Ausschalten
    @Volatile var p3PillState: Boolean = false

    // ── Seite 3 – LED-Button ──────────────────────────────────────────────────
    /** "gcode" = Moonraker G-Code; "tasmota_power" = Moonraker Power-API */
    @Volatile var klipperLedType: String = "gcode"
    @Volatile var klipperLedGcodeOn: String = ""
    @Volatile var klipperLedGcodeOff: String = ""
    @Volatile var klipperLedObject: String = ""  // Objekt zum Ablesen des LED-Status
    @Volatile var klipperLedField: String = "value"
    @Volatile var klipperLedPowerDevice: String = ""  // Moonraker-Power-Gerätename (z.B. "LED")
    @Volatile var klipperLedState: Boolean = false
    @Volatile var klipperLedLabel: String = "Led"

    // ── Seite 3 – Chamber-Heater-Button ───────────────────────────────────────
    /** "gcode" = manuelle Befehle; "heater_generic" = Moonraker heater_generic */
    @Volatile var klipperHeatType: String = "gcode"
    @Volatile var klipperHeatHeaterName: String = "chamber"
    @Volatile var klipperHeatTargetTemp: Int = 50
    @Volatile var klipperChamberHeatGcodeOn: String = ""
    @Volatile var klipperChamberHeatGcodeOff: String = ""
    @Volatile var klipperChamberHeatState: Boolean = false
    @Volatile var klipperHeatLabel: String = "Heater"
    @Volatile var p3FontScale: Int = 100

    // ── Seite 3 – Live-Druckdaten (direkt von Moonraker abgerufen) ────────────
    @Volatile var klipperIsActive: Boolean = false   // true wenn Drucker aktiv druckt
    @Volatile var klipperPrintProgress: Float = 0f   // 0.0–1.0
    // Eigenständiger Druckfortschritt für die Seite-1-Boden-Komplikation (Metrik
    // "klipper_progress"). Wird über einen separaten Moonraker-WebSocket gefüllt und
    // ist komplett unabhängig vom Seite-3-Polling (oben). 0.0–1.0.
    @Volatile var bc2KlipperProgress: Float = 0f
    @Volatile var klipperNozzleTemp: Float = 0f
    @Volatile var klipperNozzleTarget: Float = 0f
    @Volatile var klipperBedTemp: Float = 0f
    @Volatile var klipperBedTarget: Float = 0f
    @Volatile var klipperChamberTemp: Float = 0f
    @Volatile var klipperSpeedMms: Float = 0f
    @Volatile var klipperFanPercent: Float = 0f

    // ── Verbindungs-Konfig (ab v5: Uhr fragt ioBroker + Wetter selbst ab) ─────
    @Volatile var ioUseAdapter: Boolean = false
    @Volatile var ioHost: String = ""
    @Volatile var ioPort: Int = 7443
    @Volatile var ioUseHttps: Boolean = false
    @Volatile var ioUsername: String = ""
    @Volatile var ioPassword: String = ""
    @Volatile var ioUsePush: Boolean = false
    // Datenpunkt-IDs der Slots (Werte werden auf der Uhr aufgelöst)
    @Volatile var conSlot1Id: String = ""
    @Volatile var conSlot2Id: String = ""
    @Volatile var conSlot3Id: String = ""
    @Volatile var conSlot4Id: String = ""
    @Volatile var conP2Slot1Id: String = ""
    @Volatile var conP2Slot2Id: String = ""
    @Volatile var conP2Slot3Id: String = ""
    @Volatile var conP2Slot4Id: String = ""
    @Volatile var conP2BarId: String = ""
    @Volatile var conSleepId: String = ""
    // Wetter-Standort (Uhr hat kein GPS → festes lat/lon vom Handy)
    @Volatile var weatherUseFixed: Boolean = false
    @Volatile var weatherLat: Double = Double.NaN
    @Volatile var weatherLon: Double = Double.NaN
    // Abruf-Intervalle (Sekunden)
    @Volatile var slotIntervalSec: Int = 120
    @Volatile var page2IntervalSec: Int = 120
    @Volatile var weatherIntervalSec: Int = 600
    @Volatile var klipperIntervalSec: Int = 15
    // Puls-Mess-Intervall (Sekunden): wie oft der optische Sensor kurz für eine
    // Einzelmessung eingeschaltet wird (Standard 10 min). Spart Akku ggü. Live-Messung.
    @Volatile var heartRateIntervalSec: Int = 600
    // Zeitpunkt des letzten Empfangs der Verbindungs-Konfig
    @Volatile var connectionConfigReceivedAt: Long = 0L

    fun updateConnectionFromDataMap(dataMap: DataMap) {
        connectionConfigReceivedAt = System.currentTimeMillis()
        // Auch den allgemeinen Empfangs-Zeitstempel setzen, damit der Renderer den
        // "Config empfangen"-Banner zeigt, wenn nur die Verbindungs-Konfig
        // ("Speichern & Verbinden") an die Uhr übertragen wurde.
        lastConfigReceivedAt = System.currentTimeMillis()
        if (dataMap.containsKey(KEY_IO_USE_ADAPTER)) ioUseAdapter = dataMap.getBoolean(KEY_IO_USE_ADAPTER)
        dataMap.getString(KEY_IO_HOST)?.let { ioHost = it }
        if (dataMap.containsKey(KEY_IO_PORT)) ioPort = dataMap.getInt(KEY_IO_PORT)
        if (dataMap.containsKey(KEY_IO_USE_HTTPS)) ioUseHttps = dataMap.getBoolean(KEY_IO_USE_HTTPS)
        dataMap.getString(KEY_IO_USERNAME)?.let { ioUsername = it }
        dataMap.getString(KEY_IO_PASSWORD)?.let { ioPassword = it }
        if (dataMap.containsKey(KEY_IO_USE_PUSH)) ioUsePush = dataMap.getBoolean(KEY_IO_USE_PUSH)
        dataMap.getString(KEY_CON_SLOT1_ID)?.let { conSlot1Id = it }
        dataMap.getString(KEY_CON_SLOT2_ID)?.let { conSlot2Id = it }
        dataMap.getString(KEY_CON_SLOT3_ID)?.let { conSlot3Id = it }
        dataMap.getString(KEY_CON_SLOT4_ID)?.let { conSlot4Id = it }
        dataMap.getString(KEY_CON_P2_SLOT1_ID)?.let { conP2Slot1Id = it }
        dataMap.getString(KEY_CON_P2_SLOT2_ID)?.let { conP2Slot2Id = it }
        dataMap.getString(KEY_CON_P2_SLOT3_ID)?.let { conP2Slot3Id = it }
        dataMap.getString(KEY_CON_P2_SLOT4_ID)?.let { conP2Slot4Id = it }
        dataMap.getString(KEY_CON_P2_BAR_ID)?.let { conP2BarId = it }
        dataMap.getString(KEY_CON_SLEEP_ID)?.let { conSleepId = it }
        if (dataMap.containsKey(KEY_CON_WEATHER_USE_FIXED)) weatherUseFixed = dataMap.getBoolean(KEY_CON_WEATHER_USE_FIXED)
        if (dataMap.containsKey(KEY_CON_WEATHER_LAT)) weatherLat = dataMap.getDouble(KEY_CON_WEATHER_LAT)
        if (dataMap.containsKey(KEY_CON_WEATHER_LON)) weatherLon = dataMap.getDouble(KEY_CON_WEATHER_LON)
        if (dataMap.containsKey(KEY_CON_SLOT_INTERVAL)) slotIntervalSec = dataMap.getInt(KEY_CON_SLOT_INTERVAL)
        if (dataMap.containsKey(KEY_CON_PAGE2_INTERVAL)) page2IntervalSec = dataMap.getInt(KEY_CON_PAGE2_INTERVAL)
        if (dataMap.containsKey(KEY_CON_WEATHER_INTERVAL)) weatherIntervalSec = dataMap.getInt(KEY_CON_WEATHER_INTERVAL)
        if (dataMap.containsKey("con_hr_interval")) heartRateIntervalSec = dataMap.getInt("con_hr_interval")
        dataMap.getString("con_bc1_id")?.let { conBc1Id = it }
        dataMap.getString("con_bc2_id")?.let { conBc2Id = it }
        // Klipper-Verbindung
        if (dataMap.containsKey("con_klipper_enabled")) klipperEnabled = dataMap.getBoolean("con_klipper_enabled")
        dataMap.getString("con_klipper_host")?.let { klipperHost = it }
        if (dataMap.containsKey("con_klipper_port")) klipperPort = dataMap.getInt("con_klipper_port")
        dataMap.getString("con_klipper_api_key")?.let { klipperApiKey = it }
        dataMap.getString("con_klipper_chamber_obj")?.let { klipperChamberObject = it }
        if (dataMap.containsKey("con_klipper_interval")) klipperIntervalSec = dataMap.getInt("con_klipper_interval")
        // Seite 3 – Pille
        if (dataMap.containsKey("con_p3_pill_enabled"))  p3PillEnabled    = dataMap.getBoolean("con_p3_pill_enabled")
        dataMap.getString("con_p3_pill_color_true")?.let  { p3PillColorTrue  = it }
        dataMap.getString("con_p3_pill_color_false")?.let { p3PillColorFalse = it }
        dataMap.getString("con_p3_pill_object")?.let      { p3PillObject     = it }
        dataMap.getString("con_p3_pill_field")?.let       { p3PillField      = it }
        dataMap.getString("con_p3_pill_gcode_on")?.let    { p3PillGcodeOn    = it }
        dataMap.getString("con_p3_pill_gcode_off")?.let   { p3PillGcodeOff   = it }
        // Seite 3 – LED-Button
        dataMap.getString("con_klipper_led_type")?.let        { klipperLedType        = it }
        dataMap.getString("con_klipper_led_gcode_on")?.let    { klipperLedGcodeOn     = it }
        dataMap.getString("con_klipper_led_gcode_off")?.let   { klipperLedGcodeOff    = it }
        dataMap.getString("con_klipper_led_object")?.let      { klipperLedObject      = it }
        dataMap.getString("con_klipper_led_field")?.let       { klipperLedField       = it }
        dataMap.getString("con_klipper_led_power_device")?.let{ klipperLedPowerDevice = it }
        dataMap.getString("con_klipper_led_label")?.let       { klipperLedLabel       = it }
        // Seite 3 – Chamber-Heater-Button
        dataMap.getString("con_klipper_heat_type")?.let        { klipperHeatType        = it }
        dataMap.getString("con_klipper_heat_heater_name")?.let { klipperHeatHeaterName  = it }
        if (dataMap.containsKey("con_klipper_heat_target_temp")) klipperHeatTargetTemp = dataMap.getInt("con_klipper_heat_target_temp")
        dataMap.getString("con_klipper_heat_gcode_on")?.let  { klipperChamberHeatGcodeOn  = it }
        dataMap.getString("con_klipper_heat_gcode_off")?.let { klipperChamberHeatGcodeOff = it }
        dataMap.getString("con_klipper_heat_label")?.let     { klipperHeatLabel           = it }
        if (dataMap.containsKey("con_p3_font_scale")) p3FontScale = dataMap.getInt("con_p3_font_scale")
    }

    fun updateFromDataMap(dataMap: DataMap) {
        lastConfigReceivedAt = System.currentTimeMillis()
        if (dataMap.containsKey(KEY_WF_SHOW_BACKGROUND)) showBackground = dataMap.getBoolean(KEY_WF_SHOW_BACKGROUND)
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
        if (dataMap.containsKey(KEY_WF_SECONDS_GLOW_WIDTH))  secondsGlowWidth  = dataMap.getInt(KEY_WF_SECONDS_GLOW_WIDTH)
        dataMap.getString(KEY_WF_SECONDS_NUMBER_COLOR)?.let { secondsNumberColorId = it }
        if (dataMap.containsKey(KEY_WF_SHOW_WEATHER))      showWeather    = dataMap.getBoolean(KEY_WF_SHOW_WEATHER)
        if (dataMap.containsKey(KEY_WF_SHOW_SUNRISE))      showSunrise    = dataMap.getBoolean(KEY_WF_SHOW_SUNRISE)
        if (dataMap.containsKey(KEY_WF_SHOW_HEART_RATE))   showHeartRate  = dataMap.getBoolean(KEY_WF_SHOW_HEART_RATE)
        if (dataMap.containsKey(KEY_WF_SHOW_OXYGEN))       showOxygen     = dataMap.getBoolean(KEY_WF_SHOW_OXYGEN)
        if (dataMap.containsKey(KEY_WF_SHOW_CALORIES))     showCalories   = dataMap.getBoolean(KEY_WF_SHOW_CALORIES)
        if (dataMap.containsKey(KEY_WF_SHOW_STEPS))        showSteps      = dataMap.getBoolean(KEY_WF_SHOW_STEPS)
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
        dataMap.getString(KEY_WF_CUSTOM_SLOT3_LABEL)?.let { customSlot3Label = it }
        dataMap.getString(KEY_WF_CUSTOM_SLOT4_LABEL)?.let { customSlot4Label = it }
        dataMap.getString(KEY_WF_CUSTOM_SLOT4_BAR_COLOR)?.let { customSlot4BarColor = it }
        if (dataMap.containsKey(KEY_WF_CUSTOM_SLOT4_BAR_MIN)) customSlot4BarMin = dataMap.getFloat(KEY_WF_CUSTOM_SLOT4_BAR_MIN)
        if (dataMap.containsKey(KEY_WF_CUSTOM_SLOT4_BAR_MAX))        customSlot4BarMax        = dataMap.getFloat(KEY_WF_CUSTOM_SLOT4_BAR_MAX)
        if (dataMap.containsKey(KEY_WF_CUSTOM_SLOT4_BAR_SHOW_LABEL)) customSlot4BarShowLabel = dataMap.getBoolean(KEY_WF_CUSTOM_SLOT4_BAR_SHOW_LABEL)
        if (dataMap.containsKey(KEY_WF_CUSTOM_SLOT4_BAR_IS_SLIDER))  customSlot4BarIsSlider  = dataMap.getBoolean(KEY_WF_CUSTOM_SLOT4_BAR_IS_SLIDER)
        if (dataMap.containsKey(KEY_WF_CUSTOM_SLOT4_USE_KLIPPER))    customSlot4UseKlipper   = dataMap.getBoolean(KEY_WF_CUSTOM_SLOT4_USE_KLIPPER)
        dataMap.getString(KEY_WF_CUSTOM_SLOT4_KLIPPER_SOURCE)?.let  { customSlot4KlipperSource = it }
        dataMap.getString(KEY_WF_CUSTOM_SLOT4_KLIPPER_COLOR_ACT)?.let { customSlot4KlipperColorActive = it }
        if (dataMap.containsKey(KEY_WF_HR_TEXT_SCALE))      hrTextScale      = dataMap.getInt(KEY_WF_HR_TEXT_SCALE)
        if (dataMap.containsKey(KEY_WF_KCAL_TEXT_SCALE))    kcalTextScale    = dataMap.getInt(KEY_WF_KCAL_TEXT_SCALE)
        if (dataMap.containsKey(KEY_WF_STEPS_TEXT_SCALE))   stepsTextScale   = dataMap.getInt(KEY_WF_STEPS_TEXT_SCALE)
        if (dataMap.containsKey(KEY_WF_SLOT1_TEXT_SCALE))   slot1TextScale   = dataMap.getInt(KEY_WF_SLOT1_TEXT_SCALE)
        if (dataMap.containsKey(KEY_WF_SLOT2_TEXT_SCALE))   slot2TextScale   = dataMap.getInt(KEY_WF_SLOT2_TEXT_SCALE)
        if (dataMap.containsKey(KEY_WF_SLOT3_TEXT_SCALE))   slot3TextScale   = dataMap.getInt(KEY_WF_SLOT3_TEXT_SCALE)
        if (dataMap.containsKey(KEY_WF_SLOT4_TEXT_SCALE))   slot4TextScale   = dataMap.getInt(KEY_WF_SLOT4_TEXT_SCALE)
        if (dataMap.containsKey(KEY_WF_WEATHER_TEXT_SCALE)) weatherTextScale = dataMap.getInt(KEY_WF_WEATHER_TEXT_SCALE)
        if (dataMap.containsKey(KEY_WF_SUNRISE_TEXT_SCALE))        sunriseTextScale       = dataMap.getInt(KEY_WF_SUNRISE_TEXT_SCALE)
        if (dataMap.containsKey(KEY_WF_WATCH_BATTERY_TEXT_SCALE)) watchBatteryTextScale = dataMap.getInt(KEY_WF_WATCH_BATTERY_TEXT_SCALE)
        dataMap.getString(KEY_WF_BATTERY_RING_COLOR1)?.let { batteryRingColor1 = it }
        dataMap.getString(KEY_WF_BATTERY_RING_COLOR2)?.let { batteryRingColor2 = it }
        if (dataMap.containsKey(KEY_WF_BATTERY_RING_STROKE_SCALE)) batteryRingStrokeScale = dataMap.getInt(KEY_WF_BATTERY_RING_STROKE_SCALE)
        dataMap.getString(KEY_WF_BATTERY_WARN1_COLOR)?.let { batteryWarn1Color = it }
        dataMap.getString(KEY_WF_BATTERY_WARN2_COLOR)?.let { batteryWarn2Color = it }
        if (dataMap.containsKey(KEY_WF_BATTERY_WARN1_THRESHOLD)) batteryWarn1Threshold = dataMap.getInt(KEY_WF_BATTERY_WARN1_THRESHOLD)
        if (dataMap.containsKey(KEY_WF_BATTERY_WARN2_THRESHOLD)) batteryWarn2Threshold = dataMap.getInt(KEY_WF_BATTERY_WARN2_THRESHOLD)
        dataMap.getString(KEY_WF_HEALTH_DATA_SOURCE)?.let { healthDataSource = it }
        dataMap.getString(KEY_WF_HR_SOURCE)?.let { hrSource = it }
        dataMap.getString(KEY_WF_KCAL_SOURCE)?.let { kcalSource = it }
        dataMap.getString(KEY_WF_OXYGEN_SOURCE)?.let { oxygenSource = it }
        dataMap.getString(KEY_WF_HR_COMPLICATION)?.let { hrComplication = it }
        dataMap.getString(KEY_WF_KCAL_COMPLICATION)?.let { kcalComplication = it }
        dataMap.getString(KEY_WF_OXYGEN_COMPLICATION)?.let { oxygenComplication = it }
        dataMap.getString(KEY_WF_KCAL_LABEL)?.let { kcalLabel = it }
        dataMap.getString(KEY_WF_KCAL_UNIT)?.let { kcalUnit = it }
        dataMap.getString(KEY_WF_OXYGEN_LABEL)?.let { oxygenLabel = it }
        dataMap.getString(KEY_WF_OXYGEN_UNIT)?.let { oxygenUnit = it }
        dataMap.getString(KEY_WF_HR_COLOR)?.let      { hrColor      = it }
        dataMap.getString(KEY_WF_KCAL_COLOR)?.let    { kcalColor    = it }
        dataMap.getString(KEY_WF_OXYGEN_COLOR)?.let  { oxygenColor  = it }
        dataMap.getString(KEY_WF_STEPS_COLOR)?.let   { stepsColor   = it }
        dataMap.getString(KEY_WF_SLEEP_COLOR)?.let   { sleepColor   = it }
        dataMap.getString(KEY_WF_SUNRISE_COLOR)?.let { sunriseColor = it }
        dataMap.getString(KEY_WF_SLOT_COLOR)?.let    { slotColor    = it }
        dataMap.getString(KEY_WF_WEATHER_TEMP_SOURCE)?.let { weatherTempSource = it }
        dataMap.getString(KEY_WF_WEATHER_IOBROKER_ID)?.let { weatherIoBrokerId = it }
        if (dataMap.containsKey(KEY_WF_NTP_ENABLED)) ntpEnabled = dataMap.getBoolean(KEY_WF_NTP_ENABLED)
        dataMap.getString(KEY_WF_NTP_SERVER)?.let { ntpServer = it }
        // Bei deaktivierter Korrektur sofort zur reinen Systemzeit zurückkehren
        if (!ntpEnabled) ntpOffsetMs = 0L
        // ── Boden-Komplikationen ──────────────────────────────────────────────
        if (dataMap.containsKey(KEY_WF_SHOW_BOTTOM_COMP)) showBottomComp = dataMap.getBoolean(KEY_WF_SHOW_BOTTOM_COMP)
        if (dataMap.containsKey(KEY_WF_BC1_USE_IOBROKER)) bc1UseIoBroker = dataMap.getBoolean(KEY_WF_BC1_USE_IOBROKER)
        dataMap.getString(KEY_WF_BC1_LABEL)?.let  { bc1Label  = it }
        dataMap.getString(KEY_WF_BC1_COLOR)?.let  { bc1Color  = it }
        if (dataMap.containsKey(KEY_WF_BC1_RING_ENABLED)) bc1RingEnabled = dataMap.getBoolean(KEY_WF_BC1_RING_ENABLED)
        dataMap.getString(KEY_WF_BC1_RING_COLOR1)?.let { bc1RingColor1 = it }
        dataMap.getString(KEY_WF_BC1_RING_COLOR2)?.let { bc1RingColor2 = it }
        if (dataMap.containsKey(KEY_WF_BC1_RING_MIN)) bc1RingMin = dataMap.getFloat(KEY_WF_BC1_RING_MIN)
        if (dataMap.containsKey(KEY_WF_BC1_RING_MAX)) bc1RingMax = dataMap.getFloat(KEY_WF_BC1_RING_MAX)
        if (dataMap.containsKey(KEY_WF_BC1_RING_WIDTH)) bc1RingWidth = dataMap.getInt(KEY_WF_BC1_RING_WIDTH)
        if (dataMap.containsKey(KEY_WF_BC1_RING_TH_EN)) bc1RingThreshEnabled = dataMap.getBoolean(KEY_WF_BC1_RING_TH_EN)
        if (dataMap.containsKey(KEY_WF_BC1_RING_TH_VAL)) bc1RingThreshValue = dataMap.getFloat(KEY_WF_BC1_RING_TH_VAL)
        dataMap.getString(KEY_WF_BC1_RING_TH_DIR)?.let { bc1RingThreshDir = it }
        dataMap.getString(KEY_WF_BC1_RING_TH_TARGET)?.let { bc1RingThreshTarget = it }
        dataMap.getString(KEY_WF_BC1_RING_TH_COLOR)?.let { bc1RingThreshColor = it }
        if (dataMap.containsKey(KEY_WF_BC1_TEXT_SCALE)) bc1TextScale = dataMap.getInt(KEY_WF_BC1_TEXT_SCALE)
        dataMap.getString(KEY_WF_BC2_METRIC)?.let { bc2Metric = it }
        if (dataMap.containsKey(KEY_WF_BC2_USE_IOBROKER)) bc2UseIoBroker = dataMap.getBoolean(KEY_WF_BC2_USE_IOBROKER)
        dataMap.getString(KEY_WF_BC2_LABEL)?.let  { bc2Label  = it }
        dataMap.getString(KEY_WF_BC2_COLOR)?.let  { bc2Color  = it }
        if (dataMap.containsKey(KEY_WF_BC2_RING_ENABLED)) bc2RingEnabled = dataMap.getBoolean(KEY_WF_BC2_RING_ENABLED)
        dataMap.getString(KEY_WF_BC2_RING_COLOR1)?.let { bc2RingColor1 = it }
        dataMap.getString(KEY_WF_BC2_RING_COLOR2)?.let { bc2RingColor2 = it }
        if (dataMap.containsKey(KEY_WF_BC2_RING_MIN)) bc2RingMin = dataMap.getFloat(KEY_WF_BC2_RING_MIN)
        if (dataMap.containsKey(KEY_WF_BC2_RING_MAX)) bc2RingMax = dataMap.getFloat(KEY_WF_BC2_RING_MAX)
        if (dataMap.containsKey(KEY_WF_BC2_RING_WIDTH)) bc2RingWidth = dataMap.getInt(KEY_WF_BC2_RING_WIDTH)
        if (dataMap.containsKey(KEY_WF_BC2_RING_TH_EN)) bc2RingThreshEnabled = dataMap.getBoolean(KEY_WF_BC2_RING_TH_EN)
        if (dataMap.containsKey(KEY_WF_BC2_RING_TH_VAL)) bc2RingThreshValue = dataMap.getFloat(KEY_WF_BC2_RING_TH_VAL)
        dataMap.getString(KEY_WF_BC2_RING_TH_DIR)?.let { bc2RingThreshDir = it }
        dataMap.getString(KEY_WF_BC2_RING_TH_TARGET)?.let { bc2RingThreshTarget = it }
        dataMap.getString(KEY_WF_BC2_RING_TH_COLOR)?.let { bc2RingThreshColor = it }
        if (dataMap.containsKey(KEY_WF_BC2_TEXT_SCALE)) bc2TextScale = dataMap.getInt(KEY_WF_BC2_TEXT_SCALE)
    }

    fun updateP2ConfigFromDataMap(dataMap: DataMap) {
        if (dataMap.containsKey(KEY_WF_P2_PILL_ENABLED))    p2PillEnabled    = dataMap.getBoolean(KEY_WF_P2_PILL_ENABLED)
        dataMap.getString(KEY_WF_P2_PILL_COLOR_TRUE)?.let  { p2PillColorTrue  = it }
        dataMap.getString(KEY_WF_P2_PILL_COLOR_FALSE)?.let { p2PillColorFalse = it }
        dataMap.getString(KEY_WF_P2_PILL_IOBROKER_ID)?.let { p2PillIoBrokerId = it }
        dataMap.getString(KEY_WF_P2_PILL_VALUE_MODE)?.let  { p2PillValueMode  = it }
        dataMap.getString(KEY_WF_P2_PILL_FIXED_VALUE)?.let { p2PillFixedValue = it }
        if (dataMap.containsKey(KEY_WF_P2_PILL2_ENABLED))   p2Pill2Enabled    = dataMap.getBoolean(KEY_WF_P2_PILL2_ENABLED)
        dataMap.getString(KEY_WF_P2_PILL2_COLOR_TRUE)?.let  { p2Pill2ColorTrue  = it }
        dataMap.getString(KEY_WF_P2_PILL2_COLOR_FALSE)?.let { p2Pill2ColorFalse = it }
        dataMap.getString(KEY_WF_P2_PILL2_IOBROKER_ID)?.let { p2Pill2IoBrokerId = it }
        dataMap.getString(KEY_WF_P2_PILL2_VALUE_MODE)?.let  { p2Pill2ValueMode  = it }
        dataMap.getString(KEY_WF_P2_PILL2_FIXED_VALUE)?.let { p2Pill2FixedValue = it }
        if (dataMap.containsKey(KEY_WF_P2_SLOT1_TEXT_SCALE)) p2Slot1TextScale = dataMap.getInt(KEY_WF_P2_SLOT1_TEXT_SCALE)
        if (dataMap.containsKey(KEY_WF_P2_SLOT2_TEXT_SCALE)) p2Slot2TextScale = dataMap.getInt(KEY_WF_P2_SLOT2_TEXT_SCALE)
        if (dataMap.containsKey(KEY_WF_P2_SLOT3_TEXT_SCALE)) p2Slot3TextScale = dataMap.getInt(KEY_WF_P2_SLOT3_TEXT_SCALE)
        if (dataMap.containsKey(KEY_WF_P2_SLOT4_TEXT_SCALE)) p2Slot4TextScale = dataMap.getInt(KEY_WF_P2_SLOT4_TEXT_SCALE)
        if (dataMap.containsKey(KEY_WF_SLEEP_TEXT_SCALE))    sleepTextScale   = dataMap.getInt(KEY_WF_SLEEP_TEXT_SCALE)
        dataMap.getString("wf_sleep_source")?.let      { sleepSource      = it }
        dataMap.getString("wf_sleep_complication")?.let { sleepComplication = it }
        // Seite 2 – vertikaler Balken
        dataMap.getString(KEY_WF_P2_BAR_LABEL)?.let  { p2BarLabel = it }
        dataMap.getString(KEY_WF_P2_BAR_VALUE)?.let  { p2BarValue = it }
        dataMap.getString(KEY_WF_P2_BAR_COLOR)?.let  { p2BarColor = it }
        if (dataMap.containsKey(KEY_WF_P2_BAR_MIN))        p2BarMin        = dataMap.getFloat(KEY_WF_P2_BAR_MIN)
        if (dataMap.containsKey(KEY_WF_P2_BAR_MAX))        p2BarMax        = dataMap.getFloat(KEY_WF_P2_BAR_MAX)
        if (dataMap.containsKey(KEY_WF_P2_BAR_SHOW_LABEL)) p2BarShowLabel  = dataMap.getBoolean(KEY_WF_P2_BAR_SHOW_LABEL)
        if (dataMap.containsKey(KEY_WF_P2_BAR_IS_SLIDER))  p2BarIsSlider   = dataMap.getBoolean(KEY_WF_P2_BAR_IS_SLIDER)
        if (dataMap.containsKey(KEY_WF_P2_BAR_TEXT_SCALE)) p2BarTextScale  = dataMap.getInt(KEY_WF_P2_BAR_TEXT_SCALE)
        dataMap.getString(KEY_WF_P2_BAR_WARN1_COLOR)?.let { p2BarWarn1Color = it }
        dataMap.getString(KEY_WF_P2_BAR_WARN2_COLOR)?.let { p2BarWarn2Color = it }
        if (dataMap.containsKey(KEY_WF_P2_BAR_WARN1_VALUE)) p2BarWarn1Value = dataMap.getFloat(KEY_WF_P2_BAR_WARN1_VALUE)
        if (dataMap.containsKey(KEY_WF_P2_BAR_WARN2_VALUE)) p2BarWarn2Value = dataMap.getFloat(KEY_WF_P2_BAR_WARN2_VALUE)
        if (dataMap.containsKey(KEY_WF_P2_SHOW_BACKGROUND)) showBackgroundPage2 = dataMap.getBoolean(KEY_WF_P2_SHOW_BACKGROUND)
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

    /** Direkt von der Uhr abgerufene Datenpunkte übernehmen (ab v5, ohne JSON-Umweg). */
    fun updateFromCachedStates(newStates: List<CachedState>) {
        lastUpdated = System.currentTimeMillis()
        states = newStates
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
