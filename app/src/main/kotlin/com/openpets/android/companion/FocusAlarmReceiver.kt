package com.openpets.android.companion

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class FocusAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != FocusScheduler.ACTION_FOCUS_PHASE_ENDED) return
        CompanionRuntime.init(context)
        CompanionRuntime.focus.refresh()
    }
}
