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
 * Kombinierte Abfrage aller IoSync-relevanten Klipper-Daten in einer einzigen HTTP-Anfrage.
 * Enthält alles aus [KlipperPrinterStatus] plus Chamber-Heater-Target, P3-Pille und LED.
 */
data class KlipperCombinedData(
    val progress: Float,
    val nozzleTemp: Float,
    val nozzleTarget: Float,
    val bedTemp: Float,
    val bedTarget: Float,
    val chamberTemp: Float,
    val chamberHeatTarget: Float,  // target > 0 → Heater aktiv
    val speedMms: Float,
    val fanPercent: Float,
    val isActive: Boolean,
    val p3PillValue: String?,      // rohes Feld-Value oder null wenn nicht konfiguriert
    val ledValue: String?          // rohes Feld-Value oder null wenn Tasmota/nicht konfiguriert
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

    /**
     * Liest den aktuellen Status eines Moonraker-Power-Geräts (z.B. Tasmota).
     * Gibt true (on) oder false (off) zurück.
     * API: GET /machine/device_power/status?<deviceName>
     */
    suspend fun queryPowerDeviceStatus(
        host: String,
        port: Int,
        deviceName: String,
        apiKey: String = ""
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        runCatching {
            val enc = deviceName.replace(" ", "%20")
            val url = buildUrl(host, port, "/machine/device_power/status?$enc")
            val response = okHttpClient.newCall(
                Request.Builder().url(url).apiKeyHeader(apiKey).get().build()
            ).execute()
            check(response.isSuccessful) { "HTTP ${response.code}" }
            val body = response.body!!.string()
            val result = JSONObject(body).getJSONObject("result")
            result.getString(deviceName) == "on"
        }.onFailure { Log.e(TAG, "queryPowerDeviceStatus($deviceName) fehlgeschlagen: ${it.message}") }
    }

    /**
     * Schaltet ein Moonraker-Power-Gerät ein oder aus.
     * API: POST /machine/device_power/device?device=<name>&action=on|off
     */
    suspend fun setPowerDevice(
        host: String,
        port: Int,
        deviceName: String,
        on: Boolean,
        apiKey: String = ""
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val enc = deviceName.replace(" ", "%20")
            val action = if (on) "on" else "off"
            val url = buildUrl(host, port, "/machine/device_power/device?device=$enc&action=$action")
            val response = okHttpClient.newCall(
                Request.Builder().url(url).apiKeyHeader(apiKey)
                    .post("".toRequestBody(null)).build()
            ).execute()
            check(response.isSuccessful) { "HTTP ${response.code}" }
        }.onFailure { Log.e(TAG, "setPowerDevice($deviceName, on=$on) fehlgeschlagen: ${it.message}") }
    }

    /**
     * Bündelt alle IoSync-relevanten Moonraker-Abfragen in **einer** einzigen HTTP-Anfrage
     * an `/printer/objects/query`.  Nur das optionale Tasmota-Power-Device bleibt wegen des
     * anderen API-Endpunkts eine separate Anfrage ([queryPowerDeviceStatus]).
     *
     * @param chamberObject   Moonraker-Objekt für die Chamber-Temperatur/-Target
     *                        (z.B. "heater_generic chamber"), leer = überspringen
     * @param p3PillObject    Objekt für die Seite-3-Pille, leer = nicht abfragen
     * @param p3PillField     Feld innerhalb von [p3PillObject]
     * @param ledObject       Objekt für den LED-Status (nur bei G-Code-LED, NICHT Tasmota),
     *                        leer = nicht abfragen
     * @param ledField        Feld innerhalb von [ledObject]
     */
    suspend fun queryCombined(
        host: String,
        port: Int,
        chamberObject: String = "",
        p3PillObject: String = "",
        p3PillField: String = "",
        ledObject: String = "",
        ledField: String = "",
        apiKey: String = ""
    ): Result<KlipperCombinedData> = withContext(Dispatchers.IO) {
        runCatching {
            // Alle benötigten Objekte deduplizieren und zu einem Query-String verbinden.
            val objects = mutableListOf(
                "display_status", "print_stats", "extruder",
                "heater_bed", "fan", "motion_report"
            )
            if (chamberObject.isNotBlank() && chamberObject !in objects) objects += chamberObject
            if (p3PillObject.isNotBlank() && p3PillObject !in objects)   objects += p3PillObject
            if (ledObject.isNotBlank() && ledObject !in objects)          objects += ledObject

            val query = objects.joinToString("&") { it.replace(" ", "%20") }
            val url = buildUrl(host, port, "/printer/objects/query?$query")
            val response = okHttpClient.newCall(
                Request.Builder().url(url).apiKeyHeader(apiKey).get().build()
            ).execute()
            check(response.isSuccessful) { "HTTP ${response.code}" }
            val status = JSONObject(response.body!!.string())
                .getJSONObject("result").getJSONObject("status")

            fun objFloat(obj: String, field: String): Float =
                status.optJSONObject(obj)?.optDouble(field, 0.0)?.toFloat() ?: 0f

            val chamberJson = if (chamberObject.isNotBlank()) status.optJSONObject(chamberObject) else null

            KlipperCombinedData(
                progress          = objFloat("display_status", "progress"),
                nozzleTemp        = objFloat("extruder", "temperature"),
                nozzleTarget      = objFloat("extruder", "target"),
                bedTemp           = objFloat("heater_bed", "temperature"),
                bedTarget         = objFloat("heater_bed", "target"),
                chamberTemp       = chamberJson?.optDouble("temperature", 0.0)?.toFloat() ?: 0f,
                chamberHeatTarget = chamberJson?.optDouble("target", 0.0)?.toFloat() ?: 0f,
                speedMms          = objFloat("motion_report", "live_velocity"),
                fanPercent        = objFloat("fan", "speed") * 100f,
                isActive          = status.optJSONObject("print_stats")
                                        ?.optString("state", "standby") == "printing",
                p3PillValue       = if (p3PillObject.isNotBlank() && p3PillField.isNotBlank())
                                        status.optJSONObject(p3PillObject)?.opt(p3PillField)?.toString()
                                    else null,
                ledValue          = if (ledObject.isNotBlank() && ledField.isNotBlank())
                                        status.optJSONObject(ledObject)?.opt(ledField)?.toString()
                                    else null
            )
        }.onFailure { Log.e(TAG, "queryCombined fehlgeschlagen: ${it.message}") }
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
