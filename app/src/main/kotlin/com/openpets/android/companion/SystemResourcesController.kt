package com.openpets.android.companion

import android.content.Context
import com.openpets.android.FeatureHud
import com.openpets.android.PetBus
import com.openpets.android.protocol.HudItem
import java.util.concurrent.CopyOnWriteArrayList

class SystemResourcesController private constructor(context: Context, runtimeInstance: Boolean) {
    constructor(context: Context) : this(context, false)
    private val delegate: SystemResourcesController? = if (runtimeInstance) null else CompanionRuntime.run { init(context); resources }
    private val appContext = context.applicationContext
    private val reader = SystemResourcesReader(context.applicationContext)
    private val listeners = CopyOnWriteArrayList<(DeviceResources) -> Unit>()
    private var current = DeviceResources(null, null, null, null, null, null, null, null)

    @Synchronized fun snapshot(): DeviceResources = delegate?.snapshot() ?: current

    @Synchronized fun refresh() {
        delegate?.let { it.refresh(); return }
        current = reader.read()
        listeners.forEach { it(current) }
    }

    fun addListener(l: (DeviceResources) -> Unit) { (delegate ?: this).listeners.addIfAbsent(l) }
    fun removeListener(l: (DeviceResources) -> Unit) { (delegate ?: this).listeners.remove(l) }

    @Synchronized fun showOnPet() {
        delegate?.let { it.showOnPet(); return }
        val bars = buildList {
            val ctx=appContext
            current.batteryPercent?.let { add(HudItem(ctx.getString(com.openpets.android.R.string.settings_resources_battery), it.toFloat(), 100f, "green", null)) }
            current.memoryUsedMb?.let { used -> current.memoryTotalMb?.takeIf { it > 0 }?.let { total -> add(HudItem(ctx.getString(com.openpets.android.R.string.settings_resources_memory), used.toFloat(), total.toFloat(), "blue", null)) } }
            current.storageUsedMb?.let { used -> current.storageTotalMb?.takeIf { it > 0 }?.let { total -> add(HudItem(ctx.getString(com.openpets.android.R.string.settings_resources_storage), used.toFloat(), total.toFloat(), "amber", null)) } }
        }.take(4)
        PetBus.hud(FeatureHud("system-resources", "resources", appContext.getString(com.openpets.android.R.string.settings_resources_device), null, bars, claim = true))
    }

    companion object {
        internal fun createRuntime(context: Context) = SystemResourcesController(context, true)
    }
}
