package com.openpets.android.companion
import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
class DeadlineStore(context: Context) {
 private val prefs=context.getSharedPreferences("openpets_deadline", Context.MODE_PRIVATE)
 fun load(): List<Deadline> = runCatching { val a=JSONArray(prefs.getString("items","[]")); (0 until a.length()).map { val o=a.getJSONObject(it); Deadline(o.getString("id"),o.getString("title"),o.getLong("due"),o.optBoolean("completed")) } }.getOrDefault(emptyList())
 fun save(items: List<Deadline>) { val a=JSONArray(); items.forEach { a.put(JSONObject().put("id",it.id).put("title",it.title).put("due",it.dueAtMs).put("completed",it.completed)) }; prefs.edit().putString("items",a.toString()).apply() }
}
