package com.openpets.android.companion

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.openpets.android.NotificationHelper
import com.openpets.android.PetBus
import com.openpets.android.FeatureHud
import com.openpets.android.protocol.HudItem
import com.openpets.android.protocol.SayRequest
import java.util.concurrent.CopyOnWriteArrayList

class CalendarController private constructor(context: Context, runtime: Boolean) {
    constructor(context: Context) : this(context, false)
    private val delegate = if (runtime) null else CompanionRuntime.run { init(context); calendar }
    private val appContext = context.applicationContext
    private val reader = CalendarReader(appContext)
    private val scheduler = CalendarScheduler(appContext)
    private val prefs = appContext.getSharedPreferences("calendar_companion", Context.MODE_PRIVATE)
    private val listeners = CopyOnWriteArrayList<(List<DeviceEvent>) -> Unit>()

    init { if (runtime) refresh() }
    fun hasPermission(): Boolean = delegate?.hasPermission() ?: (ContextCompat.checkSelfPermission(appContext, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED)
    fun upcoming(): List<DeviceEvent> = delegate?.upcoming() ?: reader.upcoming()
    fun enabled(): Boolean = delegate?.enabled() ?: prefs.getBoolean(KEY_ENABLED, true)
    fun setEnabled(enabled: Boolean) { delegate?.setEnabled(enabled) ?: run { prefs.edit().putBoolean(KEY_ENABLED, enabled).apply(); refresh() } }
    fun addListener(listener: (List<DeviceEvent>) -> Unit) { (delegate ?: this).listeners.addIfAbsent(listener) }
    fun removeListener(listener: (List<DeviceEvent>) -> Unit) { (delegate ?: this).listeners.remove(listener) }
    fun refresh() {
        delegate?.let { it.refresh(); return }
        scheduler.cancel()
        if (enabled()) scheduler.schedule(System.currentTimeMillis() + CHECK_MS)
        listeners.forEach { it(upcoming()) }
    }
    fun showOnPet() {
        delegate?.let { it.showOnPet(); return }
        val event = upcoming().firstOrNull() ?: return
        val message = "Next: ${event.title} (${CalendarFormat.relativeTime(event.beginMs, System.currentTimeMillis())})"
        PetBus.hud(FeatureHud("calendar", "calendar", message, hud = listOf(HudItem(event.title.take(36), 1f, 1f, "info", "calendar")), claim = false))
    }
    internal fun onAlarmFired(now: Long = System.currentTimeMillis()) {
        if (!enabled()) return
        val notified = prefs.getStringSet(KEY_NOTIFIED, emptySet()).orEmpty().toMutableSet()
        upcoming().filter { it.beginMs in now..(now + WINDOW_MS) && it.id.toString() !in notified }.forEach { event ->
            notified += event.id.toString()
            prefs.edit().putStringSet(KEY_NOTIFIED, notified.toList().takeLast(256).toSet()).apply()
            NotificationHelper(appContext).notifyCalendar(event.title, appContext.getString(com.openpets.android.R.string.settings_calendar_starting_soon))
            PetBus.sink.onSay(SayRequest(message = "Next: ${event.title}", reaction = "waiting", source = "calendar", bubbleId = "calendar", pinned = false))
        }
        refresh()
    }
    companion object {
        private const val KEY_ENABLED = "enabled"
        private const val KEY_NOTIFIED = "notified"
        private const val CHECK_MS = 15 * 60_000L
        private const val WINDOW_MS = 10 * 60_000L
        internal fun createRuntime(context: Context) = CalendarController(context, true)
    }
}
