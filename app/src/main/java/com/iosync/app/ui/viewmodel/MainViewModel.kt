package com.iosync.app.ui.viewmodel

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iosync.app.BuildConfig
import com.iosync.app.data.model.SmartHomeState
import com.iosync.app.data.health.HealthConnectManager
import com.iosync.app.data.health.HealthConnectStatus
import com.iosync.app.data.sync.IoSyncSyncService
import com.iosync.app.data.network.DynamicBaseUrl
import com.iosync.app.data.network.IoSyncClient
import com.iosync.app.data.network.SmartHomeWebSocketService
import com.iosync.app.data.network.WeatherService
import com.iosync.app.data.network.WebSocketStatus
import com.iosync.app.data.repository.RepoResult
import com.iosync.app.data.repository.SmartHomeRepository
import com.iosync.app.wear.WearDataLayerService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MainUiState(
    val states: List<SmartHomeState> = emptyList(),
    val filteredStates: List<SmartHomeState> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val connectionStatus: WebSocketStatus = WebSocketStatus.DISCONNECTED,
    val searchQuery: String = "",
    val selectedRoom: String? = null,
    val host: String = BuildConfig.IOBROKER_DEFAULT_HOST,
    val port: Int = BuildConfig.IOBROKER_DEFAULT_PORT,
    val availableRooms: List<String> = emptyList(),
    // Watchface-Grundkonfiguration
    val wfTimeColor: String = "light_gray",
    val wfDateColor: String = "cyan",
    val wfShowSeconds: Boolean = true,
    val wfShowTicks: Boolean = true,
    val wfShowWeekday: Boolean = true,
    // Watchface-Datenpunkt-Optionen
    val wfShowPhoneBattery: Boolean = false,
    val wfShowIoBrokerData: Boolean = false,
    // Watchface-Sekundenring
    val wfShowSecondsRing: Boolean = false,
    val wfSecondsRingColor: String = "neon_yellow",
    val wfSecondsRingWidth: Int = 5,
    val wfSecondsGlowWidth: Int = 100,
    val wfSecondsNumberColor: String = "dim_time",
    // Datenquelle: true = IoSync Adapter, false = Simple-API
    val useIoSyncAdapter: Boolean = true,
    // IoSync Adapter Verbindung
    val ioSyncHost: String = "",
    val ioSyncPort: Int = 345,
    val ioSyncUseHttps: Boolean = false,
    val ioSyncUsername: String = "",
    val ioSyncPassword: String = "",
    // Laufende IoSync-Datenpunkte (vom Adapter abgerufen)
    val ioSyncStates: List<SmartHomeState> = emptyList(),
    val phoneBatteryLevel: Int = -1,
    // Aktions-Pille (6-Uhr-Button am Watchface)
    val actionPillEnabled: Boolean = false,
    val actionPillColorTrue: String = "cyan",
    val actionPillColorFalse: String = "red",
    val actionPillIoBrokerId: String = "",
    val actionPillValueMode: String = "toggle",
    val actionPillFixedValue: String = "",
    val actionPillState: Boolean = false,
    // Watchface: Wetter & Gesundheitsanzeige
    val wfShowWeather: Boolean = true,
    val wfShowHeartRate: Boolean = true,
    val wfShowOxygen: Boolean = false,
    val wfShowCalories: Boolean = true,
    val wfShowSteps: Boolean = true,
    // Wetter-Standort
    val weatherUseFixedLocation: Boolean = false,
    val weatherFixedLat: Double = 0.0,
    val weatherFixedLon: Double = 0.0,
    val weatherFixedCity: String = "",
    val weatherSearchResults: List<com.iosync.app.data.network.GeocodingResult> = emptyList(),
    val weatherSearching: Boolean = false,
    val weatherSearchError: String? = null,
    // Custom ioBroker-Slots (4 Datenpunkte auf dem Watchface)
    val showCustomSlots: Boolean = false,
    val customSlot1Id: String = "",
    val customSlot1Label: String = "",
    val customSlot2Id: String = "",
    val customSlot2Label: String = "",
    val customSlot3Id: String = "",
    val customSlot3Label: String = "",
    // Slot 4: Balken-Graph
    val customSlot4Id: String = "",
    val customSlot4Label: String = "",
    val customSlot4BarColor: String = "neon_yellow",
    val customSlot4BarMin: Float = 0f,
    val customSlot4BarMax: Float = 100f,
    val customSlot4BarShowLabel: Boolean = true,
    val customSlot4BarIsSlider: Boolean = false,
    // Slot 4: Klipper-Override (zeigt Klipper-Wert statt ioBroker-Wert wenn Drucker druckt)
    val customSlot4UseKlipper: Boolean = false,
    val customSlot4KlipperSource: String = "progress",          // progress|nozzle_temp|bed_temp|chamber_temp|fan|speed
    val customSlot4KlipperColorActive: String = "neon_yellow",  // Balkenfarbe während aktivem Druck
    // Slot 4 Warnstufen (absoluter Wert; leer/NaN = deaktiviert)
    val customSlot4Warn1Color: String = "orange",
    val customSlot4Warn1Value: Float = Float.NaN,
    val customSlot4Warn2Color: String = "red",
    val customSlot4Warn2Value: Float = Float.NaN,
    // Individuelle Schriftgröße je Wert (70–160, Default 100 = 100 %)
    val wfHrTextScale: Int = 100,
    val wfKcalTextScale: Int = 100,
    val wfStepsTextScale: Int = 100,
    val wfSlot1TextScale: Int = 100,
    val wfSlot2TextScale: Int = 100,
    val wfSlot3TextScale: Int = 100,
    val wfSlot4TextScale: Int = 100,
    val wfWeatherTextScale: Int = 100,
    val wfSunriseTextScale: Int = 100,
    val wfWatchBatteryTextScale: Int = 100,
    // Akku-Ring-Farben und Ringbreite
    val wfBatteryRingColor1: String = "cyan",
    val wfBatteryRingColor2: String = "neon_yellow",
    val wfBatteryRingStrokeScale: Int = 100,
    // Akku-Ring Warnstufen (Schwelle in %, 0 = deaktiviert)
    val wfBatteryWarn1Color: String = "orange",
    val wfBatteryWarn1Threshold: Int = 0,
    val wfBatteryWarn2Color: String = "red",
    val wfBatteryWarn2Threshold: Int = 0,
    // Gesundheitsdaten-Quelle pro Typ: "local" = Uhr-Sensoren, "healthconnect" = Health Connect vom Handy
    val wfHealthDataSource: String = "local",
    val wfHrSource: String = "local",
    val wfKcalSource: String = "local",
    val wfOxygenSource: String = "local",
    val wfHrComplication: String = "",
    val wfKcalComplication: String = "",
    val wfOxygenComplication: String = "",
    // Frei wählbare Metrik der beiden Health-Slots (HC-Datentyp-Key)
    val wfKcalMetric: String = "total_calories",
    val wfOxygenMetric: String = "oxygen_saturation",
    // Watchface: Hintergrundbild
    val wfShowBackground: Boolean = false,
    val p2ShowBackground: Boolean = false,
    // Gesundheitsdaten-Farben
    val wfHrColor: String      = "red",
    val wfKcalColor: String    = "orange",
    val wfOxygenColor: String  = "cyan",
    val wfStepsColor: String   = "neon_yellow",
    val wfSleepColor: String   = "purple",
    val wfSunriseColor: String = "neon_yellow",
    // ioBroker-Slot-Farbe (Wert-Text)
    val wfSlotColor: String    = "neon_yellow",
    // Wetter-Temperaturquelle
    val wfWeatherTempSource: String = "openweather",
    val wfWeatherIoBrokerId: String = "",
    // NTP-Zeitkorrektur (Offset wird auf der Uhr per NTP ermittelt)
    val wfNtpEnabled: Boolean = false,
    val wfNtpServer: String = "pool.ntp.org",
    val ntpOffsetFromWatch: Long = 0L,  // letzter von der Uhr gemeldeter Offset in ms
    // ── Seite 2 ioBroker-Slots ─────────────────────────────────────────────────
    val p2Slot1Id: String = "",
    val p2Slot1Label: String = "",
    val p2Slot2Id: String = "",
    val p2Slot2Label: String = "",
    val p2Slot3Id: String = "",
    val p2Slot3Label: String = "",
    val p2Slot4Id: String = "",
    val p2Slot4Label: String = "",
    val p2Slot1TextScale: Int = 100,
    val p2Slot2TextScale: Int = 100,
    val p2Slot3TextScale: Int = 100,
    val p2Slot4TextScale: Int = 100,
    val wfSleepTextScale: Int = 100,
    val wfSleepSource: String = "healthconnect",
    val wfSleepIoBrokerId: String = "",
    val wfSleepComplication: String = "",
    // ── Seite 2 Pillen – Pille 1 (7 Uhr) ───────────────────────────────────────
    val p2PillEnabled: Boolean = false,
    val p2PillColorTrue: String = "cyan",
    val p2PillColorFalse: String = "red",
    val p2PillIoBrokerId: String = "",
    val p2PillValueMode: String = "toggle",
    val p2PillFixedValue: String = "",
    val p2Pill1State: Boolean = false,
    // ── Seite 2 Pillen – Pille 2 (5 Uhr) ───────────────────────────────────────
    val p2Pill2Enabled: Boolean = false,
    val p2Pill2ColorTrue: String = "cyan",
    val p2Pill2ColorFalse: String = "red",
    val p2Pill2IoBrokerId: String = "",
    val p2Pill2ValueMode: String = "toggle",
    val p2Pill2FixedValue: String = "",
    val p2Pill2State: Boolean = false,
    // ── Seite 2 – vertikaler Balken ─────────────────────────────────────────
    val p2BarId: String = "",
    val p2BarLabel: String = "",
    val p2BarColor: String = "neon_yellow",
    val p2BarMin: Float = 0f,
    val p2BarMax: Float = 100f,
    val p2BarShowLabel: Boolean = true,
    val p2BarIsSlider: Boolean = false,
    val p2BarTextScale: Int = 100,
    val p2BarWarn1Color: String = "orange",
    val p2BarWarn1Value: Float = Float.NaN,
    val p2BarWarn2Color: String = "red",
    val p2BarWarn2Value: Float = Float.NaN,
    // Aktualisierungsintervalle (in Sekunden)
    val batteryPollIntervalSec: Int = 60,
    val slotPollIntervalSec: Int = 120,
    val healthPollIntervalSec: Int = 60,
    val page2SyncIntervalSec: Int = 120,
    // Sync-Status-Log für die Konsolenanzeige
    val wearSyncLog: String = "",
    // Health Connect Status
    val healthConnectStatus: HealthConnectStatus = HealthConnectStatus(),
    val healthConnectLoading: Boolean = false,
    // ── Boden-Komplikationen (2 Kreistaschen unten) ──────────────────────────
    val wfShowBottomComp: Boolean = true,
    val wfBc1UseIoBroker: Boolean = false,
    val wfBc1Id: String = "",
    val wfBc1Label: String = "BPM",
    val wfBc1Color: String = "red",
    val wfBc1RingEnabled: Boolean = true,
    val wfBc1RingColor1: String = "red",
    val wfBc1RingColor2: String = "orange",
    val wfBc1RingMin: Float = 0f,
    val wfBc1RingMax: Float = 140f,
    val wfBc1RingWidth: Int = 6,
    val wfBc1RingThreshEnabled: Boolean = false,
    val wfBc1RingThreshValue: Float = 0f,
    val wfBc1RingThreshDir: String = "above",
    val wfBc1RingThreshTarget: String = "color2",
    val wfBc1RingThreshColor: String = "red",
    val wfBc1TextScale: Int = 100,
    val wfBc2Metric: String = "kcal",
    val wfBc2UseIoBroker: Boolean = false,
    val wfBc2Id: String = "",
    val wfBc2Label: String = "KCAL",
    val wfBc2Color: String = "orange",
    val wfBc2RingEnabled: Boolean = true,
    val wfBc2RingColor1: String = "orange",
    val wfBc2RingColor2: String = "neon_yellow",
    val wfBc2RingMin: Float = 0f,
    val wfBc2RingMax: Float = 1000f,
    val wfBc2RingWidth: Int = 6,
    val wfBc2RingThreshEnabled: Boolean = false,
    val wfBc2RingThreshValue: Float = 0f,
    val wfBc2RingThreshDir: String = "above",
    val wfBc2RingThreshTarget: String = "color2",
    val wfBc2RingThreshColor: String = "red",
    val wfBc2TextScale: Int = 100,
    // ── Klipper-Verbindung (Seite 3 – Moonraker, Port 7125) ──────────────────
    val klipperEnabled: Boolean = false,
    val klipperHost: String = "",
    val klipperPort: Int = 7125,
    val klipperApiKey: String = "",
    val klipperChamberObject: String = "heater_generic chamber",
    val klipperIntervalSec: Int = 15,
    val klipperObjects: List<String> = emptyList(),
    val klipperObjectsLoading: Boolean = false,
    val klipperObjectsError: String? = null,
    // ── Seite 3 – Pille (6 Uhr, Klipper) ─────────────────────────────────────
    val p3PillEnabled: Boolean = false,
    val p3PillColorTrue: String = "cyan",
    val p3PillColorFalse: String = "red",
    val p3PillObject: String = "",
    val p3PillField: String = "value",
    val p3PillGcodeOn: String = "",
    val p3PillGcodeOff: String = "",
    val p3PillState: Boolean = false,
    // ── Seite 3 – LED-Button ──────────────────────────────────────────────────
    val klipperLedGcodeOn: String = "",
    val klipperLedGcodeOff: String = "",
    val klipperLedObject: String = "",
    val klipperLedField: String = "value",
    // ── Seite 3 – Chamber-Heater-Button ───────────────────────────────────────
    val klipperChamberHeatGcodeOn: String = "",
    val klipperChamberHeatGcodeOff: String = ""
)

@HiltViewModel
class MainViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: SmartHomeRepository,
    private val dataStore: DataStore<Preferences>,
    private val wearDataLayerService: WearDataLayerService,
    private val ioSyncClient: IoSyncClient,
    private val dynamicBaseUrl: DynamicBaseUrl,
    private val weatherService: WeatherService,
    val healthConnectManager: HealthConnectManager,
    private val klipperClient: com.iosync.app.data.network.KlipperClient
) : ViewModel() {

    companion object {
        val KEY_HOST                 = stringPreferencesKey("iobroker_host")
        val KEY_PORT                 = intPreferencesKey("iobroker_port")
        val KEY_WF_TIME_COLOR        = stringPreferencesKey("wf_time_color")
        val KEY_WF_DATE_COLOR        = stringPreferencesKey("wf_date_color")
        val KEY_WF_SHOW_SECONDS      = booleanPreferencesKey("wf_show_seconds")
        val KEY_WF_SHOW_TICKS        = booleanPreferencesKey("wf_show_ticks")
        val KEY_WF_SHOW_WEEKDAY      = booleanPreferencesKey("wf_show_weekday")
        val KEY_WF_SHOW_PHONE_BATTERY  = booleanPreferencesKey("wf_show_phone_battery")
        val KEY_WF_SHOW_IOBROKER_DATA  = booleanPreferencesKey("wf_show_iobroker_data")
        val KEY_WF_SHOW_SECONDS_RING   = booleanPreferencesKey("wf_show_seconds_ring")
        val KEY_WF_SECONDS_RING_COLOR  = stringPreferencesKey("wf_seconds_ring_color")
        val KEY_WF_SECONDS_RING_WIDTH    = intPreferencesKey("wf_seconds_ring_width")
        val KEY_WF_SECONDS_GLOW_WIDTH    = intPreferencesKey("wf_seconds_glow_width")
        val KEY_WF_SECONDS_NUMBER_COLOR  = stringPreferencesKey("wf_seconds_number_color")
        val KEY_USE_IOSYNC_ADAPTER   = booleanPreferencesKey("use_iosync_adapter")
        val KEY_IOSYNC_HOST          = stringPreferencesKey("iosync_host")
        val KEY_IOSYNC_PORT          = intPreferencesKey("iosync_port")
        val KEY_IOSYNC_USE_HTTPS     = booleanPreferencesKey("iosync_use_https")
        val KEY_IOSYNC_USERNAME      = stringPreferencesKey("iosync_username")
        val KEY_IOSYNC_PASSWORD      = stringPreferencesKey("iosync_password")
        // Aktions-Pille
        val KEY_ACTION_PILL_ENABLED      = booleanPreferencesKey("action_pill_enabled")
        val KEY_ACTION_PILL_COLOR_TRUE   = stringPreferencesKey("action_pill_color_true")
        val KEY_ACTION_PILL_COLOR_FALSE  = stringPreferencesKey("action_pill_color_false")
        val KEY_ACTION_PILL_IOBROKER_ID  = stringPreferencesKey("action_pill_iobroker_id")
        val KEY_ACTION_PILL_VALUE_MODE   = stringPreferencesKey("action_pill_value_mode")
        val KEY_ACTION_PILL_FIXED_VALUE  = stringPreferencesKey("action_pill_fixed_value")
        val KEY_ACTION_PILL_STATE        = booleanPreferencesKey("action_pill_state")

        // Wetter & Gesundheit
        val KEY_WF_SHOW_WEATHER     = booleanPreferencesKey("wf_show_weather")
        val KEY_WF_SHOW_HEART_RATE  = booleanPreferencesKey("wf_show_heart_rate")
        val KEY_WF_SHOW_OXYGEN      = booleanPreferencesKey("wf_show_oxygen")
        val KEY_WF_SHOW_CALORIES    = booleanPreferencesKey("wf_show_calories")
        val KEY_WF_SHOW_STEPS       = booleanPreferencesKey("wf_show_steps")
        // Custom ioBroker-Slots
        val KEY_SHOW_CUSTOM_SLOTS   = booleanPreferencesKey("show_custom_slots")
        val KEY_CUSTOM_SLOT1_ID     = stringPreferencesKey("custom_slot1_id")
        val KEY_CUSTOM_SLOT1_LABEL  = stringPreferencesKey("custom_slot1_label")
        val KEY_CUSTOM_SLOT2_ID     = stringPreferencesKey("custom_slot2_id")
        val KEY_CUSTOM_SLOT2_LABEL  = stringPreferencesKey("custom_slot2_label")
        val KEY_CUSTOM_SLOT3_ID     = stringPreferencesKey("custom_slot3_id")
        val KEY_CUSTOM_SLOT3_LABEL  = stringPreferencesKey("custom_slot3_label")
        val KEY_CUSTOM_SLOT4_ID     = stringPreferencesKey("custom_slot4_id")
        val KEY_CUSTOM_SLOT4_LABEL  = stringPreferencesKey("custom_slot4_label")
        val KEY_CUSTOM_SLOT4_BAR_COLOR      = stringPreferencesKey("custom_slot4_bar_color")
        val KEY_CUSTOM_SLOT4_BAR_MIN        = stringPreferencesKey("custom_slot4_bar_min")
        val KEY_CUSTOM_SLOT4_BAR_MAX        = stringPreferencesKey("custom_slot4_bar_max")
        val KEY_CUSTOM_SLOT4_BAR_SHOW_LABEL = booleanPreferencesKey("custom_slot4_bar_show_label")
        val KEY_CUSTOM_SLOT4_BAR_IS_SLIDER  = booleanPreferencesKey("custom_slot4_bar_is_slider")
        val KEY_CUSTOM_SLOT4_WARN1_COLOR = stringPreferencesKey("custom_slot4_warn1_color")
        val KEY_CUSTOM_SLOT4_WARN1_VALUE = stringPreferencesKey("custom_slot4_warn1_value")
        val KEY_CUSTOM_SLOT4_WARN2_COLOR = stringPreferencesKey("custom_slot4_warn2_color")
        val KEY_CUSTOM_SLOT4_WARN2_VALUE = stringPreferencesKey("custom_slot4_warn2_value")
        // Individuelle Schriftgrößen
        val KEY_WF_HR_TEXT_SCALE       = intPreferencesKey("wf_hr_text_scale")
        val KEY_WF_KCAL_TEXT_SCALE     = intPreferencesKey("wf_kcal_text_scale")
        val KEY_WF_STEPS_TEXT_SCALE    = intPreferencesKey("wf_steps_text_scale")
        val KEY_WF_SLOT1_TEXT_SCALE    = intPreferencesKey("wf_slot1_text_scale")
        val KEY_WF_SLOT2_TEXT_SCALE    = intPreferencesKey("wf_slot2_text_scale")
        val KEY_WF_SLOT3_TEXT_SCALE    = intPreferencesKey("wf_slot3_text_scale")
        val KEY_WF_SLOT4_TEXT_SCALE    = intPreferencesKey("wf_slot4_text_scale")
        val KEY_WF_WEATHER_TEXT_SCALE  = intPreferencesKey("wf_weather_text_scale")
        val KEY_WF_SUNRISE_TEXT_SCALE        = intPreferencesKey("wf_sunrise_text_scale")
        val KEY_WF_WATCH_BATTERY_TEXT_SCALE = intPreferencesKey("wf_watch_battery_text_scale")
        val KEY_WF_SHOW_BACKGROUND           = booleanPreferencesKey("wf_show_background")
        val KEY_P2_SHOW_BACKGROUND           = booleanPreferencesKey("p2_show_background")
        val KEY_WF_HR_COLOR      = stringPreferencesKey("wf_hr_color")
        val KEY_WF_KCAL_COLOR    = stringPreferencesKey("wf_kcal_color")
        val KEY_WF_OXYGEN_COLOR  = stringPreferencesKey("wf_oxygen_color")
        val KEY_WF_STEPS_COLOR   = stringPreferencesKey("wf_steps_color")
        val KEY_WF_SLEEP_COLOR   = stringPreferencesKey("wf_sleep_color")
        val KEY_WF_SUNRISE_COLOR = stringPreferencesKey("wf_sunrise_color")
        val KEY_WF_SLOT_COLOR    = stringPreferencesKey("wf_slot_color")
        val KEY_WF_BATTERY_RING_COLOR1       = stringPreferencesKey("wf_battery_ring_color1")
        val KEY_WF_BATTERY_RING_COLOR2       = stringPreferencesKey("wf_battery_ring_color2")
        val KEY_WF_BATTERY_RING_STROKE_SCALE = intPreferencesKey("wf_battery_ring_stroke_scale")
        val KEY_WF_BATTERY_WARN1_COLOR     = stringPreferencesKey("wf_battery_warn1_color")
        val KEY_WF_BATTERY_WARN1_THRESHOLD = intPreferencesKey("wf_battery_warn1_threshold")
        val KEY_WF_BATTERY_WARN2_COLOR     = stringPreferencesKey("wf_battery_warn2_color")
        val KEY_WF_BATTERY_WARN2_THRESHOLD = intPreferencesKey("wf_battery_warn2_threshold")
        val KEY_WF_HEALTH_DATA_SOURCE        = stringPreferencesKey("wf_health_data_source")
        // Pro-Typ Gesundheitsdaten-Quelle
        val KEY_WF_HR_SOURCE           = stringPreferencesKey("wf_hr_source")
        val KEY_WF_KCAL_SOURCE         = stringPreferencesKey("wf_kcal_source")
        val KEY_WF_OXYGEN_SOURCE       = stringPreferencesKey("wf_oxygen_source")
        // Pro-Typ gewählte Komplikation (Slot-ID als String, "" = keine)
        val KEY_WF_HR_COMPLICATION     = stringPreferencesKey("wf_hr_complication")
        val KEY_WF_KCAL_COMPLICATION   = stringPreferencesKey("wf_kcal_complication")
        val KEY_WF_OXYGEN_COMPLICATION = stringPreferencesKey("wf_oxygen_complication")
        // Frei wählbare Metrik der beiden Health-Slots (HC-Datentyp-Key)
        val KEY_WF_KCAL_METRIC         = stringPreferencesKey("wf_kcal_metric")
        val KEY_WF_OXYGEN_METRIC       = stringPreferencesKey("wf_oxygen_metric")
        // Seite 2 ioBroker-Slots
        val KEY_P2_SLOT1_ID          = stringPreferencesKey("p2_slot1_id")
        val KEY_P2_SLOT1_LABEL       = stringPreferencesKey("p2_slot1_label")
        val KEY_P2_SLOT2_ID          = stringPreferencesKey("p2_slot2_id")
        val KEY_P2_SLOT2_LABEL       = stringPreferencesKey("p2_slot2_label")
        val KEY_P2_SLOT3_ID          = stringPreferencesKey("p2_slot3_id")
        val KEY_P2_SLOT3_LABEL       = stringPreferencesKey("p2_slot3_label")
        val KEY_P2_SLOT4_ID          = stringPreferencesKey("p2_slot4_id")
        val KEY_P2_SLOT4_LABEL       = stringPreferencesKey("p2_slot4_label")
        val KEY_P2_SLOT1_TEXT_SCALE  = intPreferencesKey("p2_slot1_text_scale")
        val KEY_P2_SLOT2_TEXT_SCALE  = intPreferencesKey("p2_slot2_text_scale")
        val KEY_P2_SLOT3_TEXT_SCALE  = intPreferencesKey("p2_slot3_text_scale")
        val KEY_P2_SLOT4_TEXT_SCALE  = intPreferencesKey("p2_slot4_text_scale")
        val KEY_WF_SLEEP_TEXT_SCALE   = intPreferencesKey("wf_sleep_text_scale")
        val KEY_WF_SLEEP_SOURCE       = stringPreferencesKey("wf_sleep_source")
        val KEY_WF_SLEEP_IOBROKER_ID  = stringPreferencesKey("wf_sleep_iobroker_id")
        val KEY_WF_SLEEP_COMPLICATION = stringPreferencesKey("wf_sleep_complication")
        // Seite 2 Pillen
        val KEY_P2_PILL_ENABLED      = booleanPreferencesKey("p2_pill_enabled")
        val KEY_P2_PILL_COLOR_TRUE   = stringPreferencesKey("p2_pill_color_true")
        val KEY_P2_PILL_COLOR_FALSE  = stringPreferencesKey("p2_pill_color_false")
        val KEY_P2_PILL_IOBROKER_ID  = stringPreferencesKey("p2_pill_iobroker_id")
        val KEY_P2_PILL_VALUE_MODE   = stringPreferencesKey("p2_pill_value_mode")
        val KEY_P2_PILL_FIXED_VALUE  = stringPreferencesKey("p2_pill_fixed_value")
        val KEY_P2_PILL1_STATE       = booleanPreferencesKey("p2_pill1_state")
        // Seite 2 – Pille 2 (5 Uhr)
        val KEY_P2_PILL2_ENABLED     = booleanPreferencesKey("p2_pill2_enabled")
        val KEY_P2_PILL2_COLOR_TRUE  = stringPreferencesKey("p2_pill2_color_true")
        val KEY_P2_PILL2_COLOR_FALSE = stringPreferencesKey("p2_pill2_color_false")
        val KEY_P2_PILL2_IOBROKER_ID = stringPreferencesKey("p2_pill2_iobroker_id")
        val KEY_P2_PILL2_VALUE_MODE  = stringPreferencesKey("p2_pill2_value_mode")
        val KEY_P2_PILL2_FIXED_VALUE = stringPreferencesKey("p2_pill2_fixed_value")
        val KEY_P2_PILL2_STATE       = booleanPreferencesKey("p2_pill2_state")
        // Aktualisierungsintervalle (in Sekunden)
        val KEY_BATTERY_POLL_INTERVAL  = intPreferencesKey("battery_poll_interval_sec")
        val KEY_SLOT_POLL_INTERVAL     = intPreferencesKey("slot_poll_interval_sec")
        val KEY_HEALTH_POLL_INTERVAL   = intPreferencesKey("health_poll_interval_sec")
        val KEY_PAGE2_SYNC_INTERVAL    = intPreferencesKey("page2_sync_interval_sec")
        // Wetter-Standort
        val KEY_WEATHER_USE_FIXED   = booleanPreferencesKey("weather_use_fixed")
        val KEY_WEATHER_FIXED_LAT   = stringPreferencesKey("weather_fixed_lat")
        val KEY_WEATHER_FIXED_LON   = stringPreferencesKey("weather_fixed_lon")
        val KEY_WEATHER_FIXED_CITY  = stringPreferencesKey("weather_fixed_city")
        // Wetter-Temperaturquelle
        val KEY_WF_WEATHER_TEMP_SOURCE  = stringPreferencesKey("wf_weather_temp_source")
        val KEY_WF_WEATHER_IOBROKER_ID  = stringPreferencesKey("wf_weather_iobroker_id")
        // NTP-Zeitkorrektur
        val KEY_WF_NTP_ENABLED       = booleanPreferencesKey("wf_ntp_enabled")
        val KEY_WF_NTP_SERVER        = stringPreferencesKey("wf_ntp_server")
        val KEY_NTP_OFFSET_FROM_WATCH = longPreferencesKey("ntp_offset_from_watch")
        // Seite 2 – vertikaler Balken
        val KEY_P2_BAR_ID           = stringPreferencesKey("p2_bar_id")
        val KEY_P2_BAR_LABEL        = stringPreferencesKey("p2_bar_label")
        val KEY_P2_BAR_COLOR        = stringPreferencesKey("p2_bar_color")
        val KEY_P2_BAR_MIN          = stringPreferencesKey("p2_bar_min")
        val KEY_P2_BAR_MAX          = stringPreferencesKey("p2_bar_max")
        val KEY_P2_BAR_SHOW_LABEL   = booleanPreferencesKey("p2_bar_show_label")
        val KEY_P2_BAR_IS_SLIDER    = booleanPreferencesKey("p2_bar_is_slider")
        val KEY_P2_BAR_TEXT_SCALE   = intPreferencesKey("p2_bar_text_scale")
        val KEY_P2_BAR_WARN1_COLOR  = stringPreferencesKey("p2_bar_warn1_color")
        val KEY_P2_BAR_WARN1_VALUE  = stringPreferencesKey("p2_bar_warn1_value")
        val KEY_P2_BAR_WARN2_COLOR  = stringPreferencesKey("p2_bar_warn2_color")
        val KEY_P2_BAR_WARN2_VALUE  = stringPreferencesKey("p2_bar_warn2_value")
        // Boden-Komplikationen
        val KEY_WF_SHOW_BOTTOM_COMP  = booleanPreferencesKey("wf_show_bottom_comp")
        val KEY_WF_BC1_USE_IOBROKER  = booleanPreferencesKey("wf_bc1_use_iobroker")
        val KEY_WF_BC1_ID            = stringPreferencesKey("wf_bc1_id")
        val KEY_WF_BC1_LABEL         = stringPreferencesKey("wf_bc1_label")
        val KEY_WF_BC1_COLOR         = stringPreferencesKey("wf_bc1_color")
        val KEY_WF_BC1_RING_ENABLED  = booleanPreferencesKey("wf_bc1_ring_enabled")
        val KEY_WF_BC1_RING_COLOR1   = stringPreferencesKey("wf_bc1_ring_color1")
        val KEY_WF_BC1_RING_COLOR2   = stringPreferencesKey("wf_bc1_ring_color2")
        val KEY_WF_BC1_RING_MIN      = stringPreferencesKey("wf_bc1_ring_min")
        val KEY_WF_BC1_RING_MAX      = stringPreferencesKey("wf_bc1_ring_max")
        val KEY_WF_BC1_RING_WIDTH    = intPreferencesKey("wf_bc1_ring_width")
        val KEY_WF_BC1_RING_TH_EN    = booleanPreferencesKey("wf_bc1_ring_th_en")
        val KEY_WF_BC1_RING_TH_VAL   = stringPreferencesKey("wf_bc1_ring_th_val")
        val KEY_WF_BC1_RING_TH_DIR   = stringPreferencesKey("wf_bc1_ring_th_dir")
        val KEY_WF_BC1_RING_TH_TGT   = stringPreferencesKey("wf_bc1_ring_th_target")
        val KEY_WF_BC1_RING_TH_COLOR = stringPreferencesKey("wf_bc1_ring_th_color")
        val KEY_WF_BC1_TEXT_SCALE    = intPreferencesKey("wf_bc1_text_scale")
        val KEY_WF_BC2_METRIC        = stringPreferencesKey("wf_bc2_metric")
        val KEY_WF_BC2_USE_IOBROKER  = booleanPreferencesKey("wf_bc2_use_iobroker")
        val KEY_WF_BC2_ID            = stringPreferencesKey("wf_bc2_id")
        val KEY_WF_BC2_LABEL         = stringPreferencesKey("wf_bc2_label")
        val KEY_WF_BC2_COLOR         = stringPreferencesKey("wf_bc2_color")
        val KEY_WF_BC2_RING_ENABLED  = booleanPreferencesKey("wf_bc2_ring_enabled")
        val KEY_WF_BC2_RING_COLOR1   = stringPreferencesKey("wf_bc2_ring_color1")
        val KEY_WF_BC2_RING_COLOR2   = stringPreferencesKey("wf_bc2_ring_color2")
        val KEY_WF_BC2_RING_MIN      = stringPreferencesKey("wf_bc2_ring_min")
        val KEY_WF_BC2_RING_MAX      = stringPreferencesKey("wf_bc2_ring_max")
        val KEY_WF_BC2_RING_WIDTH    = intPreferencesKey("wf_bc2_ring_width")
        val KEY_WF_BC2_RING_TH_EN    = booleanPreferencesKey("wf_bc2_ring_th_en")
        val KEY_WF_BC2_RING_TH_VAL   = stringPreferencesKey("wf_bc2_ring_th_val")
        val KEY_WF_BC2_RING_TH_DIR   = stringPreferencesKey("wf_bc2_ring_th_dir")
        val KEY_WF_BC2_RING_TH_TGT   = stringPreferencesKey("wf_bc2_ring_th_target")
        val KEY_WF_BC2_RING_TH_COLOR = stringPreferencesKey("wf_bc2_ring_th_color")
        val KEY_WF_BC2_TEXT_SCALE    = intPreferencesKey("wf_bc2_text_scale")
        // Klipper
        val KEY_KLIPPER_ENABLED      = booleanPreferencesKey("klipper_enabled")
        val KEY_KLIPPER_HOST         = stringPreferencesKey("klipper_host")
        val KEY_KLIPPER_PORT         = intPreferencesKey("klipper_port")
        val KEY_KLIPPER_API_KEY      = stringPreferencesKey("klipper_api_key")
        val KEY_KLIPPER_CHAMBER_OBJ  = stringPreferencesKey("klipper_chamber_obj")
        val KEY_KLIPPER_INTERVAL     = intPreferencesKey("klipper_interval_sec")
        // Seite 3 – Pille
        val KEY_P3_PILL_ENABLED      = booleanPreferencesKey("p3_pill_enabled")
        val KEY_P3_PILL_COLOR_TRUE   = stringPreferencesKey("p3_pill_color_true")
        val KEY_P3_PILL_COLOR_FALSE  = stringPreferencesKey("p3_pill_color_false")
        val KEY_P3_PILL_OBJECT       = stringPreferencesKey("p3_pill_object")
        val KEY_P3_PILL_FIELD        = stringPreferencesKey("p3_pill_field")
        val KEY_P3_PILL_GCODE_ON     = stringPreferencesKey("p3_pill_gcode_on")
        val KEY_P3_PILL_GCODE_OFF    = stringPreferencesKey("p3_pill_gcode_off")
        val KEY_P3_PILL_STATE        = booleanPreferencesKey("p3_pill_state")
        // Slot 4 – Klipper-Override
        val KEY_CUSTOM_SLOT4_USE_KLIPPER        = booleanPreferencesKey("custom_slot4_use_klipper")
        val KEY_CUSTOM_SLOT4_KLIPPER_SOURCE     = stringPreferencesKey("custom_slot4_klipper_source")
        val KEY_CUSTOM_SLOT4_KLIPPER_COLOR_ACT  = stringPreferencesKey("custom_slot4_klipper_color_active")
        // Seite 3 – LED-Button
        val KEY_KLIPPER_LED_GCODE_ON  = stringPreferencesKey("klipper_led_gcode_on")
        val KEY_KLIPPER_LED_GCODE_OFF = stringPreferencesKey("klipper_led_gcode_off")
        val KEY_KLIPPER_LED_OBJECT    = stringPreferencesKey("klipper_led_object")
        val KEY_KLIPPER_LED_FIELD     = stringPreferencesKey("klipper_led_field")
        // Seite 3 – Chamber-Heater-Button
        val KEY_KLIPPER_HEAT_GCODE_ON  = stringPreferencesKey("klipper_heat_gcode_on")
        val KEY_KLIPPER_HEAT_GCODE_OFF = stringPreferencesKey("klipper_heat_gcode_off")
    }

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    val connectionStatus: StateFlow<WebSocketStatus> = repository.connectionStatus
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), WebSocketStatus.DISCONNECTED)

    private var ioSyncPollingJob: Job? = null

    /**
     * Liest die gesamte Konfiguration aus dem DataStore und überträgt sie in den UI-State.
     * Wird beim App-Start (init) und nach einer Backup-Wiederherstellung aufgerufen.
     */
    private suspend fun applyConfigFromPrefs(prefs: Preferences) {
            val host              = prefs[KEY_HOST]                 ?: BuildConfig.IOBROKER_DEFAULT_HOST
            val port              = prefs[KEY_PORT]                 ?: BuildConfig.IOBROKER_DEFAULT_PORT
            val wfTimeColor       = prefs[KEY_WF_TIME_COLOR]        ?: "light_gray"
            val wfDateColor       = prefs[KEY_WF_DATE_COLOR]        ?: "cyan"
            val wfShowSeconds     = prefs[KEY_WF_SHOW_SECONDS]      ?: true
            val wfShowTicks       = prefs[KEY_WF_SHOW_TICKS]        ?: true
            val wfShowWeekday     = prefs[KEY_WF_SHOW_WEEKDAY]      ?: true
            val wfShowPhoneBattery  = prefs[KEY_WF_SHOW_PHONE_BATTERY]  ?: false
            val wfShowIoBrokerData  = prefs[KEY_WF_SHOW_IOBROKER_DATA]  ?: false
            val wfShowSecondsRing   = prefs[KEY_WF_SHOW_SECONDS_RING]   ?: false
            val wfSecondsRingColor  = prefs[KEY_WF_SECONDS_RING_COLOR]   ?: "neon_yellow"
            val wfSecondsRingWidth  = prefs[KEY_WF_SECONDS_RING_WIDTH]   ?: 5
            val wfSecondsGlowWidth  = prefs[KEY_WF_SECONDS_GLOW_WIDTH]   ?: 100
            val wfSecondsNumberColor = prefs[KEY_WF_SECONDS_NUMBER_COLOR] ?: "dim_time"
            val useIoSyncAdapter  = prefs[KEY_USE_IOSYNC_ADAPTER]   ?: true
            val ioSyncHost        = prefs[KEY_IOSYNC_HOST]          ?: ""
            val ioSyncPort        = prefs[KEY_IOSYNC_PORT]          ?: 7443
            val ioSyncUseHttps    = prefs[KEY_IOSYNC_USE_HTTPS]     ?: false
            val ioSyncUsername    = prefs[KEY_IOSYNC_USERNAME]       ?: ""
            val ioSyncPassword    = prefs[KEY_IOSYNC_PASSWORD]       ?: ""
            val actionPillEnabled    = prefs[KEY_ACTION_PILL_ENABLED]     ?: false
            val actionPillColorTrue  = prefs[KEY_ACTION_PILL_COLOR_TRUE]  ?: "cyan"
            val actionPillColorFalse = prefs[KEY_ACTION_PILL_COLOR_FALSE] ?: "red"
            val actionPillIoBrokerId = prefs[KEY_ACTION_PILL_IOBROKER_ID] ?: ""
            val actionPillValueMode  = prefs[KEY_ACTION_PILL_VALUE_MODE]  ?: "toggle"
            val actionPillFixedValue = prefs[KEY_ACTION_PILL_FIXED_VALUE] ?: ""
            val actionPillState      = prefs[KEY_ACTION_PILL_STATE]       ?: false
            val wfShowWeather     = prefs[KEY_WF_SHOW_WEATHER]     ?: true
            val wfShowHeartRate   = prefs[KEY_WF_SHOW_HEART_RATE]  ?: true
            val wfShowOxygen      = prefs[KEY_WF_SHOW_OXYGEN]      ?: false
            val wfShowCalories    = prefs[KEY_WF_SHOW_CALORIES]    ?: true
            val wfShowSteps       = prefs[KEY_WF_SHOW_STEPS]       ?: true
            val showCustomSlots   = prefs[KEY_SHOW_CUSTOM_SLOTS]   ?: false
            val customSlot1Id     = prefs[KEY_CUSTOM_SLOT1_ID]     ?: ""
            val customSlot1Label  = prefs[KEY_CUSTOM_SLOT1_LABEL]  ?: ""
            val customSlot2Id     = prefs[KEY_CUSTOM_SLOT2_ID]     ?: ""
            val customSlot2Label  = prefs[KEY_CUSTOM_SLOT2_LABEL]  ?: ""
            val customSlot3Id     = prefs[KEY_CUSTOM_SLOT3_ID]     ?: ""
            val customSlot3Label  = prefs[KEY_CUSTOM_SLOT3_LABEL]  ?: ""
            val customSlot4Id     = prefs[KEY_CUSTOM_SLOT4_ID]     ?: ""
            val customSlot4Label  = prefs[KEY_CUSTOM_SLOT4_LABEL]  ?: ""
            val customSlot4BarColor      = prefs[KEY_CUSTOM_SLOT4_BAR_COLOR] ?: "neon_yellow"
            val customSlot4BarMin        = prefs[KEY_CUSTOM_SLOT4_BAR_MIN]?.toFloatOrNull() ?: 0f
            val customSlot4BarMax        = prefs[KEY_CUSTOM_SLOT4_BAR_MAX]?.toFloatOrNull() ?: 100f
            val customSlot4BarShowLabel  = prefs[KEY_CUSTOM_SLOT4_BAR_SHOW_LABEL] ?: true
            val customSlot4BarIsSlider   = prefs[KEY_CUSTOM_SLOT4_BAR_IS_SLIDER] ?: false
            val customSlot4UseKlipper       = prefs[KEY_CUSTOM_SLOT4_USE_KLIPPER]       ?: false
            val customSlot4KlipperSource    = prefs[KEY_CUSTOM_SLOT4_KLIPPER_SOURCE]    ?: "progress"
            val customSlot4KlipperColorAct  = prefs[KEY_CUSTOM_SLOT4_KLIPPER_COLOR_ACT] ?: "neon_yellow"
            val customSlot4Warn1Color    = prefs[KEY_CUSTOM_SLOT4_WARN1_COLOR] ?: "orange"
            val customSlot4Warn1Value    = prefs[KEY_CUSTOM_SLOT4_WARN1_VALUE]?.toFloatOrNull() ?: Float.NaN
            val customSlot4Warn2Color    = prefs[KEY_CUSTOM_SLOT4_WARN2_COLOR] ?: "red"
            val customSlot4Warn2Value    = prefs[KEY_CUSTOM_SLOT4_WARN2_VALUE]?.toFloatOrNull() ?: Float.NaN
            val wfHrTextScale      = prefs[KEY_WF_HR_TEXT_SCALE]      ?: 100
            val wfKcalTextScale    = prefs[KEY_WF_KCAL_TEXT_SCALE]    ?: 100
            val wfStepsTextScale   = prefs[KEY_WF_STEPS_TEXT_SCALE]   ?: 100
            val wfSlot1TextScale   = prefs[KEY_WF_SLOT1_TEXT_SCALE]   ?: 100
            val wfSlot2TextScale   = prefs[KEY_WF_SLOT2_TEXT_SCALE]   ?: 100
            val wfSlot3TextScale   = prefs[KEY_WF_SLOT3_TEXT_SCALE]   ?: 100
            val wfSlot4TextScale   = prefs[KEY_WF_SLOT4_TEXT_SCALE]   ?: 100
            val wfWeatherTextScale = prefs[KEY_WF_WEATHER_TEXT_SCALE] ?: 100
            val wfSunriseTextScale = prefs[KEY_WF_SUNRISE_TEXT_SCALE] ?: 100
            val wfWatchBatteryTextScale = prefs[KEY_WF_WATCH_BATTERY_TEXT_SCALE] ?: 100
            val wfShowBackground          = prefs[KEY_WF_SHOW_BACKGROUND]           ?: false
            val wfBatteryRingColor1       = prefs[KEY_WF_BATTERY_RING_COLOR1]       ?: "cyan"
            val wfBatteryRingColor2       = prefs[KEY_WF_BATTERY_RING_COLOR2]       ?: "neon_yellow"
            val wfBatteryRingStrokeScale  = prefs[KEY_WF_BATTERY_RING_STROKE_SCALE] ?: 100
            val wfBatteryWarn1Color     = prefs[KEY_WF_BATTERY_WARN1_COLOR]     ?: "orange"
            val wfBatteryWarn1Threshold = prefs[KEY_WF_BATTERY_WARN1_THRESHOLD] ?: 0
            val wfBatteryWarn2Color     = prefs[KEY_WF_BATTERY_WARN2_COLOR]     ?: "red"
            val wfBatteryWarn2Threshold = prefs[KEY_WF_BATTERY_WARN2_THRESHOLD] ?: 0
            val wfHealthDataSource = prefs[KEY_WF_HEALTH_DATA_SOURCE] ?: "local"
            val wfHrSource         = prefs[KEY_WF_HR_SOURCE]         ?: "local"
            val wfKcalSource       = prefs[KEY_WF_KCAL_SOURCE]       ?: "local"
            val wfOxygenSource     = prefs[KEY_WF_OXYGEN_SOURCE]     ?: "local"
            val wfHrComplication     = prefs[KEY_WF_HR_COMPLICATION]     ?: ""
            val wfKcalComplication   = prefs[KEY_WF_KCAL_COMPLICATION]   ?: ""
            val wfOxygenComplication = prefs[KEY_WF_OXYGEN_COMPLICATION] ?: ""
            val wfKcalMetric         = prefs[KEY_WF_KCAL_METRIC]         ?: "total_calories"
            val wfOxygenMetric       = prefs[KEY_WF_OXYGEN_METRIC]       ?: "oxygen_saturation"
            val wfHrColor      = prefs[KEY_WF_HR_COLOR]      ?: "red"
            val wfKcalColor    = prefs[KEY_WF_KCAL_COLOR]    ?: "orange"
            val wfOxygenColor  = prefs[KEY_WF_OXYGEN_COLOR]  ?: "cyan"
            val wfStepsColor   = prefs[KEY_WF_STEPS_COLOR]   ?: "neon_yellow"
            val wfSleepColor   = prefs[KEY_WF_SLEEP_COLOR]   ?: "purple"
            val wfSunriseColor = prefs[KEY_WF_SUNRISE_COLOR] ?: "neon_yellow"
            val wfSlotColor    = prefs[KEY_WF_SLOT_COLOR]    ?: "neon_yellow"
            val weatherUseFixed   = prefs[KEY_WEATHER_USE_FIXED]   ?: false
            val weatherFixedLat   = prefs[KEY_WEATHER_FIXED_LAT]?.toDoubleOrNull() ?: 0.0
            val weatherFixedLon   = prefs[KEY_WEATHER_FIXED_LON]?.toDoubleOrNull() ?: 0.0
            val weatherFixedCity  = prefs[KEY_WEATHER_FIXED_CITY]  ?: ""
            val wfWeatherTempSource = prefs[KEY_WF_WEATHER_TEMP_SOURCE] ?: "openweather"
            val wfWeatherIoBrokerId = prefs[KEY_WF_WEATHER_IOBROKER_ID] ?: ""
            val wfNtpEnabled = prefs[KEY_WF_NTP_ENABLED] ?: false
            val wfNtpServer  = prefs[KEY_WF_NTP_SERVER]  ?: "pool.ntp.org"
            val ntpOffsetFromWatch = prefs[KEY_NTP_OFFSET_FROM_WATCH] ?: 0L
            val batteryPollInterval = prefs[KEY_BATTERY_POLL_INTERVAL] ?: 60
            val slotPollInterval   = prefs[KEY_SLOT_POLL_INTERVAL]   ?: 120
            val healthPollInterval = prefs[KEY_HEALTH_POLL_INTERVAL] ?: 60
            val page2SyncInterval  = prefs[KEY_PAGE2_SYNC_INTERVAL]  ?: 120
            // Seite 2 Slots
            val p2Slot1Id     = prefs[KEY_P2_SLOT1_ID]     ?: ""
            val p2Slot1Label  = prefs[KEY_P2_SLOT1_LABEL]  ?: ""
            val p2Slot2Id     = prefs[KEY_P2_SLOT2_ID]     ?: ""
            val p2Slot2Label  = prefs[KEY_P2_SLOT2_LABEL]  ?: ""
            val p2Slot3Id     = prefs[KEY_P2_SLOT3_ID]     ?: ""
            val p2Slot3Label  = prefs[KEY_P2_SLOT3_LABEL]  ?: ""
            val p2Slot4Id     = prefs[KEY_P2_SLOT4_ID]     ?: ""
            val p2Slot4Label  = prefs[KEY_P2_SLOT4_LABEL]  ?: ""
            val p2Slot1TextScale  = prefs[KEY_P2_SLOT1_TEXT_SCALE]  ?: 100
            val p2Slot2TextScale  = prefs[KEY_P2_SLOT2_TEXT_SCALE]  ?: 100
            val p2Slot3TextScale  = prefs[KEY_P2_SLOT3_TEXT_SCALE]  ?: 100
            val p2Slot4TextScale  = prefs[KEY_P2_SLOT4_TEXT_SCALE]  ?: 100
            val wfSleepTextScale    = prefs[KEY_WF_SLEEP_TEXT_SCALE]   ?: 100
            val wfSleepSource       = prefs[KEY_WF_SLEEP_SOURCE]       ?: "healthconnect"
            val wfSleepIoBrokerId   = prefs[KEY_WF_SLEEP_IOBROKER_ID]  ?: ""
            val wfSleepComplication = prefs[KEY_WF_SLEEP_COMPLICATION] ?: ""
            // Seite 2 Pillen – Pille 1 (7 Uhr)
            val p2PillEnabled    = prefs[KEY_P2_PILL_ENABLED]     ?: false
            val p2PillColorTrue  = prefs[KEY_P2_PILL_COLOR_TRUE]  ?: "cyan"
            val p2PillColorFalse = prefs[KEY_P2_PILL_COLOR_FALSE] ?: "red"
            val p2PillIoBrokerId = prefs[KEY_P2_PILL_IOBROKER_ID] ?: ""
            val p2PillValueMode  = prefs[KEY_P2_PILL_VALUE_MODE]  ?: "toggle"
            val p2PillFixedValue = prefs[KEY_P2_PILL_FIXED_VALUE] ?: ""
            val p2Pill1State     = prefs[KEY_P2_PILL1_STATE]      ?: false
            // Seite 2 Pillen – Pille 2 (5 Uhr)
            val p2Pill2Enabled    = prefs[KEY_P2_PILL2_ENABLED]    ?: false
            val p2Pill2ColorTrue  = prefs[KEY_P2_PILL2_COLOR_TRUE]  ?: "cyan"
            val p2Pill2ColorFalse = prefs[KEY_P2_PILL2_COLOR_FALSE] ?: "red"
            val p2Pill2IoBrokerId = prefs[KEY_P2_PILL2_IOBROKER_ID] ?: ""
            val p2Pill2ValueMode  = prefs[KEY_P2_PILL2_VALUE_MODE]  ?: "toggle"
            val p2Pill2FixedValue = prefs[KEY_P2_PILL2_FIXED_VALUE] ?: ""
            val p2Pill2State      = prefs[KEY_P2_PILL2_STATE]       ?: false
            val p2ShowBackground  = prefs[KEY_P2_SHOW_BACKGROUND]   ?: false
            // Seite 2 – vertikaler Balken
            val p2BarId         = prefs[KEY_P2_BAR_ID]         ?: ""
            val p2BarLabel      = prefs[KEY_P2_BAR_LABEL]      ?: ""
            val p2BarColor      = prefs[KEY_P2_BAR_COLOR]      ?: "neon_yellow"
            val p2BarMin        = prefs[KEY_P2_BAR_MIN]?.toFloatOrNull()  ?: 0f
            val p2BarMax        = prefs[KEY_P2_BAR_MAX]?.toFloatOrNull()  ?: 100f
            val p2BarShowLabel  = prefs[KEY_P2_BAR_SHOW_LABEL] ?: true
            val p2BarIsSlider   = prefs[KEY_P2_BAR_IS_SLIDER] ?: false
            val p2BarTextScale  = prefs[KEY_P2_BAR_TEXT_SCALE] ?: 100
            val p2BarWarn1Color = prefs[KEY_P2_BAR_WARN1_COLOR] ?: "orange"
            val p2BarWarn1Value = prefs[KEY_P2_BAR_WARN1_VALUE]?.toFloatOrNull() ?: Float.NaN
            val p2BarWarn2Color = prefs[KEY_P2_BAR_WARN2_COLOR] ?: "red"
            val p2BarWarn2Value = prefs[KEY_P2_BAR_WARN2_VALUE]?.toFloatOrNull() ?: Float.NaN
            // Boden-Komplikationen
            val wfShowBottomComp  = prefs[KEY_WF_SHOW_BOTTOM_COMP]  ?: true
            val wfBc1UseIoBroker  = prefs[KEY_WF_BC1_USE_IOBROKER]  ?: false
            val wfBc1Id           = prefs[KEY_WF_BC1_ID]            ?: ""
            val wfBc1Label        = prefs[KEY_WF_BC1_LABEL]         ?: "BPM"
            val wfBc1Color        = prefs[KEY_WF_BC1_COLOR]         ?: "red"
            val wfBc1RingEnabled  = prefs[KEY_WF_BC1_RING_ENABLED]  ?: true
            val wfBc1RingColor1   = prefs[KEY_WF_BC1_RING_COLOR1]   ?: "red"
            val wfBc1RingColor2   = prefs[KEY_WF_BC1_RING_COLOR2]   ?: "orange"
            val wfBc1RingMin      = prefs[KEY_WF_BC1_RING_MIN]?.toFloatOrNull() ?: 0f
            val wfBc1RingMax      = prefs[KEY_WF_BC1_RING_MAX]?.toFloatOrNull() ?: 140f
            val wfBc1RingWidth    = prefs[KEY_WF_BC1_RING_WIDTH] ?: 6
            val wfBc1RingThreshEnabled = prefs[KEY_WF_BC1_RING_TH_EN] ?: false
            val wfBc1RingThreshValue   = prefs[KEY_WF_BC1_RING_TH_VAL]?.toFloatOrNull() ?: 0f
            val wfBc1RingThreshDir     = prefs[KEY_WF_BC1_RING_TH_DIR]    ?: "above"
            val wfBc1RingThreshTarget  = prefs[KEY_WF_BC1_RING_TH_TGT]    ?: "color2"
            val wfBc1RingThreshColor   = prefs[KEY_WF_BC1_RING_TH_COLOR]  ?: "red"
            val wfBc1TextScale    = prefs[KEY_WF_BC1_TEXT_SCALE]    ?: 100
            val wfBc2Metric       = prefs[KEY_WF_BC2_METRIC]        ?: "kcal"
            val wfBc2UseIoBroker  = prefs[KEY_WF_BC2_USE_IOBROKER]  ?: false
            val wfBc2Id           = prefs[KEY_WF_BC2_ID]            ?: ""
            val wfBc2Label        = prefs[KEY_WF_BC2_LABEL]         ?: "KCAL"
            val wfBc2Color        = prefs[KEY_WF_BC2_COLOR]         ?: "orange"
            val wfBc2RingEnabled  = prefs[KEY_WF_BC2_RING_ENABLED]  ?: true
            val wfBc2RingColor1   = prefs[KEY_WF_BC2_RING_COLOR1]   ?: "orange"
            val wfBc2RingColor2   = prefs[KEY_WF_BC2_RING_COLOR2]   ?: "neon_yellow"
            val wfBc2RingMin      = prefs[KEY_WF_BC2_RING_MIN]?.toFloatOrNull() ?: 0f
            val wfBc2RingMax      = prefs[KEY_WF_BC2_RING_MAX]?.toFloatOrNull() ?: 1000f
            val wfBc2RingWidth    = prefs[KEY_WF_BC2_RING_WIDTH] ?: 6
            val wfBc2RingThreshEnabled = prefs[KEY_WF_BC2_RING_TH_EN] ?: false
            val wfBc2RingThreshValue   = prefs[KEY_WF_BC2_RING_TH_VAL]?.toFloatOrNull() ?: 0f
            val wfBc2RingThreshDir     = prefs[KEY_WF_BC2_RING_TH_DIR]    ?: "above"
            val wfBc2RingThreshTarget  = prefs[KEY_WF_BC2_RING_TH_TGT]    ?: "color2"
            val wfBc2RingThreshColor   = prefs[KEY_WF_BC2_RING_TH_COLOR]  ?: "red"
            val wfBc2TextScale    = prefs[KEY_WF_BC2_TEXT_SCALE]    ?: 100
            // Klipper
            val klipperEnabled   = prefs[KEY_KLIPPER_ENABLED]    ?: false
            val klipperHost      = prefs[KEY_KLIPPER_HOST]        ?: ""
            val klipperPort      = prefs[KEY_KLIPPER_PORT]        ?: 7125
            val klipperApiKey    = prefs[KEY_KLIPPER_API_KEY]     ?: ""
            val klipperChamberObj = prefs[KEY_KLIPPER_CHAMBER_OBJ] ?: "heater_generic chamber"
            val klipperIntervalSec = prefs[KEY_KLIPPER_INTERVAL]  ?: 15
            // Seite 3 – Pille
            val p3PillEnabled    = prefs[KEY_P3_PILL_ENABLED]     ?: false
            val p3PillColorTrue  = prefs[KEY_P3_PILL_COLOR_TRUE]  ?: "cyan"
            val p3PillColorFalse = prefs[KEY_P3_PILL_COLOR_FALSE] ?: "red"
            val p3PillObject     = prefs[KEY_P3_PILL_OBJECT]      ?: ""
            val p3PillField      = prefs[KEY_P3_PILL_FIELD]       ?: "value"
            val p3PillGcodeOn    = prefs[KEY_P3_PILL_GCODE_ON]    ?: ""
            val p3PillGcodeOff   = prefs[KEY_P3_PILL_GCODE_OFF]   ?: ""
            val p3PillState      = prefs[KEY_P3_PILL_STATE]       ?: false
            // Seite 3 – LED-Button
            val klipperLedGcodeOn  = prefs[KEY_KLIPPER_LED_GCODE_ON]  ?: ""
            val klipperLedGcodeOff = prefs[KEY_KLIPPER_LED_GCODE_OFF] ?: ""
            val klipperLedObject   = prefs[KEY_KLIPPER_LED_OBJECT]    ?: ""
            val klipperLedField    = prefs[KEY_KLIPPER_LED_FIELD]     ?: "value"
            // Seite 3 – Chamber-Heater-Button
            val klipperHeatGcodeOn  = prefs[KEY_KLIPPER_HEAT_GCODE_ON]  ?: ""
            val klipperHeatGcodeOff = prefs[KEY_KLIPPER_HEAT_GCODE_OFF] ?: ""

            // WeatherService festen Standort konfigurieren
            weatherService.useFixedLocation = weatherUseFixed
            weatherService.fixedLat = if (weatherUseFixed) weatherFixedLat else null
            weatherService.fixedLon = if (weatherUseFixed) weatherFixedLon else null

            _uiState.update {
                it.copy(
                    host               = host,
                    port               = port,
                    wfTimeColor        = wfTimeColor,
                    wfDateColor        = wfDateColor,
                    wfShowSeconds      = wfShowSeconds,
                    wfShowTicks        = wfShowTicks,
                    wfShowWeekday      = wfShowWeekday,
                    wfShowPhoneBattery  = wfShowPhoneBattery,
                    wfShowIoBrokerData  = wfShowIoBrokerData,
                    wfShowSecondsRing   = wfShowSecondsRing,
                    wfSecondsRingColor  = wfSecondsRingColor,
                    wfSecondsRingWidth  = wfSecondsRingWidth,
                    wfSecondsGlowWidth  = wfSecondsGlowWidth,
                    wfSecondsNumberColor = wfSecondsNumberColor,
                    useIoSyncAdapter   = useIoSyncAdapter,
                    ioSyncHost         = ioSyncHost,
                    ioSyncPort         = ioSyncPort,
                    ioSyncUseHttps     = ioSyncUseHttps,
                    ioSyncUsername     = ioSyncUsername,
                    ioSyncPassword     = ioSyncPassword,
                    actionPillEnabled    = actionPillEnabled,
                    actionPillColorTrue  = actionPillColorTrue,
                    actionPillColorFalse = actionPillColorFalse,
                    actionPillIoBrokerId = actionPillIoBrokerId,
                    actionPillValueMode  = actionPillValueMode,
                    actionPillFixedValue = actionPillFixedValue,
                    actionPillState      = actionPillState,
                    wfShowWeather     = wfShowWeather,
                    wfShowHeartRate   = wfShowHeartRate,
                    wfShowOxygen      = wfShowOxygen,
                    wfShowCalories    = wfShowCalories,
                    wfShowSteps       = wfShowSteps,
                    showCustomSlots    = showCustomSlots,
                    customSlot1Id      = customSlot1Id,
                    customSlot1Label   = customSlot1Label,
                    customSlot2Id      = customSlot2Id,
                    customSlot2Label   = customSlot2Label,
                    customSlot3Id      = customSlot3Id,
                    customSlot3Label   = customSlot3Label,
                    customSlot4Id      = customSlot4Id,
                    customSlot4Label   = customSlot4Label,
                    customSlot4BarColor      = customSlot4BarColor,
                    customSlot4BarMin        = customSlot4BarMin,
                    customSlot4BarMax        = customSlot4BarMax,
                    customSlot4BarShowLabel  = customSlot4BarShowLabel,
                    customSlot4BarIsSlider        = customSlot4BarIsSlider,
                    customSlot4UseKlipper         = customSlot4UseKlipper,
                    customSlot4KlipperSource      = customSlot4KlipperSource,
                    customSlot4KlipperColorActive = customSlot4KlipperColorAct,
                    customSlot4Warn1Color         = customSlot4Warn1Color,
                    customSlot4Warn1Value    = customSlot4Warn1Value,
                    customSlot4Warn2Color    = customSlot4Warn2Color,
                    customSlot4Warn2Value    = customSlot4Warn2Value,
                    wfHrTextScale      = wfHrTextScale,
                    wfKcalTextScale    = wfKcalTextScale,
                    wfStepsTextScale   = wfStepsTextScale,
                    wfSlot1TextScale   = wfSlot1TextScale,
                    wfSlot2TextScale   = wfSlot2TextScale,
                    wfSlot3TextScale   = wfSlot3TextScale,
                    wfSlot4TextScale   = wfSlot4TextScale,
                    wfWeatherTextScale = wfWeatherTextScale,
                    wfSunriseTextScale = wfSunriseTextScale,
                    wfWatchBatteryTextScale = wfWatchBatteryTextScale,
                    wfShowBackground          = wfShowBackground,
                    wfBatteryRingColor1       = wfBatteryRingColor1,
                    wfBatteryRingColor2       = wfBatteryRingColor2,
                    wfBatteryRingStrokeScale  = wfBatteryRingStrokeScale,
                    wfBatteryWarn1Color       = wfBatteryWarn1Color,
                    wfBatteryWarn1Threshold   = wfBatteryWarn1Threshold,
                    wfBatteryWarn2Color       = wfBatteryWarn2Color,
                    wfBatteryWarn2Threshold   = wfBatteryWarn2Threshold,
                    wfHealthDataSource = wfHealthDataSource,
                    wfHrSource         = wfHrSource,
                    wfKcalSource       = wfKcalSource,
                    wfOxygenSource     = wfOxygenSource,
                    wfHrComplication     = wfHrComplication,
                    wfKcalComplication   = wfKcalComplication,
                    wfOxygenComplication = wfOxygenComplication,
                    wfKcalMetric       = wfKcalMetric,
                    wfOxygenMetric     = wfOxygenMetric,
                    wfHrColor      = wfHrColor,
                    wfKcalColor    = wfKcalColor,
                    wfOxygenColor  = wfOxygenColor,
                    wfStepsColor   = wfStepsColor,
                    wfSleepColor   = wfSleepColor,
                    wfSunriseColor = wfSunriseColor,
                    wfSlotColor    = wfSlotColor,
                    weatherUseFixedLocation = weatherUseFixed,
                    weatherFixedLat   = weatherFixedLat,
                    weatherFixedLon   = weatherFixedLon,
                    weatherFixedCity  = weatherFixedCity,
                    batteryPollIntervalSec = batteryPollInterval,
                    slotPollIntervalSec    = slotPollInterval,
                    healthPollIntervalSec  = healthPollInterval,
                    page2SyncIntervalSec   = page2SyncInterval,
                    p2Slot1Id     = p2Slot1Id,
                    p2Slot1Label  = p2Slot1Label,
                    p2Slot2Id     = p2Slot2Id,
                    p2Slot2Label  = p2Slot2Label,
                    p2Slot3Id     = p2Slot3Id,
                    p2Slot3Label  = p2Slot3Label,
                    p2Slot4Id     = p2Slot4Id,
                    p2Slot4Label  = p2Slot4Label,
                    p2Slot1TextScale = p2Slot1TextScale,
                    p2Slot2TextScale = p2Slot2TextScale,
                    p2Slot3TextScale = p2Slot3TextScale,
                    p2Slot4TextScale = p2Slot4TextScale,
                    wfSleepTextScale    = wfSleepTextScale,
                    wfSleepSource       = wfSleepSource,
                    wfSleepIoBrokerId   = wfSleepIoBrokerId,
                    wfSleepComplication = wfSleepComplication,
                    p2PillEnabled       = p2PillEnabled,
                    p2PillColorTrue  = p2PillColorTrue,
                    p2PillColorFalse = p2PillColorFalse,
                    p2PillIoBrokerId = p2PillIoBrokerId,
                    p2PillValueMode  = p2PillValueMode,
                    p2PillFixedValue = p2PillFixedValue,
                    p2Pill1State     = p2Pill1State,
                    p2Pill2Enabled    = p2Pill2Enabled,
                    p2Pill2ColorTrue  = p2Pill2ColorTrue,
                    p2Pill2ColorFalse = p2Pill2ColorFalse,
                    p2Pill2IoBrokerId = p2Pill2IoBrokerId,
                    p2Pill2ValueMode  = p2Pill2ValueMode,
                    p2Pill2FixedValue = p2Pill2FixedValue,
                    p2Pill2State      = p2Pill2State,
                    wfWeatherTempSource = wfWeatherTempSource,
                    wfWeatherIoBrokerId = wfWeatherIoBrokerId,
                    wfNtpEnabled = wfNtpEnabled,
                    wfNtpServer  = wfNtpServer,
                    ntpOffsetFromWatch = ntpOffsetFromWatch,
                    p2BarId         = p2BarId,
                    p2BarLabel      = p2BarLabel,
                    p2BarColor      = p2BarColor,
                    p2BarMin        = p2BarMin,
                    p2BarMax        = p2BarMax,
                    p2BarShowLabel  = p2BarShowLabel,
                    p2BarIsSlider   = p2BarIsSlider,
                    p2BarTextScale  = p2BarTextScale,
                    p2BarWarn1Color = p2BarWarn1Color,
                    p2BarWarn1Value = p2BarWarn1Value,
                    p2BarWarn2Color  = p2BarWarn2Color,
                    p2BarWarn2Value  = p2BarWarn2Value,
                    p2ShowBackground = p2ShowBackground,
                    wfShowBottomComp  = wfShowBottomComp,
                    wfBc1UseIoBroker  = wfBc1UseIoBroker,
                    wfBc1Id           = wfBc1Id,
                    wfBc1Label        = wfBc1Label,
                    wfBc1Color        = wfBc1Color,
                    wfBc1RingEnabled  = wfBc1RingEnabled,
                    wfBc1RingColor1   = wfBc1RingColor1,
                    wfBc1RingColor2   = wfBc1RingColor2,
                    wfBc1RingMin      = wfBc1RingMin,
                    wfBc1RingMax      = wfBc1RingMax,
                    wfBc1RingWidth    = wfBc1RingWidth,
                    wfBc1RingThreshEnabled = wfBc1RingThreshEnabled,
                    wfBc1RingThreshValue   = wfBc1RingThreshValue,
                    wfBc1RingThreshDir     = wfBc1RingThreshDir,
                    wfBc1RingThreshTarget  = wfBc1RingThreshTarget,
                    wfBc1RingThreshColor   = wfBc1RingThreshColor,
                    wfBc1TextScale    = wfBc1TextScale,
                    wfBc2Metric       = wfBc2Metric,
                    wfBc2UseIoBroker  = wfBc2UseIoBroker,
                    wfBc2Id           = wfBc2Id,
                    wfBc2Label        = wfBc2Label,
                    wfBc2Color        = wfBc2Color,
                    wfBc2RingEnabled  = wfBc2RingEnabled,
                    wfBc2RingColor1   = wfBc2RingColor1,
                    wfBc2RingColor2   = wfBc2RingColor2,
                    wfBc2RingMin      = wfBc2RingMin,
                    wfBc2RingMax      = wfBc2RingMax,
                    wfBc2RingWidth    = wfBc2RingWidth,
                    wfBc2RingThreshEnabled = wfBc2RingThreshEnabled,
                    wfBc2RingThreshValue   = wfBc2RingThreshValue,
                    wfBc2RingThreshDir     = wfBc2RingThreshDir,
                    wfBc2RingThreshTarget  = wfBc2RingThreshTarget,
                    wfBc2RingThreshColor   = wfBc2RingThreshColor,
                    wfBc2TextScale    = wfBc2TextScale,
                    klipperEnabled    = klipperEnabled,
                    klipperHost       = klipperHost,
                    klipperPort       = klipperPort,
                    klipperApiKey     = klipperApiKey,
                    klipperChamberObject = klipperChamberObj,
                    klipperIntervalSec = klipperIntervalSec,
                    p3PillEnabled     = p3PillEnabled,
                    p3PillColorTrue   = p3PillColorTrue,
                    p3PillColorFalse  = p3PillColorFalse,
                    p3PillObject      = p3PillObject,
                    p3PillField       = p3PillField,
                    p3PillGcodeOn     = p3PillGcodeOn,
                    p3PillGcodeOff    = p3PillGcodeOff,
                    p3PillState       = p3PillState,
                    klipperLedGcodeOn  = klipperLedGcodeOn,
                    klipperLedGcodeOff = klipperLedGcodeOff,
                    klipperLedObject   = klipperLedObject,
                    klipperLedField    = klipperLedField,
                    klipperChamberHeatGcodeOn  = klipperHeatGcodeOn,
                    klipperChamberHeatGcodeOff = klipperHeatGcodeOff
                )
            }
    }

    init {
        viewModelScope.launch {
            applyConfigFromPrefs(dataStore.data.first())
            val st = _uiState.value
            dynamicBaseUrl.update(st.host, st.port)
            if (st.useIoSyncAdapter && st.ioSyncHost.isNotBlank())
                startIoSyncPolling(st.ioSyncHost, st.ioSyncPort, st.ioSyncUseHttps, st.ioSyncUsername, st.ioSyncPassword)
            // Gesamter Hintergrund-Sync ans Watchface läuft im Foreground-Service,
            // damit Wetter/Akku/Slots/Health auch bei geschlossener App aktualisiert werden.
            IoSyncSyncService.start(context)
            sendPhoneBattery() // Akkustand einmalig für die App-Anzeige
            refresh()
            // Health-Connect-Status direkt beim App-Start einmal laden, damit die
            // Konfiguration sofort die verfügbaren Datentypen/Quellen kennt – ohne
            // dass der Nutzer erst die Health-Connect-Sektion aufklappen muss.
            refreshHealthConnectStatus()
        }

        // NTP-Offset von der Uhr live beobachten (WatchFaceTriggerListenerService schreibt in DataStore)
        viewModelScope.launch {
            dataStore.data.collect { prefs ->
                val offset = prefs[KEY_NTP_OFFSET_FROM_WATCH] ?: 0L
                _uiState.update { it.copy(ntpOffsetFromWatch = offset) }
            }
        }

        viewModelScope.launch {
            combine(repository.states, repository.connectionStatus) { stateMap, status ->
                val stateList = stateMap.values.sortedBy { it.name }
                val rooms = stateList.mapNotNull { it.room }.distinct().sorted()
                Triple(stateList, status, rooms)
            }.collect { (stateList, status, rooms) ->
                _uiState.update {
                    it.copy(
                        states         = stateList,
                        filteredStates = applyFilter(stateList, it.searchQuery, it.selectedRoom),
                        connectionStatus = status,
                        availableRooms = rooms
                    )
                }
            }
        }
    }

    // ── Health Connect ────────────────────────────────────────────────────

    fun refreshHealthConnectStatus() {
        viewModelScope.launch {
            _uiState.update { it.copy(healthConnectLoading = true) }
            val status = healthConnectManager.queryStatus()
            _uiState.update { it.copy(healthConnectStatus = status, healthConnectLoading = false) }
        }
    }

    // ── Daten laden ─────────────────────────────────────────────────────────

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val s = _uiState.value
            if (s.useIoSyncAdapter && s.ioSyncHost.isNotBlank()) {
                // Primär: IoSync Adapter
                ioSyncClient.fetchDataPoints(s.ioSyncHost, s.ioSyncPort, s.ioSyncUseHttps, s.ioSyncUsername, s.ioSyncPassword)
                    .onSuccess { states ->
                        val sorted = states.sortedBy { it.name }
                        _uiState.update {
                            it.copy(
                                isLoading = false, error = null,
                                states = sorted,
                                ioSyncStates = states,
                                filteredStates = applyFilter(sorted, it.searchQuery, it.selectedRoom)
                            )
                        }
                        if (s.wfShowIoBrokerData) wearDataLayerService.syncStatesToWear(states)
                        // Frische Werte sofort ans Watchface senden (Custom-Slots & Seite-2-Slots),
                        // damit das Watchface nicht auf den nächsten SyncService-Zyklus warten muss.
                        syncCustomSlotValues()
                        syncPage2SlotValues()
                    }
                    .onFailure { e ->
                        _uiState.update { it.copy(isLoading = false, error = "IoSync: ${e.message}") }
                    }
            } else {
                // Fallback: Simple-API
                when (val result = repository.fetchAllStates()) {
                    is RepoResult.Success -> _uiState.update { it.copy(isLoading = false, error = null) }
                    is RepoResult.Error   -> _uiState.update { it.copy(isLoading = false, error = result.message) }
                    is RepoResult.Loading -> Unit
                }
            }
        }
    }

    fun toggleConnection() {
        val current = _uiState.value
        when (current.connectionStatus) {
            WebSocketStatus.CONNECTED, WebSocketStatus.CONNECTING, WebSocketStatus.RECONNECTING -> {
                context.startService(Intent(context, SmartHomeWebSocketService::class.java).apply {
                    action = SmartHomeWebSocketService.ACTION_STOP
                })
            }
            WebSocketStatus.DISCONNECTED, WebSocketStatus.FAILED -> {
                if (current.host.isBlank()) return
                context.startForegroundService(Intent(context, SmartHomeWebSocketService::class.java).apply {
                    action = SmartHomeWebSocketService.ACTION_START
                    putExtra(SmartHomeWebSocketService.EXTRA_URL, "ws://${current.host}:${current.port}")
                })
            }
        }
    }

    fun updateSearchQuery(query: String) {
        _uiState.update { state ->
            state.copy(
                searchQuery    = query,
                filteredStates = applyFilter(state.states, query, state.selectedRoom)
            )
        }
    }

    fun selectRoom(room: String?) {
        _uiState.update { state ->
            state.copy(
                selectedRoom   = room,
                filteredStates = applyFilter(state.states, state.searchQuery, room)
            )
        }
    }

    /** Schreibt einen Wert direkt über die ioBroker Simple-API. */
    fun setStateValue(id: String, value: String) {
        viewModelScope.launch {
            val result = repository.setState(id, value)
            if (result is RepoResult.Error) _uiState.update { it.copy(error = result.message) }
        }
    }

    /** Schreibt einen Wert über den IoSync Adapter (mit Auth + HTTPS). */
    fun setStateValueViaIoSync(id: String, value: String) {
        viewModelScope.launch {
            val s = _uiState.value
            if (s.ioSyncHost.isBlank()) {
                _uiState.update { it.copy(error = "IoSync Adapter nicht konfiguriert") }
                return@launch
            }
            ioSyncClient.setState(s.ioSyncHost, s.ioSyncPort, s.ioSyncUseHttps, s.ioSyncUsername, s.ioSyncPassword, id, value)
                .onFailure { _uiState.update { st -> st.copy(error = "IoSync setState: ${it.message}") } }
        }
    }

    fun updateConnectionSettings(host: String, port: Int) {
        viewModelScope.launch {
            dataStore.edit { prefs ->
                prefs[KEY_HOST] = host
                prefs[KEY_PORT] = port
            }
            dynamicBaseUrl.update(host, port)
            _uiState.update { it.copy(host = host, port = port) }
        }
    }

    // ── IoSync Adapter ────────────────────────────────────────────────────────

    /**
     * Speichert die IoSync-Adapter-Verbindungseinstellungen und startet das Polling neu.
     */
    fun updateIoSyncSettings(host: String, port: Int, useHttps: Boolean, username: String, password: String) {
        viewModelScope.launch {
            dataStore.edit { prefs ->
                prefs[KEY_IOSYNC_HOST]       = host
                prefs[KEY_IOSYNC_PORT]       = port
                prefs[KEY_IOSYNC_USE_HTTPS]  = useHttps
                prefs[KEY_IOSYNC_USERNAME]   = username
                prefs[KEY_IOSYNC_PASSWORD]   = password
            }
            _uiState.update { it.copy(ioSyncHost = host, ioSyncPort = port, ioSyncUseHttps = useHttps, ioSyncUsername = username, ioSyncPassword = password) }

            ioSyncPollingJob?.cancel()
            if (host.isNotBlank()) {
                startIoSyncPolling(host, port, useHttps, username, password)
                refresh()
            }
            try { pushConnectionConfigToWear() } catch (_: Exception) {}
        }
    }

    /** Wechselt die Datenquelle zwischen IoSync Adapter und Simple-API. */
    fun updateDataSourceToggle(useIoSync: Boolean) {
        viewModelScope.launch {
            dataStore.edit { prefs -> prefs[KEY_USE_IOSYNC_ADAPTER] = useIoSync }
            _uiState.update { it.copy(useIoSyncAdapter = useIoSync) }

            if (useIoSync) {
                val s = _uiState.value
                if (s.ioSyncHost.isNotBlank()) {
                    startIoSyncPolling(s.ioSyncHost, s.ioSyncPort, s.ioSyncUseHttps, s.ioSyncUsername, s.ioSyncPassword)
                }
            } else {
                ioSyncPollingJob?.cancel()
            }
            refresh()
        }
    }

    /**
     * Aktualisiert die in der App-UI angezeigte Datenpunkt-Liste, solange die App
     * geöffnet ist. Der Versand der Werte ans Watchface erfolgt unabhängig davon im
     * [IoSyncSyncService] (überlebt das Schließen der App).
     */
    private fun startIoSyncPolling(host: String, port: Int, useHttps: Boolean, username: String, password: String) {
        ioSyncPollingJob?.cancel()
        ioSyncPollingJob = viewModelScope.launch {
            while (true) {
                ioSyncClient.fetchDataPoints(host, port, useHttps, username, password)
                    .onSuccess { states ->
                        val sorted = states.sortedBy { it.name }
                        _uiState.update {
                            it.copy(
                                ioSyncStates = states,
                                states = sorted,
                                filteredStates = applyFilter(sorted, it.searchQuery, it.selectedRoom)
                            )
                        }
                        // Frische Werte auch bei jedem Poll-Zyklus ans Watchface senden,
                        // damit die Uhr genauso aktuell ist wie die App-Anzeige.
                        val s = _uiState.value
                        if (s.wfShowIoBrokerData) wearDataLayerService.syncStatesToWear(states)
                        syncCustomSlotValues()
                        syncPage2SlotValues()
                    }
                    .onFailure { /* Fehler werden im IoSyncClient geloggt */ }
                delay(_uiState.value.slotPollIntervalSec * 1_000L)
            }
        }
    }

    // ── Watchface-Konfiguration ───────────────────────────────────────────────

    /**
     * Überträgt die vollständige Watchface-Konfiguration aus dem aktuellen [_uiState]
     * an die Uhr. Persistiert NICHTS – dient als gemeinsame Sende-Logik für Live-Vorschau
     * und Speichern.
     */
    private suspend fun pushFullConfigToWear() {
        if (!wearDataLayerService.isWatchConnected()) {
            _uiState.update { it.copy(wearSyncLog = "Fehler: Keine Uhr verbunden") }
            return
        }
        val s = _uiState.value
        wearDataLayerService.syncWatchFaceConfigToWear(
            s.wfTimeColor, s.wfDateColor, s.wfShowSeconds, s.wfShowTicks, s.wfShowWeekday,
            s.wfShowPhoneBattery, s.wfShowIoBrokerData, s.wfShowSecondsRing,
            s.wfSecondsRingColor, s.wfSecondsRingWidth, s.wfSecondsGlowWidth, s.wfSecondsNumberColor,
            s.actionPillEnabled, s.actionPillColorTrue, s.actionPillColorFalse,
            s.actionPillIoBrokerId, s.actionPillValueMode, s.actionPillFixedValue, s.actionPillState,
            s.wfShowWeather, s.wfShowHeartRate, s.wfShowOxygen, s.wfShowCalories, s.wfShowSteps,
            s.showCustomSlots, s.customSlot1Label, s.customSlot2Label,
            s.customSlot3Label, s.customSlot4Label, s.customSlot4BarColor, s.customSlot4BarMin, s.customSlot4BarMax,
            s.customSlot4BarShowLabel, s.customSlot4BarIsSlider,
            customSlot4UseKlipper = s.customSlot4UseKlipper,
            customSlot4KlipperSource = s.customSlot4KlipperSource,
            customSlot4KlipperColorActive = s.customSlot4KlipperColorActive,
            s.wfHrTextScale, s.wfKcalTextScale, s.wfStepsTextScale, s.wfSlot1TextScale, s.wfSlot2TextScale, s.wfSlot3TextScale, s.wfSlot4TextScale,
            s.wfWeatherTextScale, s.wfSunriseTextScale, s.wfWatchBatteryTextScale,
            s.wfBatteryRingColor1, s.wfBatteryRingColor2, s.wfBatteryRingStrokeScale,
            s.wfHealthDataSource,
            s.wfHrSource, s.wfKcalSource, s.wfOxygenSource,
            s.wfHrComplication, s.wfKcalComplication, s.wfOxygenComplication,
            s.wfBatteryWarn1Color, s.wfBatteryWarn1Threshold,
            s.wfBatteryWarn2Color, s.wfBatteryWarn2Threshold,
            showBackground = s.wfShowBackground,
            hrColor = s.wfHrColor, kcalColor = s.wfKcalColor, oxygenColor = s.wfOxygenColor,
            stepsColor = s.wfStepsColor, sleepColor = s.wfSleepColor,
            sunriseColor = s.wfSunriseColor, slotColor = s.wfSlotColor,
            weatherTempSource = s.wfWeatherTempSource, weatherIoBrokerId = s.wfWeatherIoBrokerId,
            // Dynamische Überschrift + Format-Einheit der frei wählbaren Health-Slots
            kcalLabel   = HealthConnectManager.metricLabel(s.wfKcalMetric),
            kcalUnit    = HealthConnectManager.metricUnit(s.wfKcalMetric),
            oxygenLabel = HealthConnectManager.metricLabel(s.wfOxygenMetric),
            oxygenUnit  = HealthConnectManager.metricUnit(s.wfOxygenMetric),
            ntpEnabled  = s.wfNtpEnabled,
            ntpServer   = s.wfNtpServer,
            // Boden-Komplikationen
            showBottomComp  = s.wfShowBottomComp,
            bc1UseIoBroker  = s.wfBc1UseIoBroker,
            bc1Label        = s.wfBc1Label,
            bc1Color        = s.wfBc1Color,
            bc1RingEnabled  = s.wfBc1RingEnabled,
            bc1RingColor1   = s.wfBc1RingColor1,
            bc1RingColor2   = s.wfBc1RingColor2,
            bc1RingMin      = s.wfBc1RingMin,
            bc1RingMax      = s.wfBc1RingMax,
            bc1RingWidth    = s.wfBc1RingWidth,
            bc1RingThreshEnabled = s.wfBc1RingThreshEnabled,
            bc1RingThreshValue   = s.wfBc1RingThreshValue,
            bc1RingThreshDir     = s.wfBc1RingThreshDir,
            bc1RingThreshTarget  = s.wfBc1RingThreshTarget,
            bc1RingThreshColor   = s.wfBc1RingThreshColor,
            bc1TextScale    = s.wfBc1TextScale,
            bc2Metric       = s.wfBc2Metric,
            bc2UseIoBroker  = s.wfBc2UseIoBroker,
            bc2Label        = s.wfBc2Label,
            bc2Color        = s.wfBc2Color,
            bc2RingEnabled  = s.wfBc2RingEnabled,
            bc2RingColor1   = s.wfBc2RingColor1,
            bc2RingColor2   = s.wfBc2RingColor2,
            bc2RingMin      = s.wfBc2RingMin,
            bc2RingMax      = s.wfBc2RingMax,
            bc2RingWidth    = s.wfBc2RingWidth,
            bc2RingThreshEnabled = s.wfBc2RingThreshEnabled,
            bc2RingThreshValue   = s.wfBc2RingThreshValue,
            bc2RingThreshDir     = s.wfBc2RingThreshDir,
            bc2RingThreshTarget  = s.wfBc2RingThreshTarget,
            bc2RingThreshColor   = s.wfBc2RingThreshColor,
            bc2TextScale    = s.wfBc2TextScale
        )
        pushConnectionConfigToWear()
    }

    /**
     * Überträgt die Verbindungs-/Datenpunkt-Konfiguration ans Watchface (ab v5).
     *
     * Ab v5 ruft die Uhr die ioBroker-Datenpunkte und das Wetter selbst ab. Das
     * Handy sendet nur noch diese Einstellungen (Adapter-Zugang, Datenpunkt-IDs der
     * Slots/Balken/Schlaf-Quelle, Wetter-Standort, Abruf-Intervalle) – nicht mehr
     * die Werte. Gelesen wird direkt aus dem DataStore, damit immer der gespeicherte
     * Stand übertragen wird.
     */
    private suspend fun pushConnectionConfigToWear() {
        val prefs = dataStore.data.first()
        wearDataLayerService.syncConnectionConfigToWear(
            useAdapter = prefs[KEY_USE_IOSYNC_ADAPTER] ?: true,
            host       = prefs[KEY_IOSYNC_HOST] ?: "",
            port       = prefs[KEY_IOSYNC_PORT] ?: 7443,
            useHttps   = prefs[KEY_IOSYNC_USE_HTTPS] ?: false,
            username   = prefs[KEY_IOSYNC_USERNAME] ?: "",
            password   = prefs[KEY_IOSYNC_PASSWORD] ?: "",
            usePush    = true,
            slot1Id = prefs[KEY_CUSTOM_SLOT1_ID] ?: "",
            slot2Id = prefs[KEY_CUSTOM_SLOT2_ID] ?: "",
            slot3Id = prefs[KEY_CUSTOM_SLOT3_ID] ?: "",
            slot4Id = prefs[KEY_CUSTOM_SLOT4_ID] ?: "",
            p2Slot1Id = prefs[KEY_P2_SLOT1_ID] ?: "",
            p2Slot2Id = prefs[KEY_P2_SLOT2_ID] ?: "",
            p2Slot3Id = prefs[KEY_P2_SLOT3_ID] ?: "",
            p2Slot4Id = prefs[KEY_P2_SLOT4_ID] ?: "",
            p2BarId   = prefs[KEY_P2_BAR_ID] ?: "",
            sleepId   = prefs[KEY_WF_SLEEP_IOBROKER_ID] ?: "",
            weatherUseFixed = prefs[KEY_WEATHER_USE_FIXED] ?: false,
            weatherLat = prefs[KEY_WEATHER_FIXED_LAT]?.toDoubleOrNull() ?: Double.NaN,
            weatherLon = prefs[KEY_WEATHER_FIXED_LON]?.toDoubleOrNull() ?: Double.NaN,
            slotIntervalSec  = prefs[KEY_SLOT_POLL_INTERVAL] ?: 120,
            page2IntervalSec = prefs[KEY_PAGE2_SYNC_INTERVAL] ?: 120,
            weatherIntervalSec = 600,
            bc1Id = prefs[KEY_WF_BC1_ID] ?: "",
            bc2Id = prefs[KEY_WF_BC2_ID] ?: "",
            klipperEnabled   = prefs[KEY_KLIPPER_ENABLED]  ?: false,
            klipperHost      = prefs[KEY_KLIPPER_HOST]    ?: "",
            klipperPort      = prefs[KEY_KLIPPER_PORT]    ?: 7125,
            klipperApiKey    = prefs[KEY_KLIPPER_API_KEY] ?: "",
            klipperChamberObject = prefs[KEY_KLIPPER_CHAMBER_OBJ] ?: "heater_generic chamber",
            klipperIntervalSec   = prefs[KEY_KLIPPER_INTERVAL] ?: 15,
            p3PillEnabled    = prefs[KEY_P3_PILL_ENABLED]     ?: false,
            p3PillColorTrue  = prefs[KEY_P3_PILL_COLOR_TRUE]  ?: "cyan",
            p3PillColorFalse = prefs[KEY_P3_PILL_COLOR_FALSE] ?: "red",
            p3PillObject     = prefs[KEY_P3_PILL_OBJECT]      ?: "",
            p3PillField      = prefs[KEY_P3_PILL_FIELD]       ?: "value",
            p3PillGcodeOn    = prefs[KEY_P3_PILL_GCODE_ON]    ?: "",
            p3PillGcodeOff   = prefs[KEY_P3_PILL_GCODE_OFF]   ?: "",
            klipperLedObject   = prefs[KEY_KLIPPER_LED_OBJECT]    ?: "",
            klipperLedField    = prefs[KEY_KLIPPER_LED_FIELD]     ?: "value",
            klipperLedGcodeOn  = prefs[KEY_KLIPPER_LED_GCODE_ON]  ?: "",
            klipperLedGcodeOff = prefs[KEY_KLIPPER_LED_GCODE_OFF] ?: "",
            klipperHeatGcodeOn  = prefs[KEY_KLIPPER_HEAT_GCODE_ON]  ?: "",
            klipperHeatGcodeOff = prefs[KEY_KLIPPER_HEAT_GCODE_OFF] ?: ""
        )
    }

    /**
     * Aktiviert/deaktiviert die NTP-Zeitkorrektur und wählt den NTP-Server.
     * Persistiert die Auswahl und überträgt die Konfiguration an die Uhr, die den
     * Offset dann selbst per NTP ermittelt.
     */
    fun setNtpCorrection(enabled: Boolean, server: String) {
        viewModelScope.launch {
            dataStore.edit { prefs ->
                prefs[KEY_WF_NTP_ENABLED] = enabled
                prefs[KEY_WF_NTP_SERVER]  = server
            }
            _uiState.update { it.copy(wfNtpEnabled = enabled, wfNtpServer = server) }
            try {
                pushFullConfigToWear()
            } catch (e: Exception) {
                _uiState.update { it.copy(wearSyncLog = "Fehler: ${e.message}") }
            }
        }
    }

    /**
     * Speichert den von der Uhr gemeldeten NTP-Offset und aktualisiert den UI-State.
     */
    fun updateNtpOffsetFromWatch(offsetMs: Long) {
        viewModelScope.launch {
            dataStore.edit { prefs -> prefs[KEY_NTP_OFFSET_FROM_WATCH] = offsetMs }
            _uiState.update { it.copy(ntpOffsetFromWatch = offsetMs) }
        }
    }

    /**
     * Live-Vorschau für Seite-1-Einstellungen: aktualisiert nur den In-Memory-State
     * und überträgt an die Uhr – OHNE Persistenz im DataStore.
     */
    fun previewWatchFaceConfig(
        timeColor: String,
        dateColor: String,
        showSeconds: Boolean,
        showTicks: Boolean,
        showWeekday: Boolean,
        showPhoneBattery: Boolean,
        showIoBrokerData: Boolean,
        showSecondsRing: Boolean,
        secondsRingColor: String,
        secondsRingWidth: Int,
        secondsGlowWidth: Int = 100,
        secondsNumberColor: String = _uiState.value.wfSecondsNumberColor,
        showWeather: Boolean,
        showHeartRate: Boolean,
        showOxygen: Boolean,
        showCalories: Boolean,
        showSteps: Boolean = _uiState.value.wfShowSteps,
        showCustomSlots: Boolean = _uiState.value.showCustomSlots,
        customSlot1Label: String = _uiState.value.customSlot1Label,
        customSlot2Label: String = _uiState.value.customSlot2Label,
        hrTextScale: Int = _uiState.value.wfHrTextScale,
        kcalTextScale: Int = _uiState.value.wfKcalTextScale,
        stepsTextScale: Int = _uiState.value.wfStepsTextScale,
        weatherTextScale: Int = _uiState.value.wfWeatherTextScale,
        sunriseTextScale: Int = _uiState.value.wfSunriseTextScale,
        watchBatteryTextScale: Int = _uiState.value.wfWatchBatteryTextScale,
        batteryRingColor1: String = _uiState.value.wfBatteryRingColor1,
        batteryRingColor2: String = _uiState.value.wfBatteryRingColor2,
        batteryRingStrokeScale: Int = _uiState.value.wfBatteryRingStrokeScale,
        batteryWarn1Color: String = _uiState.value.wfBatteryWarn1Color,
        batteryWarn1Threshold: Int = _uiState.value.wfBatteryWarn1Threshold,
        batteryWarn2Color: String = _uiState.value.wfBatteryWarn2Color,
        batteryWarn2Threshold: Int = _uiState.value.wfBatteryWarn2Threshold,
        showBackground: Boolean = _uiState.value.wfShowBackground,
        hrColor: String = _uiState.value.wfHrColor,
        kcalColor: String = _uiState.value.wfKcalColor,
        oxygenColor: String = _uiState.value.wfOxygenColor,
        stepsColor: String = _uiState.value.wfStepsColor,
        sleepColor: String = _uiState.value.wfSleepColor,
        sunriseColor: String = _uiState.value.wfSunriseColor,
        slotColor: String = _uiState.value.wfSlotColor,
        weatherTempSource: String = _uiState.value.wfWeatherTempSource,
        weatherIoBrokerId: String = _uiState.value.wfWeatherIoBrokerId
    ) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    wfTimeColor        = timeColor,
                    wfDateColor        = dateColor,
                    wfShowSeconds      = showSeconds,
                    wfShowTicks        = showTicks,
                    wfShowWeekday      = showWeekday,
                    wfShowPhoneBattery = showPhoneBattery,
                    wfShowIoBrokerData = showIoBrokerData,
                    wfShowSecondsRing  = showSecondsRing,
                    wfSecondsRingColor  = secondsRingColor,
                    wfSecondsRingWidth  = secondsRingWidth,
                    wfSecondsGlowWidth  = secondsGlowWidth,
                    wfSecondsNumberColor = secondsNumberColor,
                    wfShowWeather       = showWeather,
                    wfShowHeartRate    = showHeartRate,
                    wfShowOxygen       = showOxygen,
                    wfShowCalories     = showCalories,
                    wfShowSteps        = showSteps,
                    showCustomSlots    = showCustomSlots,
                    customSlot1Label   = customSlot1Label,
                    customSlot2Label   = customSlot2Label,
                    wfHrTextScale      = hrTextScale,
                    wfKcalTextScale    = kcalTextScale,
                    wfStepsTextScale   = stepsTextScale,
                    wfWeatherTextScale = weatherTextScale,
                    wfSunriseTextScale = sunriseTextScale,
                    wfWatchBatteryTextScale = watchBatteryTextScale,
                    wfBatteryRingColor1       = batteryRingColor1,
                    wfBatteryRingColor2       = batteryRingColor2,
                    wfBatteryRingStrokeScale  = batteryRingStrokeScale,
                    wfBatteryWarn1Color       = batteryWarn1Color,
                    wfBatteryWarn1Threshold   = batteryWarn1Threshold,
                    wfBatteryWarn2Color       = batteryWarn2Color,
                    wfBatteryWarn2Threshold   = batteryWarn2Threshold,
                    wfShowBackground   = showBackground,
                    wfHrColor      = hrColor,
                    wfKcalColor    = kcalColor,
                    wfOxygenColor  = oxygenColor,
                    wfStepsColor   = stepsColor,
                    wfSleepColor   = sleepColor,
                    wfSunriseColor = sunriseColor,
                    wfSlotColor    = slotColor,
                    wfWeatherTempSource = weatherTempSource,
                    wfWeatherIoBrokerId = weatherIoBrokerId
                )
            }
            // Wetter-Quelle sofort im DataStore persistieren, da der Hintergrund-Service
            // (IoSyncSyncService) ausschließlich den DataStore liest und sonst bei einem
            // Preview-only-Wechsel (ohne "Auf Uhr übertragen") den falschen Wert sieht.
            dataStore.edit { prefs ->
                prefs[KEY_WF_WEATHER_TEMP_SOURCE] = weatherTempSource
                prefs[KEY_WF_WEATHER_IOBROKER_ID] = weatherIoBrokerId
            }
            try {
                pushFullConfigToWear()
            } catch (e: Exception) {
                _uiState.update { it.copy(wearSyncLog = "Fehler: ${e.message}") }
            }
            // Hintergrund-Sync-Service mit neuer Konfiguration neu starten
            IoSyncSyncService.start(context)
            // Sofort Wetterdaten abrufen wenn Quelle OpenWeather ist
            if (weatherTempSource == "openweather") {
                weatherService.fetchWeather()
                    .onSuccess { wearDataLayerService.syncWeatherToWear(it.temperature, it.condition) }
            }
            if (showIoBrokerData && _uiState.value.ioSyncStates.isNotEmpty()) {
                wearDataLayerService.syncStatesToWear(_uiState.value.ioSyncStates)
            }
        }
    }

    fun updateWatchFaceConfig(
        timeColor: String,
        dateColor: String,
        showSeconds: Boolean,
        showTicks: Boolean,
        showWeekday: Boolean,
        showPhoneBattery: Boolean,
        showIoBrokerData: Boolean,
        showSecondsRing: Boolean,
        secondsRingColor: String,
        secondsRingWidth: Int,
        secondsGlowWidth: Int = 100,
        secondsNumberColor: String = _uiState.value.wfSecondsNumberColor,
        showWeather: Boolean,
        showHeartRate: Boolean,
        showOxygen: Boolean,
        showCalories: Boolean,
        showSteps: Boolean = _uiState.value.wfShowSteps,
        showCustomSlots: Boolean = _uiState.value.showCustomSlots,
        customSlot1Label: String = _uiState.value.customSlot1Label,
        customSlot2Label: String = _uiState.value.customSlot2Label,
        customSlot3Label: String = _uiState.value.customSlot3Label,
        customSlot4Label: String = _uiState.value.customSlot4Label,
        customSlot4BarColor: String = _uiState.value.customSlot4BarColor,
        customSlot4BarMin: Float = _uiState.value.customSlot4BarMin,
        customSlot4BarMax: Float = _uiState.value.customSlot4BarMax,
        hrTextScale: Int = _uiState.value.wfHrTextScale,
        kcalTextScale: Int = _uiState.value.wfKcalTextScale,
        stepsTextScale: Int = _uiState.value.wfStepsTextScale,
        slot1TextScale: Int = _uiState.value.wfSlot1TextScale,
        slot2TextScale: Int = _uiState.value.wfSlot2TextScale,
        slot3TextScale: Int = _uiState.value.wfSlot3TextScale,
        slot4TextScale: Int = _uiState.value.wfSlot4TextScale,
        weatherTextScale: Int = _uiState.value.wfWeatherTextScale,
        sunriseTextScale: Int = _uiState.value.wfSunriseTextScale,
        watchBatteryTextScale: Int = _uiState.value.wfWatchBatteryTextScale,
        batteryRingColor1: String = _uiState.value.wfBatteryRingColor1,
        batteryRingColor2: String = _uiState.value.wfBatteryRingColor2,
        batteryRingStrokeScale: Int = _uiState.value.wfBatteryRingStrokeScale,
        batteryWarn1Color: String = _uiState.value.wfBatteryWarn1Color,
        batteryWarn1Threshold: Int = _uiState.value.wfBatteryWarn1Threshold,
        batteryWarn2Color: String = _uiState.value.wfBatteryWarn2Color,
        batteryWarn2Threshold: Int = _uiState.value.wfBatteryWarn2Threshold,
        healthDataSource: String = _uiState.value.wfHealthDataSource,
        hrSource: String = _uiState.value.wfHrSource,
        kcalSource: String = _uiState.value.wfKcalSource,
        oxygenSource: String = _uiState.value.wfOxygenSource,
        showBackground: Boolean = _uiState.value.wfShowBackground,
        hrColor: String = _uiState.value.wfHrColor,
        kcalColor: String = _uiState.value.wfKcalColor,
        oxygenColor: String = _uiState.value.wfOxygenColor,
        stepsColor: String = _uiState.value.wfStepsColor,
        sleepColor: String = _uiState.value.wfSleepColor,
        sunriseColor: String = _uiState.value.wfSunriseColor,
        slotColor: String = _uiState.value.wfSlotColor,
        weatherTempSource: String = _uiState.value.wfWeatherTempSource,
        weatherIoBrokerId: String = _uiState.value.wfWeatherIoBrokerId
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(wearSyncLog = "Sende Watchface-Konfiguration …") }
            dataStore.edit { prefs ->
                prefs[KEY_WF_TIME_COLOR]          = timeColor
                prefs[KEY_WF_DATE_COLOR]          = dateColor
                prefs[KEY_WF_SHOW_SECONDS]        = showSeconds
                prefs[KEY_WF_SHOW_TICKS]          = showTicks
                prefs[KEY_WF_SHOW_WEEKDAY]        = showWeekday
                prefs[KEY_WF_SHOW_PHONE_BATTERY]  = showPhoneBattery
                prefs[KEY_WF_SHOW_IOBROKER_DATA]  = showIoBrokerData
                prefs[KEY_WF_SHOW_SECONDS_RING]   = showSecondsRing
                prefs[KEY_WF_SECONDS_RING_COLOR]  = secondsRingColor
                prefs[KEY_WF_SECONDS_RING_WIDTH]   = secondsRingWidth
                prefs[KEY_WF_SECONDS_GLOW_WIDTH]   = secondsGlowWidth
                prefs[KEY_WF_SECONDS_NUMBER_COLOR] = secondsNumberColor
                prefs[KEY_WF_SHOW_WEATHER]         = showWeather
                prefs[KEY_WF_SHOW_HEART_RATE]     = showHeartRate
                prefs[KEY_WF_SHOW_OXYGEN]         = showOxygen
                prefs[KEY_WF_SHOW_CALORIES]       = showCalories
                prefs[KEY_WF_SHOW_STEPS]          = showSteps
                prefs[KEY_WF_HR_TEXT_SCALE]       = hrTextScale
                prefs[KEY_WF_KCAL_TEXT_SCALE]     = kcalTextScale
                prefs[KEY_WF_STEPS_TEXT_SCALE]    = stepsTextScale
                prefs[KEY_WF_SLOT1_TEXT_SCALE]    = slot1TextScale
                prefs[KEY_WF_SLOT2_TEXT_SCALE]    = slot2TextScale
                prefs[KEY_WF_SLOT3_TEXT_SCALE]    = slot3TextScale
                prefs[KEY_WF_SLOT4_TEXT_SCALE]    = slot4TextScale
                prefs[KEY_WF_WEATHER_TEXT_SCALE]  = weatherTextScale
                prefs[KEY_WF_SUNRISE_TEXT_SCALE]        = sunriseTextScale
                prefs[KEY_WF_WATCH_BATTERY_TEXT_SCALE] = watchBatteryTextScale
                prefs[KEY_WF_BATTERY_RING_COLOR1]       = batteryRingColor1
                prefs[KEY_WF_BATTERY_RING_COLOR2]       = batteryRingColor2
                prefs[KEY_WF_BATTERY_RING_STROKE_SCALE] = batteryRingStrokeScale
                prefs[KEY_WF_BATTERY_WARN1_COLOR]       = batteryWarn1Color
                prefs[KEY_WF_BATTERY_WARN1_THRESHOLD]   = batteryWarn1Threshold
                prefs[KEY_WF_BATTERY_WARN2_COLOR]       = batteryWarn2Color
                prefs[KEY_WF_BATTERY_WARN2_THRESHOLD]   = batteryWarn2Threshold
                prefs[KEY_WF_HEALTH_DATA_SOURCE]        = healthDataSource
                prefs[KEY_WF_SHOW_BACKGROUND]           = showBackground
                prefs[KEY_WF_HR_COLOR]      = hrColor
                prefs[KEY_WF_KCAL_COLOR]    = kcalColor
                prefs[KEY_WF_OXYGEN_COLOR]  = oxygenColor
                prefs[KEY_WF_STEPS_COLOR]   = stepsColor
                prefs[KEY_WF_SLEEP_COLOR]   = sleepColor
                prefs[KEY_WF_SUNRISE_COLOR] = sunriseColor
                prefs[KEY_WF_SLOT_COLOR]    = slotColor
                prefs[KEY_WF_WEATHER_TEMP_SOURCE] = weatherTempSource
                prefs[KEY_WF_WEATHER_IOBROKER_ID] = weatherIoBrokerId
            }
            _uiState.update {
                it.copy(
                    wfTimeColor        = timeColor,
                    wfDateColor        = dateColor,
                    wfShowSeconds      = showSeconds,
                    wfShowTicks        = showTicks,
                    wfShowWeekday      = showWeekday,
                    wfShowPhoneBattery = showPhoneBattery,
                    wfShowIoBrokerData = showIoBrokerData,
                    wfShowSecondsRing  = showSecondsRing,
                    wfSecondsRingColor  = secondsRingColor,
                    wfSecondsRingWidth  = secondsRingWidth,
                    wfSecondsGlowWidth  = secondsGlowWidth,
                    wfSecondsNumberColor = secondsNumberColor,
                    wfShowWeather       = showWeather,
                    wfShowHeartRate    = showHeartRate,
                    wfShowOxygen       = showOxygen,
                    wfShowCalories     = showCalories,
                    wfShowSteps        = showSteps,
                    wfHrTextScale      = hrTextScale,
                    wfKcalTextScale    = kcalTextScale,
                    wfStepsTextScale   = stepsTextScale,
                    wfSlot1TextScale   = slot1TextScale,
                    wfSlot2TextScale   = slot2TextScale,
                    wfSlot3TextScale   = slot3TextScale,
                    wfSlot4TextScale   = slot4TextScale,
                    wfWeatherTextScale = weatherTextScale,
                    wfSunriseTextScale = sunriseTextScale,
                    wfWatchBatteryTextScale = watchBatteryTextScale,
                    wfBatteryRingColor1       = batteryRingColor1,
                    wfBatteryRingColor2       = batteryRingColor2,
                    wfBatteryRingStrokeScale  = batteryRingStrokeScale,
                    wfBatteryWarn1Color       = batteryWarn1Color,
                    wfBatteryWarn1Threshold   = batteryWarn1Threshold,
                    wfBatteryWarn2Color       = batteryWarn2Color,
                    wfBatteryWarn2Threshold   = batteryWarn2Threshold,
                    wfHealthDataSource = healthDataSource,
                    wfShowBackground   = showBackground,
                    wfHrColor      = hrColor,
                    wfKcalColor    = kcalColor,
                    wfOxygenColor  = oxygenColor,
                    wfStepsColor   = stepsColor,
                    wfSleepColor   = sleepColor,
                    wfSunriseColor = sunriseColor,
                    wfSlotColor    = slotColor,
                    wfWeatherTempSource = weatherTempSource,
                    wfWeatherIoBrokerId = weatherIoBrokerId
                )
            }
            try {
                pushFullConfigToWear()
                _uiState.update { it.copy(wearSyncLog = "Watchface-Konfiguration übertragen") }
            } catch (e: Exception) {
                _uiState.update { it.copy(wearSyncLog = "Fehler: ${e.message}") }
            }

            // Hintergrund-Sync-Service mit neuer Konfiguration neu starten
            IoSyncSyncService.start(context)

            // IoSync-States sofort an Watchface senden wenn gerade aktiviert
            if (showIoBrokerData && _uiState.value.ioSyncStates.isNotEmpty()) {
                wearDataLayerService.syncStatesToWear(_uiState.value.ioSyncStates)
            }
        }
    }

    // ── Boden-Komplikationen ──────────────────────────────────────────────────

    /**
     * Speichert die Konfiguration der beiden Boden-Komplikationen (BC1/BC2) und
     * überträgt sie an das Watchface.
     */
    fun setBottomCompConfig(
        showBottomComp: Boolean,
        bc1UseIoBroker: Boolean, bc1Id: String, bc1Label: String, bc1Color: String,
        bc1RingEnabled: Boolean, bc1RingColor1: String, bc1RingColor2: String,
        bc1RingMin: Float, bc1RingMax: Float, bc1RingWidth: Int,
        bc1RingThreshEnabled: Boolean, bc1RingThreshValue: Float,
        bc1RingThreshDir: String, bc1RingThreshTarget: String, bc1RingThreshColor: String,
        bc1TextScale: Int,
        bc2Metric: String,
        bc2UseIoBroker: Boolean, bc2Id: String, bc2Label: String, bc2Color: String,
        bc2RingEnabled: Boolean, bc2RingColor1: String, bc2RingColor2: String,
        bc2RingMin: Float, bc2RingMax: Float, bc2RingWidth: Int,
        bc2RingThreshEnabled: Boolean, bc2RingThreshValue: Float,
        bc2RingThreshDir: String, bc2RingThreshTarget: String, bc2RingThreshColor: String,
        bc2TextScale: Int
    ) {
        viewModelScope.launch {
            dataStore.edit { prefs ->
                prefs[KEY_WF_SHOW_BOTTOM_COMP]  = showBottomComp
                prefs[KEY_WF_BC1_USE_IOBROKER]  = bc1UseIoBroker
                prefs[KEY_WF_BC1_ID]            = bc1Id
                prefs[KEY_WF_BC1_LABEL]         = bc1Label
                prefs[KEY_WF_BC1_COLOR]         = bc1Color
                prefs[KEY_WF_BC1_RING_ENABLED]  = bc1RingEnabled
                prefs[KEY_WF_BC1_RING_COLOR1]   = bc1RingColor1
                prefs[KEY_WF_BC1_RING_COLOR2]   = bc1RingColor2
                prefs[KEY_WF_BC1_RING_MIN]      = bc1RingMin.toString()
                prefs[KEY_WF_BC1_RING_MAX]      = bc1RingMax.toString()
                prefs[KEY_WF_BC1_RING_WIDTH]    = bc1RingWidth
                prefs[KEY_WF_BC1_RING_TH_EN]    = bc1RingThreshEnabled
                prefs[KEY_WF_BC1_RING_TH_VAL]   = bc1RingThreshValue.toString()
                prefs[KEY_WF_BC1_RING_TH_DIR]   = bc1RingThreshDir
                prefs[KEY_WF_BC1_RING_TH_TGT]   = bc1RingThreshTarget
                prefs[KEY_WF_BC1_RING_TH_COLOR] = bc1RingThreshColor
                prefs[KEY_WF_BC1_TEXT_SCALE]    = bc1TextScale
                prefs[KEY_WF_BC2_METRIC]        = bc2Metric
                prefs[KEY_WF_BC2_USE_IOBROKER]  = bc2UseIoBroker
                prefs[KEY_WF_BC2_ID]            = bc2Id
                prefs[KEY_WF_BC2_LABEL]         = bc2Label
                prefs[KEY_WF_BC2_COLOR]         = bc2Color
                prefs[KEY_WF_BC2_RING_ENABLED]  = bc2RingEnabled
                prefs[KEY_WF_BC2_RING_COLOR1]   = bc2RingColor1
                prefs[KEY_WF_BC2_RING_COLOR2]   = bc2RingColor2
                prefs[KEY_WF_BC2_RING_MIN]      = bc2RingMin.toString()
                prefs[KEY_WF_BC2_RING_MAX]      = bc2RingMax.toString()
                prefs[KEY_WF_BC2_RING_WIDTH]    = bc2RingWidth
                prefs[KEY_WF_BC2_RING_TH_EN]    = bc2RingThreshEnabled
                prefs[KEY_WF_BC2_RING_TH_VAL]   = bc2RingThreshValue.toString()
                prefs[KEY_WF_BC2_RING_TH_DIR]   = bc2RingThreshDir
                prefs[KEY_WF_BC2_RING_TH_TGT]   = bc2RingThreshTarget
                prefs[KEY_WF_BC2_RING_TH_COLOR] = bc2RingThreshColor
                prefs[KEY_WF_BC2_TEXT_SCALE]    = bc2TextScale
            }
            _uiState.update {
                it.copy(
                    wfShowBottomComp  = showBottomComp,
                    wfBc1UseIoBroker  = bc1UseIoBroker, wfBc1Id = bc1Id,
                    wfBc1Label = bc1Label, wfBc1Color = bc1Color,
                    wfBc1RingEnabled = bc1RingEnabled,
                    wfBc1RingColor1 = bc1RingColor1, wfBc1RingColor2 = bc1RingColor2,
                    wfBc1RingMin = bc1RingMin, wfBc1RingMax = bc1RingMax,
                    wfBc1RingWidth = bc1RingWidth,
                    wfBc1RingThreshEnabled = bc1RingThreshEnabled,
                    wfBc1RingThreshValue = bc1RingThreshValue,
                    wfBc1RingThreshDir = bc1RingThreshDir,
                    wfBc1RingThreshTarget = bc1RingThreshTarget,
                    wfBc1RingThreshColor = bc1RingThreshColor,
                    wfBc1TextScale = bc1TextScale,
                    wfBc2Metric = bc2Metric,
                    wfBc2UseIoBroker = bc2UseIoBroker, wfBc2Id = bc2Id,
                    wfBc2Label = bc2Label, wfBc2Color = bc2Color,
                    wfBc2RingEnabled = bc2RingEnabled,
                    wfBc2RingColor1 = bc2RingColor1, wfBc2RingColor2 = bc2RingColor2,
                    wfBc2RingMin = bc2RingMin, wfBc2RingMax = bc2RingMax,
                    wfBc2RingWidth = bc2RingWidth,
                    wfBc2RingThreshEnabled = bc2RingThreshEnabled,
                    wfBc2RingThreshValue = bc2RingThreshValue,
                    wfBc2RingThreshDir = bc2RingThreshDir,
                    wfBc2RingThreshTarget = bc2RingThreshTarget,
                    wfBc2RingThreshColor = bc2RingThreshColor,
                    wfBc2TextScale = bc2TextScale
                )
            }
            try {
                pushFullConfigToWear()
                _uiState.update { it.copy(wearSyncLog = "Boden-Komplikationen übertragen") }
            } catch (e: Exception) {
                _uiState.update { it.copy(wearSyncLog = "Fehler: ${e.message}") }
            }
        }
    }

    // ── Klipper & Seite 3 ─────────────────────────────────────────────────────

    /**
     * Speichert nur die Klipper-Verbindungseinstellungen (Aktivierung, Host, Port)
     * und überträgt die Connection-Config an die Uhr.
     */
    fun saveKlipperConnection(enabled: Boolean, host: String, port: Int, apiKey: String = "") {
        viewModelScope.launch {
            dataStore.edit { prefs ->
                prefs[KEY_KLIPPER_ENABLED] = enabled
                prefs[KEY_KLIPPER_HOST]    = host
                prefs[KEY_KLIPPER_PORT]    = port
                prefs[KEY_KLIPPER_API_KEY] = apiKey
            }
            _uiState.update { it.copy(klipperEnabled = enabled, klipperHost = host, klipperPort = port, klipperApiKey = apiKey) }
            try {
                pushConnectionConfigToWear()
                _uiState.update { it.copy(wearSyncLog = "Klipper-Verbindung gespeichert") }
            } catch (e: Exception) {
                _uiState.update { it.copy(wearSyncLog = "Fehler: ${e.message}") }
            }
        }
    }

    /**
     * Speichert Klipper-Verbindung + Seite-3-Konfiguration und überträgt die Connection-Config.
     */
    fun setKlipperAndP3PillConfig(
        klipperEnabled: Boolean,
        klipperHost: String,
        klipperPort: Int,
        klipperApiKey: String,
        klipperChamberObject: String,
        klipperIntervalSec: Int,
        p3PillEnabled: Boolean,
        p3PillColorTrue: String,
        p3PillColorFalse: String,
        p3PillObject: String,
        p3PillField: String,
        p3PillGcodeOn: String,
        p3PillGcodeOff: String,
        klipperLedGcodeOn: String,
        klipperLedGcodeOff: String,
        klipperLedObject: String,
        klipperLedField: String,
        klipperChamberHeatGcodeOn: String,
        klipperChamberHeatGcodeOff: String
    ) {
        viewModelScope.launch {
            dataStore.edit { prefs ->
                prefs[KEY_KLIPPER_ENABLED]     = klipperEnabled
                prefs[KEY_KLIPPER_HOST]        = klipperHost
                prefs[KEY_KLIPPER_PORT]        = klipperPort
                prefs[KEY_KLIPPER_API_KEY]     = klipperApiKey
                prefs[KEY_KLIPPER_CHAMBER_OBJ] = klipperChamberObject
                prefs[KEY_KLIPPER_INTERVAL]    = klipperIntervalSec
                prefs[KEY_P3_PILL_ENABLED]     = p3PillEnabled
                prefs[KEY_P3_PILL_COLOR_TRUE]  = p3PillColorTrue
                prefs[KEY_P3_PILL_COLOR_FALSE] = p3PillColorFalse
                prefs[KEY_P3_PILL_OBJECT]      = p3PillObject
                prefs[KEY_P3_PILL_FIELD]       = p3PillField
                prefs[KEY_P3_PILL_GCODE_ON]    = p3PillGcodeOn
                prefs[KEY_P3_PILL_GCODE_OFF]   = p3PillGcodeOff
                prefs[KEY_KLIPPER_LED_GCODE_ON]  = klipperLedGcodeOn
                prefs[KEY_KLIPPER_LED_GCODE_OFF] = klipperLedGcodeOff
                prefs[KEY_KLIPPER_LED_OBJECT]    = klipperLedObject
                prefs[KEY_KLIPPER_LED_FIELD]     = klipperLedField
                prefs[KEY_KLIPPER_HEAT_GCODE_ON]  = klipperChamberHeatGcodeOn
                prefs[KEY_KLIPPER_HEAT_GCODE_OFF] = klipperChamberHeatGcodeOff
            }
            _uiState.update {
                it.copy(
                    klipperEnabled    = klipperEnabled,
                    klipperHost       = klipperHost,
                    klipperPort       = klipperPort,
                    klipperApiKey     = klipperApiKey,
                    klipperChamberObject = klipperChamberObject,
                    klipperIntervalSec = klipperIntervalSec,
                    p3PillEnabled     = p3PillEnabled,
                    p3PillColorTrue   = p3PillColorTrue,
                    p3PillColorFalse  = p3PillColorFalse,
                    p3PillObject      = p3PillObject,
                    p3PillField       = p3PillField,
                    p3PillGcodeOn     = p3PillGcodeOn,
                    p3PillGcodeOff    = p3PillGcodeOff,
                    klipperLedGcodeOn  = klipperLedGcodeOn,
                    klipperLedGcodeOff = klipperLedGcodeOff,
                    klipperLedObject   = klipperLedObject,
                    klipperLedField    = klipperLedField,
                    klipperChamberHeatGcodeOn  = klipperChamberHeatGcodeOn,
                    klipperChamberHeatGcodeOff = klipperChamberHeatGcodeOff
                )
            }
            try {
                pushConnectionConfigToWear()
                _uiState.update { it.copy(wearSyncLog = "Klipper-Konfig übertragen") }
            } catch (e: Exception) {
                _uiState.update { it.copy(wearSyncLog = "Fehler: ${e.message}") }
            }
        }
    }

    /**
     * Ruft die verfügbaren Drucker-Objekte von Moonraker ab (für die UI-Auswahl).
     */
    fun loadKlipperObjects(host: String, port: Int, apiKey: String = "") {
        viewModelScope.launch {
            _uiState.update { it.copy(klipperObjectsLoading = true, klipperObjectsError = null) }
            klipperClient.fetchObjects(host, port, apiKey)
                .onSuccess { objects ->
                    _uiState.update { it.copy(klipperObjects = objects, klipperObjectsLoading = false) }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(klipperObjectsError = e.message ?: "Verbindung fehlgeschlagen", klipperObjectsLoading = false) }
                }
        }
    }

    /** Schaltet die Seite-3-Pille (Klipper) von der App aus (Vorschau/Test). */
    fun toggleP3PillFromApp() {
        viewModelScope.launch {
            val s = _uiState.value
            if (s.klipperHost.isBlank()) return@launch
            val gcode = if (s.p3PillState) s.p3PillGcodeOff else s.p3PillGcodeOn
            if (gcode.isBlank()) return@launch
            val newState = !s.p3PillState
            _uiState.update { it.copy(p3PillState = newState) }
            dataStore.edit { prefs -> prefs[KEY_P3_PILL_STATE] = newState }
        }
    }

    // ── Aktions-Pille ─────────────────────────────────────────────────────────

    /**
     * Speichert die Konfiguration der Aktions-Pille und überträgt sie an das Watchface.
     */
    fun updateActionPillConfig(
        enabled: Boolean,
        colorTrue: String,
        colorFalse: String,
        ioBrokerId: String,
        valueMode: String,
        fixedValue: String
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(wearSyncLog = "Sende Aktions-Pille-Konfiguration …") }
            dataStore.edit { prefs ->
                prefs[KEY_ACTION_PILL_ENABLED]     = enabled
                prefs[KEY_ACTION_PILL_COLOR_TRUE]  = colorTrue
                prefs[KEY_ACTION_PILL_COLOR_FALSE] = colorFalse
                prefs[KEY_ACTION_PILL_IOBROKER_ID] = ioBrokerId
                prefs[KEY_ACTION_PILL_VALUE_MODE]  = valueMode
                prefs[KEY_ACTION_PILL_FIXED_VALUE] = fixedValue
            }
            _uiState.update {
                it.copy(
                    actionPillEnabled    = enabled,
                    actionPillColorTrue  = colorTrue,
                    actionPillColorFalse = colorFalse,
                    actionPillIoBrokerId = ioBrokerId,
                    actionPillValueMode  = valueMode,
                    actionPillFixedValue = fixedValue
                )
            }
            try {
                pushFullConfigToWear()
                _uiState.update { it.copy(wearSyncLog = "Aktions-Pille-Konfiguration übertragen") }
            } catch (e: Exception) {
                _uiState.update { it.copy(wearSyncLog = "Fehler: ${e.message}") }
            }
        }
    }

    /**
     * Live-Vorschau der Aktions-Pille: nur In-Memory-State + Senden, keine Persistenz.
     */
    fun previewActionPillConfig(
        enabled: Boolean,
        colorTrue: String,
        colorFalse: String,
        ioBrokerId: String,
        valueMode: String,
        fixedValue: String
    ) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    actionPillEnabled    = enabled,
                    actionPillColorTrue  = colorTrue,
                    actionPillColorFalse = colorFalse,
                    actionPillIoBrokerId = ioBrokerId,
                    actionPillValueMode  = valueMode,
                    actionPillFixedValue = fixedValue
                )
            }
            try {
                pushFullConfigToWear()
            } catch (e: Exception) {
                _uiState.update { it.copy(wearSyncLog = "Fehler: ${e.message}") }
            }
        }
    }

    /**
     * Wird vom WatchFaceTriggerListenerService aufgerufen, nachdem ein ioBroker-Befehl
     * ausgeführt wurde. Aktualisiert den lokalen Pille-Status und sendet ihn ans Watchface.
     */
    fun updateActionPillState(newState: Boolean) {
        viewModelScope.launch {
            dataStore.edit { prefs -> prefs[KEY_ACTION_PILL_STATE] = newState }
            _uiState.update { it.copy(actionPillState = newState) }
            wearDataLayerService.syncActionPillStateToWear(newState)
        }
    }

    fun updateP2Pill1State(newState: Boolean) {
        viewModelScope.launch {
            val ioBrokerId = _uiState.value.p2PillIoBrokerId
            dataStore.edit { prefs -> prefs[KEY_P2_PILL1_STATE] = newState }
            val newValue = if (newState) "true" else "false"
            _uiState.update { st ->
                st.copy(
                    p2Pill1State = newState,
                    ioSyncStates = if (ioBrokerId.isNotBlank())
                        st.ioSyncStates.map { if (it.id == ioBrokerId) it.copy(value = newValue) else it }
                    else st.ioSyncStates
                )
            }
            wearDataLayerService.syncP2PillStatesToWear(newState, _uiState.value.p2Pill2State)
            syncPage2SlotValues()
        }
    }

    fun updateP2Pill2State(newState: Boolean) {
        viewModelScope.launch {
            val ioBrokerId = _uiState.value.p2Pill2IoBrokerId
            dataStore.edit { prefs -> prefs[KEY_P2_PILL2_STATE] = newState }
            val newValue = if (newState) "true" else "false"
            _uiState.update { st ->
                st.copy(
                    p2Pill2State = newState,
                    ioSyncStates = if (ioBrokerId.isNotBlank())
                        st.ioSyncStates.map { if (it.id == ioBrokerId) it.copy(value = newValue) else it }
                    else st.ioSyncStates
                )
            }
            wearDataLayerService.syncP2PillStatesToWear(_uiState.value.p2Pill1State, newState)
            syncPage2SlotValues()
        }
    }

    /** Wählt automatisch Simple-API oder IoSync je nach aktueller Konfiguration. */
    fun setStateValueSmart(id: String, value: String) {
        val s = _uiState.value
        if (s.useIoSyncAdapter && s.ioSyncHost.isNotBlank()) {
            setStateValueViaIoSync(id, value)
        } else {
            setStateValue(id, value)
        }
    }

    /** Sendet Pille-1-Aktion (Seite 2) direkt aus der App an ioBroker. */
    fun toggleP2Pill1FromApp() {
        viewModelScope.launch {
            val s = _uiState.value
            if (!s.p2PillEnabled) return@launch
            val ioBrokerId = s.p2PillIoBrokerId
            if (ioBrokerId.isBlank()) {
                _uiState.update { it.copy(error = "Pille 1: Kein ioBroker-Datenpunkt konfiguriert") }
                return@launch
            }
            if (s.ioSyncHost.isBlank()) {
                _uiState.update { it.copy(error = "Pille 1: IoSync Adapter nicht konfiguriert") }
                return@launch
            }
            val currentState = s.p2Pill1State
            val valueToSend = when (s.p2PillValueMode) {
                "true"  -> "true"
                "false" -> "false"
                "fixed" -> s.p2PillFixedValue
                else    -> if (currentState) "false" else "true"
            }
            ioSyncClient.setState(s.ioSyncHost, s.ioSyncPort, s.ioSyncUseHttps, s.ioSyncUsername, s.ioSyncPassword, ioBrokerId, valueToSend)
                .onSuccess {
                    val newState = when (s.p2PillValueMode) {
                        "toggle" -> !currentState; "true" -> true; "false" -> false; else -> currentState
                    }
                    dataStore.edit { p -> p[KEY_P2_PILL1_STATE] = newState }
                    val newValue = if (newState) "true" else "false"
                    _uiState.update { st ->
                        st.copy(
                            p2Pill1State = newState,
                            ioSyncStates = st.ioSyncStates.map { if (it.id == ioBrokerId) it.copy(value = newValue) else it }
                        )
                    }
                    wearDataLayerService.syncP2PillStatesToWear(newState, _uiState.value.p2Pill2State)
                    syncPage2SlotValues()
                }
                .onFailure { err -> _uiState.update { st -> st.copy(error = "Pille 1: ${err.message}") } }
        }
    }

    /** Sendet Pille-2-Aktion (Seite 2) direkt aus der App an ioBroker. */
    fun toggleP2Pill2FromApp() {
        viewModelScope.launch {
            val s = _uiState.value
            if (!s.p2Pill2Enabled) return@launch
            val ioBrokerId = s.p2Pill2IoBrokerId
            if (ioBrokerId.isBlank()) {
                _uiState.update { it.copy(error = "Pille 2: Kein ioBroker-Datenpunkt konfiguriert") }
                return@launch
            }
            if (s.ioSyncHost.isBlank()) {
                _uiState.update { it.copy(error = "Pille 2: IoSync Adapter nicht konfiguriert") }
                return@launch
            }
            val currentState = s.p2Pill2State
            val valueToSend = when (s.p2Pill2ValueMode) {
                "true"  -> "true"
                "false" -> "false"
                "fixed" -> s.p2Pill2FixedValue
                else    -> if (currentState) "false" else "true"
            }
            ioSyncClient.setState(s.ioSyncHost, s.ioSyncPort, s.ioSyncUseHttps, s.ioSyncUsername, s.ioSyncPassword, ioBrokerId, valueToSend)
                .onSuccess {
                    val newState = when (s.p2Pill2ValueMode) {
                        "toggle" -> !currentState; "true" -> true; "false" -> false; else -> currentState
                    }
                    dataStore.edit { p -> p[KEY_P2_PILL2_STATE] = newState }
                    val newValue = if (newState) "true" else "false"
                    _uiState.update { st ->
                        st.copy(
                            p2Pill2State = newState,
                            ioSyncStates = st.ioSyncStates.map { if (it.id == ioBrokerId) it.copy(value = newValue) else it }
                        )
                    }
                    wearDataLayerService.syncP2PillStatesToWear(_uiState.value.p2Pill1State, newState)
                    syncPage2SlotValues()
                }
                .onFailure { err -> _uiState.update { st -> st.copy(error = "Pille 2: ${err.message}") } }
        }
    }

    // ── Aktualisierungsintervalle ─────────────────────────────────────────────

    /** Speichert die Polling-Intervalle und startet die Jobs mit neuem Intervall neu. */
    fun updatePollIntervals(batterySec: Int, slotSec: Int, healthSec: Int = _uiState.value.healthPollIntervalSec, page2Sec: Int = _uiState.value.page2SyncIntervalSec) {
        viewModelScope.launch {
            dataStore.edit { prefs ->
                prefs[KEY_BATTERY_POLL_INTERVAL] = batterySec
                prefs[KEY_SLOT_POLL_INTERVAL]    = slotSec
                prefs[KEY_HEALTH_POLL_INTERVAL]  = healthSec
                prefs[KEY_PAGE2_SYNC_INTERVAL]   = page2Sec
            }
            _uiState.update { it.copy(batteryPollIntervalSec = batterySec, slotPollIntervalSec = slotSec, healthPollIntervalSec = healthSec, page2SyncIntervalSec = page2Sec) }
            // Hintergrund-Sync-Service mit neuen Intervallen neu starten
            IoSyncSyncService.start(context)
            // UI-Liste mit neuem Intervall neu pollen
            if (ioSyncPollingJob?.isActive == true) {
                val s = _uiState.value
                if (s.ioSyncHost.isNotBlank()) startIoSyncPolling(s.ioSyncHost, s.ioSyncPort, s.ioSyncUseHttps, s.ioSyncUsername, s.ioSyncPassword)
            }
        }
    }

    // ── Custom ioBroker-Slots ────────────────────────────────────────────────

    /**
     * Speichert die Konfiguration der Custom-Slots und überträgt sie an die Uhr.
     */
    fun updateCustomSlotsConfig(
        enabled: Boolean,
        slot1Id: String,
        slot1Label: String,
        slot2Id: String,
        slot2Label: String,
        slot3Id: String = "",
        slot3Label: String = "",
        slot4Id: String = "",
        slot4Label: String = "",
        slot4BarColor: String = "neon_yellow",
        slot4BarMin: Float = 0f,
        slot4BarMax: Float = 100f,
        slot4BarShowLabel: Boolean = true,
        slot4BarIsSlider: Boolean = false,
        slot1TextScale: Int = _uiState.value.wfSlot1TextScale,
        slot2TextScale: Int = _uiState.value.wfSlot2TextScale,
        slot3TextScale: Int = _uiState.value.wfSlot3TextScale,
        slot4TextScale: Int = _uiState.value.wfSlot4TextScale,
        slot4Warn1Color: String = _uiState.value.customSlot4Warn1Color,
        slot4Warn1Value: Float = _uiState.value.customSlot4Warn1Value,
        slot4Warn2Color: String = _uiState.value.customSlot4Warn2Color,
        slot4Warn2Value: Float = _uiState.value.customSlot4Warn2Value,
        slot4UseKlipper: Boolean = _uiState.value.customSlot4UseKlipper,
        slot4KlipperSource: String = _uiState.value.customSlot4KlipperSource,
        slot4KlipperColorActive: String = _uiState.value.customSlot4KlipperColorActive
    ) {
        viewModelScope.launch {
            dataStore.edit { prefs ->
                prefs[KEY_SHOW_CUSTOM_SLOTS]  = enabled
                prefs[KEY_CUSTOM_SLOT1_ID]    = slot1Id
                prefs[KEY_CUSTOM_SLOT1_LABEL] = slot1Label
                prefs[KEY_CUSTOM_SLOT2_ID]    = slot2Id
                prefs[KEY_CUSTOM_SLOT2_LABEL] = slot2Label
                prefs[KEY_CUSTOM_SLOT3_ID]    = slot3Id
                prefs[KEY_CUSTOM_SLOT3_LABEL] = slot3Label
                prefs[KEY_CUSTOM_SLOT4_ID]    = slot4Id
                prefs[KEY_CUSTOM_SLOT4_LABEL] = slot4Label
                prefs[KEY_CUSTOM_SLOT4_BAR_COLOR]      = slot4BarColor
                prefs[KEY_CUSTOM_SLOT4_BAR_MIN]        = slot4BarMin.toString()
                prefs[KEY_CUSTOM_SLOT4_BAR_MAX]        = slot4BarMax.toString()
                prefs[KEY_CUSTOM_SLOT4_BAR_SHOW_LABEL] = slot4BarShowLabel
                prefs[KEY_CUSTOM_SLOT4_BAR_IS_SLIDER]      = slot4BarIsSlider
                prefs[KEY_CUSTOM_SLOT4_USE_KLIPPER]        = slot4UseKlipper
                prefs[KEY_CUSTOM_SLOT4_KLIPPER_SOURCE]     = slot4KlipperSource
                prefs[KEY_CUSTOM_SLOT4_KLIPPER_COLOR_ACT]  = slot4KlipperColorActive
                prefs[KEY_CUSTOM_SLOT4_WARN1_COLOR] = slot4Warn1Color
                prefs[KEY_CUSTOM_SLOT4_WARN1_VALUE] = if (slot4Warn1Value.isNaN()) "" else slot4Warn1Value.toString()
                prefs[KEY_CUSTOM_SLOT4_WARN2_COLOR] = slot4Warn2Color
                prefs[KEY_CUSTOM_SLOT4_WARN2_VALUE] = if (slot4Warn2Value.isNaN()) "" else slot4Warn2Value.toString()
                prefs[KEY_WF_SLOT1_TEXT_SCALE] = slot1TextScale
                prefs[KEY_WF_SLOT2_TEXT_SCALE] = slot2TextScale
                prefs[KEY_WF_SLOT3_TEXT_SCALE] = slot3TextScale
                prefs[KEY_WF_SLOT4_TEXT_SCALE] = slot4TextScale
            }
            _uiState.update {
                it.copy(
                    showCustomSlots  = enabled,
                    customSlot1Id    = slot1Id,
                    customSlot1Label = slot1Label,
                    customSlot2Id    = slot2Id,
                    customSlot2Label = slot2Label,
                    customSlot3Id    = slot3Id,
                    customSlot3Label = slot3Label,
                    customSlot4Id    = slot4Id,
                    customSlot4Label = slot4Label,
                    customSlot4BarColor      = slot4BarColor,
                    customSlot4BarMin        = slot4BarMin,
                    customSlot4BarMax        = slot4BarMax,
                    customSlot4BarShowLabel  = slot4BarShowLabel,
                    customSlot4BarIsSlider        = slot4BarIsSlider,
                    customSlot4UseKlipper         = slot4UseKlipper,
                    customSlot4KlipperSource      = slot4KlipperSource,
                    customSlot4KlipperColorActive = slot4KlipperColorActive,
                    customSlot4Warn1Color    = slot4Warn1Color,
                    customSlot4Warn1Value    = slot4Warn1Value,
                    customSlot4Warn2Color    = slot4Warn2Color,
                    customSlot4Warn2Value    = slot4Warn2Value,
                    wfSlot1TextScale = slot1TextScale,
                    wfSlot2TextScale = slot2TextScale,
                    wfSlot3TextScale = slot3TextScale,
                    wfSlot4TextScale = slot4TextScale
                )
            }
            // Sofort Werte senden falls Daten vorhanden
            if (enabled) {
                if (!wearDataLayerService.isWatchConnected()) {
                    _uiState.update { it.copy(wearSyncLog = "Fehler: Keine Uhr verbunden") }
                    return@launch
                }
                _uiState.update { it.copy(wearSyncLog = "Sende Slot-Daten …") }
                syncCustomSlotValues()
                pushFullConfigToWear()
                _uiState.update { it.copy(wearSyncLog = "Slot-Daten übertragen") }
            }
        }
    }

    /**
     * Live-Vorschau der Custom-Slots: nur In-Memory-State + Senden, keine Persistenz.
     */
    fun previewCustomSlotsConfig(
        enabled: Boolean,
        slot1Id: String,
        slot1Label: String,
        slot2Id: String,
        slot2Label: String,
        slot3Id: String = "",
        slot3Label: String = "",
        slot4Id: String = "",
        slot4Label: String = "",
        slot4BarColor: String = "neon_yellow",
        slot4BarMin: Float = 0f,
        slot4BarMax: Float = 100f,
        slot4BarShowLabel: Boolean = true,
        slot4BarIsSlider: Boolean = false,
        slot1TextScale: Int = _uiState.value.wfSlot1TextScale,
        slot2TextScale: Int = _uiState.value.wfSlot2TextScale,
        slot3TextScale: Int = _uiState.value.wfSlot3TextScale,
        slot4TextScale: Int = _uiState.value.wfSlot4TextScale,
        slot4Warn1Color: String = _uiState.value.customSlot4Warn1Color,
        slot4Warn1Value: Float = _uiState.value.customSlot4Warn1Value,
        slot4Warn2Color: String = _uiState.value.customSlot4Warn2Color,
        slot4Warn2Value: Float = _uiState.value.customSlot4Warn2Value,
        slot4UseKlipper: Boolean = _uiState.value.customSlot4UseKlipper,
        slot4KlipperSource: String = _uiState.value.customSlot4KlipperSource,
        slot4KlipperColorActive: String = _uiState.value.customSlot4KlipperColorActive
    ) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    showCustomSlots  = enabled,
                    customSlot1Id    = slot1Id,
                    customSlot1Label = slot1Label,
                    customSlot2Id    = slot2Id,
                    customSlot2Label = slot2Label,
                    customSlot3Id    = slot3Id,
                    customSlot3Label = slot3Label,
                    customSlot4Id    = slot4Id,
                    customSlot4Label = slot4Label,
                    customSlot4BarColor      = slot4BarColor,
                    customSlot4BarMin        = slot4BarMin,
                    customSlot4BarMax        = slot4BarMax,
                    customSlot4BarShowLabel  = slot4BarShowLabel,
                    customSlot4BarIsSlider        = slot4BarIsSlider,
                    customSlot4UseKlipper         = slot4UseKlipper,
                    customSlot4KlipperSource      = slot4KlipperSource,
                    customSlot4KlipperColorActive = slot4KlipperColorActive,
                    customSlot4Warn1Color    = slot4Warn1Color,
                    customSlot4Warn1Value    = slot4Warn1Value,
                    customSlot4Warn2Color    = slot4Warn2Color,
                    customSlot4Warn2Value    = slot4Warn2Value,
                    wfSlot1TextScale = slot1TextScale,
                    wfSlot2TextScale = slot2TextScale,
                    wfSlot3TextScale = slot3TextScale,
                    wfSlot4TextScale = slot4TextScale
                )
            }
            try {
                if (enabled) syncCustomSlotValues()
                pushFullConfigToWear()
            } catch (e: Exception) {
                _uiState.update { it.copy(wearSyncLog = "Fehler: ${e.message}") }
            }
        }
    }

    /**
     * Liest die aktuellen Werte für die Custom-Slots aus den ioBroker-States
     * und sendet sie an die Uhr.
     */
    private suspend fun syncCustomSlotValues() {
        val s = _uiState.value
        if (!s.showCustomSlots) return
        val states = s.ioSyncStates.ifEmpty { s.states }

        val val1 = states.firstOrNull { it.id == s.customSlot1Id }
        val val2 = states.firstOrNull { it.id == s.customSlot2Id }
        val val3 = states.firstOrNull { it.id == s.customSlot3Id }
        val val4 = states.firstOrNull { it.id == s.customSlot4Id }

        wearDataLayerService.syncCustomSlotsToWear(
            s.customSlot1Label, formatSlotValue(val1?.value),
            s.customSlot2Label, formatSlotValue(val2?.value),
            s.customSlot3Label, formatSlotValue(val3?.value),
            s.customSlot4Label, formatSlotValue(val4?.value),
            s.customSlot4BarColor, s.customSlot4BarMin, s.customSlot4BarMax, s.customSlot4BarShowLabel,
            s.customSlot4BarIsSlider,
            s.customSlot4Warn1Color, s.customSlot4Warn1Value,
            s.customSlot4Warn2Color, s.customSlot4Warn2Value
        )
    }

    // ── Seite-2-Konfiguration ────────────────────────────────────────────────

    /**
     * Speichert die Seite-2-Konfiguration (4 Slots + 2 Pillen) und überträgt sie an die Uhr.
     */
    fun updatePage2Config(
        slot1Id: String, slot1Label: String,
        slot2Id: String, slot2Label: String,
        slot3Id: String, slot3Label: String,
        slot4Id: String, slot4Label: String,
        slot1TextScale: Int = _uiState.value.p2Slot1TextScale,
        slot2TextScale: Int = _uiState.value.p2Slot2TextScale,
        slot3TextScale: Int = _uiState.value.p2Slot3TextScale,
        slot4TextScale: Int = _uiState.value.p2Slot4TextScale,
        sleepTextScale: Int = _uiState.value.wfSleepTextScale,
        sleepSource: String = _uiState.value.wfSleepSource,
        sleepIoBrokerId: String = _uiState.value.wfSleepIoBrokerId,
        sleepComplication: String = _uiState.value.wfSleepComplication,
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
        p2BarId: String = _uiState.value.p2BarId,
        p2BarLabel: String = _uiState.value.p2BarLabel,
        p2BarColor: String = _uiState.value.p2BarColor,
        p2BarMin: Float = _uiState.value.p2BarMin,
        p2BarMax: Float = _uiState.value.p2BarMax,
        p2BarShowLabel: Boolean = _uiState.value.p2BarShowLabel,
        p2BarIsSlider: Boolean = _uiState.value.p2BarIsSlider,
        p2BarTextScale: Int = _uiState.value.p2BarTextScale,
        p2BarWarn1Color: String = _uiState.value.p2BarWarn1Color,
        p2BarWarn1Value: Float = _uiState.value.p2BarWarn1Value,
        p2BarWarn2Color: String = _uiState.value.p2BarWarn2Color,
        p2BarWarn2Value: Float = _uiState.value.p2BarWarn2Value,
        p2ShowBackground: Boolean = _uiState.value.p2ShowBackground
    ) {
        viewModelScope.launch {
            dataStore.edit { prefs ->
                prefs[KEY_P2_SLOT1_ID]    = slot1Id
                prefs[KEY_P2_SLOT1_LABEL] = slot1Label
                prefs[KEY_P2_SLOT2_ID]    = slot2Id
                prefs[KEY_P2_SLOT2_LABEL] = slot2Label
                prefs[KEY_P2_SLOT3_ID]    = slot3Id
                prefs[KEY_P2_SLOT3_LABEL] = slot3Label
                prefs[KEY_P2_SLOT4_ID]    = slot4Id
                prefs[KEY_P2_SLOT4_LABEL] = slot4Label
                prefs[KEY_P2_SLOT1_TEXT_SCALE] = slot1TextScale
                prefs[KEY_P2_SLOT2_TEXT_SCALE] = slot2TextScale
                prefs[KEY_P2_SLOT3_TEXT_SCALE] = slot3TextScale
                prefs[KEY_P2_SLOT4_TEXT_SCALE] = slot4TextScale
                prefs[KEY_WF_SLEEP_TEXT_SCALE]   = sleepTextScale
                prefs[KEY_WF_SLEEP_SOURCE]       = sleepSource
                prefs[KEY_WF_SLEEP_IOBROKER_ID]  = sleepIoBrokerId
                prefs[KEY_WF_SLEEP_COMPLICATION] = sleepComplication
                prefs[KEY_P2_PILL_ENABLED]       = p2PillEnabled
                prefs[KEY_P2_PILL_COLOR_TRUE]   = p2PillColorTrue
                prefs[KEY_P2_PILL_COLOR_FALSE]  = p2PillColorFalse
                prefs[KEY_P2_PILL_IOBROKER_ID]  = p2PillIoBrokerId
                prefs[KEY_P2_PILL_VALUE_MODE]   = p2PillValueMode
                prefs[KEY_P2_PILL_FIXED_VALUE]  = p2PillFixedValue
                prefs[KEY_P2_PILL2_ENABLED]     = p2Pill2Enabled
                prefs[KEY_P2_PILL2_COLOR_TRUE]  = p2Pill2ColorTrue
                prefs[KEY_P2_PILL2_COLOR_FALSE] = p2Pill2ColorFalse
                prefs[KEY_P2_PILL2_IOBROKER_ID] = p2Pill2IoBrokerId
                prefs[KEY_P2_PILL2_VALUE_MODE]  = p2Pill2ValueMode
                prefs[KEY_P2_PILL2_FIXED_VALUE] = p2Pill2FixedValue
                prefs[KEY_P2_BAR_ID]          = p2BarId
                prefs[KEY_P2_BAR_LABEL]       = p2BarLabel
                prefs[KEY_P2_BAR_COLOR]       = p2BarColor
                prefs[KEY_P2_BAR_MIN]         = p2BarMin.toString()
                prefs[KEY_P2_BAR_MAX]         = p2BarMax.toString()
                prefs[KEY_P2_BAR_SHOW_LABEL]  = p2BarShowLabel
                prefs[KEY_P2_BAR_IS_SLIDER]   = p2BarIsSlider
                prefs[KEY_P2_BAR_TEXT_SCALE]  = p2BarTextScale
                prefs[KEY_P2_BAR_WARN1_COLOR] = p2BarWarn1Color
                prefs[KEY_P2_BAR_WARN1_VALUE] = p2BarWarn1Value.toString()
                prefs[KEY_P2_BAR_WARN2_COLOR] = p2BarWarn2Color
                prefs[KEY_P2_BAR_WARN2_VALUE] = p2BarWarn2Value.toString()
                prefs[KEY_P2_SHOW_BACKGROUND] = p2ShowBackground
            }
            _uiState.update {
                it.copy(
                    p2Slot1Id    = slot1Id,    p2Slot1Label = slot1Label,
                    p2Slot2Id    = slot2Id,    p2Slot2Label = slot2Label,
                    p2Slot3Id    = slot3Id,    p2Slot3Label = slot3Label,
                    p2Slot4Id    = slot4Id,    p2Slot4Label = slot4Label,
                    p2Slot1TextScale = slot1TextScale, p2Slot2TextScale = slot2TextScale,
                    p2Slot3TextScale = slot3TextScale, p2Slot4TextScale = slot4TextScale,
                    wfSleepTextScale    = sleepTextScale,
                    wfSleepSource       = sleepSource,
                    wfSleepIoBrokerId   = sleepIoBrokerId,
                    wfSleepComplication = sleepComplication,
                    p2PillEnabled    = p2PillEnabled,
                    p2PillColorTrue  = p2PillColorTrue,
                    p2PillColorFalse = p2PillColorFalse,
                    p2PillIoBrokerId = p2PillIoBrokerId,
                    p2PillValueMode  = p2PillValueMode,
                    p2PillFixedValue = p2PillFixedValue,
                    p2Pill2Enabled    = p2Pill2Enabled,
                    p2Pill2ColorTrue  = p2Pill2ColorTrue,
                    p2Pill2ColorFalse = p2Pill2ColorFalse,
                    p2Pill2IoBrokerId = p2Pill2IoBrokerId,
                    p2Pill2ValueMode  = p2Pill2ValueMode,
                    p2Pill2FixedValue = p2Pill2FixedValue,
                    p2BarId          = p2BarId,
                    p2BarLabel       = p2BarLabel,
                    p2BarColor       = p2BarColor,
                    p2BarMin         = p2BarMin,
                    p2BarMax         = p2BarMax,
                    p2BarShowLabel   = p2BarShowLabel,
                    p2BarIsSlider    = p2BarIsSlider,
                    p2BarTextScale   = p2BarTextScale,
                    p2BarWarn1Color  = p2BarWarn1Color,
                    p2BarWarn1Value  = p2BarWarn1Value,
                    p2BarWarn2Color  = p2BarWarn2Color,
                    p2BarWarn2Value  = p2BarWarn2Value,
                    p2ShowBackground = p2ShowBackground
                )
            }
            _uiState.update { it.copy(wearSyncLog = "Sende Seite-2-Konfig …") }
            try {
                pushPage2ConfigToWear()
                _uiState.update { it.copy(wearSyncLog = "Seite-2-Konfig übertragen") }
            } catch (e: Exception) {
                _uiState.update { it.copy(wearSyncLog = "Fehler: ${e.message}") }
            }
        }
    }

    /**
     * Überträgt die vollständige Seite-2-Konfiguration aus dem aktuellen [_uiState]
     * an die Uhr (inkl. Slot-Werte). Persistiert NICHTS.
     */
    private suspend fun pushPage2ConfigToWear() {
        if (!wearDataLayerService.isWatchConnected()) {
            _uiState.update { it.copy(wearSyncLog = "Fehler: Keine Uhr verbunden") }
            return
        }
        val s = _uiState.value
        wearDataLayerService.syncPage2ConfigToWear(
            s.p2PillEnabled, s.p2PillColorTrue, s.p2PillColorFalse,
            s.p2PillIoBrokerId, s.p2PillValueMode, s.p2PillFixedValue,
            s.p2Pill2Enabled, s.p2Pill2ColorTrue, s.p2Pill2ColorFalse,
            s.p2Pill2IoBrokerId, s.p2Pill2ValueMode, s.p2Pill2FixedValue,
            s.p2Slot1TextScale, s.p2Slot2TextScale, s.p2Slot3TextScale, s.p2Slot4TextScale, s.wfSleepTextScale,
            s.wfSleepSource, s.wfSleepComplication,
            s.p2BarLabel, s.p2BarColor, s.p2BarMin, s.p2BarMax, s.p2BarShowLabel, s.p2BarIsSlider, s.p2BarTextScale,
            s.p2BarWarn1Color, s.p2BarWarn1Value, s.p2BarWarn2Color, s.p2BarWarn2Value,
            s.p2ShowBackground
        )
        pushConnectionConfigToWear()
        syncPage2SlotValues()
    }

    /**
     * Live-Vorschau der Seite-2-Konfiguration: nur In-Memory-State + Senden, keine Persistenz.
     */
    fun previewPage2Config(
        slot1Id: String, slot1Label: String,
        slot2Id: String, slot2Label: String,
        slot3Id: String, slot3Label: String,
        slot4Id: String, slot4Label: String,
        slot1TextScale: Int = _uiState.value.p2Slot1TextScale,
        slot2TextScale: Int = _uiState.value.p2Slot2TextScale,
        slot3TextScale: Int = _uiState.value.p2Slot3TextScale,
        slot4TextScale: Int = _uiState.value.p2Slot4TextScale,
        sleepTextScale: Int = _uiState.value.wfSleepTextScale,
        sleepSource: String = _uiState.value.wfSleepSource,
        sleepIoBrokerId: String = _uiState.value.wfSleepIoBrokerId,
        sleepComplication: String = _uiState.value.wfSleepComplication,
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
        p2BarId: String = _uiState.value.p2BarId,
        p2BarLabel: String = _uiState.value.p2BarLabel,
        p2BarColor: String = _uiState.value.p2BarColor,
        p2BarMin: Float = _uiState.value.p2BarMin,
        p2BarMax: Float = _uiState.value.p2BarMax,
        p2BarShowLabel: Boolean = _uiState.value.p2BarShowLabel,
        p2BarIsSlider: Boolean = _uiState.value.p2BarIsSlider,
        p2BarTextScale: Int = _uiState.value.p2BarTextScale,
        p2BarWarn1Color: String = _uiState.value.p2BarWarn1Color,
        p2BarWarn1Value: Float = _uiState.value.p2BarWarn1Value,
        p2BarWarn2Color: String = _uiState.value.p2BarWarn2Color,
        p2BarWarn2Value: Float = _uiState.value.p2BarWarn2Value,
        p2ShowBackground: Boolean = _uiState.value.p2ShowBackground
    ) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    p2Slot1Id    = slot1Id,    p2Slot1Label = slot1Label,
                    p2Slot2Id    = slot2Id,    p2Slot2Label = slot2Label,
                    p2Slot3Id    = slot3Id,    p2Slot3Label = slot3Label,
                    p2Slot4Id    = slot4Id,    p2Slot4Label = slot4Label,
                    p2Slot1TextScale = slot1TextScale, p2Slot2TextScale = slot2TextScale,
                    p2Slot3TextScale = slot3TextScale, p2Slot4TextScale = slot4TextScale,
                    wfSleepTextScale    = sleepTextScale,
                    wfSleepSource       = sleepSource,
                    wfSleepIoBrokerId   = sleepIoBrokerId,
                    wfSleepComplication = sleepComplication,
                    p2PillEnabled    = p2PillEnabled,
                    p2PillColorTrue  = p2PillColorTrue,
                    p2PillColorFalse = p2PillColorFalse,
                    p2PillIoBrokerId = p2PillIoBrokerId,
                    p2PillValueMode  = p2PillValueMode,
                    p2PillFixedValue = p2PillFixedValue,
                    p2Pill2Enabled    = p2Pill2Enabled,
                    p2Pill2ColorTrue  = p2Pill2ColorTrue,
                    p2Pill2ColorFalse = p2Pill2ColorFalse,
                    p2Pill2IoBrokerId = p2Pill2IoBrokerId,
                    p2Pill2ValueMode  = p2Pill2ValueMode,
                    p2Pill2FixedValue = p2Pill2FixedValue,
                    p2BarId          = p2BarId,
                    p2BarLabel       = p2BarLabel,
                    p2BarColor       = p2BarColor,
                    p2BarMin         = p2BarMin,
                    p2BarMax         = p2BarMax,
                    p2BarShowLabel   = p2BarShowLabel,
                    p2BarIsSlider    = p2BarIsSlider,
                    p2BarTextScale   = p2BarTextScale,
                    p2BarWarn1Color  = p2BarWarn1Color,
                    p2BarWarn1Value  = p2BarWarn1Value,
                    p2BarWarn2Color  = p2BarWarn2Color,
                    p2BarWarn2Value  = p2BarWarn2Value,
                    p2ShowBackground = p2ShowBackground
                )
            }
            try {
                pushPage2ConfigToWear()
            } catch (e: Exception) {
                _uiState.update { it.copy(wearSyncLog = "Fehler: ${e.message}") }
            }
        }
    }

    private suspend fun syncPage2SlotValues() {
        val s = _uiState.value
        val states = s.ioSyncStates.ifEmpty { s.states }
        val val1 = states.firstOrNull { it.id == s.p2Slot1Id }
        val val2 = states.firstOrNull { it.id == s.p2Slot2Id }
        val val3 = states.firstOrNull { it.id == s.p2Slot3Id }
        val val4 = states.firstOrNull { it.id == s.p2Slot4Id }
        val barState = states.firstOrNull { it.id == s.p2BarId }
        wearDataLayerService.syncPage2SlotsToWear(
            s.p2Slot1Label, formatSlotValue(val1?.value),
            s.p2Slot2Label, formatSlotValue(val2?.value),
            s.p2Slot3Label, formatSlotValue(val3?.value),
            s.p2Slot4Label, formatSlotValue(val4?.value),
            p2BarValue = if (s.p2BarId.isNotBlank()) formatSlotValue(barState?.value) else "--"
        )
    }

    private fun formatSlotValue(value: String?): String {
        if (value == null) return "--"
        val num = value.toDoubleOrNull() ?: return value.take(6)
        return String.format(java.util.Locale.US, "%.1f", num)
    }

    // ── Handy-Akku ────────────────────────────────────────────────────────────

    /**
     * Verarbeitet ein Battery-Intent: aktualisiert die App-Anzeige und sendet
     * den Wert sofort ans Watchface.
     */
    private fun handleBatteryIntent(batteryIntent: Intent?) {
        if (batteryIntent == null) return
        val level = batteryIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = batteryIntent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        val status = batteryIntent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL
        val percent = if (level >= 0 && scale > 0) (level * 100 / scale) else -1
        if (percent >= 0) {
            _uiState.update { it.copy(phoneBatteryLevel = percent) }
            val showBattery = _uiState.value.wfShowPhoneBattery
            viewModelScope.launch {
                wearDataLayerService.syncPhoneBatteryToWear(percent, isCharging, showBattery)
            }
        }
    }

    /**
     * Empfängt jede Akkustand-Änderung des Systems (ACTION_BATTERY_CHANGED),
     * damit die App-Anzeige nie einfriert und der Wert bei jeder Veränderung
     * sofort ans Watchface gepusht wird.
     */
    private val batteryReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            handleBatteryIntent(intent)
        }
    }

    private fun registerBatteryReceiver() {
        // Der zurückgegebene Sticky-Intent liefert sofort den aktuellen Stand.
        val sticky = context.registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        handleBatteryIntent(sticky)
    }

    /**
     * Liest den aktuellen Akkustand einmalig, aktualisiert die App-Anzeige und
     * sendet den Wert sofort ans Watchface (z. B. beim Speichern der Config).
     */
    fun sendPhoneBattery() {
        viewModelScope.launch {
            handleBatteryIntent(context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)))
        }
    }

    override fun onCleared() {
        super.onCleared()
        runCatching { context.unregisterReceiver(batteryReceiver) }
    }

    // ── Wetter ─────────────────────────────────────────────────────────────────

    private var weatherSearchJob: Job? = null

    /** Sucht Orte per OpenWeatherMap Geocoding. */
    fun searchWeatherLocations(query: String) {
        weatherSearchJob?.cancel()
        if (query.length < 2) {
            _uiState.update { it.copy(weatherSearchResults = emptyList(), weatherSearching = false, weatherSearchError = null) }
            return
        }
        _uiState.update { it.copy(weatherSearching = true, weatherSearchError = null) }
        weatherSearchJob = viewModelScope.launch {
            delay(300) // Debounce
            weatherService.searchLocations(query)
                .onSuccess { results ->
                    _uiState.update { it.copy(weatherSearchResults = results, weatherSearching = false, weatherSearchError = null) }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(weatherSearchResults = emptyList(), weatherSearching = false, weatherSearchError = e.message ?: "Unbekannter Fehler") }
                }
        }
    }

    /** Leert die Wetter-Suchergebnisse. */
    fun clearWeatherSearchResults() {
        weatherSearchJob?.cancel()
        _uiState.update { it.copy(weatherSearchResults = emptyList(), weatherSearching = false) }
    }

    /** Setzt einen festen Wetter-Standort. */
    fun setFixedWeatherLocation(lat: Double, lon: Double, city: String) {
        viewModelScope.launch {
            dataStore.edit { prefs ->
                prefs[KEY_WEATHER_USE_FIXED] = true
                prefs[KEY_WEATHER_FIXED_LAT] = lat.toString()
                prefs[KEY_WEATHER_FIXED_LON] = lon.toString()
                prefs[KEY_WEATHER_FIXED_CITY] = city
            }
            weatherService.useFixedLocation = true
            weatherService.fixedLat = lat
            weatherService.fixedLon = lon
            _uiState.update { it.copy(
                weatherUseFixedLocation = true,
                weatherFixedLat = lat,
                weatherFixedLon = lon,
                weatherFixedCity = city,
                weatherSearchResults = emptyList(),
                weatherSearching = false
            ) }
            // Sofort neue Wetterdaten abrufen
            weatherService.fetchWeather()
                .onSuccess { wearDataLayerService.syncWeatherToWear(it.temperature, it.condition) }
        }
    }

    /** Wechselt auf GPS-basierten Echtzeit-Standort für Wetter. */
    fun useRealtimeWeatherLocation() {
        viewModelScope.launch {
            dataStore.edit { prefs ->
                prefs[KEY_WEATHER_USE_FIXED] = false
                prefs[KEY_WEATHER_FIXED_CITY] = ""
            }
            weatherService.useFixedLocation = false
            weatherService.fixedLat = null
            weatherService.fixedLon = null
            _uiState.update { it.copy(
                weatherUseFixedLocation = false,
                weatherFixedCity = ""
            ) }
            // Sofort neue Wetterdaten abrufen
            weatherService.fetchWeather()
                .onSuccess { wearDataLayerService.syncWeatherToWear(it.temperature, it.condition) }
        }
    }

    // ── Gesundheitsdaten-Quelle pro Typ ──────────────────────────────────────

    /**
     * Speichert die pro-Typ Gesundheitsdaten-Quellen und startet/stoppt das Health-Polling.
     */
    fun updateHealthSourceConfig(
        hrSource: String,
        kcalSource: String,
        oxygenSource: String,
        hrComplication: String = "",
        kcalComplication: String = "",
        oxygenComplication: String = "",
        kcalMetric: String = "total_calories",
        oxygenMetric: String = "oxygen_saturation"
    ) {
        viewModelScope.launch {
            dataStore.edit { prefs ->
                prefs[KEY_WF_HR_SOURCE]          = hrSource
                prefs[KEY_WF_KCAL_SOURCE]        = kcalSource
                prefs[KEY_WF_OXYGEN_SOURCE]      = oxygenSource
                prefs[KEY_WF_HR_COMPLICATION]    = hrComplication
                prefs[KEY_WF_KCAL_COMPLICATION]  = kcalComplication
                prefs[KEY_WF_OXYGEN_COMPLICATION] = oxygenComplication
                prefs[KEY_WF_KCAL_METRIC]        = kcalMetric
                prefs[KEY_WF_OXYGEN_METRIC]      = oxygenMetric
            }
            // Globale Quelle ableiten: "phone" wenn mindestens ein Typ Health Connect nutzt
            val anyHealthConnect = hrSource == "healthconnect" || kcalSource == "healthconnect" || oxygenSource == "healthconnect"
            val globalSource = if (anyHealthConnect) "phone" else "local"
            dataStore.edit { prefs -> prefs[KEY_WF_HEALTH_DATA_SOURCE] = globalSource }

            _uiState.update {
                it.copy(
                    wfHrSource = hrSource,
                    wfKcalSource = kcalSource,
                    wfOxygenSource = oxygenSource,
                    wfHrComplication = hrComplication,
                    wfKcalComplication = kcalComplication,
                    wfOxygenComplication = oxygenComplication,
                    wfKcalMetric = kcalMetric,
                    wfOxygenMetric = oxygenMetric,
                    wfHealthDataSource = globalSource
                )
            }

            // Config an Uhr übertragen (damit sie weiß welche Quellen pro Typ gelten)
            try {
                pushFullConfigToWear()
                _uiState.update { it.copy(wearSyncLog = "Health-Quellen-Konfiguration übertragen") }
            } catch (e: Exception) {
                _uiState.update { it.copy(wearSyncLog = "Fehler: ${e.message}") }
            }

            // Hintergrund-Sync-Service neu starten, damit er die neue Health-Quelle übernimmt
            IoSyncSyncService.start(context)
        }
    }

    /**
     * Live-Vorschau der Health-Quellen: nur In-Memory-State + Senden, keine Persistenz.
     * Startet zusätzlich den Hintergrund-Sync-Service, damit die Vorschau auch
     * tatsächlich Daten liefert.
     */
    fun previewHealthSourceConfig(
        hrSource: String,
        kcalSource: String,
        oxygenSource: String,
        hrComplication: String = "",
        kcalComplication: String = "",
        oxygenComplication: String = "",
        kcalMetric: String = "total_calories",
        oxygenMetric: String = "oxygen_saturation"
    ) {
        viewModelScope.launch {
            val anyHealthConnect = hrSource == "healthconnect" || kcalSource == "healthconnect" || oxygenSource == "healthconnect"
            val globalSource = if (anyHealthConnect) "phone" else "local"
            // Persistenz erforderlich: SyncService liest immer aus DataStore
            dataStore.edit { prefs ->
                prefs[KEY_WF_HR_SOURCE]          = hrSource
                prefs[KEY_WF_KCAL_SOURCE]        = kcalSource
                prefs[KEY_WF_OXYGEN_SOURCE]      = oxygenSource
                prefs[KEY_WF_HR_COMPLICATION]    = hrComplication
                prefs[KEY_WF_KCAL_COMPLICATION]  = kcalComplication
                prefs[KEY_WF_OXYGEN_COMPLICATION] = oxygenComplication
                prefs[KEY_WF_KCAL_METRIC]        = kcalMetric
                prefs[KEY_WF_OXYGEN_METRIC]      = oxygenMetric
                prefs[KEY_WF_HEALTH_DATA_SOURCE] = globalSource
            }
            _uiState.update {
                it.copy(
                    wfHrSource = hrSource,
                    wfKcalSource = kcalSource,
                    wfOxygenSource = oxygenSource,
                    wfHrComplication = hrComplication,
                    wfKcalComplication = kcalComplication,
                    wfOxygenComplication = oxygenComplication,
                    wfKcalMetric = kcalMetric,
                    wfOxygenMetric = oxygenMetric,
                    wfHealthDataSource = globalSource
                )
            }
            try {
                pushFullConfigToWear()
            } catch (e: Exception) {
                _uiState.update { it.copy(wearSyncLog = "Fehler: ${e.message}") }
            }
            IoSyncSyncService.start(context)
        }
    }

    // ── Backup & Wiederherstellen ───────────────────────────────────────────────

    /**
     * Sichert ALLE veränderbaren Werte (Farben, Breiten, Intervalle, Slots, Pillen …)
     * aus dem DataStore als JSON in eine .ios-Datei im Dokumente-Ordner.
     * Der gesamte Preferences-Inhalt wird generisch (mit Typ-Information) serialisiert,
     * sodass auch zukünftig hinzukommende Einstellungen automatisch mitgesichert werden.
     */
    fun backupConfigToDocuments(onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            try {
                val prefs = dataStore.data.first()
                val data = org.json.JSONObject()
                prefs.asMap().forEach { (key, value) ->
                    val entry = org.json.JSONObject()
                    when (value) {
                        is Boolean -> { entry.put("t", "boolean"); entry.put("v", value) }
                        is Int     -> { entry.put("t", "int");     entry.put("v", value) }
                        is Long    -> { entry.put("t", "long");    entry.put("v", value) }
                        is Float   -> { entry.put("t", "float");   entry.put("v", value.toDouble()) }
                        is Double  -> { entry.put("t", "double");  entry.put("v", value) }
                        is String  -> { entry.put("t", "string");  entry.put("v", value) }
                        is Set<*>  -> {
                            entry.put("t", "stringset")
                            entry.put("v", org.json.JSONArray(value.map { it.toString() }))
                        }
                        else -> return@forEach
                    }
                    data.put(key.name, entry)
                }
                val root = org.json.JSONObject().apply {
                    put("_iosync_backup", true)
                    put("version", BuildConfig.VERSION_CODE)
                    put("created", System.currentTimeMillis())
                    put("data", data)
                }
                val json = root.toString(2)

                val stamp = java.text.SimpleDateFormat("yyyy-MM-dd_HHmm", java.util.Locale.GERMANY)
                    .format(java.util.Date())
                val fileName = "iosync-$stamp.ios"
                val savedPath = writeBackupToDocuments(fileName, json)
                onResult(true, savedPath)
            } catch (e: Exception) {
                onResult(false, e.message ?: "Unbekannter Fehler")
            }
        }
    }

    /** Schreibt die Backup-Datei in den öffentlichen Dokumente-Ordner. */
    private fun writeBackupToDocuments(fileName: String, content: String): String {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val values = android.content.ContentValues().apply {
                put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "application/octet-stream")
                put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOCUMENTS)
            }
            val collection = android.provider.MediaStore.Files
                .getContentUri(android.provider.MediaStore.VOLUME_EXTERNAL_PRIMARY)
            val uri = resolver.insert(collection, values)
                ?: throw java.io.IOException("Datei konnte nicht angelegt werden")
            resolver.openOutputStream(uri)?.use { it.write(content.toByteArray(Charsets.UTF_8)) }
                ?: throw java.io.IOException("Kein Schreibzugriff auf die Datei")
            "Dokumente/$fileName"
        } else {
            val dir = android.os.Environment
                .getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOCUMENTS)
            if (!dir.exists()) dir.mkdirs()
            val file = java.io.File(dir, fileName)
            file.writeText(content)
            file.absolutePath
        }
    }

    /**
     * Stellt die Konfiguration aus einer zuvor erstellten .ios-Backup-Datei wieder her.
     * Liest ausschließlich Dateien im IoSync-Backup-Format; alle Werte werden in den
     * DataStore zurückgeschrieben, der UI-State neu geladen und an die Uhr übertragen.
     */
    fun restoreConfigFromUri(uri: android.net.Uri, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            try {
                val text = context.contentResolver.openInputStream(uri)?.use {
                    it.readBytes().toString(Charsets.UTF_8)
                } ?: throw java.io.IOException("Datei konnte nicht gelesen werden")

                val root = org.json.JSONObject(text)
                if (!root.optBoolean("_iosync_backup", false)) {
                    throw IllegalArgumentException("Keine gültige IoSync-Backup-Datei (.ios)")
                }
                val data = root.getJSONObject("data")
                dataStore.edit { prefs ->
                    data.keys().forEach { name ->
                        val entry = data.getJSONObject(name)
                        when (entry.getString("t")) {
                            "boolean"   -> prefs[booleanPreferencesKey(name)]   = entry.getBoolean("v")
                            "int"       -> prefs[intPreferencesKey(name)]       = entry.getInt("v")
                            "long"      -> prefs[longPreferencesKey(name)]      = entry.getLong("v")
                            "float"     -> prefs[floatPreferencesKey(name)]     = entry.getDouble("v").toFloat()
                            "double"    -> prefs[doublePreferencesKey(name)]    = entry.getDouble("v")
                            "string"    -> prefs[stringPreferencesKey(name)]    = entry.getString("v")
                            "stringset" -> {
                                val arr = entry.getJSONArray("v")
                                prefs[stringSetPreferencesKey(name)] =
                                    (0 until arr.length()).map { arr.getString(it) }.toSet()
                            }
                        }
                    }
                }

                // Wiederhergestellte Konfiguration in den UI-State laden und an die Uhr senden
                applyConfigFromPrefs(dataStore.data.first())
                val st = _uiState.value
                dynamicBaseUrl.update(st.host, st.port)
                if (st.useIoSyncAdapter && st.ioSyncHost.isNotBlank())
                    startIoSyncPolling(st.ioSyncHost, st.ioSyncPort, st.ioSyncUseHttps, st.ioSyncUsername, st.ioSyncPassword)
                try { pushFullConfigToWear() } catch (_: Exception) {}
                IoSyncSyncService.start(context)
                onResult(true, "Konfiguration wiederhergestellt")
            } catch (e: Exception) {
                onResult(false, e.message ?: "Unbekannter Fehler")
            }
        }
    }

    // ── Hilfsfunktionen ───────────────────────────────────────────────────────

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun getStateById(id: String): SmartHomeState? =
        _uiState.value.states.firstOrNull { it.id == id }

    private fun applyFilter(
        states: List<SmartHomeState>,
        query: String,
        room: String?
    ): List<SmartHomeState> {
        var result = states
        if (room != null) result = result.filter { it.room == room }
        if (query.isNotBlank()) {
            val q = query.lowercase()
            result = result.filter {
                it.id.lowercase().contains(q) ||
                it.name.lowercase().contains(q) ||
                it.value?.lowercase()?.contains(q) == true
            }
        }
        return result
    }
}
