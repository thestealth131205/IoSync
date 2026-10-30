package com.iosync.app.data.network

import android.util.Log
import com.iosync.app.data.model.SmartHomeState
import com.iosync.app.data.model.StateType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

private const val TAG = "HomeAssistantClient"

/**
 * HTTP-Client für Home Assistant (REST-API), Gegenstück zu [IoSyncClient].
 *
 * API-Endpunkte:
 *   GET  /api/states                          → alle Entities (Datenpunkte)
 *   POST /api/services/{domain}/{service}     → Gerät wirklich steuern
 *   GET  /api/                                → Health-Check ("API running.")
 *
 * Auth: Bearer-Token (Langzeit-Zugriffstoken aus HA) statt Username/Passwort.
 * Trust-All-SSL-Context wie beim IoSync-Adapter (selbstsigniertes LAN-Zertifikat möglich).
 */
@Singleton
class HomeAssistantClient @Inject constructor() {

    private val trustAllManager = object : X509TrustManager {
        override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) = Unit
        override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) = Unit
        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
    }

    private val okHttpClient: OkHttpClient by lazy {
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
     * Ruft alle Entities von Home Assistant ab und bildet sie auf [SmartHomeState] ab.
     * entity_id → id, attributes.friendly_name → name (Fallback entity_id),
     * state "on"/"off" → Boolean "true"/"false", sonst Zahl oder String.
     */
    suspend fun fetchDataPoints(
        host: String,
        port: Int,
        useHttps: Boolean,
        token: String
    ): Result<List<SmartHomeState>> = withContext(Dispatchers.IO) {
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
                SmartHomeState(
                    id        = entityId,
                    name      = attrs.optString("friendly_name", "").ifBlank { entityId },
                    value     = normalizeValue(rawState),
                    type      = inferType(rawState),
                    unit      = attrs.optString("unit_of_measurement", "").takeIf { it.isNotEmpty() },
                    timestamp = System.currentTimeMillis()
                )
            }
        }.onFailure { Log.e(TAG, "fetchDataPoints fehlgeschlagen: ${it.message}") }
    }

    /**
     * Steuert eine HA-Entity wirklich (Service-Call), statt nur den angezeigten
     * Zustand zu überschreiben. Der Dienst wird anhand der Entity-Domain (Teil vor
     * dem ".") und des gewünschten Werts heuristisch bestimmt.
     */
    suspend fun setState(
        host: String,
        port: Int,
        useHttps: Boolean,
        token: String,
        id: String,
        value: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val domain = id.substringBefore('.', missingDelimiterValue = "")
            require(domain.isNotEmpty()) { "Ungültige Entity-ID: $id" }
            val (service, body) = resolveService(domain, id, value)
            val url = buildUrl(host, port, "/api/services/$domain/$service", useHttps)
            val response = okHttpClient.newCall(
                buildRequest(url, token).post(body.toString().toRequestBody("application/json".toMediaType())).build()
            ).execute()
            check(response.isSuccessful) { "HTTP ${response.code}" }
        }.onFailure { Log.e(TAG, "setState($id) fehlgeschlagen: ${it.message}") }
    }

    suspend fun checkHealth(host: String, port: Int, useHttps: Boolean, token: String): Boolean =
        withContext(Dispatchers.IO) {
            runCatching {
                val url = buildUrl(host, port, "/api/", useHttps)
                okHttpClient.newCall(buildRequest(url, token).get().build()).execute().isSuccessful
            }.getOrDefault(false)
        }

    private fun normalizeValue(rawState: String): String = when (rawState) {
        "on" -> "true"
        "off" -> "false"
        else -> rawState
    }

    private fun inferType(rawState: String): StateType = when {
        rawState == "on" || rawState == "off" || rawState == "true" || rawState == "false" -> StateType.BOOLEAN
        rawState.toDoubleOrNull() != null -> StateType.NUMBER
        else -> StateType.STRING
    }

    /**
     * Bildet (Domain, Wert) auf einen Service-Call ab. Deckt die über das Watchface
     * steuerbaren Fälle ab: Pillen-Toggle (switch/light/lock/cover/...), Balken/Slider
     * (number/input_number), Farb-Streifen + Weißton-Buttons (light: Hex bzw. Kelvin).
     */
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
