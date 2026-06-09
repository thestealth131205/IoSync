package com.iosync.watchface.data

import android.util.Log
import com.iosync.watchface.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL

private const val TAG = "WatchWeatherService"
private const val OPENWEATHER_API_KEY = BuildConfig.OPENWEATHER_API_KEY

data class WatchWeatherData(
    val temperature: Int,
    val condition: String // clear, partly_cloudy, cloudy, rain, snow, frost, thunderstorm
)

/**
 * Holt aktuelle Wetterdaten direkt auf der Uhr über die OpenWeatherMap API.
 *
 * Die Uhr hat keinen GPS-/Standort-Zugriff wie das Handy — daher werden die
 * Koordinaten (lat/lon) vom Handy aus der Konfiguration übertragen.
 * Der API-Key wird zur Build-Zeit über BuildConfig eingebettet.
 */
object WatchWeatherService {

    suspend fun fetchWeather(lat: Double, lon: Double): Result<WatchWeatherData> =
        withContext(Dispatchers.IO) {
            try {
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
                Result.success(WatchWeatherData(temp, condition))
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
        id in 200..232 -> "thunderstorm"
        id in 300..321 && temp <= 0 -> "frost"
        id in 300..321 -> "rain"
        id in 500..531 && temp <= 0 -> "frost"
        id in 500..531 -> "rain"
        id in 600..622 -> "snow"
        id in 701..781 -> "cloudy"
        id == 800 -> "clear"
        id == 801 -> "partly_cloudy"
        id in 802..804 -> "cloudy"
        else -> "cloudy"
    }
}
