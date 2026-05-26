package com.iosync.app.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Represents a single ioBroker / Home Assistant data point.
 *
 * @param id        Unique object ID, e.g. "hm-rpc.0.OEQ123456.1.STATE"
 * @param name      Human-readable display name
 * @param value     Current value as string representation (may be bool, number, string)
 * @param type      Value type: "boolean", "number", "string", "mixed"
 * @param unit      Optional unit string, e.g. "°C", "%", "lux"
 * @param room      Optional room/area assignment
 * @param timestamp Unix epoch milliseconds of last update
 * @param ack       Acknowledgement flag from ioBroker
 * @param quality   Quality flag: 0 = good, anything else = error
 */
@JsonClass(generateAdapter = true)
@Entity(tableName = "smart_home_states")
data class SmartHomeState(
    @PrimaryKey
    @Json(name = "id")
    val id: String,

    @Json(name = "name")
    val name: String,

    @Json(name = "value")
    val value: String?,

    @Json(name = "type")
    val type: StateType,

    @Json(name = "unit")
    val unit: String? = null,

    @Json(name = "room")
    val room: String? = null,

    @Json(name = "timestamp")
    val timestamp: Long,

    @Json(name = "ack")
    val ack: Boolean = true,

    @Json(name = "quality")
    val quality: Int = 0
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

/**
 * Lightweight wrapper for real-time WebSocket state updates.
 */
@JsonClass(generateAdapter = true)
data class StateUpdateEvent(
    @Json(name = "id") val id: String,
    @Json(name = "value") val value: String?,
    @Json(name = "timestamp") val timestamp: Long,
    @Json(name = "ack") val ack: Boolean = true,
    @Json(name = "quality") val quality: Int = 0
)

/**
 * Command sent from the app back to ioBroker / HA to change a value.
 */
@JsonClass(generateAdapter = true)
data class StateControlCommand(
    @Json(name = "id") val id: String,
    @Json(name = "value") val value: String,
    @Json(name = "type") val type: String = "setState"
)
