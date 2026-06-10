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
    suspend fun queryObjectField(
        host: String,
        port: Int,
        objectName: String,
        field: String
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val encodedObj = objectName.replace(" ", "%20")
            val url = buildUrl(host, port, "/printer/objects/query?$encodedObj")
            val response = okHttpClient.newCall(
                Request.Builder().url(url).get().build()
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
        gcode: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val url = buildUrl(host, port, "/printer/gcode/script")
            val body = JSONObject().apply { put("script", gcode) }
                .toString().toRequestBody("application/json".toMediaType())
            val response = okHttpClient.newCall(
                Request.Builder().url(url).post(body).build()
            ).execute()
            check(response.isSuccessful) { "HTTP ${response.code}" }
        }.onFailure { Log.e(TAG, "sendGcode($gcode) fehlgeschlagen: ${it.message}") }
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
