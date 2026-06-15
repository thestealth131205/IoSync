package com.iosync.app.data.network

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.URL
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

data class AddressSearchResult(
    val displayName: String,
    val lat: Double,
    val lon: Double
)

/**
 * Adresssuche über OpenStreetMap Nominatim.
 * Unterstützt Straße + Hausnummer mit Live-Vorschlägen.
 * Kein API-Key erforderlich.
 */
@Singleton
class NominatimService @Inject constructor() {

    suspend fun searchAddress(query: String): Result<List<AddressSearchResult>> = withContext(Dispatchers.IO) {
        try {
            if (query.isBlank()) return@withContext Result.success(emptyList())
            val encoded = URLEncoder.encode(query, "UTF-8")
            val url = "https://nominatim.openstreetmap.org/search" +
                    "?q=$encoded&format=json&limit=5&addressdetails=0"
            val connection = URL(url).openConnection()
            connection.setRequestProperty("User-Agent", "IoSync Android App/1.0")
            val json = connection.getInputStream().bufferedReader().readText()
            val arr = JSONArray(json)
            val results = mutableListOf<AddressSearchResult>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                results.add(
                    AddressSearchResult(
                        displayName = obj.getString("display_name"),
                        lat = obj.getDouble("lat"),
                        lon = obj.getDouble("lon")
                    )
                )
            }
            Result.success(results)
        } catch (e: Exception) {
            Log.e("NominatimService", "Adresssuche fehlgeschlagen", e)
            Result.failure(e)
        }
    }
}
