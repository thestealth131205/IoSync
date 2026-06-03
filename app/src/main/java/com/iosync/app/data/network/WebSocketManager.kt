package com.iosync.app.data.network

import android.util.Log
import com.iosync.app.data.model.StateUpdateEvent
import com.squareup.moshi.Moshi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "WebSocketManager"
private const val RECONNECT_DELAY_MS = 5_000L
private const val MAX_RECONNECT_ATTEMPTS = 10

enum class WebSocketStatus {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    RECONNECTING,
    FAILED
}

@Singleton
class WebSocketManager @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val moshi: Moshi
) {

    private var webSocket: WebSocket? = null
    private var serverUrl: String = ""
    private var reconnectAttempts = 0
    private var shouldReconnect = true

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _status = MutableStateFlow(WebSocketStatus.DISCONNECTED)
    val status: StateFlow<WebSocketStatus> = _status.asStateFlow()

    private val _stateUpdates = MutableSharedFlow<StateUpdateEvent>(replay = 0, extraBufferCapacity = 64)
    val stateUpdates: SharedFlow<StateUpdateEvent> = _stateUpdates.asSharedFlow()

    private val _rawMessages = MutableSharedFlow<String>(replay = 0, extraBufferCapacity = 64)
    val rawMessages: SharedFlow<String> = _rawMessages.asSharedFlow()

    private val stateUpdateAdapter by lazy {
        moshi.adapter(StateUpdateEvent::class.java)
    }

    fun connect(url: String) {
        serverUrl = url
        shouldReconnect = true
        reconnectAttempts = 0
        openConnection()
    }

    fun disconnect() {
        shouldReconnect = false
        webSocket?.close(1000, "Client disconnect")
        webSocket = null
        _status.value = WebSocketStatus.DISCONNECTED
    }

    fun send(message: String): Boolean {
        return webSocket?.send(message) ?: false
    }

    private fun openConnection() {
        if (_status.value == WebSocketStatus.CONNECTING) return
        if (serverUrl.isBlank()) {
            Log.w(TAG, "openConnection aufgerufen ohne gültige URL – abgebrochen")
            _status.value = WebSocketStatus.DISCONNECTED
            return
        }

        _status.value = WebSocketStatus.CONNECTING
        Log.d(TAG, "Connecting to $serverUrl")

        val request = try {
            Request.Builder()
                .url(serverUrl)
                .addHeader("User-Agent", "IoSync-Android/1.0")
                .build()
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "Ungültige WebSocket-URL '$serverUrl': ${e.message}")
            _status.value = WebSocketStatus.FAILED
            return
        }

        webSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {

            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "WebSocket connected")
                reconnectAttempts = 0
                _status.value = WebSocketStatus.CONNECTED
                // Subscribe to all state changes
                webSocket.send("""{"type":"subscribe","pattern":"*"}""")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                scope.launch {
                    _rawMessages.emit(text)
                    parseAndEmit(text)
                }
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                scope.launch {
                    val text = bytes.utf8()
                    _rawMessages.emit(text)
                    parseAndEmit(text)
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket closing: $code $reason")
                webSocket.close(1000, null)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket closed: $code $reason")
                _status.value = WebSocketStatus.DISCONNECTED
                if (shouldReconnect) scheduleReconnect()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "WebSocket failure: ${t.message}")
                _status.value = WebSocketStatus.RECONNECTING
                if (shouldReconnect) scheduleReconnect()
            }
        })
    }

    private fun parseAndEmit(text: String) {
        try {
            val event = stateUpdateAdapter.fromJson(text)
            if (event != null) {
                scope.launch { _stateUpdates.emit(event) }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not parse message as StateUpdateEvent: ${e.message}")
        }
    }

    private fun scheduleReconnect() {
        if (reconnectAttempts >= MAX_RECONNECT_ATTEMPTS) {
            Log.e(TAG, "Max reconnect attempts reached — giving up")
            _status.value = WebSocketStatus.FAILED
            return
        }
        reconnectAttempts++
        val delayMs = RECONNECT_DELAY_MS * reconnectAttempts
        Log.d(TAG, "Reconnecting in ${delayMs}ms (attempt $reconnectAttempts/$MAX_RECONNECT_ATTEMPTS)")
        scope.launch {
            delay(delayMs)
            if (shouldReconnect) openConnection()
        }
    }
}
