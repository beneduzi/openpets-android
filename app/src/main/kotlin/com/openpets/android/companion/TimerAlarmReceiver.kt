package com.openpets.android.companion

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class TimerAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != TimerScheduler.ACTION_TIMER_FINISHED) return
        CompanionRuntime.init(context)
        CompanionRuntime.timer.refresh()
    }
}
