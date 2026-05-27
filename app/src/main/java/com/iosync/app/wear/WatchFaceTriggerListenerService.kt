package com.iosync.app.wear

import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import com.iosync.app.data.network.IoSyncClient
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
private const val PATH_ACTION_TRIGGER = "/iosync/watchface/action_trigger"

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
        if (messageEvent.path != PATH_ACTION_TRIGGER) return
        Log.d(TAG, "Aktions-Trigger vom Watchface empfangen")
        scope.launch { handleTrigger() }
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

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
