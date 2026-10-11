package com.openpets.android.companion

import android.content.Context
import com.openpets.android.PetBus
import com.openpets.android.FeatureHud
import java.util.concurrent.CopyOnWriteArrayList

class DeadlineController private constructor(context: Context, runtimeInstance: Boolean) {
    constructor(context: Context) : this(context, false)
    private val delegate: DeadlineController? = if (runtimeInstance) null else CompanionRuntime.deadline
    private val appContext = context.applicationContext
    private val store = DeadlineStore(appContext)
    private val scheduler = DeadlineScheduler(appContext)
    private val engine = DeadlineEngine()
    private val listeners = CopyOnWriteArrayList<(List<Deadline>) -> Unit>()
    init { engine.replaceAll(store.load()); reconcile() }
    @Synchronized fun upcoming(): List<Deadline> = delegate?.upcoming() ?: engine.upcoming(System.currentTimeMillis())
    @Synchronized fun nearest(): Deadline? = delegate?.nearest() ?: engine.nearest(System.currentTimeMillis())
    @Synchronized fun refresh() { delegate?.let { it.refresh(); return }; reconcile(); notifyListeners() }
    @Synchronized fun add(title: String, dueAtMs: Long): Deadline { delegate?.let { return it.add(title,dueAtMs) }; return engine.add(title,dueAtMs).also { persist(); notifyListeners() } }
    @Synchronized fun rename(id: String,title: String) { delegate?.let { it.rename(id,title); return }; if(engine.rename(id,title)) changed() }
    @Synchronized fun reschedule(id: String,dueAtMs: Long) { delegate?.let { it.reschedule(id,dueAtMs); return }; if(engine.reschedule(id,dueAtMs)) changed() }
    @Synchronized fun complete(id: String) { delegate?.let { it.complete(id); return }; if(engine.complete(id)) changed() }
    @Synchronized fun remove(id: String) { delegate?.let { it.remove(id); return }; if(engine.remove(id)) changed() }
    @Synchronized fun showOnPet() { delegate?.let { it.showOnPet(); return }; nearest()?.let { PetBus.hud(FeatureHud("deadline", "deadline", it.title, relative(it.dueAtMs), emptyList(), true)) } }
    fun addListener(l:(List<Deadline>)->Unit) { (delegate ?: this).listeners.addIfAbsent(l) }
    fun removeListener(l:(List<Deadline>)->Unit) { (delegate ?: this).listeners.remove(l) }
    private fun changed() { persist(); notifyListeners() }
    private fun persist() { store.save(engine.all()); scheduler.cancel(); engine.nextReminderAt(System.currentTimeMillis())?.let(scheduler::schedule) }
    private fun reconcile() = persist()
    private fun notifyListeners() { val v=upcoming(); listeners.forEach { it(v) } }
    private fun relative(time:Long):String { val delta=time-System.currentTimeMillis(); if(delta<0)return appContext.getString(com.openpets.android.R.string.settings_deadline_overdue); val min=delta/60000; return when { min>=1440 -> appContext.getString(com.openpets.android.R.string.settings_deadline_days,(min/1440).toInt()); min>=60 -> appContext.getString(com.openpets.android.R.string.settings_deadline_hours,(min/60).toInt()); else -> appContext.getString(com.openpets.android.R.string.settings_deadline_minutes,min.toInt()) } }
    companion object { internal fun createRuntime(context:Context)=DeadlineController(context,true) }
}
