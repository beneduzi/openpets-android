package com.openpets.android.companion

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class ReminderStore(context: Context) {
    private val preferences = context.getSharedPreferences("openpets_reminders", Context.MODE_PRIVATE)

    fun load(): List<Reminder> = runCatching {
        val array = JSONArray(preferences.getString(KEY_REMINDERS, "[]"))
        (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            val id = item.optString("id").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val title = item.optString("title").trim().takeIf { it.isNotEmpty() && it.length <= ReminderEngine.MAX_TITLE_LENGTH }
                ?: return@mapNotNull null
            Reminder(id, title, item.optLong("dueAtMs"), item.optBoolean("completed"), item.optBoolean("notified"))
        }
    }.getOrDefault(emptyList())

    fun save(list: List<Reminder>) {
        val array = JSONArray()
        list.forEach { reminder ->
            array.put(JSONObject().apply {
                put("id", reminder.id)
                put("title", reminder.title)
                put("dueAtMs", reminder.dueAtMs)
                put("completed", reminder.completed)
                put("notified", reminder.notified)
            })
        }
        preferences.edit().putString(KEY_REMINDERS, array.toString()).apply()
    }

    companion object { private const val KEY_REMINDERS = "reminders" }
}
