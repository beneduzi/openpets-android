package com.openpets.android.companion

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

class CalendarScheduler(context: Context) {
    private val appContext = context.applicationContext
    fun schedule(at: Long) {
        val manager = appContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = PendingIntent.getBroadcast(appContext, 4540, Intent(appContext, CalendarAlarmReceiver::class.java).setAction(ACTION), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !manager.canScheduleExactAlarms()) manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi) else manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
    }
    fun cancel() {
        val manager = appContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = PendingIntent.getBroadcast(appContext, 4540, Intent(appContext, CalendarAlarmReceiver::class.java).setAction(ACTION), PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE) ?: return
        manager.cancel(pi); pi.cancel()
    }
    companion object { const val ACTION = "com.openpets.android.companion.CALENDAR_CHECK" }
}
