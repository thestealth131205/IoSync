package com.iosync.wear.data.repository

import android.util.Log
import com.iosync.wear.data.model.SmartHomeState
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "WearRepository"

@Singleton
class WearRepository @Inject constructor(
    private val moshi: Moshi
) {

    private val statesAdapter by lazy {
        moshi.adapter<List<SmartHomeState>>(
            Types.newParameterizedType(List::class.java, SmartHomeState::class.java)
        )
    }

    private val _states = MutableStateFlow<List<SmartHomeState>>(emptyList())
    val states: StateFlow<List<SmartHomeState>> = _states.asStateFlow()

    private val _lastSync = MutableStateFlow(0L)
    val lastSync: StateFlow<Long> = _lastSync.asStateFlow()

    private val _isPhoneConnected = MutableStateFlow(false)
    val isPhoneConnected: StateFlow<Boolean> = _isPhoneConnected.asStateFlow()

    fun updateStatesFromJson(json: String) {
        try {
            val parsed = statesAdapter.fromJson(json) ?: emptyList()
            _states.value = parsed.sortedBy { it.name }
            _lastSync.value = System.currentTimeMillis()
            Log.d(TAG, "Updated ${parsed.size} states from Data Layer")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse states JSON", e)
        }
    }

    fun setPhoneConnected(connected: Boolean) {
        _isPhoneConnected.value = connected
    }

    fun getStateById(id: String): SmartHomeState? =
        _states.value.firstOrNull { it.id == id }

    fun getStatesByRoom(room: String): List<SmartHomeState> =
        _states.value.filter { it.room == room }

    fun getAvailableRooms(): List<String> =
        _states.value.mapNotNull { it.room }.distinct().sorted()
}
