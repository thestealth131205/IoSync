package com.iosync.app.data.network

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

import com.iosync.app.BuildConfig

private const val TAG = "WeatherService"
private const val OPENWEATHER_API_KEY = BuildConfig.OPENWEATHER_API_KEY

data class WeatherData(
    val temperature: Int,
    val condition: String // clear, partly_cloudy, cloudy, rain, snow, frost, thunderstorm
)

data class GeocodingResult(
    val name: String,
    val country: String,
    val state: String?,
    val lat: Double,
    val lon: Double
) {
    val displayName: String get() = buildString {
        append(name)
        if (!state.isNullOrBlank()) append(", $state")
        append(" ($country)")
    }
}

/**
 * Holt aktuelle Wetterdaten über die OpenWeatherMap API.
 * Unterstützt festen Standort oder GPS-basierte Abfrage.
 */
@Singleton
class WeatherService @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val fusedLocationClient by lazy {
        LocationServices.getFusedLocationProviderClient(context)
    }

    // Fester Standort (wird vom ViewModel gesetzt)
    @Volatile var fixedLat: Double? = null
    @Volatile var fixedLon: Double? = null
    @Volatile var useFixedLocation: Boolean = false

    /**
     * Sucht Orte per OpenWeatherMap Geocoding API.
     */
    suspend fun searchLocations(query: String): Result<List<GeocodingResult>> = withContext(Dispatchers.IO) {
        try {
            if (query.isBlank()) return@withContext Result.success(emptyList())
            val url = "https://api.openweathermap.org/geo/1.0/direct?" +
                    "q=${java.net.URLEncoder.encode(query, "UTF-8")}&limit=5&appid=$OPENWEATHER_API_KEY"
            val json = URL(url).readText()
            val arr = org.json.JSONArray(json)
            val results = mutableListOf<GeocodingResult>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                results.add(GeocodingResult(
                    name = obj.getString("name"),
                    country = obj.optString("country", ""),
                    state = obj.optString("state", null),
                    lat = obj.getDouble("lat"),
                    lon = obj.getDouble("lon")
                ))
            }
            Result.success(results)
        } catch (e: Exception) {
            Log.e(TAG, "Geocoding fehlgeschlagen", e)
            Result.failure(e)
        }
    }

    suspend fun fetchWeather(): Result<WeatherData> = withContext(Dispatchers.IO) {
        try {
            val lat: Double
            val lon: Double

            if (useFixedLocation && fixedLat != null && fixedLon != null) {
                lat = fixedLat!!
                lon = fixedLon!!
            } else {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
                    != PackageManager.PERMISSION_GRANTED
                ) {
                    return@withContext Result.failure(SecurityException("Standort-Berechtigung fehlt"))
                }

                val location = fusedLocationClient.getCurrentLocation(
                    Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                    CancellationTokenSource().token
                ).await()

                if (location == null) {
                    return@withContext Result.failure(Exception("Standort nicht verfügbar"))
                }
                lat = location.latitude
                lon = location.longitude
            }

            val url = "https://api.openweathermap.org/data/2.5/weather?" +
                    "lat=$lat&lon=$lon" +
                    "&appid=$OPENWEATHER_API_KEY&units=metric&lang=de"

            val json = URL(url).readText()
            val obj = JSONObject(json)
            val main = obj.getJSONObject("main")
            val temp = main.getDouble("temp").toInt()
            val weatherArray = obj.getJSONArray("weather")
            val weatherId = weatherArray.getJSONObject(0).getInt("id")
            val condition = owmToCondition(weatherId, temp)

            Log.d(TAG, "Wetter: ${temp}°C, $condition (OWM ID $weatherId)")
            Result.success(WeatherData(temp, condition))
        } catch (e: Exception) {
            Log.e(TAG, "Wetter-Abfrage fehlgeschlagen", e)
            Result.failure(e)
        }
    }

    /**
     * Wandelt OpenWeatherMap Condition-IDs in einfache Kategorie-Strings um.
     * https://openweathermap.org/weather-conditions
     */
    private fun owmToCondition(id: Int, temp: Int): String = when {
        id in 200..232 -> "thunderstorm"  // Gewitter
        id in 300..321 && temp <= 0 -> "frost" // Gefrierender Nieselregen
        id in 300..321 -> "rain"          // Nieselregen
        id in 500..531 && temp <= 0 -> "frost" // Gefrierender Regen
        id in 500..531 -> "rain"          // Regen
        id in 600..622 -> "snow"          // Schnee
        id in 701..781 -> "cloudy"        // Nebel / Dunst
        id == 800 -> "clear"              // Klarer Himmel
        id == 801 -> "partly_cloudy"      // Leicht bewölkt
        id in 802..804 -> "cloudy"        // Bewölkt
        else -> "cloudy"
    }
}
