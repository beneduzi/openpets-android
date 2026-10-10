package com.openpets.android.companion

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.openpets.android.NotificationHelper
import com.openpets.android.PetBus
import com.openpets.android.protocol.HudItem
import com.openpets.android.protocol.SayRequest

class FocusAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != FocusScheduler.ACTION_FOCUS_PHASE_ENDED) return
        val appContext = context.applicationContext
        val store = FocusStore(appContext)
        val stored = store.load()
        if (stored.snapshot.state != FocusState.FOCUS && stored.snapshot.state != FocusState.BREAK) return

        val engine = FocusEngine()
        engine.restore(stored.snapshot, stored.finishAtMs, stored.focusMs, stored.breakMs)
        val priorState = stored.snapshot.state
        val snapshot = engine.snapshot()
        store.save(snapshot, stored.focusMs, stored.breakMs, engine.finishAtMs())

        if (snapshot.state != priorState) {
            val title = if (snapshot.state == FocusState.BREAK) "Break time" else "Focus session"
            NotificationHelper(appContext).notifyFocusFinished(title, title)
            val total = snapshot.phaseTotalMs.toFloat().coerceAtLeast(1f)
            PetBus.sink.onSay(
                SayRequest(
                    message = title,
                    reaction = "testing",
                    source = "focus",
                    hud = listOf(HudItem(title, snapshot.phaseRemainingMs.toFloat(), total, "info", "focus")),
                    bubbleId = "focus-buddy",
                    pinned = true,
                ),
            )
        }
        if (snapshot.state == FocusState.FOCUS || snapshot.state == FocusState.BREAK) {
            FocusScheduler(appContext).schedule(engine.finishAtMs())
        }
    }
}
