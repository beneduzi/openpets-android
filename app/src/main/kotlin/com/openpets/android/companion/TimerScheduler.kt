package com.openpets.android.companion

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

class TimerScheduler(context: Context) {
    private val appContext = context.applicationContext
    private val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun schedule(triggerAtMs: Long) {
        val intent = PendingIntent.getBroadcast(
            appContext, REQUEST_CODE, Intent(appContext, TimerAlarmReceiver::class.java).setAction(ACTION_TIMER_FINISHED),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMs, intent)
        } else {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMs, intent)
        }
    }

    fun cancel() {
        val intent = PendingIntent.getBroadcast(
            appContext, REQUEST_CODE, Intent(appContext, TimerAlarmReceiver::class.java).setAction(ACTION_TIMER_FINISHED),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        ) ?: return
        alarmManager.cancel(intent)
        intent.cancel()
    }

    companion object {
        const val ACTION_TIMER_FINISHED = "com.openpets.android.companion.TIMER_FINISHED"
        private const val REQUEST_CODE = 4317
    }
}
