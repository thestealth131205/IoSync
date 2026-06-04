package com.iosync.app.data.network

import android.util.Log
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

private const val TAG = "IoSyncPushClient"
private const val RECONNECT_DELAY_MS = 5_000L
private const val MAX_RECONNECT_DELAY_MS = 60_000L

/**
 * Echtzeit-Push-Empfänger für den IoSync ioBroker-Adapter.
 *
 * Hält eine Server-Sent-Events-Verbindung zu `GET /api/stream` offen und ruft bei
 * jeder Wertänderung [onEvent] auf. Der IoSync-Adapter sendet dort ein "update"-Event,
 * sobald sich ein konfigurierter Datenpunkt relevant ändert (Boolean-Wechsel,
 * Dezimal ≥ 0.2, Ganzzahl ≥ 1).
 *
 * Verwendet einen Trust-All-SSL-Context, da der Adapter im LAN typischerweise ein
 * selbstsigniertes Zertifikat ausstellt. Read-Timeout = 0 (kein Timeout), damit die
 * Verbindung dauerhaft offen bleibt.
 *
 * Reconnect erfolgt automatisch mit linearem Backoff. Liefert der Server 404/403
 * (Push serverseitig deaktiviert), wird nicht weiter versucht.
 */
@Singleton
class IoSyncPushClient @Inject constructor() {

    private val trustAllManager = object : X509TrustManager {
        override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) = Unit
        override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) = Unit
        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
    }

    private val client: OkHttpClient by lazy {
        val sslContext = SSLContext.getInstance("TLS").apply {
            init(null, arrayOf<TrustManager>(trustAllManager), SecureRandom())
        }
        OkHttpClient.Builder()
            .sslSocketFactory(sslContext.socketFactory, trustAllManager)
            .hostnameVerifier { _, _ -> true }
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS)   // SSE: kein Read-Timeout
            .writeTimeout(15, TimeUnit.SECONDS)
            .pingInterval(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    private var eventSource: EventSource? = null
    private var reconnectThread: Thread? = null

    @Volatile private var active = false
    private var reconnectAttempts = 0

    // Verbindungsparameter (für Reconnect zwischengespeichert)
    private var url: String = ""
    private var username: String = ""
    private var password: String = ""
    private var onEventCallback: ((String) -> Unit)? = null
    private var onConnectedCallback: ((Boolean) -> Unit)? = null

    /**
     * Startet die Push-Verbindung. Idempotent: ein bereits laufender Stream wird zuvor beendet.
     *
     * @param onEvent     wird mit dem JSON-Datenteil jedes "update"-Events aufgerufen
     * @param onConnected Statuscallback (true = verbunden, false = getrennt)
     */
    @Synchronized
    fun start(
        host: String,
        port: Int,
        useHttps: Boolean,
        username: String,
        password: String,
        onEvent: (String) -> Unit,
        onConnected: (Boolean) -> Unit = {}
    ) {
        stop()
        if (host.isBlank()) {
            Log.w(TAG, "start abgebrochen – kein Host konfiguriert")
            return
        }
        this.url = buildUrl(host, port, useHttps)
        this.username = username
        this.password = password
        this.onEventCallback = onEvent
        this.onConnectedCallback = onConnected
        this.active = true
        this.reconnectAttempts = 0
        openConnection()
    }

    /** Beendet die Push-Verbindung und unterbindet weitere Reconnects. */
    @Synchronized
    fun stop() {
        active = false
        eventSource?.cancel()
        eventSource = null
        reconnectThread?.interrupt()
        reconnectThread = null
    }

    private fun openConnection() {
        if (!active) return
        val request = try {
            Request.Builder()
                .url(url)
                .addHeader("Accept", "text/event-stream")
                .apply {
                    if (username.isNotBlank() && password.isNotBlank()) {
                        addHeader("Authorization", Credentials.basic(username, password))
                    }
                }
                .build()
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "Ungültige Stream-URL '$url': ${e.message}")
            active = false
            return
        }

        Log.d(TAG, "Verbinde zu $url")
        eventSource = EventSources.createFactory(client).newEventSource(request, object : EventSourceListener() {

            override fun onOpen(eventSource: EventSource, response: Response) {
                Log.d(TAG, "Push-Verbindung offen")
                reconnectAttempts = 0
                onConnectedCallback?.invoke(true)
            }

            override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                onEventCallback?.invoke(data)
            }

            override fun onClosed(eventSource: EventSource) {
                Log.d(TAG, "Push-Verbindung geschlossen")
                onConnectedCallback?.invoke(false)
                scheduleReconnect()
            }

            override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
                onConnectedCallback?.invoke(false)
                val code = response?.code
                if (code == 404 || code == 403) {
                    Log.i(TAG, "Push serverseitig deaktiviert (HTTP $code) – kein Reconnect")
                    active = false
                    return
                }
                Log.w(TAG, "Push-Verbindung fehlgeschlagen: ${t?.message ?: "HTTP $code"}")
                scheduleReconnect()
            }
        })
    }

    private fun scheduleReconnect() {
        if (!active) return
        reconnectAttempts++
        val delayMs = (RECONNECT_DELAY_MS * reconnectAttempts).coerceAtMost(MAX_RECONNECT_DELAY_MS)
        Log.d(TAG, "Reconnect in ${delayMs}ms (Versuch $reconnectAttempts)")
        reconnectThread = Thread {
            try {
                Thread.sleep(delayMs)
                synchronized(this) { if (active) openConnection() }
            } catch (e: InterruptedException) {
                // stop() wurde aufgerufen
            }
        }.also { it.isDaemon = true; it.start() }
    }

    private fun buildUrl(host: String, port: Int, useHttps: Boolean): String {
        val h = host.trim().trimEnd('/')
        return if (h.startsWith("http://") || h.startsWith("https://")) {
            "$h:$port/api/stream"
        } else {
            val scheme = if (useHttps) "https" else "http"
            "$scheme://$h:$port/api/stream"
        }
    }
}
