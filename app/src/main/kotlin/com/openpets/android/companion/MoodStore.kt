package com.openpets.android.companion

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class MoodStore(context: Context) {
    private val preferences = context.getSharedPreferences("openpets_mood", Context.MODE_PRIVATE)

    fun load(): List<MoodEntry> = runCatching {
        val array = JSONArray(preferences.getString(KEY_ENTRIES, "[]"))
        (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            val value = item.optInt("value", 0).takeIf { it in 1..5 } ?: return@mapNotNull null
            MoodEntry(value, item.optString("note").takeIf { it.isNotBlank() && it != "null" }, item.optLong("atMs"))
        }.sortedByDescending { it.atMs }.take(MAX_STORED_ENTRIES)
    }.getOrDefault(emptyList())

    fun save(entries: List<MoodEntry>) {
        val array = JSONArray()
        entries.sortedByDescending { it.atMs }.take(MAX_STORED_ENTRIES).forEach { entry ->
            array.put(JSONObject().apply {
                put("value", entry.value)
                put("note", entry.note)
                put("atMs", entry.atMs)
            })
        }
        preferences.edit().putString(KEY_ENTRIES, array.toString()).apply()
    }

    companion object {
        const val MAX_STORED_ENTRIES = 200
        private const val KEY_ENTRIES = "entries"
    }
}
