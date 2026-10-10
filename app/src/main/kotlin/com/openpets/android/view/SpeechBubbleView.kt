package com.openpets.android.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.BackgroundColorSpan
import android.text.style.StyleSpan
import android.text.style.TypefaceSpan
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.core.graphics.ColorUtils
import com.openpets.android.protocol.HudItem
import com.openpets.android.protocol.PetAction
import com.openpets.android.protocol.TRANSIENT_DISPLAY_MS
import kotlin.math.min

/**
 * Speech bubble: optional project label, per-project accent, markdown message,
 * HUD progress rows, action buttons, and pin support.
 */
class SpeechBubbleView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    private val density = context.resources.displayMetrics.density
    private val scaledDensity = density * context.resources.configuration.fontScale

    private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF172033.toInt()
        textSize = 16f * scaledDensity
        typeface = Typeface.DEFAULT
        isFakeBoldText = true
    }
    private val labelPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 11f * scaledDensity
        typeface = Typeface.DEFAULT
        isFakeBoldText = true
    }
    private val smallPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 10f * scaledDensity
        typeface = Typeface.DEFAULT
    }
    private val smallBoldPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 10f * scaledDensity
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    private val buttonTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 12f * scaledDensity
        typeface = Typeface.DEFAULT
        isFakeBoldText = true
        textAlign = Paint.Align.CENTER
    }

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1f)
    }
    private val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = dp(1f) }
    private val barBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val barFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val buttonPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val tailPath = Path()

    private val cornerRadius = dp(14f)
    private val padding = dp(12f)
    private val tailHeight = dp(8f)
    private val maxBubbleWidth = dp(320f)
    private val buttonHeight = dp(30f)
    private val buttonGap = dp(6f)
    private val hudRowHeight = dp(22f)

    private var message: CharSequence? = null
    private var label: String? = null
    private var accent: Int? = null
    private var actions: List<PetAction> = emptyList()
    private var hud: List<HudItem> = emptyList()
    private var pinned = false
    var bubbleId: String? = null
        private set
    private var onAction: ((PetAction) -> Unit)? = null
    private var layout: StaticLayout? = null
    private var labelHeight = 0f
    private var buttonWidth = 0f
    private val buttonRects = ArrayList<Pair<RectF, PetAction>>()

    private val handler = Handler(Looper.getMainLooper())
    private val dismissRunnable = Runnable { dismiss() }

    val isShowing: Boolean get() = message != null || label != null

    fun show(
        text: String,
        accent: Int? = null,
        label: String? = null,
        durationMs: Long = TRANSIENT_DISPLAY_MS.toLong(),
        actions: List<PetAction> = emptyList(),
        hud: List<HudItem> = emptyList(),
        bubbleId: String? = null,
        pinned: Boolean = false,
        onAction: ((PetAction) -> Unit)? = null,
    ) {
        this.message = renderMarkdown(text)
        this.label = label
        this.accent = accent
        this.actions = actions
        this.hud = hud
        this.bubbleId = bubbleId
        this.pinned = pinned
        this.onAction = onAction
        layout = null
        visibility = VISIBLE
        isClickable = actions.isNotEmpty() || pinned
        handler.removeCallbacks(dismissRunnable)
        if (durationMs > 0 && !pinned) handler.postDelayed(dismissRunnable, durationMs)
        requestLayout()
        invalidate()
    }

    fun dismiss() {
        handler.removeCallbacks(dismissRunnable)
        if (message == null && label == null && visibility == GONE) return
        message = null
        label = null
        accent = null
        actions = emptyList()
        hud = emptyList()
        pinned = false
        bubbleId = null
        onAction = null
        layout = null
        buttonRects.clear()
        visibility = GONE
        requestLayout()
        invalidate()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        handler.removeCallbacks(dismissRunnable)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val available = MeasureSpec.getSize(widthMeasureSpec)
        val maxContentWidth = (minOf(maxBubbleWidth, available.toFloat()) - 2 * padding).toInt().coerceAtLeast(24)

        val msg = message
        var contentWidth = 0f
        val textLayout = if (msg != null) {
            StaticLayout.Builder.obtain(msg, 0, msg.length, textPaint, maxContentWidth)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setIncludePad(false)
                .setMaxLines(6)
                .build().also { l ->
                    for (line in 0 until l.lineCount) contentWidth = maxOf(contentWidth, l.getLineWidth(line))
                }
        } else {
            null
        }

        val lbl = label
        labelHeight = if (lbl != null) {
            val fm = labelPaint.fontMetrics
            contentWidth = maxOf(contentWidth, labelPaint.measureText(lbl))
            fm.descent - fm.ascent
        } else {
            0f
        }

        if (hud.isNotEmpty()) {
            contentWidth = maxOf(contentWidth, dp(150f))
        }

        val actionCount = actions.size
        var buttonsHeight = 0f
        buttonWidth = 0f
        if (actionCount > 0) {
            val availableContent = maxContentWidth.toFloat()
            buttonWidth = ((availableContent - (actionCount - 1) * buttonGap) / actionCount)
                .coerceIn(dp(48f), dp(120f))
            contentWidth = maxOf(contentWidth, actionCount * buttonWidth + (actionCount - 1) * buttonGap)
            buttonsHeight = buttonHeight + dp(8f)
        }

        if (msg == null && lbl == null && actionCount == 0 && hud.isEmpty()) {
            setMeasuredDimension(0, 0)
            return
        }

        layout = textLayout
        val width = (contentWidth + 2 * padding).toInt()
            .coerceAtMost(maxBubbleWidth.toInt())
            .coerceAtLeast(dp(72f).toInt())
        val gap = if (textLayout != null && lbl != null) dp(6f).toInt() else 0
        val hudHeight = if (hud.isNotEmpty()) (hud.size * hudRowHeight + dp(6f)).toInt() else 0
        val contentHeight = (textLayout?.height ?: 0) + labelHeight.toInt() + gap + hudHeight + buttonsHeight.toInt()
        val height = contentHeight + (2 * padding).toInt() + tailHeight.toInt()
        setMeasuredDimension(width, height)
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val bodyBottom = height - tailHeight
        val body = RectF(0f, 0f, w, bodyBottom)

        val bgStart: Int
        val bgEnd: Int
        val borderColor: Int
        if (accent != null) {
            bgStart = ColorUtils.blendARGB(Color.WHITE, accent!!, 0.16f)
            bgEnd = ColorUtils.blendARGB(Color.WHITE, accent!!, 0.32f)
            borderColor = ColorUtils.setAlphaComponent(accent!!, 0x66)
        } else {
            bgStart = 0xFFEFF6FF.toInt()
            bgEnd = 0xFFEDE9FE.toInt()
            borderColor = 0xB3FFFFFF.toInt()
        }
        borderPaint.color = borderColor

        fillPaint.shader = LinearGradient(0f, 0f, w, bodyBottom, bgStart, bgEnd, Shader.TileMode.CLAMP)
        canvas.drawRoundRect(body, cornerRadius, cornerRadius, fillPaint)
        canvas.drawRoundRect(body, cornerRadius, cornerRadius, borderPaint)

        val centerX = w / 2f
        tailPath.reset()
        tailPath.moveTo(centerX - dp(7f), bodyBottom - dp(1f))
        tailPath.lineTo(centerX, height.toFloat())
        tailPath.lineTo(centerX + dp(7f), bodyBottom - dp(1f))
        tailPath.close()
        canvas.drawPath(tailPath, fillPaint)

        var y = padding
        val lbl = label
        if (lbl != null) {
            labelPaint.color = if (accent != null) {
                ColorUtils.blendARGB(accent!!, Color.BLACK, 0.15f)
            } else {
                0xFF2563EB.toInt()
            }
            canvas.drawText(lbl, padding, y - labelPaint.fontMetrics.ascent, labelPaint)
            y += labelHeight + dp(6f)
            dividerPaint.color = if (accent != null) {
                ColorUtils.setAlphaComponent(accent!!, 0x33)
            } else {
                0x1F1E3A8A
            }
            canvas.drawLine(padding, y - dp(3f), w - padding, y - dp(3f), dividerPaint)
        }

        val textLayout = layout
        if (textLayout != null) {
            canvas.save()
            canvas.translate(padding, y)
            textLayout.draw(canvas)
            canvas.restore()
            y += textLayout.height
        }

        if (hud.isNotEmpty()) {
            y += dp(6f)
            val contentRight = w - padding
            val barLeft = padding
            val barRight = contentRight
            for (item in hud) {
                smallBoldPaint.color = 0xFF334155.toInt()
                canvas.drawText(item.label, barLeft, y + smallBoldPaint.textSize, smallBoldPaint)
                val valueText = formatValue(item)
                smallPaint.color = 0xFF475569.toInt()
                val vw = smallPaint.measureText(valueText)
                canvas.drawText(valueText, barRight - vw, y + smallPaint.textSize, smallPaint)
                val barTop = y + hudRowHeight - dp(6f)
                val barRect = RectF(barLeft, barTop, barRight, barTop + dp(4f))
                barBgPaint.color = 0x26475569
                canvas.drawRoundRect(barRect, dp(2f), dp(2f), barBgPaint)
                val fraction = if (item.max > 0f) (item.value / item.max).coerceIn(0f, 1f) else 0f
                if (fraction > 0f) {
                    val fillRect = RectF(barLeft, barTop, barLeft + (barRight - barLeft) * fraction, barTop + dp(4f))
                    barFillPaint.color = toneColor(item.tone)
                    canvas.drawRoundRect(fillRect, dp(2f), dp(2f), barFillPaint)
                }
                y += hudRowHeight
            }
        }

        if (actions.isNotEmpty()) {
            buttonRects.clear()
            val rowTop = y + dp(6f)
            var x = padding
            for (action in actions) {
                val rect = RectF(x, rowTop, x + buttonWidth, rowTop + buttonHeight)
                buttonPaint.color = when (action.style) {
                    "primary" -> 0xFF2563EB.toInt()
                    "danger" -> 0xFFEF4444.toInt()
                    else -> 0x1A1E3A8A
                }
                buttonTextPaint.color = when (action.style) {
                    "primary", "danger" -> Color.WHITE
                    else -> 0xFF1E293B.toInt()
                }
                canvas.drawRoundRect(rect, dp(8f), dp(8f), buttonPaint)
                val baseline = rect.centerY() - (buttonTextPaint.descent() + buttonTextPaint.ascent()) / 2f
                canvas.drawText(action.label, rect.centerX(), baseline, buttonTextPaint)
                buttonRects.add(rect to action)
                x += buttonWidth + buttonGap
            }
        }
    }

    private fun formatValue(item: HudItem): String {
        val v = if (item.value == item.value.toLong().toFloat()) item.value.toLong().toString() else String.format("%.1f", item.value)
        return "$v/${item.max.toLong()}"
    }

    private fun toneColor(tone: String?): Int = when (tone) {
        "amber" -> 0xFFD97706.toInt()
        "blue" -> 0xFF2563EB.toInt()
        "green" -> 0xFF16A34A.toInt()
        "pink" -> 0xFFDB2777.toInt()
        "red" -> 0xFFDC2626.toInt()
        else -> 0xFF475569.toInt()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val hasActions = actions.isNotEmpty()
        if (!hasActions && !pinned) return super.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> return true
            MotionEvent.ACTION_UP -> {
                for ((rect, action) in buttonRects) {
                    if (rect.contains(event.x, event.y)) {
                        onAction?.invoke(action)
                        return true
                    }
                }
                if (pinned && !isOverAnyButton(event.x, event.y)) dismiss()
            }
            MotionEvent.ACTION_CANCEL -> return true
        }
        return super.onTouchEvent(event)
    }

    private fun isOverAnyButton(x: Float, y: Float): Boolean = buttonRects.any { it.first.contains(x, y) }

    /** Very small markdown subset: **bold**, *italic* / _italic_, `code`. */
    private fun renderMarkdown(text: String): CharSequence {
        val patterns = listOf(
            Regex("\\*\\*(.+?)\\*\\*") to 0,
            Regex("(?<![\\w*])\\*(?!\\s)(.+?)(?<!\\s)\\*(?![\\w*])") to 1,
            Regex("`([^`]+)`") to 2,
        )
        val out = SpannableStringBuilder()
        var cursor = 0
        while (cursor < text.length) {
            var bestStart = -1
            var bestEnd = -1
            var bestGroup = ""
            var bestKind = -1
            for ((regex, kind) in patterns) {
                val m = regex.find(text, cursor) ?: continue
                if (bestStart == -1 || m.range.first < bestStart) {
                    bestStart = m.range.first
                    bestEnd = m.range.last + 1
                    bestGroup = m.groupValues[1]
                    bestKind = kind
                }
            }
            if (bestStart == -1) {
                out.append(text.substring(cursor))
                break
            }
            if (bestStart > cursor) out.append(text.substring(cursor, bestStart))
            val spanStart = out.length
            out.append(bestGroup)
            val spanEnd = out.length
            when (bestKind) {
                0 -> out.setSpan(StyleSpan(Typeface.BOLD), spanStart, spanEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                1 -> out.setSpan(StyleSpan(Typeface.ITALIC), spanStart, spanEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                2 -> {
                    out.setSpan(TypefaceSpan("monospace"), spanStart, spanEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    out.setSpan(BackgroundColorSpan(0x141E3A8A), spanStart, spanEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
            }
            cursor = bestEnd
        }
        return out
    }

    private fun dp(value: Float): Float = value * density
}
