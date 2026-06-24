package com.iosync.app.data.geofence

import org.json.JSONArray
import org.json.JSONObject

/**
 * Ein einzelner Geofence-Standort der Standort-Vibration. Mehrere Standorte werden
 * als JSON-Liste in den Preferences gespeichert. Jeder Standort hat seinen eigenen
 * Umkreis ([radiusMeters]); das Prüf-Intervall gilt dagegen global für alle Standorte.
 */
data class GeofenceLocation(
    val id: String,
    val lat: Double,
    val lon: Double,
    val address: String,
    val radiusMeters: Int
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("lat", lat)
        put("lon", lon)
        put("address", address)
        put("radius", radiusMeters)
    }

    companion object {
        fun fromJson(o: JSONObject): GeofenceLocation = GeofenceLocation(
            id = o.optString("id"),
            lat = o.optDouble("lat", 0.0),
            lon = o.optDouble("lon", 0.0),
            address = o.optString("address", ""),
            radiusMeters = o.optInt("radius", 300)
        )

        fun listToJson(list: List<GeofenceLocation>): String =
            JSONArray().apply { list.forEach { put(it.toJson()) } }.toString()

        fun listFromJson(json: String?): List<GeofenceLocation> {
            if (json.isNullOrBlank()) return emptyList()
            return try {
                val arr = JSONArray(json)
                (0 until arr.length()).mapNotNull { i ->
                    val obj = arr.optJSONObject(i) ?: return@mapNotNull null
                    fromJson(obj).takeIf { it.id.isNotBlank() }
                }
            } catch (_: Exception) {
                emptyList()
            }
        }
    }
}
