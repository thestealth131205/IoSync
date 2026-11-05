package com.iosync.app.data.geofence

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.location.Location
import androidx.core.app.NotificationCompat
import com.iosync.app.MainActivity
import com.iosync.app.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Zentrale Notification-Logik für die Standort-Vibration (Geofence).
 *
 * Drei Notification-Arten:
 *  1. Persistente Foreground-Notification ([CHANNEL_PERSISTENT]) – hält den
 *     Überwachungs-Prozess am Laufen (siehe [GeofenceService]).
 *  2. Standort-aktualisiert-Notification ([CHANNEL_EVENTS]) – informiert, dass der
 *     GPS-Standort gerade neu abgeglichen wurde.
 *  3. Innerhalb/Außerhalb-Notification ([CHANNEL_EVENTS]) – meldet, ob man sich im
 *     gewählten Bereich befindet.
 */
object GeofenceNotifications {

    const val CHANNEL_PERSISTENT = "iosync_geofence_persistent"
    const val CHANNEL_EVENTS = "iosync_geofence_events"

    const val NOTIFICATION_ID_PERSISTENT = 1010
    const val NOTIFICATION_ID_UPDATED = 1011
    const val NOTIFICATION_ID_REGION = 1012

    /** Benachrichtigungs-Schalter in den Einstellungen (Backup & Wiederherstellen-Bereich). */
    const val KEY_NOTIFY_LOCATION_UPDATED = "notify_location_updated"
    const val KEY_NOTIFY_REGION_STATUS = "notify_region_status"

    private const val KEY_LAST_NOTIFIED_LAT = "last_notified_lat"
    private const val KEY_LAST_NOTIFIED_LON = "last_notified_lon"

    /** "Standort aktualisiert" feuert erst wieder ab dieser Abweichung von der zuletzt gemeldeten Position. */
    private const val MIN_UPDATE_DISTANCE_METERS = 300f

    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.GERMANY)

    fun ensureChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_PERSISTENT) == null) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_PERSISTENT,
                    "Standort-Überwachung",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Hält die Standort-Vibration im Hintergrund aktiv"
                    setShowBadge(false)
                    setSound(null, null)
                    enableVibration(false)
                }
            )
        }
        if (manager.getNotificationChannel(CHANNEL_EVENTS) == null) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_EVENTS,
                    "Standort-Ereignisse",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Meldet Standort-Updates und das Betreten/Verlassen des Bereichs"
                    setShowBadge(true)
                }
            )
        }
    }

    private fun openAppIntent(context: Context): PendingIntent =
        PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

    /** Persistente Foreground-Notification, die den Prozess am Laufen hält. */
    fun buildPersistent(context: Context, address: String): android.app.Notification {
        ensureChannels(context)
        val text = if (address.isNotBlank())
            "Überwacht: $address"
        else
            "Standort-Überwachung aktiv"
        return NotificationCompat.Builder(context, CHANNEL_PERSISTENT)
            .setContentTitle("Standort-Vibration aktiv")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(openAppIntent(context))
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    /**
     * Notification: der GPS-Standort wurde gerade neu abgeglichen.
     *
     * Feuert nur, wenn der Benachrichtigungs-Schalter aktiv ist UND (sofern [lat]/[lon]
     * bekannt sind) sich die Position seit der letzten Meldung um mindestens
     * [MIN_UPDATE_DISTANCE_METERS] verändert hat – sonst würde bei jedem Intervall-Check
     * (z.B. alle 60s) eine neue Benachrichtigung erscheinen, auch im Stillstand.
     */
    fun notifyLocationUpdated(context: Context, address: String, lat: Double? = null, lon: Double? = null) {
        val prefs = context.getSharedPreferences(GEOFENCE_PREFS_NAME, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(KEY_NOTIFY_LOCATION_UPDATED, true)) return

        if (lat != null && lon != null) {
            val lastLat = prefs.getFloat(KEY_LAST_NOTIFIED_LAT, Float.NaN)
            val lastLon = prefs.getFloat(KEY_LAST_NOTIFIED_LON, Float.NaN)
            if (!lastLat.isNaN() && !lastLon.isNaN()) {
                val results = FloatArray(1)
                Location.distanceBetween(lastLat.toDouble(), lastLon.toDouble(), lat, lon, results)
                if (results[0] < MIN_UPDATE_DISTANCE_METERS) return
            }
            prefs.edit()
                .putFloat(KEY_LAST_NOTIFIED_LAT, lat.toFloat())
                .putFloat(KEY_LAST_NOTIFIED_LON, lon.toFloat())
                .apply()
        }

        ensureChannels(context)
        val now = timeFormat.format(Date())
        val text = if (address.isNotBlank())
            "Position um $now mit „$address\" abgeglichen"
        else
            "Position um $now neu abgeglichen"
        val notification = NotificationCompat.Builder(context, CHANNEL_EVENTS)
            .setContentTitle("Standort aktualisiert")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(openAppIntent(context))
            .setAutoCancel(true)
            .build()
        postIfAllowed(context, NOTIFICATION_ID_UPDATED, notification)
    }

    /** Notification: man befindet sich innerhalb bzw. außerhalb des Bereichs. */
    fun notifyRegionStatus(context: Context, inside: Boolean, address: String) {
        val prefs = context.getSharedPreferences(GEOFENCE_PREFS_NAME, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(KEY_NOTIFY_REGION_STATUS, true)) return
        ensureChannels(context)
        val title = if (inside) "Bereich betreten" else "Bereich verlassen"
        val base = if (inside)
            "Du befindest dich im gewählten Standort – Klingelmodus auf Vibration."
        else
            "Du hast den gewählten Standort verlassen – Klingelmodus wiederhergestellt."
        val text = if (address.isNotBlank()) "$base\n$address" else base
        val notification = NotificationCompat.Builder(context, CHANNEL_EVENTS)
            .setContentTitle(title)
            .setContentText(base)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(openAppIntent(context))
            .setAutoCancel(true)
            .build()
        postIfAllowed(context, NOTIFICATION_ID_REGION, notification)
    }

    private fun postIfAllowed(context: Context, id: Int, notification: android.app.Notification) {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.areNotificationsEnabled()) {
            manager.notify(id, notification)
        }
    }
}
