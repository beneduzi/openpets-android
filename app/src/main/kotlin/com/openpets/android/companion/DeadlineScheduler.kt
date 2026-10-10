package com.openpets.android.companion

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

class DeadlineScheduler(private val context: Context) {
    fun schedule(at: Long) {
        val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = pendingIntent()
        if (Build.VERSION.SDK_INT >= 31 && !alarm.canScheduleExactAlarms()) alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        else if (Build.VERSION.SDK_INT >= 23) alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        else alarm.setExact(AlarmManager.RTC_WAKEUP, at, pi)
    }
    fun cancel() { (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(pendingIntent()) }
    private fun pendingIntent() = PendingIntent.getBroadcast(context, REQUEST_CODE, Intent(context, DeadlineAlarmReceiver::class.java).setAction(ACTION_DEADLINE_DUE), PendingIntent.FLAG_UPDATE_CURRENT or if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0)
    companion object { const val ACTION_DEADLINE_DUE = "com.openpets.android.action.DEADLINE_DUE"; private const val REQUEST_CODE = 23041 }
}
