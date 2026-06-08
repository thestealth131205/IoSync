package com.iosync.app.data.network

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.iosync.app.MainActivity
import com.iosync.app.R
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Foreground service that keeps the WebSocket connection alive
 * even when the app is in the background.
 */
@AndroidEntryPoint
class SmartHomeWebSocketService : Service() {

    @Inject
    lateinit var webSocketManager: WebSocketManager

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val url = intent.getStringExtra(EXTRA_URL) ?: return START_NOT_STICKY
                startForeground(NOTIFICATION_ID, buildNotification())
                webSocketManager.connect(url)
            }
            ACTION_STOP -> {
                webSocketManager.disconnect()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        webSocketManager.disconnect()
        super.onDestroy()
    }

    private fun buildNotification(): Notification {
        val channelId = "iosync_api_channel"
        val manager = getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(channelId) == null) {
            val channel = NotificationChannel(
                channelId,
                "IoSync Live-Verbindung",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Hält die Verbindung zu Smart Home aktiv"
                setShowBadge(false)
            }
            manager.createNotificationChannel(channel)
        }

        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("IoSync")
            .setContentText("Smart-Home-Verbindung aktiv")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    companion object {
        const val ACTION_START = "com.iosync.app.START_WEBSOCKET"
        const val ACTION_STOP = "com.iosync.app.STOP_WEBSOCKET"
        const val EXTRA_URL = "extra_ws_url"
        private const val NOTIFICATION_ID = 1001
    }
}
