package com.iosync.app.data.repository

import android.util.Log
import com.iosync.app.data.model.SmartHomeState
import com.iosync.app.data.model.StateControlCommand
import com.iosync.app.data.model.StateUpdateEvent
import com.iosync.app.data.network.ApiService
import com.iosync.app.data.network.WebSocketManager
import com.iosync.app.data.network.WebSocketStatus
import com.iosync.app.wear.WearDataLayerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "SmartHomeRepository"

sealed class RepoResult<out T> {
    data class Success<T>(val data: T) : RepoResult<T>()
    data class Error(val message: String, val cause: Throwable? = null) : RepoResult<Nothing>()
    data object Loading : RepoResult<Nothing>()
}

@Singleton
class SmartHomeRepository @Inject constructor(
    private val apiService: ApiService,
    private val webSocketManager: WebSocketManager,
    private val wearDataLayerService: WearDataLayerService
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _states = MutableStateFlow<Map<String, SmartHomeState>>(emptyMap())
    val states: StateFlow<Map<String, SmartHomeState>> = _states.asStateFlow()

    val connectionStatus: StateFlow<WebSocketStatus> = webSocketManager.status
    val stateUpdates: SharedFlow<StateUpdateEvent> = webSocketManager.stateUpdates

    init {
        // Apply real-time updates from WebSocket to the local cache
        webSocketManager.stateUpdates
            .onEach { event -> applyUpdate(event) }
            .catch { e -> Log.e(TAG, "Error processing state update", e) }
            .launchIn(scope)
    }

    suspend fun fetchAllStates(pattern: String = "*"): RepoResult<List<SmartHomeState>> {
        return withContext(Dispatchers.IO) {
            try {
                val response = apiService.getAllStates(pattern)
                if (response.isSuccessful) {
                    val stateList = response.body() ?: emptyList()
                    val stateMap = stateList.associateBy { it.id }
                    _states.value = stateMap
                    // Sync to wearables after fresh fetch
                    scope.launch { wearDataLayerService.syncStatesToWear(stateList) }
                    RepoResult.Success(stateList)
                } else {
                    RepoResult.Error("HTTP ${response.code()}: ${response.message()}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "fetchAllStates failed", e)
                RepoResult.Error(e.message ?: "Unknown error", e)
            }
        }
    }

    suspend fun fetchState(id: String): RepoResult<SmartHomeState> {
        return withContext(Dispatchers.IO) {
            try {
                val response = apiService.getState(id)
                if (response.isSuccessful) {
                    val state = response.body()!!
                    updateLocalState(state)
                    RepoResult.Success(state)
                } else {
                    RepoResult.Error("HTTP ${response.code()}: ${response.message()}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "fetchState($id) failed", e)
                RepoResult.Error(e.message ?: "Unknown error", e)
            }
        }
    }

    suspend fun setState(id: String, value: String): RepoResult<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val response = apiService.setStatePost(StateControlCommand(id = id, value = value))
                if (response.isSuccessful) {
                    RepoResult.Success(Unit)
                } else {
                    RepoResult.Error("HTTP ${response.code()}: ${response.message()}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "setState($id, $value) failed", e)
                RepoResult.Error(e.message ?: "Unknown error", e)
            }
        }
    }

    fun connectWebSocket(url: String) {
        webSocketManager.connect(url)
    }

    fun disconnectWebSocket() {
        webSocketManager.disconnect()
    }

    private fun applyUpdate(event: StateUpdateEvent) {
        val current = _states.value.toMutableMap()
        val existing = current[event.id]
        if (existing != null) {
            current[event.id] = existing.copy(
                value = event.value,
                timestamp = event.timestamp,
                ack = event.ack,
                quality = event.quality
            )
            _states.value = current
            // Push individual update to Wear
            scope.launch {
                wearDataLayerService.syncStatesToWear(current.values.toList())
            }
        }
    }

    private fun updateLocalState(state: SmartHomeState) {
        val current = _states.value.toMutableMap()
        current[state.id] = state
        _states.value = current
    }
}
