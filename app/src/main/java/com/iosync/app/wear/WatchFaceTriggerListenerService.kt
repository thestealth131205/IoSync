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
import com.iosync.app.data.sleep.HealthySleep
import com.iosync.app.data.sync.IoSyncSyncService
import com.iosync.app.ui.viewmodel.MainViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

private const val TAG = "WFTriggerListener"
private const val PATH_NTP_OFFSET_FROM_WATCH = "/iosync/watchface/ntp_offset"
private const val KEY_NTP_OFFSET_MS          = "ntp_offset_ms"
private const val PATH_REQUEST_BATTERY       = "/iosync/phone/request_battery"
// Schlaf-/Wach-Status der Healthy-Uhr-App, vom Watchface weitergereicht (HealthySleepStateProvider)
private const val PATH_HEALTHY_SLEEP_STATE   = "/iosync/watch/healthy_sleep"
private const val KEY_SLEEPING               = "sleeping"
private const val KEY_CHANGED_AT             = "changed_at"
private const val KEY_SLEEP_START            = "sleep_start"
private const val KEY_SLEEP_END              = "sleep_end"

/**
 * Empfängt den von der Uhr selbst ermittelten NTP-Offset und legt ihn im DataStore ab.
 *
 * Ab v5 schaltet die Uhr ioBroker-Datenpunkte (Pillen, Slider) direkt am Adapter –
 * die früheren Trigger-Messages (Aktions-Pille, Seite-2-Pillen, Slider) und die
 * Refresh-Anforderung entfallen daher. Übrig bleibt der NTP-Offset, den die Uhr
 * weiterhin ans Handy meldet (zur Anzeige / Abgleich).
 *
 * Ab v5.3 zusätzlich: Schlaf-/Wach-Status der Healthy-Uhr-App → Home Assistant ([HealthySleep]).
 */
@AndroidEntryPoint
class WatchFaceTriggerListenerService : WearableListenerService() {

    @Inject lateinit var dataStore: DataStore<Preferences>

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        dataEvents.forEach { event ->
            if (event.type == DataEvent.TYPE_CHANGED &&
                event.dataItem.uri.path == PATH_HEALTHY_SLEEP_STATE) {
                val dataMap = DataMapItem.fromDataItem(event.dataItem).dataMap
                val sleeping = dataMap.getBoolean(KEY_SLEEPING)
                Log.d(TAG, "Schlafstatus von Healthy empfangen: ${if (sleeping) "schläft" else "wach"}")
                // Blockierend speichern: der Dienst kann direkt nach onDataChanged beendet werden.
                runBlocking {
                    HealthySleep.onStateFromWatch(
                        context    = this@WatchFaceTriggerListenerService,
                        dataStore  = dataStore,
                        sleeping   = sleeping,
                        changedAt  = dataMap.getLong(KEY_CHANGED_AT, System.currentTimeMillis()),
                        sleepStart = dataMap.getLong(KEY_SLEEP_START, 0L).takeIf { it > 0 },
                        sleepEnd   = dataMap.getLong(KEY_SLEEP_END, 0L).takeIf { it > 0 }
                    )
                }
            } else if (event.type == DataEvent.TYPE_CHANGED &&
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

    override fun onMessageReceived(event: MessageEvent) {
        if (event.path == PATH_REQUEST_BATTERY) {
            Log.d(TAG, "Akku-Anfrage von Uhr empfangen → sofortiger Akku-Push")
            IoSyncSyncService.syncNow(this)
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
