package com.iosync.watchface.data

import android.util.Log
import com.iosync.watchface.datalayer.CachedState
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
            .writeTimeout(10, TimeUnit.SECONDS)
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
