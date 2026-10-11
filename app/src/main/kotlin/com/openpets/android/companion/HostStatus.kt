package com.openpets.android.companion

import android.content.Context
import kotlin.math.roundToInt
import android.os.Handler
import android.os.Looper
import com.openpets.android.FeatureHud
import com.openpets.android.PetBus
import com.openpets.android.protocol.HudItem
import com.openpets.android.settings.AppSettings

data class HostStatus(
    val cpuPct: Int?, val ramUsedMb: Long?, val ramTotalMb: Long?, val diskUsedPct: Int?,
    val netRxKbps: Long?, val netTxKbps: Long?, val loadAvg: Double?, val tsMs: Long,
    val usagePct: Int? = null, val usageLabel: String? = null,
)

class HostStatusController(context: Context) {
    private val settings = AppSettings(context.applicationContext)
    private val handler = Handler(Looper.getMainLooper())
    @Volatile private var current: HostStatus? = null
    private val clear = Runnable {
        current = null
        PetBus.sink.onDismissBubble("host-status")
    }

    fun update(status: HostStatus) {
        if (!settings.hostStatusEnabled) {
            handler.removeCallbacks(clear)
            current = null
            PetBus.sink.onDismissBubble("host-status")
            return
        }
        current = status
        val hud = buildList {
            status.cpuPct?.let { add(HudItem("CPU", it.toFloat(), 100f, "green", null)) }
            if (status.ramUsedMb != null && status.ramTotalMb != null && status.ramTotalMb > 0) add(HudItem("RAM", (100f * status.ramUsedMb / status.ramTotalMb).roundToInt().toFloat(), 100f, "blue", null))
            status.diskUsedPct?.let { add(HudItem("Disk", it.toFloat(), 100f, "amber", null)) }
            status.usagePct?.let { add(HudItem(status.usageLabel ?: "Usage", it.toFloat(), 100f, "pink", null)) }
        }
        val memory = if (status.ramUsedMb != null && status.ramTotalMb != null) "${"%.1f".format(status.ramUsedMb / 1024.0)} / ${"%.1f".format(status.ramTotalMb / 1024.0)} GB" else ""
        PetBus.hud(FeatureHud("host-status", "host", "Host", memory, hud, claim = true))
        handler.removeCallbacks(clear)
        handler.postDelayed(clear, STALE_MS)
    }

    fun latest(): HostStatus? = current

    companion object { private const val STALE_MS = 30_000L }
}
