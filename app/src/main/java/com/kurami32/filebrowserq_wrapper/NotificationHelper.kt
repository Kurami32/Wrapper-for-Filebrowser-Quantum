package com.kurami32.filebrowserq_wrapper

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

@Suppress("unused")
class NotificationHelper(private val context: Context) {
    private val channelId = "filebrowser_downloads"
    private val notificationId = 1
    private val tag = "NotificationHelper"

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        try {
            val name = "File Downloads"
            val description = "Notifications for file downloads"
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(channelId, name, importance)
            channel.description = description

            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        } catch (ex: Throwable) {
            Log.w(tag, "Failed to create notification channel", ex)
        }
    }

    fun createNotification(title: String, content: String, icon: Int) {
        if (NotificationManagerCompat.from(context).areNotificationsEnabled().not()) {
            return
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        val flags = PendingIntent.FLAG_IMMUTABLE
        val pendingIntent = PendingIntent.getActivity(context, 0, intent, flags)

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(icon)
            .setContentTitle(title)
            .setContentText(content)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(notificationId, notification)
        } catch (ex: SecurityException) {
            Log.w(tag, "No permission to show notifications", ex)
        }
    }
}