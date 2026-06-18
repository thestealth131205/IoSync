package com.iosync.watchface.data

import android.util.Log
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject

private const val TAG = "WatchKlipperProgressSocket"

/**
 * Eigenständiger Moonraker-WebSocket NUR für den Druckfortschritt
 * (display_status.progress).
 *
 * Wird ausschließlich für die Seite-1-Boden-Komplikation "Druck-Status (%)" genutzt
 * und ist komplett unabhängig vom Seite-3-HTTP-Polling ([WatchDataSyncManager.klipperLoop]).
 * Es wird per JSON-RPC NUR `display_status` abonniert → es kommt nur dieser eine Wert
 * über die Leitung, kein vollständiger Druckerstatus.
 */
object WatchKlipperProgressSocket {

    private var webSocket: WebSocket? = null
    @Volatile private var onProgress: ((Float) -> Unit)? = null

    /** Startet den WebSocket. Ein bereits laufender wird zuvor geschlossen. */
    fun start(host: String, port: Int, apiKey: String, onProgress: (Float) -> Unit) {
        stop()
        this.onProgress = onProgress
        val url = buildWsUrl(host, port)
        val request = Request.Builder().url(url).apply {
            if (apiKey.isNotBlank()) addHeader("X-Api-Key", apiKey)
        }.build()
        Log.d(TAG, "Verbinde Moonraker-WebSocket: $url")
        webSocket = WatchKlipperClient.okHttpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) {
                // Nur display_status.progress abonnieren → nur dieser eine Wert.
                val subscribe = JSONObject().apply {
                    put("jsonrpc", "2.0")
                    put("method", "printer.objects.subscribe")
                    put("params", JSONObject().apply {
                        put("objects", JSONObject().apply {
                            put("display_status", JSONArray().put("progress"))
                        })
                    })
                    put("id", 1)
                }
                ws.send(subscribe.toString())
            }

            override fun onMessage(ws: WebSocket, text: String) {
                parseProgress(text)?.let { this@WatchKlipperProgressSocket.onProgress?.invoke(it) }
            }

            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                Log.w(TAG, "WebSocket-Fehler: ${t.message}")
            }
        })
    }

    /** Schließt den WebSocket und löst den Callback. */
    fun stop() {
        webSocket?.cancel()
        webSocket = null
        onProgress = null
    }

    /**
     * Liest den Fortschritt (0.0–1.0) aus einer Moonraker-Nachricht.
     * Unterstützt sowohl die Abo-Antwort (result.status.display_status.progress)
     * als auch laufende Updates (notify_status_update → params[0].display_status.progress).
     */
    private fun parseProgress(text: String): Float? {
        return try {
            val json = JSONObject(text)
            // Abo-Antwort
            json.optJSONObject("result")?.optJSONObject("status")
                ?.optJSONObject("display_status")?.let { ds ->
                    if (ds.has("progress")) return ds.getDouble("progress").toFloat()
                }
            // Laufende Updates
            if (json.optString("method") == "notify_status_update") {
                val params = json.optJSONArray("params") ?: return null
                params.optJSONObject(0)?.optJSONObject("display_status")?.let { ds ->
                    if (ds.has("progress")) return ds.getDouble("progress").toFloat()
                }
            }
            null
        } catch (e: Exception) {
            null
        }
    }

    private fun buildWsUrl(host: String, port: Int): String {
        val h = host.trim().trimEnd('/')
        val base = when {
            h.startsWith("https://") -> "wss://" + h.removePrefix("https://")
            h.startsWith("http://")  -> "ws://"  + h.removePrefix("http://")
            else                     -> "ws://$h"
        }
        return "$base:$port/websocket"
    }
}
