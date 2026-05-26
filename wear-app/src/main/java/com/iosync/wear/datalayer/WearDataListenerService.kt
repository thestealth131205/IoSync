package com.iosync.wear.datalayer

import android.util.Log
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.WearableListenerService
import com.iosync.wear.data.repository.WearRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "WearDataListener"
private const val PATH_STATES = "/iosync/smarthome/states"
private const val KEY_STATES_JSON = "states_json"
private const val KEY_TIMESTAMP = "timestamp"

@AndroidEntryPoint
class WearDataListenerService : WearableListenerService() {

    @Inject
    lateinit var wearRepository: WearRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        dataEvents.forEach { event ->
            when {
                event.type == DataEvent.TYPE_CHANGED &&
                event.dataItem.uri.path?.startsWith("/iosync/smarthome") == true -> {
                    handleSmartHomeDataChange(event)
                }
                event.type == DataEvent.TYPE_DELETED -> {
                    Log.d(TAG, "Data item deleted: ${event.dataItem.uri.path}")
                }
            }
        }
    }

    private fun handleSmartHomeDataChange(event: DataEvent) {
        val path = event.dataItem.uri.path ?: return
        Log.d(TAG, "Data changed on path: $path")

        if (path == PATH_STATES) {
            val dataMap = DataMapItem.fromDataItem(event.dataItem).dataMap
            val json = dataMap.getString(KEY_STATES_JSON) ?: return
            val timestamp = dataMap.getLong(KEY_TIMESTAMP, 0L)
            Log.d(TAG, "Received states update, timestamp=$timestamp")

            scope.launch {
                wearRepository.updateStatesFromJson(json)
                wearRepository.setPhoneConnected(true)
            }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
