package com.openpets.android.companion

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

class ReminderScheduler(context: Context) {
    private val appContext = context.applicationContext
    private val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun schedule(triggerAtMs: Long) {
        val pendingIntent = PendingIntent.getBroadcast(
            appContext, REQUEST_CODE,
            Intent(appContext, ReminderAlarmReceiver::class.java).setAction(ACTION_REMINDER_DUE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMs, pendingIntent)
        } else {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMs, pendingIntent)
        }
    }

    fun cancel() {
        val pendingIntent = PendingIntent.getBroadcast(
            appContext, REQUEST_CODE,
            Intent(appContext, ReminderAlarmReceiver::class.java).setAction(ACTION_REMINDER_DUE),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        ) ?: return
        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()
    }

    companion object {
        const val ACTION_REMINDER_DUE = "com.openpets.android.companion.REMINDER_DUE"
        private const val REQUEST_CODE = 4318
    }
}
