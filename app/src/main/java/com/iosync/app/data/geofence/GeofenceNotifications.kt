package com.iosync.app.data.geofence

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
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

    /** Notification: der GPS-Standort wurde gerade neu abgeglichen. */
    fun notifyLocationUpdated(context: Context, address: String) {
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
