package com.openpets.android.companion

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.openpets.android.NotificationHelper
import com.openpets.android.PetBus
import com.openpets.android.protocol.SayRequest

class DeadlineAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != DeadlineScheduler.ACTION_DEADLINE_DUE) return
        val store = DeadlineStore(context.applicationContext)
        val engine = DeadlineEngine().apply { replaceAll(store.load()) }
        val now = System.currentTimeMillis()
        engine.all().filterNot { it.completed }.forEach { d ->
            val offset = listOf(DeadlineEngine.DAY, DeadlineEngine.HOUR, DeadlineEngine.FIFTEEN_MIN, 0L).firstOrNull { kotlin.math.abs((d.dueAtMs - it) - now) <= TOLERANCE } ?: return@forEach
            val label = engine.reminderOffsetFor(d.dueAtMs, d.dueAtMs - offset)?.let(context::getString) ?: return@forEach
            NotificationHelper(context).notifyDeadline(d.title, context.getString(com.openpets.android.R.string.settings_deadline_due_in, label))
            PetBus.sink.onSay(SayRequest(message = context.getString(com.openpets.android.R.string.settings_deadline_title, d.title), reaction = "waiting", source = "deadline", bubbleId = "deadline", pinned = false))
        }
        DeadlineScheduler(context).also { it.cancel(); engine.nextReminderAt(now)?.let(it::schedule) }
    }
    companion object { private const val TOLERANCE = 60_000L }
}
