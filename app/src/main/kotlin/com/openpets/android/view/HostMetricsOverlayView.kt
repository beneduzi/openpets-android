package com.openpets.android.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import com.openpets.android.companion.HostStatus
import java.util.Locale
import kotlin.math.max

/** Quiet, screen-edge summary of companion host metrics. */
class HostMetricsOverlayView(context: Context) : View(context) {
    var overlayEnabled: Boolean = true
        set(value) { field = value; visibility = if (value && rows.isNotEmpty()) VISIBLE else GONE }
    private data class Row(val label: String, val value: String, val fraction: Float, val color: Int)

    private val density = resources.displayMetrics.density
    private val panel = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x38000000 }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF9E9E9E.toInt(); textSize = 10f * density }
    private val title = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x889E9E9E.toInt(); textSize = 9f * density; letterSpacing = 0.14f }
    private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x24FFFFFF }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bounds = RectF()
    private val rows = ArrayList<Row>(4)
    private var memoryLine: String? = null

    init { visibility = GONE }

    fun update(status: HostStatus?) {
        rows.clear()
        memoryLine = null
        if (status == null || !overlayEnabled) {
            visibility = GONE
            return
        }
        status.cpuPct?.let { rows.add(Row("CPU", "$it/100", it / 100f, 0xFF16A34A.toInt())) }
        if (status.ramUsedMb != null && status.ramTotalMb != null && status.ramTotalMb > 0) {
            val pct = (100f * status.ramUsedMb / status.ramTotalMb).toInt()
            rows.add(Row("RAM", "$pct/100", pct / 100f, 0xFF2563EB.toInt()))
            memoryLine = String.format(Locale.US, "%.1f / %.1f GB", status.ramUsedMb / 1024.0, status.ramTotalMb / 1024.0)
        }
        status.diskUsedPct?.let { rows.add(Row("Disk", "$it/100", it / 100f, 0xFFD97706.toInt())) }
        status.usagePct?.let { rows.add(Row(status.usageLabel ?: "Usage", "$it/100", it / 100f, 0xFFDB2777.toInt())) }
        visibility = if (rows.isEmpty() || !overlayEnabled) GONE else VISIBLE
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val pad = 9f * density
        bounds.set(0f, 0f, width.toFloat(), height.toFloat())
        canvas.drawRoundRect(bounds, 12f * density, 12f * density, panel)
        var y = pad + title.textSize
        canvas.drawText("HOST", pad, y, title)
        y += 14f * density
        val labelWidth = 30f * density
        val valueWidth = 36f * density
        val barLeft = pad + labelWidth
        val barRight = max(barLeft, width - pad - valueWidth)
        rows.forEach { row ->
            val baseline = y + text.textSize
            canvas.drawText(row.label, pad, baseline, text)
            text.textAlign = Paint.Align.RIGHT
            canvas.drawText(row.value, width - pad, baseline, text)
            text.textAlign = Paint.Align.LEFT
            val top = y + 3f * density
            bounds.set(barLeft, top, barRight, top + 4f * density)
            canvas.drawRoundRect(bounds, 2f * density, 2f * density, track)
            fill.color = row.color
            bounds.right = barLeft + (barRight - barLeft) * row.fraction.coerceIn(0f, 1f)
            canvas.drawRoundRect(bounds, 2f * density, 2f * density, fill)
            y += 20f * density
        }
        memoryLine?.let {
            text.textSize = 9f * density
            canvas.drawText(it, pad, y + text.textSize, text)
        }
    }
}
