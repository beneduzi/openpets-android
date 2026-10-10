package com.openpets.android.companion

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class CalendarAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != CalendarScheduler.ACTION) return
        CompanionRuntime.init(context)
        CompanionRuntime.calendar.onAlarmFired()
    }
}
