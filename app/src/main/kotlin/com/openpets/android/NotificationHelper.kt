package com.openpets.android

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat

/** Posts system notifications for agent events (used when the app is backgrounded). */
class NotificationHelper(private val context: Context) {

    fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(EVENT_CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                EVENT_CHANNEL_ID,
                context.getString(R.string.events_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = context.getString(R.string.events_channel_description)
                setShowBadge(true)
            },
        )
    }

    fun notifyEvent(title: String?, text: String) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        ensureChannel()
        val notification = NotificationCompat.Builder(context, EVENT_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle(title?.takeIf { it.isNotBlank() } ?: context.getString(R.string.app_name))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        manager.notify(EVENT_NOTIFICATION_ID, notification)
    }

    companion object {
        const val EVENT_CHANNEL_ID = "openpets_events"
        const val EVENT_NOTIFICATION_ID = 1011
    }
}
