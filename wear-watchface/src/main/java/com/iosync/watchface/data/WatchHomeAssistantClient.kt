package com.iosync.watchface.data

import android.util.Log
import com.iosync.watchface.datalayer.CachedState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

private const val TAG = "WatchHomeAssistantClient"

/**
 * HTTP-Client für Home Assistant — direkt von der Uhr aus (Gegenstück zu
 * [WatchIoSyncClient]). Portiert aus der App (HomeAssistantClient). Auth per
 * Bearer-Token statt Username/Passwort.
 */
object WatchHomeAssistantClient {

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

    private val okHttpClient: OkHttpClient by lazy {
        val sslContext = trustAllSslContext()
        OkHttpClient.Builder()
            .sslSocketFactory(sslContext.socketFactory, trustAllManager)
            .hostnameVerifier { _, _ -> true }
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .build()
    }

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

    suspend fun fetchDataPoints(
        host: String,
        port: Int,
        useHttps: Boolean,
        token: String
    ): Result<List<CachedState>> = withContext(Dispatchers.IO) {
        runCatching {
            val url = buildUrl(host, port, "/api/states", useHttps)
            val response = okHttpClient.newCall(
                buildRequest(url, token).get().build()
            ).execute()
            check(response.isSuccessful) { "HTTP ${response.code}" }
            val arr = JSONArray(response.body!!.string())
            (0 until arr.length()).map { i ->
                val entity = arr.getJSONObject(i)
                val entityId = entity.getString("entity_id")
                val attrs = entity.optJSONObject("attributes") ?: JSONObject()
                val rawState = entity.optString("state", "")
                CachedState(
                    id    = entityId,
                    name  = attrs.optString("friendly_name", "").ifBlank { entityId },
                    value = normalizeValue(rawState),
                    unit  = attrs.optString("unit_of_measurement", "").takeIf { it.isNotEmpty() },
                    type  = inferType(rawState)
                )
            }
        }.onFailure { Log.e(TAG, "fetchDataPoints fehlgeschlagen: ${it.message}") }
    }

    /**
     * Steuert eine HA-Entity direkt von der Uhr aus (Service-Call), mit demselben
     * Retry-Verhalten wie [WatchIoSyncClient.setState].
     */
    suspend fun setState(
        host: String,
        port: Int,
        useHttps: Boolean,
        token: String,
        id: String,
        value: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val domain = id.substringBefore('.', missingDelimiterValue = "")
        if (domain.isEmpty()) return@withContext Result.failure(IllegalArgumentException("Ungültige Entity-ID: $id"))
        val (service, body) = resolveService(domain, id, value)
        val url = buildUrl(host, port, "/api/services/$domain/$service", useHttps)
        var lastError: Throwable? = null
        for (attempt in 1..SET_STATE_MAX_ATTEMPTS) {
            val result = runCatching {
                val requestBody = body.toString().toRequestBody("application/json".toMediaType())
                val response = commandClient.newCall(
                    buildRequest(url, token).post(requestBody).build()
                ).execute()
                response.use { check(it.isSuccessful) { "HTTP ${it.code}" } }
            }
            if (result.isSuccess) {
                if (attempt > 1) Log.d(TAG, "setState($id) erfolgreich nach Versuch $attempt")
                return@withContext result
            }
            lastError = result.exceptionOrNull()
            Log.w(TAG, "setState($id) Versuch $attempt/$SET_STATE_MAX_ATTEMPTS fehlgeschlagen: ${lastError?.message}")
            if (attempt < SET_STATE_MAX_ATTEMPTS) delay(SET_STATE_BACKOFF_MS * attempt)
        }
        Log.e(TAG, "setState($id) endgültig fehlgeschlagen: ${lastError?.message}")
        Result.failure(lastError ?: IllegalStateException("setState fehlgeschlagen"))
    }

    private fun normalizeValue(rawState: String): String = when (rawState) {
        "on" -> "true"
        "off" -> "false"
        else -> rawState
    }

    private fun inferType(rawState: String): String = when {
        rawState == "on" || rawState == "off" || rawState == "true" || rawState == "false" -> "boolean"
        rawState.toDoubleOrNull() != null -> "number"
        else -> "string"
    }

    private fun resolveService(domain: String, id: String, value: String): Pair<String, JSONObject> {
        val isOn = value == "true" || value == "1" || value == "on"
        val base = JSONObject().put("entity_id", id)
        return when (domain) {
            "light" -> {
                when {
                    Regex("^[0-9A-Fa-f]{6}$").matches(value) -> {
                        val rgb = JSONArray().put(value.substring(0, 2).toInt(16))
                            .put(value.substring(2, 4).toInt(16))
                            .put(value.substring(4, 6).toInt(16))
                        "turn_on" to JSONObject(base.toString()).put("rgb_color", rgb)
                    }
                    value.toIntOrNull()?.let { it in 1000..40000 } == true ->
                        "turn_on" to JSONObject(base.toString()).put("color_temp_kelvin", value.toInt())
                    else -> (if (isOn) "turn_on" else "turn_off") to base
                }
            }
            "cover" -> (if (isOn) "open_cover" else "close_cover") to base
            "lock"  -> (if (isOn) "lock" else "unlock") to base
            "script" -> "turn_on" to base
            "number", "input_number" ->
                "set_value" to JSONObject(base.toString()).put("value", value.toDoubleOrNull() ?: 0.0)
            else -> (if (isOn) "turn_on" else "turn_off") to base
        }
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

    private fun buildRequest(url: String, token: String) =
        Request.Builder().url(url).apply {
            if (token.isNotBlank()) addHeader("Authorization", "Bearer $token")
        }
}
