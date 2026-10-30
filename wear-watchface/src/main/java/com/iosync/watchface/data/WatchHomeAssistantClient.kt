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
import kotlin.math.roundToInt
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

private const val TAG = "WatchHomeAssistantClient"

/** Trenner für virtuelle Attribut-Datenpunkte, z.B. "light.wohnzimmer#brightness". */
private const val ATTR_SEPARATOR = '#'
private const val ATTR_BRIGHTNESS = "brightness"
private const val ATTR_COLOR = "color"
private const val ATTR_COLOR_TEMP = "color_temp"
// climate.* / weather.*: Suffix = Name des HA-Attributs
private const val ATTR_CURRENT_TEMP = "current_temperature"
private const val ATTR_TARGET_TEMP = "temperature"
private const val ATTR_CURRENT_HUMIDITY = "current_humidity"
private const val ATTR_HUMIDITY = "humidity"
private const val ATTR_WIND_SPEED = "wind_speed"

/** HA-Zustände ohne Messwert → als "kein Wert" (null) behandeln, Anzeige "--". */
private val NO_VALUE_STATES = setOf("unknown", "unavailable", "")

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
            (0 until arr.length()).flatMap { i ->
                val entity = arr.getJSONObject(i)
                val entityId = entity.getString("entity_id")
                val attrs = entity.optJSONObject("attributes") ?: JSONObject()
                val rawState = entity.optString("state", "")
                val main = CachedState(
                    id    = entityId,
                    name  = attrs.optString("friendly_name", "").ifBlank { entityId },
                    value = normalizeValue(rawState),
                    unit  = attrs.optString("unit_of_measurement", "").takeIf { it.isNotEmpty() },
                    type  = inferType(rawState)
                )
                listOf(main) + attributePoints(entityId, attrs, rawState).map { p ->
                    CachedState(
                        id    = p.id,
                        name  = "${main.name} ${p.label}",
                        value = p.value,
                        unit  = p.unit,
                        type  = if (p.numeric) "number" else "string"
                    )
                }
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
        val entityId = id.substringBefore(ATTR_SEPARATOR)
        val domain = entityId.substringBefore('.', missingDelimiterValue = "")
        if (domain.isEmpty()) return@withContext Result.failure(IllegalArgumentException("Ungültige Entity-ID: $id"))
        val (service, body) = runCatching { resolveService(domain, id, value) }
            .getOrElse { return@withContext Result.failure(it) }
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

    private fun normalizeValue(rawState: String): String? = when (rawState) {
        "on" -> "true"
        "off" -> "false"
        in NO_VALUE_STATES -> null
        else -> rawState
    }

    private fun inferType(rawState: String): String = when {
        rawState == "on" || rawState == "off" || rawState == "true" || rawState == "false" -> "boolean"
        rawState.toDoubleOrNull() != null -> "number"
        else -> "string"
    }

    private fun resolveService(domain: String, id: String, value: String): Pair<String, JSONObject> {
        val isOn = value == "true" || value == "1" || value == "on"
        val base = JSONObject().put("entity_id", id.substringBefore(ATTR_SEPARATOR))
        val attr = id.substringAfter(ATTR_SEPARATOR, missingDelimiterValue = "")
        // Nur Licht-Attribute und die Thermostat-Soll-Temperatur sind steuerbar
        require(attr.isEmpty() || domain == "light" || (domain == "climate" && attr == ATTR_TARGET_TEMP)) {
            "$id ist nur lesbar"
        }
        return when (domain) {
            "light" -> {
                val num = value.toDoubleOrNull()
                when {
                    attr == ATTR_BRIGHTNESS -> brightnessService(base, num)
                    // Weißton-Datenpunkt: Kelvin-Wert explizit anhand des Attributs erkennen,
                    // nicht anhand des Zahlenbereichs (brach sonst bei Werten außerhalb 1000–40000,
                    // z.B. aus alten ioBroker-Mired-Settings, zu "turn_off" durch).
                    attr == ATTR_COLOR_TEMP -> {
                        val kelvin = value.toIntOrNull()
                        if (kelvin == null || kelvin <= 0) "turn_off" to base
                        else "turn_on" to JSONObject(base.toString()).put("color_temp_kelvin", kelvin)
                    }
                    Regex("^#?[0-9A-Fa-f]{6}$").matches(value) -> {
                        val hex = value.removePrefix("#")
                        val rgb = JSONArray().put(hex.substring(0, 2).toInt(16))
                            .put(hex.substring(2, 4).toInt(16))
                            .put(hex.substring(4, 6).toInt(16))
                        "turn_on" to JSONObject(base.toString()).put("rgb_color", rgb)
                    }
                    value.toIntOrNull()?.let { it in 1000..40000 } == true ->
                        "turn_on" to JSONObject(base.toString()).put("color_temp_kelvin", value.toInt())
                    // Slider-Wert 0–100 direkt auf die Lampe → Helligkeit in %
                    !isOn && num != null && num in 0.0..100.0 && value != "0" -> brightnessService(base, num)
                    else -> (if (isOn) "turn_on" else "turn_off") to base
                }
            }
            "climate" -> if (attr == ATTR_TARGET_TEMP) {
                val temp = requireNotNull(value.toDoubleOrNull()) { "Ungültige Temperatur: $value" }
                "set_temperature" to JSONObject(base.toString()).put("temperature", temp)
            } else (if (isOn) "turn_on" else "turn_off") to base
            "cover" -> (if (isOn) "open_cover" else "close_cover") to base
            "lock"  -> (if (isOn) "lock" else "unlock") to base
            "script" -> "turn_on" to base
            "number", "input_number" ->
                "set_value" to JSONObject(base.toString()).put("value", value.toDoubleOrNull() ?: 0.0)
            else -> (if (isOn) "turn_on" else "turn_off") to base
        }
    }

    private fun brightnessService(base: JSONObject, pct: Double?): Pair<String, JSONObject> =
        if (pct == null || pct <= 0.0) "turn_off" to base
        else "turn_on" to JSONObject(base.toString()).put("brightness_pct", pct.coerceIn(1.0, 100.0).roundToInt())

    /** Virtueller Datenpunkt für ein Entity-Attribut (z.B. Helligkeit, Soll-Temperatur). */
    private data class AttributePoint(val id: String, val label: String, val value: String?, val unit: String?, val numeric: Boolean)

    /**
     * HA führt viele Messwerte nicht als eigene Entities, sondern als Attribute einer
     * Entity. Damit sie wie ioBroker-Datenpunkte auswählbar sind, werden sie als
     * virtuelle IDs "<entity_id>#<attribut>" bereitgestellt:
     *  - light.*   → Helligkeit / Farbe / Weißton (steuerbar; Seite-2-Slider, Farbwahlrad, Weißton-Buttons)
     *  - climate.* → Ist-Temperatur / Soll-Temperatur (steuerbar) / Luftfeuchte
     *  - weather.* → Temperatur / Luftfeuchte / Wind (nur lesbar)
     */
    private fun attributePoints(entityId: String, attrs: JSONObject, rawState: String): List<AttributePoint> =
        when (entityId.substringBefore('.')) {
            "light"   -> lightAttributePoints(entityId, attrs, rawState)
            "climate" -> listOfNotNull(
                numericAttributePoint(entityId, attrs, ATTR_CURRENT_TEMP, "Ist-Temperatur", "°C"),
                numericAttributePoint(entityId, attrs, ATTR_TARGET_TEMP, "Soll-Temperatur", "°C"),
                numericAttributePoint(entityId, attrs, ATTR_CURRENT_HUMIDITY, "Luftfeuchte", "%")
            )
            "weather" -> listOfNotNull(
                numericAttributePoint(entityId, attrs, ATTR_TARGET_TEMP, "Temperatur",
                    attrs.optString("temperature_unit", "").ifBlank { "°C" }),
                numericAttributePoint(entityId, attrs, ATTR_HUMIDITY, "Luftfeuchte", "%"),
                numericAttributePoint(entityId, attrs, ATTR_WIND_SPEED, "Wind",
                    attrs.optString("wind_speed_unit", "").ifBlank { "km/h" })
            )
            else -> emptyList()
        }

    /** Zahl-Attribut als virtuellen Datenpunkt; fehlt das Attribut ganz → kein Eintrag, ist es null → Wert "--". */
    private fun numericAttributePoint(entityId: String, attrs: JSONObject, key: String, label: String, unit: String): AttributePoint? {
        if (!attrs.has(key)) return null
        val value = if (attrs.isNull(key)) null else attrs.optDouble(key).takeIf { !it.isNaN() }
            ?.let { if (it % 1.0 == 0.0) it.toLong().toString() else it.toString() }
        return AttributePoint("$entityId$ATTR_SEPARATOR$key", label, value, unit, true)
    }

    private fun lightAttributePoints(entityId: String, attrs: JSONObject, rawState: String): List<AttributePoint> {
        val modes = attrs.optJSONArray("supported_color_modes")
            ?.let { arr -> (0 until arr.length()).map { arr.optString(it) } } ?: emptyList()
        val isOn = rawState == "on"
        val points = mutableListOf<AttributePoint>()
        if (modes.any { it != "onoff" }) {
            val pct = if (isOn) attrs.optDouble("brightness", 0.0).let { (it / 255.0 * 100.0).roundToInt() } else 0
            points += AttributePoint("$entityId$ATTR_SEPARATOR$ATTR_BRIGHTNESS", "Helligkeit", pct.toString(), "%", true)
        }
        if (modes.any { it in setOf("hs", "rgb", "rgbw", "rgbww", "xy") }) {
            val rgb = attrs.optJSONArray("rgb_color")
            val hex = if (isOn && rgb != null && rgb.length() >= 3)
                "%02X%02X%02X".format(rgb.optInt(0), rgb.optInt(1), rgb.optInt(2)) else null
            points += AttributePoint("$entityId$ATTR_SEPARATOR$ATTR_COLOR", "Farbe", hex, null, false)
        }
        if ("color_temp" in modes) {
            val kelvin = if (isOn && attrs.has("color_temp_kelvin") && !attrs.isNull("color_temp_kelvin"))
                attrs.optInt("color_temp_kelvin").toString() else null
            points += AttributePoint("$entityId$ATTR_SEPARATOR$ATTR_COLOR_TEMP", "Weißton", kelvin, "K", true)
        }
        return points
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
