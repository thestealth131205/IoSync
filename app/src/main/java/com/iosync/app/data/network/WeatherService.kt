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

/**
 * Holt aktuelle Wetterdaten über die OpenWeatherMap API.
 * Benötigt ACCESS_COARSE_LOCATION für den Standort.
 */
@Singleton
class WeatherService @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val fusedLocationClient by lazy {
        LocationServices.getFusedLocationProviderClient(context)
    }

    suspend fun fetchWeather(): Result<WeatherData> = withContext(Dispatchers.IO) {
        try {
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

            val url = "https://api.openweathermap.org/data/2.5/weather?" +
                    "lat=${location.latitude}&lon=${location.longitude}" +
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
