package com.openpets.android.companion

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class ReminderAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != ReminderScheduler.ACTION_REMINDER_DUE) return
        CompanionRuntime.init(context)
        CompanionRuntime.reminders.refresh()
    }
}
