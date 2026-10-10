package com.openpets.android.companion

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.openpets.android.NotificationHelper
import com.openpets.android.PetBus
import com.openpets.android.protocol.HudItem
import com.openpets.android.protocol.SayRequest

class TimerAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != TimerScheduler.ACTION_TIMER_FINISHED) return
        val store = TimerStore(context)
        val stored = store.load()
        if (stored.snapshot.state != TimerRunState.RUNNING || System.currentTimeMillis() < stored.alarmTriggerMs) return
        val finished = stored.snapshot.copy(state = TimerRunState.FINISHED, remainingMs = 0L)
        store.save(finished, System.currentTimeMillis(), 0L, 0L)
        NotificationHelper(context).notifyTimerFinished("Timer finished", stored.snapshot.label ?: "Your timer is done.")
        PetBus.sink.onSay(
            SayRequest(
                message = "Timer finished",
                reaction = "success",
                source = "timer",
                hud = listOf(HudItem(stored.snapshot.label ?: "Timer", 0f, 1f, "success", "timer")),
                bubbleId = "simple-timer",
                pinned = true,
            ),
        )
    }
}
