package com.iosync.watchface.data

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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

private const val TAG = "WatchKlipperClient"

/** Zusammengefasste Druckzustandsdaten von Moonraker. */
data class KlipperPrinterStatus(
    val progress: Float,      // 0.0 – 1.0
    val nozzleTemp: Float,
    val nozzleTarget: Float,
    val bedTemp: Float,
    val bedTarget: Float,
    val chamberTemp: Float,
    val speedMms: Float,
    val fanPercent: Float,    // 0 – 100
    val isActive: Boolean     // true wenn Drucker gerade druckt (print_stats.state == "printing")
)

/**
 * HTTP-Client für die Moonraker-API des Klipper-3D-Druckers.
 *
 * Ruft Drucker-Objekte ab (GET /printer/objects/query)
 * und sendet G-Code-Befehle (POST /printer/gcode/script).
 */
object WatchKlipperClient {

    private val trustAllManager = object : X509TrustManager {
        override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) = Unit
        override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) = Unit
        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
    }

    val okHttpClient: OkHttpClient by lazy {
        val sslContext = SSLContext.getInstance("TLS").apply {
            init(null, arrayOf<TrustManager>(trustAllManager), SecureRandom())
        }
        OkHttpClient.Builder()
            .sslSocketFactory(sslContext.socketFactory, trustAllManager)
            .hostnameVerifier { _, _ -> true }
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(8, TimeUnit.SECONDS)
            .build()
    }

    /**
     * Ruft ein Drucker-Objekt ab und liest ein bestimmtes Feld aus.
     * Beispiel: objectName = "output_pin my_led", field = "value" → "1.0"
     */
    private fun Request.Builder.apiKeyHeader(apiKey: String) =
        apply { if (apiKey.isNotBlank()) addHeader("X-Api-Key", apiKey) }

    suspend fun queryObjectField(
        host: String,
        port: Int,
        objectName: String,
        field: String,
        apiKey: String = ""
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val encodedObj = objectName.replace(" ", "%20")
            val url = buildUrl(host, port, "/printer/objects/query?$encodedObj")
            val response = okHttpClient.newCall(
                Request.Builder().url(url).apiKeyHeader(apiKey).get().build()
            ).execute()
            check(response.isSuccessful) { "HTTP ${response.code}" }
            val body = response.body!!.string()
            val root = JSONObject(body)
            val status = root.getJSONObject("result").getJSONObject("status")
            val objData = status.getJSONObject(objectName)
            objData.get(field).toString()
        }.onFailure { Log.e(TAG, "queryObjectField($objectName/$field) fehlgeschlagen: ${it.message}") }
    }

    /**
     * Sendet einen G-Code-Befehl (z.B. "SET_PIN PIN=my_led VALUE=1").
     */
    suspend fun sendGcode(
        host: String,
        port: Int,
        gcode: String,
        apiKey: String = ""
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val url = buildUrl(host, port, "/printer/gcode/script")
            val body = JSONObject().apply { put("script", gcode) }
                .toString().toRequestBody("application/json".toMediaType())
            val response = okHttpClient.newCall(
                Request.Builder().url(url).apiKeyHeader(apiKey).post(body).build()
            ).execute()
            check(response.isSuccessful) { "HTTP ${response.code}" }
        }.onFailure { Log.e(TAG, "sendGcode($gcode) fehlgeschlagen: ${it.message}") }
    }

    /**
     * Ruft alle relevanten Druckdaten in einer einzigen Anfrage ab.
     * Gibt [KlipperPrinterStatus] zurück oder schlägt fehl.
     * @param chamberObject  Moonraker-Objektname für die Chamber-Temperatur
     *                       (z.B. "heater_generic chamber").
     */
    suspend fun queryPrinterStatus(
        host: String,
        port: Int,
        chamberObject: String = "heater_generic chamber",
        apiKey: String = ""
    ): Result<KlipperPrinterStatus> = withContext(Dispatchers.IO) {
        runCatching {
            val encObj = chamberObject.replace(" ", "%20")
            val url = buildUrl(
                host, port,
                "/printer/objects/query?display_status&print_stats&extruder&heater_bed&fan&motion_report&$encObj"
            )
            val response = okHttpClient.newCall(
                Request.Builder().url(url).apiKeyHeader(apiKey).get().build()
            ).execute()
            check(response.isSuccessful) { "HTTP ${response.code}" }
            val root = JSONObject(response.body!!.string())
            val status = root.getJSONObject("result").getJSONObject("status")

            fun objFloat(obj: String, field: String): Float =
                status.optJSONObject(obj)?.optDouble(field, 0.0)?.toFloat() ?: 0f

            val progress = objFloat("display_status", "progress")
            val printState   = status.optJSONObject("print_stats")?.optString("state", "standby") ?: "standby"
            val nozzleTemp   = objFloat("extruder", "temperature")
            val nozzleTarget = objFloat("extruder", "target")
            val bedTemp      = objFloat("heater_bed", "temperature")
            val bedTarget    = objFloat("heater_bed", "target")
            val chamberTemp  = status.optJSONObject(chamberObject)?.optDouble("temperature", 0.0)?.toFloat() ?: 0f
            val fanSpeed     = objFloat("fan", "speed") * 100f          // 0..1 → %
            val speedMms     = objFloat("motion_report", "live_velocity")

            KlipperPrinterStatus(
                progress     = progress,
                nozzleTemp   = nozzleTemp,
                nozzleTarget = nozzleTarget,
                bedTemp      = bedTemp,
                bedTarget    = bedTarget,
                chamberTemp  = chamberTemp,
                speedMms     = speedMms,
                fanPercent   = fanSpeed,
                isActive     = printState == "printing"
            )
        }.onFailure { Log.e(TAG, "queryPrinterStatus fehlgeschlagen: ${it.message}") }
    }

    /**
     * Liest ein einzelnes Feld eines Moonraker-Objekts und gibt es als Boolean zurück.
     * Nützlich um den aktuellen Status von LED oder Chamber-Heater zu ermitteln.
     */
    suspend fun queryBoolField(
        host: String,
        port: Int,
        objectName: String,
        field: String,
        apiKey: String = ""
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        runCatching {
            val raw = queryObjectField(host, port, objectName, field, apiKey).getOrThrow()
            raw == "true" || raw == "1" || raw == "1.0" || raw.toDoubleOrNull()?.let { it > 0 } == true
        }.onFailure { Log.e(TAG, "queryBoolField($objectName/$field) fehlgeschlagen: ${it.message}") }
    }

    private fun buildUrl(host: String, port: Int, path: String): String {
        val h = host.trim().trimEnd('/')
        return if (h.startsWith("http://") || h.startsWith("https://")) {
            "$h:$port$path"
        } else {
            "http://$h:$port$path"
        }
    }
}
