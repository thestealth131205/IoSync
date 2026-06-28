package com.iosync.watchface.data

import android.util.Log
import com.iosync.watchface.datalayer.CachedState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.ConnectionPool
import okhttp3.Credentials
import okhttp3.Dispatcher
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

private const val TAG = "WatchIoSyncClient"

/**
 * HTTP/HTTPS-Client für den IoSync ioBroker-Adapter — direkt von der Uhr aus.
 *
 * Portiert aus der App (IoSyncClient). Die Uhr fragt die Datenpunkte selbst ab
 * und schreibt Werte (setState) direkt in den Adapter, ohne Umweg übers Handy.
 *
 * Trust-All-SSL-Context, da der Adapter typischerweise ein selbstsigniertes
 * Zertifikat im lokalen Netz ausstellt.
 */
object WatchIoSyncClient {

    // Befehls-Retry: Anzahl Versuche und Backoff-Basis (wird je Versuch multipliziert:
    // 300 ms, 600 ms). Hält den Befehl reaktionsschnell, überbrückt aber kurze
    // Verstopfungen der BT-Leitung.
    private const val SET_STATE_MAX_ATTEMPTS = 3
    private const val SET_STATE_BACKOFF_MS = 300L

    private val trustAllManager = object : X509TrustManager {
        override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) = Unit
        override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) = Unit
        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
    }

    private fun trustAllSslContext(): SSLContext =
        SSLContext.getInstance("TLS").apply {
            init(null, arrayOf<TrustManager>(trustAllManager), SecureRandom())
        }

    // Client für den schweren Datenabruf (große /api/datapoints-Antworten, lange Timeouts).
    val okHttpClient: OkHttpClient by lazy {
        val sslContext = trustAllSslContext()
        OkHttpClient.Builder()
            .sslSocketFactory(sslContext.socketFactory, trustAllManager)
            .hostnameVerifier { _, _ -> true }
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .build()
    }

    // Separater, schlanker Client NUR für Schaltbefehle (setState).
    // Eigener Dispatcher + eigener ConnectionPool, damit ein Tap-POST nie hinter
    // einem laufenden, großen /api/datapoints-GET im Pool warten muss. Kurze Timeouts,
    // damit ein hängender Versuch schnell abbricht und der Retry greifen kann.
    private val commandClient: OkHttpClient by lazy {
        val sslContext = trustAllSslContext()
        val dispatcher = Dispatcher().apply {
            maxRequests = 4
            maxRequestsPerHost = 4
        }
        OkHttpClient.Builder()
            .sslSocketFactory(sslContext.socketFactory, trustAllManager)
            .hostnameVerifier { _, _ -> true }
            .dispatcher(dispatcher)
            .connectionPool(ConnectionPool(2, 5, TimeUnit.MINUTES))
            .connectTimeout(6, TimeUnit.SECONDS)
            .readTimeout(6, TimeUnit.SECONDS)
            .writeTimeout(6, TimeUnit.SECONDS)
            .build()
    }

    /**
     * Ruft alle konfigurierten Datenpunkte vom IoSync Adapter ab.
     */
    suspend fun fetchDataPoints(
        host: String,
        port: Int,
        useHttps: Boolean,
        username: String,
        password: String
    ): Result<List<CachedState>> = withContext(Dispatchers.IO) {
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
                CachedState(
                    id    = dp.getString("id"),
                    name  = dp.getString("alias"),
                    value = if (dp.isNull("value")) null else dp.get("value").toString(),
                    unit  = dp.optString("unit", "").takeIf { it.isNotEmpty() },
                    type  = dp.optString("type", "mixed")
                )
            }
        }.onFailure { Log.e(TAG, "fetchDataPoints fehlgeschlagen: ${it.message}") }
    }

    /**
     * Schreibt einen Wert in ioBroker über den IoSync Adapter.
     *
     * Nutzt den separaten [commandClient] (eigener Pool/Dispatcher), damit der
     * Befehl nicht hinter dem schweren Datenabruf wartet. Bei Fehlschlag wird
     * bis zu [SET_STATE_MAX_ATTEMPTS]-mal mit kurzem Backoff wiederholt — die
     * gemeinsame BT-Leitung ist oft nur kurz belegt, ein zweiter Versuch kommt
     * dann durch (löst „Befehl kommt gar nicht an").
     */
    suspend fun setState(
        host: String,
        port: Int,
        useHttps: Boolean,
        username: String,
        password: String,
        id: String,
        value: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val url  = buildUrl(host, port, "/api/setState", useHttps)
        var lastError: Throwable? = null
        for (attempt in 1..SET_STATE_MAX_ATTEMPTS) {
            val result = runCatching {
                val body = JSONObject().apply { put("id", id); put("value", value) }
                    .toString().toRequestBody("application/json".toMediaType())
                val response = commandClient.newCall(
                    buildRequest(url, username, password).post(body).build()
                ).execute()
                response.use { check(it.isSuccessful) { "HTTP ${it.code}" } }
            }
            if (result.isSuccess) {
                if (attempt > 1) Log.d(TAG, "setState($id) erfolgreich nach Versuch $attempt")
                return@withContext result
            }
            lastError = result.exceptionOrNull()
            Log.w(TAG, "setState($id) Versuch $attempt/$SET_STATE_MAX_ATTEMPTS fehlgeschlagen: ${lastError?.message}")
            if (attempt < SET_STATE_MAX_ATTEMPTS) {
                delay(SET_STATE_BACKOFF_MS * attempt)
            }
        }
        Log.e(TAG, "setState($id) endgültig fehlgeschlagen: ${lastError?.message}")
        Result.failure(lastError ?: IllegalStateException("setState fehlgeschlagen"))
    }

    private fun buildUrl(host: String, port: Int, path: String, useHttps: Boolean): String {
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
