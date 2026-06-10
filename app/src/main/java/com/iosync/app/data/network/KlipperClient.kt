package com.iosync.app.data.network

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

private const val TAG = "KlipperClient"

/**
 * HTTP-Client für die Moonraker-API des Klipper-3D-Druckers (Telefon-Seite).
 *
 * Ruft verfügbare Drucker-Objekte ab, damit der Nutzer in der App einen
 * Datenpunkt für die Watchface-Pille auf Seite 3 auswählen kann.
 *
 * Moonraker läuft standardmäßig auf Port 7125.
 */
@Singleton
class KlipperClient @Inject constructor() {

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
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    /**
     * Ruft alle verfügbaren Drucker-Objekte von Moonraker ab.
     * GET /printer/objects/list
     * Gibt die Liste alphabetisch sortiert zurück, z.B.:
     *   ["extruder", "fan", "heater_bed", "output_pin my_led", ...]
     */
    suspend fun fetchObjects(host: String, port: Int, apiKey: String = ""): Result<List<String>> = withContext(Dispatchers.IO) {
        runCatching {
            val url = buildUrl(host, port, "/printer/objects/list")
            val req = Request.Builder().url(url)
                .apply { if (apiKey.isNotBlank()) addHeader("X-Api-Key", apiKey) }
                .get().build()
            val response = okHttpClient.newCall(req).execute()
            check(response.isSuccessful) { "HTTP ${response.code}" }
            val root = JSONObject(response.body!!.string())
            val arr = root.getJSONObject("result").getJSONArray("objects")
            (0 until arr.length()).map { arr.getString(it) }.sorted()
        }.onFailure { Log.e(TAG, "fetchObjects fehlgeschlagen: ${it.message}") }
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
