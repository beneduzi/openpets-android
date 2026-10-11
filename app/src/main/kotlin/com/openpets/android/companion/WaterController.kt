package com.openpets.android.companion

import android.content.Context
import com.openpets.android.NotificationHelper
import com.openpets.android.PetBus
import com.openpets.android.protocol.SayRequest
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Application-scoped API: settings(): WaterSettings; refresh();
 * update(enabled, intervalMinutes, fromHour, toHour); addListener/removeListener.
 * Construction with a Context delegates to the shared [CompanionRuntime] instance.
 */
class WaterController private constructor(context: Context, runtimeInstance: Boolean) {
    constructor(context: Context) : this(context, false)

    private val delegate: WaterController? = if (runtimeInstance) null else CompanionRuntime.run { init(context); water }
    private val appContext = context.applicationContext
    private val store = WaterStore(appContext)
    private val scheduler = WaterScheduler(appContext)
    private val engine = WaterEngine()
    private val listeners = CopyOnWriteArrayList<(WaterSettings) -> Unit>()
    private var current: WaterSettings
    private var nextAlarmAtMs: Long

    init {
        val stored = store.load()
        current = engine.clamp(stored.settings)
        nextAlarmAtMs = stored.nextAlarmAtMs
    }

    @Synchronized fun settings(): WaterSettings = delegate?.settings() ?: current

    @Synchronized fun refresh() {
        delegate?.let { it.refresh(); return }
        scheduleAndPersist(System.currentTimeMillis())
    }

    @Synchronized fun update(enabled: Boolean, intervalMinutes: Int, fromHour: Int, toHour: Int) {
        delegate?.let { it.update(enabled, intervalMinutes, fromHour, toHour); return }
        current = engine.clamp(current.copy(
            enabled = enabled,
            intervalMinutes = intervalMinutes,
            fromHour = fromHour,
            toHour = toHour,
        ))
        scheduleAndPersist(System.currentTimeMillis())
        listeners.forEach { it(current) }
    }

    fun addListener(l: (WaterSettings) -> Unit) { (delegate ?: this).listeners.addIfAbsent(l) }
    fun removeListener(l: (WaterSettings) -> Unit) { (delegate ?: this).listeners.remove(l) }

    /** Called by the alarm receiver; persists the fire before notifying to prevent repeats. */
    @Synchronized internal fun onAlarmFired(now: Long = System.currentTimeMillis()) {
        delegate?.let { it.onAlarmFired(now); return }
        if (!current.enabled) {
            scheduleAndPersist(now)
            return
        }
        if (nextAlarmAtMs > 0L && now < nextAlarmAtMs) {
            scheduleAndPersist(now)
            return
        }
        current = engine.onFired(now, current)
        scheduleAndPersist(now)
        NotificationHelper(appContext).notifyWater(appContext.getString(com.openpets.android.R.string.settings_water_notify_title), appContext.getString(com.openpets.android.R.string.settings_water_notify_body))
        PetBus.sink.onSay(
            SayRequest(
                message = appContext.getString(com.openpets.android.R.string.settings_water_notify_body),
                reaction = "waiting",
                source = "water",
                bubbleId = "water-reminder",
                pinned = false,
            ),
        )
        listeners.forEach { it(current) }
    }

    private fun scheduleAndPersist(now: Long) {
        scheduler.cancel()
        nextAlarmAtMs = engine.nextFire(now, current) ?: 0L
        if (nextAlarmAtMs > 0L) scheduler.schedule(nextAlarmAtMs)
        store.save(current, nextAlarmAtMs)
    }

    companion object {
        internal fun createRuntime(context: Context) = WaterController(context, true)
    }
}
