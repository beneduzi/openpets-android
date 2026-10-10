package com.openpets.android.companion

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.openpets.android.NotificationHelper
import com.openpets.android.PetBus
import com.openpets.android.protocol.HudItem
import com.openpets.android.protocol.SayRequest

class ReminderAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != ReminderScheduler.ACTION_REMINDER_DUE) return
        val appContext = context.applicationContext
        val store = ReminderStore(appContext)
        val engine = ReminderEngine()
        engine.replaceAll(store.load())
        val now = System.currentTimeMillis()
        val due = engine.due(now)
        due.forEach { engine.markNotified(it.id) }
        store.save(engine.all())

        val notifier = NotificationHelper(appContext)
        due.forEach { notifier.notifyReminder(it.id, it.title, it.title) }
        due.firstOrNull()?.let { nearest ->
            val hudLabel = nearest.title.take(HUD_TITLE_MAX)
            PetBus.sink.onSay(
                SayRequest(
                    message = nearest.title,
                    reaction = "waiting",
                    source = "reminder",
                    hud = listOf(HudItem(hudLabel, 1f, 1f, "warning", "reminder")),
                    bubbleId = "quick-reminder-${nearest.id}",
                    pinned = true,
                ),
            )
        }
        engine.nextTrigger(now)?.let { ReminderScheduler(appContext).schedule(it) }
    }

    companion object { private const val HUD_TITLE_MAX = 36 }
}
