package org.sharenow.fileshare.platform

import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

class TransferForegroundService : Service() {
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            else -> {
                val notification = NotificationCompat.Builder(this, TRANSFER_NOTIFICATION_CHANNEL_ID)
                    .setSmallIcon(android.R.drawable.stat_sys_upload)
                    .setContentTitle(intent?.getStringExtra(EXTRA_TITLE) ?: "Share Now transfer")
                    .setContentText(intent?.getStringExtra(EXTRA_MESSAGE) ?: "Transfer in progress")
                    .setOngoing(true)
                    .setOnlyAlertOnce(true)
                    .setCategory(NotificationCompat.CATEGORY_PROGRESS)
                    .setPriority(NotificationCompat.PRIORITY_LOW)
                    .build()

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(TRANSFER_NOTIFICATION_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
                } else {
                    startForeground(TRANSFER_NOTIFICATION_ID, notification)
                }
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_START = "com.xherit.fileshare.action.START_TRANSFER_SERVICE"
        const val ACTION_STOP = "com.xherit.fileshare.action.STOP_TRANSFER_SERVICE"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_MESSAGE = "extra_message"
    }
}
