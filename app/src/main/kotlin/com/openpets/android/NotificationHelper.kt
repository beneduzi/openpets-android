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

    fun notifyTimerFinished(title: String, text: String) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        ensureTimerChannel()
        val notification = NotificationCompat.Builder(context, TIMER_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        manager.notify(TIMER_NOTIFICATION_ID, notification)
    }

    private fun ensureTimerChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(TIMER_CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(TIMER_CHANNEL_ID, "Timer", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Simple timer notifications"
                    setShowBadge(true)
                },
            )
        }
    }

    fun notifyReminder(id: String, title: String, text: String) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        ensureRemindersChannel()
        val notification = NotificationCompat.Builder(context, REMINDERS_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        manager.notify(REMINDER_NOTIFICATION_BASE + (id.hashCode() and 0x3fffffff), notification)
    }

    private fun ensureRemindersChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(REMINDERS_CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(REMINDERS_CHANNEL_ID, "Reminders", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Quick reminder notifications"
                    setShowBadge(true)
                },
            )
        }
    }

    fun notifyFocusFinished(title: String, text: String) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        ensureFocusChannel()
        val notification = NotificationCompat.Builder(context, FOCUS_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        manager.notify(FOCUS_NOTIFICATION_ID, notification)
    }

    private fun ensureFocusChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(FOCUS_CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(FOCUS_CHANNEL_ID, "Focus Buddy", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Focus session and break notifications"
                    setShowBadge(true)
                },
            )
        }
    }

    fun notifyWater(title: String, text: String) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        ensureWaterChannel()
        val notification = NotificationCompat.Builder(context, WATER_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        manager.notify(WATER_NOTIFICATION_ID, notification)
    }

    private fun ensureWaterChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(WATER_CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(WATER_CHANNEL_ID, "Water reminders", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Hydration reminder notifications"
                    setShowBadge(true)
                },
            )
        }
    }

    fun notifyRoutine(title: String, text: String) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        ensureRoutineChannel()
        val notification = NotificationCompat.Builder(context, ROUTINE_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        manager.notify(ROUTINE_NOTIFICATION_ID, notification)
    }

    private fun ensureRoutineChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(ROUTINE_CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(ROUTINE_CHANNEL_ID, "Routine check-ins", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Morning and evening routine notifications"
                    setShowBadge(true)
                },
            )
        }
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
        const val TIMER_CHANNEL_ID = "openpets_timer"
        const val TIMER_NOTIFICATION_ID = 1012
        const val REMINDERS_CHANNEL_ID = "openpets_reminders"
        const val FOCUS_CHANNEL_ID = "openpets_focus"
        const val FOCUS_NOTIFICATION_ID = 1013
        const val WATER_CHANNEL_ID = "openpets_water"
        const val WATER_NOTIFICATION_ID = 1014
        const val ROUTINE_CHANNEL_ID = "openpets_routine"
        const val ROUTINE_NOTIFICATION_ID = 1015
        private const val REMINDER_NOTIFICATION_BASE = 20_000
    }
}
