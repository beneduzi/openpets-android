package com.openpets.android.companion

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class ChecklistStore(context: Context) {
    private val prefs = context.getSharedPreferences("openpets_checklist", Context.MODE_PRIVATE)

    fun load(): List<ChecklistItem> = runCatching {
        val array = JSONArray(prefs.getString("items", "[]"))
        (0 until array.length()).map { index ->
            val item = array.getJSONObject(index)
            ChecklistItem(item.getString("id"), item.getString("label"), item.optBoolean("done"))
        }
    }.getOrDefault(emptyList())

    fun save(items: List<ChecklistItem>) {
        val array = JSONArray()
        items.forEach { array.put(JSONObject().put("id", it.id).put("label", it.label).put("done", it.done)) }
        prefs.edit().putString("items", array.toString()).apply()
    }
}
