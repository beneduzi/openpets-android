package com.openpets.android.companion

import android.content.Context
import com.openpets.android.NotificationHelper
import com.openpets.android.PetBus
import com.openpets.android.protocol.SayRequest
import java.util.concurrent.CopyOnWriteArrayList

/** Application-scoped API for configuring, scheduling, and reconciling routine check-ins. */
class RoutineController private constructor(context: Context, runtimeInstance: Boolean) {
    constructor(context: Context) : this(context, false)

    private val delegate: RoutineController? = if (runtimeInstance) null else CompanionRuntime.run { init(context); routine }
    private val appContext = context.applicationContext
    private val store = RoutineStore(appContext)
    private val scheduler = RoutineScheduler(appContext)
    private val engine = RoutineEngine()
    private val listeners = CopyOnWriteArrayList<(RoutineSettings) -> Unit>()
    private var current: RoutineSettings
    private var nextAlarmAtMs: Long

    init {
        val stored = store.load()
        current = engine.clamp(stored.settings)
        nextAlarmAtMs = stored.nextAlarmAtMs
    }

    @Synchronized fun settings(): RoutineSettings = delegate?.settings() ?: current

    @Synchronized fun refresh() {
        delegate?.let { it.refresh(); return }
        scheduleAndPersist(System.currentTimeMillis())
    }

    @Synchronized fun update(
        morningEnabled: Boolean,
        morningHour: Int,
        morningMinute: Int,
        eveningEnabled: Boolean,
        eveningHour: Int,
        eveningMinute: Int,
    ) {
        delegate?.let {
            it.update(morningEnabled, morningHour, morningMinute, eveningEnabled, eveningHour, eveningMinute)
            return
        }
        current = engine.clamp(current.copy(
            morningEnabled = morningEnabled,
            morningHour = morningHour,
            morningMinute = morningMinute,
            eveningEnabled = eveningEnabled,
            eveningHour = eveningHour,
            eveningMinute = eveningMinute,
        ))
        scheduleAndPersist(System.currentTimeMillis())
        listeners.forEach { it(current) }
    }

    fun addListener(l: (RoutineSettings) -> Unit) { (delegate ?: this).listeners.addIfAbsent(l) }
    fun removeListener(l: (RoutineSettings) -> Unit) { (delegate ?: this).listeners.remove(l) }

    /** Receiver entry point. Persisting before event delivery makes repeated alarms harmless. */
    @Synchronized internal fun onAlarmFired(now: Long = System.currentTimeMillis()) {
        delegate?.let { it.onAlarmFired(now); return }
        val storedAlarm = nextAlarmAtMs
        if (storedAlarm > 0L && now < storedAlarm) {
            scheduleAndPersist(now)
            return
        }
        val slot = engine.slotFor(if (storedAlarm > 0L) storedAlarm else now, current)
        if (slot == null) {
            scheduleAndPersist(now)
            return
        }
        if (current.lastFiredAtMs >= storedAlarm && storedAlarm > 0L) {
            scheduleAndPersist(now)
            return
        }
        current = current.copy(lastFiredAtMs = now)
        scheduleAndPersist(now)
        val message = if (slot == RoutineSlot.MORNING) appContext.getString(com.openpets.android.R.string.settings_routine_morning_message) else appContext.getString(com.openpets.android.R.string.settings_routine_evening_message)
        NotificationHelper(appContext).notifyRoutine(appContext.getString(com.openpets.android.R.string.settings_routine_checkin_title), message)
        PetBus.sink.onSay(
            SayRequest(
                message = message,
                reaction = "waving",
                source = "routine",
                bubbleId = "routine",
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
        internal fun createRuntime(context: Context) = RoutineController(context, true)
    }
}
