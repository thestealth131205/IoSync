package com.iosync.wear.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Wear OS mirror of the phone's SmartHomeState model.
 * Deserialized from JSON received via the Wearable Data Layer.
 */
@JsonClass(generateAdapter = true)
data class SmartHomeState(
    @Json(name = "id") val id: String,
    @Json(name = "name") val name: String,
    @Json(name = "value") val value: String?,
    @Json(name = "type") val type: StateType,
    @Json(name = "unit") val unit: String? = null,
    @Json(name = "room") val room: String? = null,
    @Json(name = "timestamp") val timestamp: Long,
    @Json(name = "ack") val ack: Boolean = true,
    @Json(name = "quality") val quality: Int = 0
) {
    val isOnline: Boolean get() = quality == 0
    val displayValue: String get() = when {
        value == null -> "–"
        unit != null -> "$value $unit"
        else -> value
    }
}

enum class StateType {
    @Json(name = "boolean") BOOLEAN,
    @Json(name = "number") NUMBER,
    @Json(name = "string") STRING,
    @Json(name = "mixed") MIXED
}
