package com.openpets.android.companion

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class RoutineAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != RoutineScheduler.ACTION_ROUTINE_DUE) return
        CompanionRuntime.init(context)
        CompanionRuntime.routine.onAlarmFired()
    }
}
