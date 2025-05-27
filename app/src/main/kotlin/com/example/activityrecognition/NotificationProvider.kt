package com.example.activityrecognition

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat

class NotificationProvider(private val context: Context) {

    companion object {
        private const val NOTIFICATION_CHANNEL_ID = "in_vehicle_service_channel"
        private const val SERVICE_REQUEST_CODE = 1990
        const val NOTIFICATION_ID = 1
    }

    fun createNotification(activityName: String): Notification {
        val notificationIntent = Intent(context, MainActivity::class.java)
        val pendingIntentFlags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        val pendingIntent = PendingIntent.getActivity(
            context, SERVICE_REQUEST_CODE, notificationIntent, pendingIntentFlags
        )

        val notification =
            NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
                .setContentTitle("Activity Recognition")
                .setContentText("Detected activity: $activityName.")
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentIntent(pendingIntent)
                .setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
                .build()

        return notification
    }

    fun createNotificationChannel() {
        val serviceChannel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            "In Movement Service Channel",
            NotificationManager.IMPORTANCE_DEFAULT
        )
        val manager = context.getSystemService(NotificationManager::class.java)
        manager?.createNotificationChannel(serviceChannel)
    }
}