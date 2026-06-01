package com.iosync.app.ui.viewmodel

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iosync.app.BuildConfig
import com.iosync.app.data.model.SmartHomeState
import com.iosync.app.data.health.HealthConnectManager
import com.iosync.app.data.health.HealthConnectStatus
import com.iosync.app.data.health.HealthSyncService
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
    // Watchface: Hintergrundbild
    val wfShowBackground: Boolean = false,
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
    // ── Seite 2 Pillen ──────────────────────────────────────────────────────────
    val p2PillEnabled: Boolean = false,
    val p2PillColorTrue: String = "cyan",
    val p2PillColorFalse: String = "red",
    val p2PillIoBrokerId: String = "",
    val p2PillValueMode: String = "toggle",
    val p2PillFixedValue: String = "",
    // ── Seite 2 – vertikaler Balken ─────────────────────────────────────────
    val p2BarId: String = "",
    val p2BarLabel: String = "",
    val p2BarColor: String = "neon_yellow",
    val p2BarMin: Float = 0f,
    val p2BarMax: Float = 100f,
    val p2BarShowLabel: Boolean = true,
    val p2BarTextScale: Int = 100,
    val p2BarWarn1Color: String = "orange",
    val p2BarWarn1Value: Float = Float.NaN,
    val p2BarWarn2Color: String = "red",
    val p2BarWarn2Value: Float = Float.NaN,
    // Aktualisierungsintervalle (in Sekunden)
    val batteryPollIntervalSec: Int = 60,
    val slotPollIntervalSec: Int = 300,
    val healthPollIntervalSec: Int = 60,
    // Sync-Status-Log für die Konsolenanzeige
    val wearSyncLog: String = "",
    // Health Connect Status
    val healthConnectStatus: HealthConnectStatus = HealthConnectStatus(),
    val healthConnectLoading: Boolean = false
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
    val healthConnectManager: HealthConnectManager
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
        val KEY_WF_SLEEP_TEXT_SCALE  = intPreferencesKey("wf_sleep_text_scale")
        // Seite 2 Pillen
        val KEY_P2_PILL_ENABLED      = booleanPreferencesKey("p2_pill_enabled")
        val KEY_P2_PILL_COLOR_TRUE   = stringPreferencesKey("p2_pill_color_true")
        val KEY_P2_PILL_COLOR_FALSE  = stringPreferencesKey("p2_pill_color_false")
        val KEY_P2_PILL_IOBROKER_ID  = stringPreferencesKey("p2_pill_iobroker_id")
        val KEY_P2_PILL_VALUE_MODE   = stringPreferencesKey("p2_pill_value_mode")
        val KEY_P2_PILL_FIXED_VALUE  = stringPreferencesKey("p2_pill_fixed_value")
        // Aktualisierungsintervalle (in Sekunden)
        val KEY_BATTERY_POLL_INTERVAL  = intPreferencesKey("battery_poll_interval_sec")
        val KEY_SLOT_POLL_INTERVAL     = intPreferencesKey("slot_poll_interval_sec")
        val KEY_HEALTH_POLL_INTERVAL   = intPreferencesKey("health_poll_interval_sec")
        // Wetter-Standort
        val KEY_WEATHER_USE_FIXED   = booleanPreferencesKey("weather_use_fixed")
        val KEY_WEATHER_FIXED_LAT   = stringPreferencesKey("weather_fixed_lat")
        val KEY_WEATHER_FIXED_LON   = stringPreferencesKey("weather_fixed_lon")
        val KEY_WEATHER_FIXED_CITY  = stringPreferencesKey("weather_fixed_city")
        // Wetter-Temperaturquelle
        val KEY_WF_WEATHER_TEMP_SOURCE  = stringPreferencesKey("wf_weather_temp_source")
        val KEY_WF_WEATHER_IOBROKER_ID  = stringPreferencesKey("wf_weather_iobroker_id")
        // Seite 2 – vertikaler Balken
        val KEY_P2_BAR_ID           = stringPreferencesKey("p2_bar_id")
        val KEY_P2_BAR_LABEL        = stringPreferencesKey("p2_bar_label")
        val KEY_P2_BAR_COLOR        = stringPreferencesKey("p2_bar_color")
        val KEY_P2_BAR_MIN          = stringPreferencesKey("p2_bar_min")
        val KEY_P2_BAR_MAX          = stringPreferencesKey("p2_bar_max")
        val KEY_P2_BAR_SHOW_LABEL   = booleanPreferencesKey("p2_bar_show_label")
        val KEY_P2_BAR_TEXT_SCALE   = intPreferencesKey("p2_bar_text_scale")
        val KEY_P2_BAR_WARN1_COLOR  = stringPreferencesKey("p2_bar_warn1_color")
        val KEY_P2_BAR_WARN1_VALUE  = stringPreferencesKey("p2_bar_warn1_value")
        val KEY_P2_BAR_WARN2_COLOR  = stringPreferencesKey("p2_bar_warn2_color")
        val KEY_P2_BAR_WARN2_VALUE  = stringPreferencesKey("p2_bar_warn2_value")

        // Wetter-Sync-Intervall (15 Minuten)
        private const val WEATHER_SYNC_INTERVAL_MS = 900_000L
    }

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    val connectionStatus: StateFlow<WebSocketStatus> = repository.connectionStatus
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), WebSocketStatus.DISCONNECTED)

    private var ioSyncPollingJob: Job? = null
    private var batteryPollingJob: Job? = null
    private var weatherPollingJob: Job? = null

    init {
        viewModelScope.launch {
            val prefs = dataStore.data.first()
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
            val batteryPollInterval = prefs[KEY_BATTERY_POLL_INTERVAL] ?: 60
            val slotPollInterval   = prefs[KEY_SLOT_POLL_INTERVAL]   ?: 300
            val healthPollInterval = prefs[KEY_HEALTH_POLL_INTERVAL] ?: 60
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
            val wfSleepTextScale  = prefs[KEY_WF_SLEEP_TEXT_SCALE]  ?: 100
            // Seite 2 Pillen
            val p2PillEnabled    = prefs[KEY_P2_PILL_ENABLED]     ?: false
            val p2PillColorTrue  = prefs[KEY_P2_PILL_COLOR_TRUE]  ?: "cyan"
            val p2PillColorFalse = prefs[KEY_P2_PILL_COLOR_FALSE] ?: "red"
            val p2PillIoBrokerId = prefs[KEY_P2_PILL_IOBROKER_ID] ?: ""
            val p2PillValueMode  = prefs[KEY_P2_PILL_VALUE_MODE]  ?: "toggle"
            val p2PillFixedValue = prefs[KEY_P2_PILL_FIXED_VALUE] ?: ""
            // Seite 2 – vertikaler Balken
            val p2BarId         = prefs[KEY_P2_BAR_ID]         ?: ""
            val p2BarLabel      = prefs[KEY_P2_BAR_LABEL]      ?: ""
            val p2BarColor      = prefs[KEY_P2_BAR_COLOR]      ?: "neon_yellow"
            val p2BarMin        = prefs[KEY_P2_BAR_MIN]?.toFloatOrNull()  ?: 0f
            val p2BarMax        = prefs[KEY_P2_BAR_MAX]?.toFloatOrNull()  ?: 100f
            val p2BarShowLabel  = prefs[KEY_P2_BAR_SHOW_LABEL] ?: true
            val p2BarTextScale  = prefs[KEY_P2_BAR_TEXT_SCALE] ?: 100
            val p2BarWarn1Color = prefs[KEY_P2_BAR_WARN1_COLOR] ?: "orange"
            val p2BarWarn1Value = prefs[KEY_P2_BAR_WARN1_VALUE]?.toFloatOrNull() ?: Float.NaN
            val p2BarWarn2Color = prefs[KEY_P2_BAR_WARN2_COLOR] ?: "red"
            val p2BarWarn2Value = prefs[KEY_P2_BAR_WARN2_VALUE]?.toFloatOrNull() ?: Float.NaN

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
                    customSlot4Warn1Color    = customSlot4Warn1Color,
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
                    wfSleepTextScale = wfSleepTextScale,
                    p2PillEnabled    = p2PillEnabled,
                    p2PillColorTrue  = p2PillColorTrue,
                    p2PillColorFalse = p2PillColorFalse,
                    p2PillIoBrokerId = p2PillIoBrokerId,
                    p2PillValueMode  = p2PillValueMode,
                    p2PillFixedValue = p2PillFixedValue,
                    wfWeatherTempSource = wfWeatherTempSource,
                    wfWeatherIoBrokerId = wfWeatherIoBrokerId,
                    p2BarId         = p2BarId,
                    p2BarLabel      = p2BarLabel,
                    p2BarColor      = p2BarColor,
                    p2BarMin        = p2BarMin,
                    p2BarMax        = p2BarMax,
                    p2BarShowLabel  = p2BarShowLabel,
                    p2BarTextScale  = p2BarTextScale,
                    p2BarWarn1Color = p2BarWarn1Color,
                    p2BarWarn1Value = p2BarWarn1Value,
                    p2BarWarn2Color = p2BarWarn2Color,
                    p2BarWarn2Value = p2BarWarn2Value
                )
            }

            dynamicBaseUrl.update(host, port)
            if (useIoSyncAdapter && ioSyncHost.isNotBlank()) startIoSyncPolling(ioSyncHost, ioSyncPort, ioSyncUseHttps, ioSyncUsername, ioSyncPassword)
            if (wfShowPhoneBattery) startBatteryPolling()
            if (wfShowWeather) startWeatherPolling()
            if (wfHrSource == "healthconnect" || wfKcalSource == "healthconnect" || wfOxygenSource == "healthconnect") HealthSyncService.start(context)
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

        refresh()
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

    /** Startet das periodische Abrufen vom IoSync Adapter (primäre Datenquelle). */
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
                        if (_uiState.value.wfShowIoBrokerData) {
                            wearDataLayerService.syncStatesToWear(states)
                        }
                        if (_uiState.value.showCustomSlots) syncCustomSlotValues()
                        if (_uiState.value.p2Slot1Id.isNotBlank() || _uiState.value.p2Slot2Id.isNotBlank() ||
                            _uiState.value.p2Slot3Id.isNotBlank() || _uiState.value.p2Slot4Id.isNotBlank()) {
                            syncPage2SlotValues()
                        }
                    }
                    .onFailure { /* Fehler werden im IoSyncClient geloggt */ }
                delay(_uiState.value.slotPollIntervalSec * 1_000L)
            }
        }
    }

    // ── Watchface-Konfiguration ───────────────────────────────────────────────

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
                if (!wearDataLayerService.isWatchConnected()) {
                    _uiState.update { it.copy(wearSyncLog = "Fehler: Keine Uhr verbunden") }
                    return@launch
                }
                val s = _uiState.value
                wearDataLayerService.syncWatchFaceConfigToWear(
                    timeColor, dateColor, showSeconds, showTicks, showWeekday, showPhoneBattery, showIoBrokerData,
                    showSecondsRing, secondsRingColor, secondsRingWidth, secondsGlowWidth, secondsNumberColor,
                    s.actionPillEnabled, s.actionPillColorTrue, s.actionPillColorFalse,
                    s.actionPillIoBrokerId, s.actionPillValueMode, s.actionPillFixedValue, s.actionPillState,
                    showWeather, showHeartRate, showOxygen, showCalories, showSteps,
                    showCustomSlots, customSlot1Label, customSlot2Label,
                    customSlot3Label, customSlot4Label, customSlot4BarColor, customSlot4BarMin, customSlot4BarMax,
                    s.customSlot4BarShowLabel,
                    hrTextScale, kcalTextScale, stepsTextScale, slot1TextScale, slot2TextScale, slot3TextScale, slot4TextScale,
                    weatherTextScale, sunriseTextScale, watchBatteryTextScale,
                    batteryRingColor1, batteryRingColor2, batteryRingStrokeScale,
                    healthDataSource,
                    hrSource, kcalSource, oxygenSource,
                    batteryWarn1Color = batteryWarn1Color, batteryWarn1Threshold = batteryWarn1Threshold,
                    batteryWarn2Color = batteryWarn2Color, batteryWarn2Threshold = batteryWarn2Threshold,
                    showBackground = showBackground,
                    hrColor = hrColor, kcalColor = kcalColor, oxygenColor = oxygenColor,
                    stepsColor = stepsColor, sleepColor = sleepColor,
                    sunriseColor = sunriseColor, slotColor = slotColor,
                    weatherTempSource = weatherTempSource, weatherIoBrokerId = weatherIoBrokerId
                )
                _uiState.update { it.copy(wearSyncLog = "Watchface-Konfiguration übertragen") }
            } catch (e: Exception) {
                _uiState.update { it.copy(wearSyncLog = "Fehler: ${e.message}") }
            }

            // Akku-Polling starten/stoppen je nach Konfiguration
            if (showPhoneBattery && batteryPollingJob?.isActive != true) {
                startBatteryPolling()
            } else if (!showPhoneBattery) {
                batteryPollingJob?.cancel()
            }

            // IoSync-States sofort an Watchface senden wenn gerade aktiviert
            if (showIoBrokerData && _uiState.value.ioSyncStates.isNotEmpty()) {
                wearDataLayerService.syncStatesToWear(_uiState.value.ioSyncStates)
            }

            // Wetter-Polling starten/stoppen
            if (showWeather && weatherPollingJob?.isActive != true) {
                startWeatherPolling()
            } else if (!showWeather) {
                weatherPollingJob?.cancel()
            }
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
            val currentState = _uiState.value.actionPillState
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
                if (!wearDataLayerService.isWatchConnected()) {
                    _uiState.update { it.copy(wearSyncLog = "Fehler: Keine Uhr verbunden") }
                    return@launch
                }
                val s = _uiState.value
                wearDataLayerService.syncWatchFaceConfigToWear(
                    s.wfTimeColor, s.wfDateColor, s.wfShowSeconds, s.wfShowTicks, s.wfShowWeekday,
                    s.wfShowPhoneBattery, s.wfShowIoBrokerData, s.wfShowSecondsRing,
                    s.wfSecondsRingColor, s.wfSecondsRingWidth, s.wfSecondsGlowWidth, s.wfSecondsNumberColor,
                    enabled, colorTrue, colorFalse, ioBrokerId, valueMode, fixedValue, currentState,
                    s.wfShowWeather, s.wfShowHeartRate, s.wfShowOxygen, s.wfShowCalories,
                    s.wfShowSteps, s.showCustomSlots, s.customSlot1Label, s.customSlot2Label,
                    s.customSlot3Label, s.customSlot4Label, s.customSlot4BarColor, s.customSlot4BarMin, s.customSlot4BarMax,
                    s.customSlot4BarShowLabel,
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
                    sunriseColor = s.wfSunriseColor, slotColor = s.wfSlotColor
                )
                _uiState.update { it.copy(wearSyncLog = "Aktions-Pille-Konfiguration übertragen") }
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

    // ── Aktualisierungsintervalle ─────────────────────────────────────────────

    /** Speichert die Polling-Intervalle und startet die Jobs mit neuem Intervall neu. */
    fun updatePollIntervals(batterySec: Int, slotSec: Int, healthSec: Int = _uiState.value.healthPollIntervalSec) {
        viewModelScope.launch {
            dataStore.edit { prefs ->
                prefs[KEY_BATTERY_POLL_INTERVAL] = batterySec
                prefs[KEY_SLOT_POLL_INTERVAL]    = slotSec
                prefs[KEY_HEALTH_POLL_INTERVAL]  = healthSec
            }
            _uiState.update { it.copy(batteryPollIntervalSec = batterySec, slotPollIntervalSec = slotSec, healthPollIntervalSec = healthSec) }
            // Laufende Jobs mit neuem Intervall neu starten
            if (batteryPollingJob?.isActive == true) startBatteryPolling()
            // Health-Sync-Service mit neuem Intervall neu starten, falls eine Quelle Health Connect nutzt
            val s0 = _uiState.value
            if (s0.wfHrSource == "healthconnect" || s0.wfKcalSource == "healthconnect" || s0.wfOxygenSource == "healthconnect") {
                HealthSyncService.start(context)
            }
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
        slot1TextScale: Int = _uiState.value.wfSlot1TextScale,
        slot2TextScale: Int = _uiState.value.wfSlot2TextScale,
        slot3TextScale: Int = _uiState.value.wfSlot3TextScale,
        slot4TextScale: Int = _uiState.value.wfSlot4TextScale,
        slot4Warn1Color: String = _uiState.value.customSlot4Warn1Color,
        slot4Warn1Value: Float = _uiState.value.customSlot4Warn1Value,
        slot4Warn2Color: String = _uiState.value.customSlot4Warn2Color,
        slot4Warn2Value: Float = _uiState.value.customSlot4Warn2Value
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
                val s2 = _uiState.value
                wearDataLayerService.syncWatchFaceConfigToWear(
                    s2.wfTimeColor, s2.wfDateColor, s2.wfShowSeconds, s2.wfShowTicks, s2.wfShowWeekday,
                    s2.wfShowPhoneBattery, s2.wfShowIoBrokerData, s2.wfShowSecondsRing,
                    s2.wfSecondsRingColor, s2.wfSecondsRingWidth, s2.wfSecondsGlowWidth, s2.wfSecondsNumberColor,
                    s2.actionPillEnabled, s2.actionPillColorTrue, s2.actionPillColorFalse,
                    s2.actionPillIoBrokerId, s2.actionPillValueMode, s2.actionPillFixedValue, s2.actionPillState,
                    s2.wfShowWeather, s2.wfShowHeartRate, s2.wfShowOxygen, s2.wfShowCalories,
                    s2.wfShowSteps, s2.showCustomSlots, s2.customSlot1Label, s2.customSlot2Label,
                    s2.customSlot3Label, s2.customSlot4Label, s2.customSlot4BarColor, s2.customSlot4BarMin, s2.customSlot4BarMax,
                    s2.customSlot4BarShowLabel,
                    s2.wfHrTextScale, s2.wfKcalTextScale, s2.wfStepsTextScale, s2.wfSlot1TextScale, s2.wfSlot2TextScale, s2.wfSlot3TextScale, s2.wfSlot4TextScale,
                    s2.wfWeatherTextScale, s2.wfSunriseTextScale, s2.wfWatchBatteryTextScale,
                    s2.wfBatteryRingColor1, s2.wfBatteryRingColor2, s2.wfBatteryRingStrokeScale,
                    s2.wfHealthDataSource,
                    s2.wfHrSource, s2.wfKcalSource, s2.wfOxygenSource,
                    s2.wfHrComplication, s2.wfKcalComplication, s2.wfOxygenComplication,
                    s2.wfBatteryWarn1Color, s2.wfBatteryWarn1Threshold,
                    s2.wfBatteryWarn2Color, s2.wfBatteryWarn2Threshold,
                    showBackground = s2.wfShowBackground,
                    hrColor = s2.wfHrColor, kcalColor = s2.wfKcalColor, oxygenColor = s2.wfOxygenColor,
                    stepsColor = s2.wfStepsColor, sleepColor = s2.wfSleepColor,
                    sunriseColor = s2.wfSunriseColor, slotColor = s2.wfSlotColor
                )
                _uiState.update { it.copy(wearSyncLog = "Slot-Daten übertragen") }
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
        p2PillEnabled: Boolean,
        p2PillColorTrue: String,
        p2PillColorFalse: String,
        p2PillIoBrokerId: String,
        p2PillValueMode: String,
        p2PillFixedValue: String,
        p2BarId: String = _uiState.value.p2BarId,
        p2BarLabel: String = _uiState.value.p2BarLabel,
        p2BarColor: String = _uiState.value.p2BarColor,
        p2BarMin: Float = _uiState.value.p2BarMin,
        p2BarMax: Float = _uiState.value.p2BarMax,
        p2BarShowLabel: Boolean = _uiState.value.p2BarShowLabel,
        p2BarTextScale: Int = _uiState.value.p2BarTextScale,
        p2BarWarn1Color: String = _uiState.value.p2BarWarn1Color,
        p2BarWarn1Value: Float = _uiState.value.p2BarWarn1Value,
        p2BarWarn2Color: String = _uiState.value.p2BarWarn2Color,
        p2BarWarn2Value: Float = _uiState.value.p2BarWarn2Value
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
                prefs[KEY_WF_SLEEP_TEXT_SCALE] = sleepTextScale
                prefs[KEY_P2_PILL_ENABLED]      = p2PillEnabled
                prefs[KEY_P2_PILL_COLOR_TRUE]   = p2PillColorTrue
                prefs[KEY_P2_PILL_COLOR_FALSE]  = p2PillColorFalse
                prefs[KEY_P2_PILL_IOBROKER_ID]  = p2PillIoBrokerId
                prefs[KEY_P2_PILL_VALUE_MODE]   = p2PillValueMode
                prefs[KEY_P2_PILL_FIXED_VALUE]  = p2PillFixedValue
                prefs[KEY_P2_BAR_ID]          = p2BarId
                prefs[KEY_P2_BAR_LABEL]       = p2BarLabel
                prefs[KEY_P2_BAR_COLOR]       = p2BarColor
                prefs[KEY_P2_BAR_MIN]         = p2BarMin.toString()
                prefs[KEY_P2_BAR_MAX]         = p2BarMax.toString()
                prefs[KEY_P2_BAR_SHOW_LABEL]  = p2BarShowLabel
                prefs[KEY_P2_BAR_TEXT_SCALE]  = p2BarTextScale
                prefs[KEY_P2_BAR_WARN1_COLOR] = p2BarWarn1Color
                prefs[KEY_P2_BAR_WARN1_VALUE] = p2BarWarn1Value.toString()
                prefs[KEY_P2_BAR_WARN2_COLOR] = p2BarWarn2Color
                prefs[KEY_P2_BAR_WARN2_VALUE] = p2BarWarn2Value.toString()
            }
            _uiState.update {
                it.copy(
                    p2Slot1Id    = slot1Id,    p2Slot1Label = slot1Label,
                    p2Slot2Id    = slot2Id,    p2Slot2Label = slot2Label,
                    p2Slot3Id    = slot3Id,    p2Slot3Label = slot3Label,
                    p2Slot4Id    = slot4Id,    p2Slot4Label = slot4Label,
                    p2Slot1TextScale = slot1TextScale, p2Slot2TextScale = slot2TextScale,
                    p2Slot3TextScale = slot3TextScale, p2Slot4TextScale = slot4TextScale,
                    wfSleepTextScale = sleepTextScale,
                    p2PillEnabled    = p2PillEnabled,
                    p2PillColorTrue  = p2PillColorTrue,
                    p2PillColorFalse = p2PillColorFalse,
                    p2PillIoBrokerId = p2PillIoBrokerId,
                    p2PillValueMode  = p2PillValueMode,
                    p2PillFixedValue = p2PillFixedValue,
                    p2BarId          = p2BarId,
                    p2BarLabel       = p2BarLabel,
                    p2BarColor       = p2BarColor,
                    p2BarMin         = p2BarMin,
                    p2BarMax         = p2BarMax,
                    p2BarShowLabel   = p2BarShowLabel,
                    p2BarTextScale   = p2BarTextScale,
                    p2BarWarn1Color  = p2BarWarn1Color,
                    p2BarWarn1Value  = p2BarWarn1Value,
                    p2BarWarn2Color  = p2BarWarn2Color,
                    p2BarWarn2Value  = p2BarWarn2Value
                )
            }
            if (!wearDataLayerService.isWatchConnected()) {
                _uiState.update { it.copy(wearSyncLog = "Fehler: Keine Uhr verbunden") }
                return@launch
            }
            _uiState.update { it.copy(wearSyncLog = "Sende Seite-2-Konfig …") }
            val s = _uiState.value
            wearDataLayerService.syncPage2ConfigToWear(
                p2PillEnabled, p2PillColorTrue, p2PillColorFalse,
                p2PillIoBrokerId, p2PillValueMode, p2PillFixedValue,
                slot1TextScale, slot2TextScale, slot3TextScale, slot4TextScale, sleepTextScale,
                p2BarLabel, p2BarColor, p2BarMin, p2BarMax, p2BarShowLabel, p2BarTextScale,
                p2BarWarn1Color, p2BarWarn1Value, p2BarWarn2Color, p2BarWarn2Value
            )
            syncPage2SlotValues()
            _uiState.update { it.copy(wearSyncLog = "Seite-2-Konfig übertragen") }
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

    /** Startet das periodische Senden des Handy-Akkustands ans Watchface. */
    private fun startBatteryPolling() {
        batteryPollingJob?.cancel()
        batteryPollingJob = viewModelScope.launch {
            while (true) {
                sendPhoneBattery()
                delay(_uiState.value.batteryPollIntervalSec * 1_000L)
            }
        }
    }

    /** Liest den aktuellen Akkustand und überträgt ihn an das Watchface. */
    fun sendPhoneBattery() {
        viewModelScope.launch {
            val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            if (batteryIntent != null) {
                val level = batteryIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = batteryIntent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
                val status = batteryIntent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                        status == BatteryManager.BATTERY_STATUS_FULL
                val percent = if (level >= 0 && scale > 0) (level * 100 / scale) else -1
                if (percent >= 0) {
                    _uiState.update { it.copy(phoneBatteryLevel = percent) }
                    wearDataLayerService.syncPhoneBatteryToWear(percent, isCharging)
                }
            }
        }
    }

    // ── Wetter ─────────────────────────────────────────────────────────────────

    /** Startet das periodische Abrufen und Übertragen der Wetterdaten an die Uhr. */
    private fun startWeatherPolling() {
        weatherPollingJob?.cancel()
        weatherPollingJob = viewModelScope.launch {
            while (true) {
                weatherService.fetchWeather()
                    .onSuccess { weather ->
                        wearDataLayerService.syncWeatherToWear(weather.temperature, weather.condition)
                    }
                delay(WEATHER_SYNC_INTERVAL_MS)
            }
        }
    }

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
        oxygenComplication: String = ""
    ) {
        viewModelScope.launch {
            dataStore.edit { prefs ->
                prefs[KEY_WF_HR_SOURCE]          = hrSource
                prefs[KEY_WF_KCAL_SOURCE]        = kcalSource
                prefs[KEY_WF_OXYGEN_SOURCE]      = oxygenSource
                prefs[KEY_WF_HR_COMPLICATION]    = hrComplication
                prefs[KEY_WF_KCAL_COMPLICATION]  = kcalComplication
                prefs[KEY_WF_OXYGEN_COMPLICATION] = oxygenComplication
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
                    wfHealthDataSource = globalSource
                )
            }

            // Config an Uhr übertragen (damit sie weiß welche Quellen pro Typ gelten)
            try {
                if (!wearDataLayerService.isWatchConnected()) {
                    _uiState.update { it.copy(wearSyncLog = "Fehler: Keine Uhr verbunden") }
                    return@launch
                }
                val s = _uiState.value
                wearDataLayerService.syncWatchFaceConfigToWear(
                    s.wfTimeColor, s.wfDateColor, s.wfShowSeconds, s.wfShowTicks, s.wfShowWeekday,
                    s.wfShowPhoneBattery, s.wfShowIoBrokerData, s.wfShowSecondsRing,
                    s.wfSecondsRingColor, s.wfSecondsRingWidth, s.wfSecondsGlowWidth, s.wfSecondsNumberColor,
                    s.actionPillEnabled, s.actionPillColorTrue, s.actionPillColorFalse,
                    s.actionPillIoBrokerId, s.actionPillValueMode, s.actionPillFixedValue, s.actionPillState,
                    s.wfShowWeather, s.wfShowHeartRate, s.wfShowOxygen, s.wfShowCalories,
                    s.wfShowSteps, s.showCustomSlots, s.customSlot1Label, s.customSlot2Label,
                    s.customSlot3Label, s.customSlot4Label, s.customSlot4BarColor, s.customSlot4BarMin, s.customSlot4BarMax,
                    s.customSlot4BarShowLabel,
                    s.wfHrTextScale, s.wfKcalTextScale, s.wfStepsTextScale, s.wfSlot1TextScale, s.wfSlot2TextScale, s.wfSlot3TextScale, s.wfSlot4TextScale,
                    s.wfWeatherTextScale, s.wfSunriseTextScale, s.wfWatchBatteryTextScale,
                    s.wfBatteryRingColor1, s.wfBatteryRingColor2, s.wfBatteryRingStrokeScale,
                    globalSource,
                    hrSource, kcalSource, oxygenSource,
                    hrComplication, kcalComplication, oxygenComplication,
                    s.wfBatteryWarn1Color, s.wfBatteryWarn1Threshold,
                    s.wfBatteryWarn2Color, s.wfBatteryWarn2Threshold,
                    showBackground = s.wfShowBackground,
                    hrColor = s.wfHrColor, kcalColor = s.wfKcalColor, oxygenColor = s.wfOxygenColor,
                    stepsColor = s.wfStepsColor, sleepColor = s.wfSleepColor,
                    sunriseColor = s.wfSunriseColor, slotColor = s.wfSlotColor
                )
                _uiState.update { it.copy(wearSyncLog = "Health-Quellen-Konfiguration übertragen") }
            } catch (e: Exception) {
                _uiState.update { it.copy(wearSyncLog = "Fehler: ${e.message}") }
            }

            // Health-Sync-Service starten/stoppen (läuft als Foreground-Service im Hintergrund)
            if (anyHealthConnect) {
                HealthSyncService.start(context)
            } else {
                HealthSyncService.stop(context)
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
