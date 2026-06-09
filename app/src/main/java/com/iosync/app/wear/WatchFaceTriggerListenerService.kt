package com.iosync.app.wear

import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.WearableListenerService
import com.iosync.app.ui.viewmodel.MainViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "WFTriggerListener"
private const val PATH_NTP_OFFSET_FROM_WATCH = "/iosync/watchface/ntp_offset"
private const val KEY_NTP_OFFSET_MS          = "ntp_offset_ms"

/**
 * Empfängt den von der Uhr selbst ermittelten NTP-Offset und legt ihn im DataStore ab.
 *
 * Ab v5 schaltet die Uhr ioBroker-Datenpunkte (Pillen, Slider) direkt am Adapter –
 * die früheren Trigger-Messages (Aktions-Pille, Seite-2-Pillen, Slider) und die
 * Refresh-Anforderung entfallen daher. Übrig bleibt der NTP-Offset, den die Uhr
 * weiterhin ans Handy meldet (zur Anzeige / Abgleich).
 */
@AndroidEntryPoint
class WatchFaceTriggerListenerService : WearableListenerService() {

    @Inject lateinit var dataStore: DataStore<Preferences>

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

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

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
