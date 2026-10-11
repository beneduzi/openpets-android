package com.openpets.android.companion

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.openpets.android.PetBus
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
        PetBus.hostMetrics(null)
    }

    fun update(status: HostStatus) {
        if (!settings.hostStatusEnabled) {
            handler.removeCallbacks(clear)
            current = null
            PetBus.hostMetrics(null)
            return
        }
        current = status
        PetBus.hostMetrics(status)
        handler.removeCallbacks(clear)
        handler.postDelayed(clear, STALE_MS)
    }

    fun latest(): HostStatus? = current

    companion object { private const val STALE_MS = 30_000L }
}
