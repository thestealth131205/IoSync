package com.iosync.app.data.network

import android.util.Log
import com.iosync.app.data.model.SmartHomeState
import com.iosync.app.data.model.StateType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

private const val TAG = "IoSyncClient"

/**
 * HTTP/HTTPS-Client für den IoSync ioBroker-Adapter.
 *
 * Verwendet einen Trust-All-SSL-Context, da der Adapter typischerweise ein
 * selbstsigniertes Zertifikat im lokalen Netzwerk ausstellt.
 *
 * API-Endpunkte:
 *   GET  /api/datapoints           → alle konfigurierten Datenpunkte
 *   GET  /api/datapoints/{alias}   → einzelner Datenpunkt
 *   GET  /api/state/{id}           → Direktabfrage nach ioBroker-ID
 *   POST /api/setState             → Wert in ioBroker schreiben  { id, value }
 *   GET  /api/health               → Adapter-Status
 */
@Singleton
class IoSyncClient @Inject constructor() {

    private val trustAllManager = object : X509TrustManager {
        override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) = Unit
        override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) = Unit
        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
    }

    // Separater OkHttp-Client mit Trust-All für selbstsignierte LAN-Zertifikate
    val okHttpClient: OkHttpClient by lazy {
        val sslContext = SSLContext.getInstance("TLS").apply {
            init(null, arrayOf<TrustManager>(trustAllManager), SecureRandom())
        }
        OkHttpClient.Builder()
            .sslSocketFactory(sslContext.socketFactory, trustAllManager)
            .hostnameVerifier { _, _ -> true }
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .build()
    }

    /**
     * Ruft alle konfigurierten Datenpunkte vom IoSync Adapter ab.
     * Gibt sie als SmartHomeState-Liste zurück (alias → name, id bleibt id).
     */
    suspend fun fetchDataPoints(
        host: String,
        port: Int,
        useHttps: Boolean = false,
        username: String,
        password: String
    ): Result<List<SmartHomeState>> = withContext(Dispatchers.IO) {
        runCatching {
            val url = buildUrl(host, port, "/api/datapoints", useHttps)
            val response = okHttpClient.newCall(
                buildRequest(url, username, password).get().build()
            ).execute()
            check(response.isSuccessful) { "HTTP ${response.code}" }
            val root = JSONObject(response.body!!.string())
            val arr = root.getJSONArray("datapoints")
            (0 until arr.length()).map { i ->
                val dp = arr.getJSONObject(i)
                SmartHomeState(
                    id        = dp.getString("id"),
                    name      = dp.getString("alias"),
                    value     = if (dp.isNull("value")) null else dp.get("value").toString(),
                    type      = when (dp.optString("type")) {
                        "boolean" -> StateType.BOOLEAN
                        "number"  -> StateType.NUMBER
                        "string"  -> StateType.STRING
                        else      -> StateType.MIXED
                    },
                    unit      = dp.optString("unit", "").takeIf { it.isNotEmpty() },
                    timestamp = dp.optLong("timestamp", System.currentTimeMillis())
                )
            }
        }.onFailure { Log.e(TAG, "fetchDataPoints fehlgeschlagen: ${it.message}") }
    }

    /**
     * Schreibt einen Wert in ioBroker über den IoSync Adapter.
     * @param id    ioBroker-Objekt-ID, z.B. "hm-rpc.0.OEQ123.1.STATE"
     * @param value Neuer Wert als String (Adapter erkennt Boolean/Number automatisch)
     */
    suspend fun setState(
        host: String,
        port: Int,
        useHttps: Boolean = false,
        username: String,
        password: String,
        id: String,
        value: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val url  = buildUrl(host, port, "/api/setState", useHttps)
            val body = JSONObject().apply { put("id", id); put("value", value) }
                .toString().toRequestBody("application/json".toMediaType())
            val response = okHttpClient.newCall(
                buildRequest(url, username, password).post(body).build()
            ).execute()
            check(response.isSuccessful) { "HTTP ${response.code}" }
        }.onFailure { Log.e(TAG, "setState($id) fehlgeschlagen: ${it.message}") }
    }

    /**
     * Einfacher Health-Check gegen den Adapter.
     */
    suspend fun checkHealth(
        host: String,
        port: Int,
        useHttps: Boolean = false,
        username: String,
        password: String
    ): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val url = buildUrl(host, port, "/api/health", useHttps)
            val response = okHttpClient.newCall(
                buildRequest(url, username, password).get().build()
            ).execute()
            response.isSuccessful
        }.getOrDefault(false)
    }

    private fun buildUrl(host: String, port: Int, path: String, useHttps: Boolean = false): String {
        val h = host.trim().trimEnd('/')
        return if (h.startsWith("http://") || h.startsWith("https://")) {
            "$h:$port$path"
        } else {
            val scheme = if (useHttps) "https" else "http"
            "$scheme://$h:$port$path"
        }
    }

    private fun buildRequest(url: String, username: String, password: String) =
        Request.Builder().url(url).apply {
            if (username.isNotBlank() && password.isNotBlank()) {
                addHeader("Authorization", Credentials.basic(username, password))
            }
        }
}
