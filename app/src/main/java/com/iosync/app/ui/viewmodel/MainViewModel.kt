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
    // Datenquelle: true = IoSync Adapter, false = Simple-API
    val useIoSyncAdapter: Boolean = true,
    // IoSync Adapter Verbindung
    val ioSyncHost: String = "",
    val ioSyncPort: Int = 345,
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
    val wfShowCalories: Boolean = true
)

@HiltViewModel
class MainViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: SmartHomeRepository,
    private val dataStore: DataStore<Preferences>,
    private val wearDataLayerService: WearDataLayerService,
    private val ioSyncClient: IoSyncClient,
    private val dynamicBaseUrl: DynamicBaseUrl,
    private val weatherService: WeatherService
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
        val KEY_WF_SECONDS_RING_WIDTH  = intPreferencesKey("wf_seconds_ring_width")
        val KEY_USE_IOSYNC_ADAPTER   = booleanPreferencesKey("use_iosync_adapter")
        val KEY_IOSYNC_HOST          = stringPreferencesKey("iosync_host")
        val KEY_IOSYNC_PORT          = intPreferencesKey("iosync_port")
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

        // Polling-Intervall für IoSync Datenpunkte (30 Sekunden)
        private const val IOSYNC_POLL_INTERVAL_MS = 30_000L
        // Akku-Sync-Intervall (60 Sekunden)
        private const val BATTERY_SYNC_INTERVAL_MS = 60_000L
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
            val wfSecondsRingColor  = prefs[KEY_WF_SECONDS_RING_COLOR]  ?: "neon_yellow"
            val wfSecondsRingWidth  = prefs[KEY_WF_SECONDS_RING_WIDTH]  ?: 5
            val useIoSyncAdapter  = prefs[KEY_USE_IOSYNC_ADAPTER]   ?: true
            val ioSyncHost        = prefs[KEY_IOSYNC_HOST]          ?: ""
            val ioSyncPort        = prefs[KEY_IOSYNC_PORT]          ?: 7443
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
                    useIoSyncAdapter   = useIoSyncAdapter,
                    ioSyncHost         = ioSyncHost,
                    ioSyncPort         = ioSyncPort,
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
                    wfShowCalories    = wfShowCalories
                )
            }

            dynamicBaseUrl.update(host, port)
            if (useIoSyncAdapter && ioSyncHost.isNotBlank()) startIoSyncPolling(ioSyncHost, ioSyncPort, ioSyncUsername, ioSyncPassword)
            if (wfShowPhoneBattery) startBatteryPolling()
            if (wfShowWeather) startWeatherPolling()
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

    // ── Daten laden ─────────────────────────────────────────────────────────

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val s = _uiState.value
            if (s.useIoSyncAdapter && s.ioSyncHost.isNotBlank()) {
                // Primär: IoSync Adapter
                ioSyncClient.fetchDataPoints(s.ioSyncHost, s.ioSyncPort, s.ioSyncUsername, s.ioSyncPassword)
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
            ioSyncClient.setState(s.ioSyncHost, s.ioSyncPort, s.ioSyncUsername, s.ioSyncPassword, id, value)
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
    fun updateIoSyncSettings(host: String, port: Int, username: String, password: String) {
        viewModelScope.launch {
            dataStore.edit { prefs ->
                prefs[KEY_IOSYNC_HOST]     = host
                prefs[KEY_IOSYNC_PORT]     = port
                prefs[KEY_IOSYNC_USERNAME] = username
                prefs[KEY_IOSYNC_PASSWORD] = password
            }
            _uiState.update { it.copy(ioSyncHost = host, ioSyncPort = port, ioSyncUsername = username, ioSyncPassword = password) }

            ioSyncPollingJob?.cancel()
            if (host.isNotBlank()) {
                startIoSyncPolling(host, port, username, password)
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
                    startIoSyncPolling(s.ioSyncHost, s.ioSyncPort, s.ioSyncUsername, s.ioSyncPassword)
                }
            } else {
                ioSyncPollingJob?.cancel()
            }
            refresh()
        }
    }

    /** Startet das periodische Abrufen vom IoSync Adapter (primäre Datenquelle). */
    private fun startIoSyncPolling(host: String, port: Int, username: String, password: String) {
        ioSyncPollingJob?.cancel()
        ioSyncPollingJob = viewModelScope.launch {
            while (true) {
                ioSyncClient.fetchDataPoints(host, port, username, password)
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
                    }
                    .onFailure { /* Fehler werden im IoSyncClient geloggt */ }
                delay(IOSYNC_POLL_INTERVAL_MS)
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
        showWeather: Boolean,
        showHeartRate: Boolean,
        showOxygen: Boolean,
        showCalories: Boolean
    ) {
        viewModelScope.launch {
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
                prefs[KEY_WF_SECONDS_RING_WIDTH]  = secondsRingWidth
                prefs[KEY_WF_SHOW_WEATHER]        = showWeather
                prefs[KEY_WF_SHOW_HEART_RATE]     = showHeartRate
                prefs[KEY_WF_SHOW_OXYGEN]         = showOxygen
                prefs[KEY_WF_SHOW_CALORIES]       = showCalories
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
                    wfSecondsRingColor = secondsRingColor,
                    wfSecondsRingWidth = secondsRingWidth,
                    wfShowWeather      = showWeather,
                    wfShowHeartRate    = showHeartRate,
                    wfShowOxygen       = showOxygen,
                    wfShowCalories     = showCalories
                )
            }
            val s = _uiState.value
            wearDataLayerService.syncWatchFaceConfigToWear(
                timeColor, dateColor, showSeconds, showTicks, showWeekday, showPhoneBattery, showIoBrokerData,
                showSecondsRing, secondsRingColor, secondsRingWidth,
                s.actionPillEnabled, s.actionPillColorTrue, s.actionPillColorFalse,
                s.actionPillIoBrokerId, s.actionPillValueMode, s.actionPillFixedValue, s.actionPillState,
                showWeather, showHeartRate, showOxygen, showCalories
            )

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
            val s = _uiState.value
            wearDataLayerService.syncWatchFaceConfigToWear(
                s.wfTimeColor, s.wfDateColor, s.wfShowSeconds, s.wfShowTicks, s.wfShowWeekday,
                s.wfShowPhoneBattery, s.wfShowIoBrokerData, s.wfShowSecondsRing,
                s.wfSecondsRingColor, s.wfSecondsRingWidth,
                enabled, colorTrue, colorFalse, ioBrokerId, valueMode, fixedValue, currentState,
                s.wfShowWeather, s.wfShowHeartRate, s.wfShowOxygen, s.wfShowCalories
            )
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

    // ── Handy-Akku ────────────────────────────────────────────────────────────

    /** Startet das periodische Senden des Handy-Akkustands ans Watchface. */
    private fun startBatteryPolling() {
        batteryPollingJob?.cancel()
        batteryPollingJob = viewModelScope.launch {
            while (true) {
                sendPhoneBattery()
                delay(BATTERY_SYNC_INTERVAL_MS)
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
