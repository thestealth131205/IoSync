package com.iosync.app.wear

import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import com.iosync.app.data.network.IoSyncClient
import com.iosync.app.data.sync.IoSyncSyncService
import com.iosync.app.ui.viewmodel.MainViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "WFTriggerListener"
private const val PATH_ACTION_TRIGGER      = "/iosync/watchface/action_trigger"
private const val PATH_P2_PILL1_TRIGGER    = "/iosync/watchface/p2_pill1_trigger"
private const val PATH_P2_PILL2_TRIGGER    = "/iosync/watchface/p2_pill2_trigger"
private const val PATH_P2_SLIDER_VALUE     = "/iosync/watchface/p2_slider_value"
private const val PATH_NTP_OFFSET_FROM_WATCH = "/iosync/watchface/ntp_offset"
private const val KEY_NTP_OFFSET_MS          = "ntp_offset_ms"
private const val PATH_REQUEST_REFRESH       = "/iosync/watchface/request_refresh"

/**
 * Empfängt Doppelklick-Trigger vom Watchface und führt den konfigurierten ioBroker-Befehl aus.
 *
 * Ablauf:
 *  1. Watchface → Doppelklick auf Aktions-Pille → MessageClient.sendMessage(PATH_ACTION_TRIGGER)
 *  2. Dieser Service empfängt die Message (onMessageReceived)
 *  3. Liest Pill-Konfiguration aus dem DataStore
 *  4. Berechnet den zu sendenden Wert (Toggle / true / false / fester Wert)
 *  5. Sendet Wert via IoSyncClient an ioBroker
 *  6. Aktualisiert den neuen Pille-Status im DataStore und überträgt ihn ans Watchface
 */
@AndroidEntryPoint
class WatchFaceTriggerListenerService : WearableListenerService() {

    @Inject lateinit var ioSyncClient: IoSyncClient
    @Inject lateinit var dataStore: DataStore<Preferences>
    @Inject lateinit var wearDataLayerService: WearDataLayerService

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onMessageReceived(messageEvent: MessageEvent) {
        when (messageEvent.path) {
            PATH_ACTION_TRIGGER   -> { Log.d(TAG, "Aktions-Trigger vom Watchface empfangen"); scope.launch { handleTrigger() } }
            PATH_P2_PILL1_TRIGGER -> { Log.d(TAG, "P2-Pille-1-Trigger vom Watchface empfangen"); scope.launch { handleP2Pill1Trigger() } }
            PATH_P2_PILL2_TRIGGER -> { Log.d(TAG, "P2-Pille-2-Trigger vom Watchface empfangen"); scope.launch { handleP2Pill2Trigger() } }
            PATH_P2_SLIDER_VALUE  -> {
                val value = String(messageEvent.data, Charsets.UTF_8)
                Log.d(TAG, "Slider-Wert '$value' vom Watchface empfangen")
                scope.launch { handleSliderValue(value) }
            }
            PATH_REQUEST_REFRESH  -> {
                Log.d(TAG, "Refresh-Anforderung vom Watchface empfangen (Display an) → Sofort-Sync")
                IoSyncSyncService.syncNow(applicationContext)
            }
        }
    }

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        dataEvents.forEach { event ->
            if (event.type == DataEvent.TYPE_CHANGED &&
                event.dataItem.uri.path == PATH_NTP_OFFSET_FROM_WATCH) {
                val dataMap = DataMapItem.fromDataItem(event.dataItem).dataMap
                val offsetMs = dataMap.getLong(KEY_NTP_OFFSET_MS, Long.MIN_VALUE)
                if (offsetMs != Long.MIN_VALUE) {
                    Log.d(TAG, "NTP-Offset von Uhr empfangen: $offsetMs ms")
                    scope.launch {
                        dataStore.edit { prefs ->
                            prefs[MainViewModel.KEY_NTP_OFFSET_FROM_WATCH] = offsetMs
                        }
                    }
                }
            }
        }
        dataEvents.release()
    }

    private suspend fun handleTrigger() {
        val prefs = dataStore.data.first()

        val host        = prefs[MainViewModel.KEY_IOSYNC_HOST]          ?: ""
        val port        = prefs[MainViewModel.KEY_IOSYNC_PORT]          ?: 345
        val useHttps    = prefs[MainViewModel.KEY_IOSYNC_USE_HTTPS]       ?: false
        val username    = prefs[MainViewModel.KEY_IOSYNC_USERNAME]       ?: ""
        val password    = prefs[MainViewModel.KEY_IOSYNC_PASSWORD]       ?: ""
        val ioBrokerId  = prefs[MainViewModel.KEY_ACTION_PILL_IOBROKER_ID] ?: ""
        val valueMode   = prefs[MainViewModel.KEY_ACTION_PILL_VALUE_MODE]  ?: "toggle"
        val fixedValue  = prefs[MainViewModel.KEY_ACTION_PILL_FIXED_VALUE] ?: ""
        val currentState = prefs[MainViewModel.KEY_ACTION_PILL_STATE]    ?: false

        if (host.isBlank() || ioBrokerId.isBlank()) {
            Log.w(TAG, "Trigger ignoriert: Host oder ioBroker-ID nicht konfiguriert")
            return
        }

        val valueToSend = when (valueMode) {
            "true"   -> "true"
            "false"  -> "false"
            "fixed"  -> fixedValue
            "toggle" -> if (currentState) "false" else "true"
            else     -> return
        }

        Log.d(TAG, "Sende '$valueToSend' an ioBroker-Datenpunkt '$ioBrokerId'")
        ioSyncClient.setState(host, port, useHttps, username, password, ioBrokerId, valueToSend)
            .onSuccess {
                Log.d(TAG, "ioBroker-Befehl erfolgreich")
                // Bei Toggle: neuen Status speichern und ans Watchface senden
                val newState = when (valueMode) {
                    "toggle" -> !currentState
                    "true"   -> true
                    "false"  -> false
                    else     -> currentState
                }
                dataStore.edit { p -> p[MainViewModel.KEY_ACTION_PILL_STATE] = newState }
                wearDataLayerService.syncActionPillStateToWear(newState)
            }
            .onFailure {
                Log.e(TAG, "ioBroker-Befehl fehlgeschlagen: ${it.message}")
            }
    }

    private suspend fun handleP2Pill1Trigger() {
        val prefs        = dataStore.data.first()
        val host         = prefs[MainViewModel.KEY_IOSYNC_HOST]           ?: ""
        val port         = prefs[MainViewModel.KEY_IOSYNC_PORT]           ?: 345
        val useHttps     = prefs[MainViewModel.KEY_IOSYNC_USE_HTTPS]      ?: false
        val username     = prefs[MainViewModel.KEY_IOSYNC_USERNAME]       ?: ""
        val password     = prefs[MainViewModel.KEY_IOSYNC_PASSWORD]       ?: ""
        val ioBrokerId   = prefs[MainViewModel.KEY_P2_PILL_IOBROKER_ID]   ?: ""
        val valueMode    = prefs[MainViewModel.KEY_P2_PILL_VALUE_MODE]    ?: "toggle"
        val fixedValue   = prefs[MainViewModel.KEY_P2_PILL_FIXED_VALUE]   ?: ""
        val currentState = prefs[MainViewModel.KEY_P2_PILL1_STATE]        ?: false
        if (host.isBlank() || ioBrokerId.isBlank()) return
        val valueToSend = when (valueMode) {
            "true" -> "true"; "false" -> "false"; "fixed" -> fixedValue
            "toggle" -> if (currentState) "false" else "true"; else -> return
        }
        ioSyncClient.setState(host, port, useHttps, username, password, ioBrokerId, valueToSend)
            .onSuccess {
                val newState = when (valueMode) { "toggle" -> !currentState; "true" -> true; "false" -> false; else -> currentState }
                dataStore.edit { p -> p[MainViewModel.KEY_P2_PILL1_STATE] = newState }
                wearDataLayerService.syncP2PillStatesToWear(newState, prefs[MainViewModel.KEY_P2_PILL2_STATE] ?: false)
            }
            .onFailure { Log.e(TAG, "P2-Pille-1-Befehl fehlgeschlagen: ${it.message}") }
    }

    private suspend fun handleP2Pill2Trigger() {
        val prefs        = dataStore.data.first()
        val host         = prefs[MainViewModel.KEY_IOSYNC_HOST]           ?: ""
        val port         = prefs[MainViewModel.KEY_IOSYNC_PORT]           ?: 345
        val useHttps     = prefs[MainViewModel.KEY_IOSYNC_USE_HTTPS]      ?: false
        val username     = prefs[MainViewModel.KEY_IOSYNC_USERNAME]       ?: ""
        val password     = prefs[MainViewModel.KEY_IOSYNC_PASSWORD]       ?: ""
        val ioBrokerId   = prefs[MainViewModel.KEY_P2_PILL2_IOBROKER_ID]  ?: ""
        val valueMode    = prefs[MainViewModel.KEY_P2_PILL2_VALUE_MODE]   ?: "toggle"
        val fixedValue   = prefs[MainViewModel.KEY_P2_PILL2_FIXED_VALUE]  ?: ""
        val currentState = prefs[MainViewModel.KEY_P2_PILL2_STATE]        ?: false
        if (host.isBlank() || ioBrokerId.isBlank()) return
        val valueToSend = when (valueMode) {
            "true" -> "true"; "false" -> "false"; "fixed" -> fixedValue
            "toggle" -> if (currentState) "false" else "true"; else -> return
        }
        ioSyncClient.setState(host, port, useHttps, username, password, ioBrokerId, valueToSend)
            .onSuccess {
                val newState = when (valueMode) { "toggle" -> !currentState; "true" -> true; "false" -> false; else -> currentState }
                dataStore.edit { p -> p[MainViewModel.KEY_P2_PILL2_STATE] = newState }
                wearDataLayerService.syncP2PillStatesToWear(prefs[MainViewModel.KEY_P2_PILL1_STATE] ?: false, newState)
            }
            .onFailure { Log.e(TAG, "P2-Pille-2-Befehl fehlgeschlagen: ${it.message}") }
    }

    /** Schreibt den am Slider (Seite 2) getippten Wert in den ioBroker-Datenpunkt des Balkens. */
    private suspend fun handleSliderValue(value: String) {
        val prefs      = dataStore.data.first()
        val host       = prefs[MainViewModel.KEY_IOSYNC_HOST]      ?: ""
        val port       = prefs[MainViewModel.KEY_IOSYNC_PORT]      ?: 345
        val useHttps   = prefs[MainViewModel.KEY_IOSYNC_USE_HTTPS] ?: false
        val username   = prefs[MainViewModel.KEY_IOSYNC_USERNAME]  ?: ""
        val password   = prefs[MainViewModel.KEY_IOSYNC_PASSWORD]  ?: ""
        val ioBrokerId = prefs[MainViewModel.KEY_P2_BAR_ID]        ?: ""
        if (host.isBlank() || ioBrokerId.isBlank()) {
            Log.w(TAG, "Slider-Wert ignoriert: Host oder Balken-ID nicht konfiguriert")
            return
        }
        Log.d(TAG, "Sende Slider-Wert '$value' an ioBroker-Datenpunkt '$ioBrokerId'")
        ioSyncClient.setState(host, port, useHttps, username, password, ioBrokerId, value)
            .onFailure { Log.e(TAG, "Slider-Wert fehlgeschlagen: ${it.message}") }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
