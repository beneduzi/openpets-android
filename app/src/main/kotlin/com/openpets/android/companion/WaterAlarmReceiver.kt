package com.openpets.android.companion

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class WaterAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != WaterScheduler.ACTION_WATER_DUE) return
        CompanionRuntime.init(context)
        CompanionRuntime.water.onAlarmFired()
    }
}
