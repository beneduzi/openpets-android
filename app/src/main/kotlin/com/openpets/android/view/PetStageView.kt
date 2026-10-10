package com.openpets.android.view

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.AttributeSet
import android.util.Base64
import android.view.Gravity
import android.view.MotionEvent
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import com.openpets.android.pet.MediaLoader
import com.openpets.android.pet.PetPackage
import com.openpets.android.protocol.BUSY_REACTIONS
import com.openpets.android.protocol.BubbleInput
import com.openpets.android.protocol.DEFAULT_WAITING_ANIMATION_MS
import com.openpets.android.protocol.MediaRequest
import com.openpets.android.protocol.PetAction
import com.openpets.android.protocol.PetReactionStateMachine
import com.openpets.android.protocol.ProjectColorStore
import com.openpets.android.protocol.SPRITE_FRAME_HEIGHT
import com.openpets.android.protocol.SPRITE_FRAME_WIDTH
import com.openpets.android.protocol.SayRequest
import com.openpets.android.protocol.SpriteState
import com.openpets.android.protocol.TRANSIENT_DISPLAY_MS
import com.openpets.android.protocol.configuredSpriteState
import com.openpets.android.protocol.resolveReactionSpriteState
import com.openpets.android.protocol.sanitizeSource
import com.openpets.android.protocol.spriteStateFor
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.abs
import kotlin.random.Random

/**
 * The pet "stage": composes a [PetView] (bottom-center) and a [SpeechBubbleView]
 * (above the pet), owns the reaction state machine, and makes the pet roam
 * horizontally (using running-left/right sprites) when idle.
 */
class PetStageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : ViewGroup(context, attrs) {

    private val petView = PetView(context)
    private val bubbleView = SpeechBubbleView(context)
    private val mediaView = ImageView(context)
    private val formView = LinearLayout(context)
    private val formFields = ArrayList<Pair<BubbleInput, EditText>>()
    private var formSubmitUrl: String? = null
    private val machine = PetReactionStateMachine()
    private val colorStore = ProjectColorStore()
    private var lastAccent: Int? = null

    private val handler = Handler(Looper.getMainLooper())
    private var scheduledRoam: Runnable? = null
    private var activeWalk: Runnable? = null
    private var busyReset: Runnable? = null
    private var mediaHide: Runnable? = null
    private var mediaActive = false
    private var overrides: Map<String, String> = emptyMap()
    private var waitingDurationMs: Int = DEFAULT_WAITING_ANIMATION_MS

    /** Tap on the empty area (wave); long-press (settings). */
    var onTap: (() -> Unit)? = null
    var onLongPress: (() -> Unit)? = null

    private var touchDownX = 0f
    private var draggingPet = false
    private var longPressFired = false
    private val longPressRunnable = Runnable {
        longPressFired = true
        onLongPress?.invoke()
    }

    /** Whether the pet roams by itself when idle. */
    var roamEnabled: Boolean = true

    /** Fraction of stage width the pet occupies. */
    var petScale: Float = 0.7f
        set(value) {
            val clamped = value.coerceIn(0.2f, 1.0f)
            if (field != clamped) {
                field = clamped
                requestLayout()
            }
        }

    val currentReaction: String get() = machine.currentReaction

    init {
        addView(mediaView)
        mediaView.visibility = GONE
        mediaView.scaleType = ImageView.ScaleType.FIT_CENTER
        mediaView.adjustViewBounds = true
        formView.orientation = LinearLayout.VERTICAL
        formView.gravity = Gravity.CENTER
        formView.visibility = GONE
        formView.setBackgroundColor(0xF2FFFFFF.toInt())
        formView.setPadding(dp(28f), dp(28f), dp(28f), dp(28f))
        addView(formView)
        addView(petView)
        addView(bubbleView)
        bubbleView.visibility = GONE
        petView.onAnimationComplete = {
            machine.onAnimationComplete()
            applyReactionState()
        }
    }

    fun bind(petPackage: PetPackage) {
        petView.bind(petPackage)
    }

    /** Applies the configured reaction→animation overrides and `waiting` duration. */
    fun setAnimationConfig(overrides: Map<String, String>, waitingDurationMs: Int) {
        this.overrides = overrides
        this.waitingDurationMs = waitingDurationMs
        applyReactionState()
    }

    private fun currentSprite(): SpriteState =
        configuredSpriteState(resolveReactionSpriteState(machine.currentReaction, overrides), waitingDurationMs)

    /** Apply an agent reaction, optionally tagged with its project [source]. */
    fun react(reaction: String, source: String? = null) {
        machine.onReaction(reaction)
        colorStore.accentFor(source)?.let { lastAccent = it }
        applyReactionState()
    }

    /** Show a speech bubble, optionally applying a reaction first. */
    fun say(request: SayRequest) {
        val reaction = request.reaction
        if (reaction != null) react(reaction, request.source)
        val accent = colorStore.accentFor(request.source) ?: lastAccent
        showForm(request)
        bubbleView.show(
            text = request.message,
            accent = accent,
            label = sanitizeSource(request.source),
            durationMs = if (request.pinned) 0L else TRANSIENT_DISPLAY_MS.toLong(),
            actions = request.actions,
            hud = request.hud,
            bubbleId = request.bubbleId,
            pinned = request.pinned,
            onAction = { action -> handleAction(action) },
        )
    }

    fun dismissBubble(id: String?) {
        val current = bubbleView.bubbleId
        if (id == null || current == null || current == id) {
            bubbleView.dismiss()
            hideForm()
        }
    }

    private fun handleAction(action: PetAction) {
        action.callbackUrl?.let { url -> postJson(url, mapOf("actionId" to action.id)) }
        action.url?.let { openUrl(it) }
    }

    private fun showForm(request: SayRequest) {
        formFields.clear()
        formView.removeAllViews()
        formSubmitUrl = request.submitUrl
        if (request.inputs.isEmpty() || request.submitUrl == null) {
            hideForm()
            return
        }
        for (input in request.inputs) {
            val field = EditText(context).apply {
                hint = input.placeholder ?: input.label
                if (input.type == "number") {
                    inputType = android.text.InputType.TYPE_CLASS_NUMBER
                }
            }
            formFields.add(input to field)
            formView.addView(
                field,
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(60f)).apply {
                    bottomMargin = dp(10f)
                },
            )
        }
        val submit = android.widget.Button(context).apply {
            text = "Enviar"
            isAllCaps = false
            setOnClickListener { submitForm() }
        }
        formView.addView(
            submit,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(52f)),
        )
        formView.visibility = VISIBLE
        mediaActive = true
        cancelRoam()
        movePetTo(maxHorizontalOffset(), 300L)
        requestLayout()
    }

    private fun hideForm() {
        formView.visibility = GONE
        formView.removeAllViews()
        formFields.clear()
        formSubmitUrl = null
        if (mediaView.visibility != VISIBLE) {
            mediaActive = false
            if (roamEnabled && machine.currentReaction == "idle") scheduleRoam(nextRoamDelay())
        }
        requestLayout()
    }

    private fun submitForm() {
        val values = LinkedHashMap<String, String>()
        formFields.forEach { (input, field) -> values[input.id] = field.text.toString() }
        formSubmitUrl?.let { url -> postJson(url, values) }
        hideForm()
        bubbleView.dismiss()
    }

    private fun postJson(url: String, values: Map<String, String>) {
        val body = buildString {
            append("{")
            var first = true
            values.forEach { (key, value) ->
                if (!first) append(",")
                first = false
                append("\"").append(key.replace("\"", "")).append("\":\"")
                    .append(value.replace("\\", "\\\\").replace("\"", "'")).append("\"")
            }
            append("}")
        }
        Thread {
            try {
                val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 8000
                    readTimeout = 8000
                    doOutput = true
                    setRequestProperty("content-type", "application/json")
                }
                connection.outputStream.use { out: OutputStream -> out.write(body.toByteArray(Charsets.UTF_8)) }
                connection.responseCode
                connection.disconnect()
            } catch (_: Exception) {
            }
        }.apply { isDaemon = true }.start()
    }

    /** Download and display an image/GIF (pet.showMedia) above the pet. */
    fun showMedia(request: MediaRequest) {
        val reaction = request.reaction
        if (reaction != null) react(reaction)
        mediaActive = true
        cancelRoam()
        movePetTo(maxHorizontalOffset(), 300L)
        val accent = colorStore.accentFor(null) ?: lastAccent
        request.message?.let { caption ->
            bubbleView.show(
                text = caption,
                accent = accent,
                label = null,
                durationMs = if (request.pinned) 0L else request.durationMs.toLong(),
                actions = request.actions,
                hud = emptyList(),
                bubbleId = request.bubbleId,
                pinned = request.pinned,
                onAction = { action -> handleAction(action) },
            )
        }
        val dataBase64 = request.dataBase64
        val url = request.url
        Thread {
            try {
                val bitmap = when {
                    dataBase64 != null -> decodeBase64(dataBase64)
                    url != null -> MediaLoader.download(url)
                    else -> null
                } ?: throw IllegalStateException("no image")
                bitmap.density = resources.displayMetrics.densityDpi
                post {
                    mediaView.setImageBitmap(bitmap)
                    mediaView.visibility = VISIBLE
                    val clickUrl = request.clickUrl
                    mediaView.setOnClickListener { if (clickUrl != null) openUrl(clickUrl) }
                    requestLayout()
                    scheduleMediaHide(request.durationMs.toLong())
                }
            } catch (_: Exception) {
                post { scheduleMediaHide(0L) }
            }
        }.apply { isDaemon = true }.start()
    }

    private fun decodeBase64(data: String): Bitmap {
        val bytes = Base64.decode(data, Base64.DEFAULT)
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            ?: throw IllegalStateException("unsupported image")
    }

    private fun openUrl(url: String) {
        try {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        } catch (_: Exception) {
        }
    }

    private fun scheduleMediaHide(durationMs: Long) {
        mediaHide?.let { handler.removeCallbacks(it) }
        val runnable = Runnable {
            mediaActive = false
            mediaView.visibility = GONE
            mediaView.setImageDrawable(null)
            requestLayout()
            if (roamEnabled && machine.currentReaction == "idle") scheduleRoam(nextRoamDelay())
        }
        mediaHide = runnable
        handler.postDelayed(runnable, durationMs.coerceAtLeast(1L))
    }

    /** Trigger a walk immediately (used for testing / gestures). */
    fun roamNow() {
        cancelRoam()
        if (machine.currentReaction == "idle") startWalk()
    }

    // ---- roaming ----

    private fun applyReactionState() {
        cancelWalk()
        petView.spriteState = currentSprite()
        scheduleBusyReset()
        if (roamEnabled && !mediaActive && machine.currentReaction == "idle") {
            scheduleRoam(nextRoamDelay())
        } else {
            cancelRoam()
        }
    }

    /** Busy reactions (thinking/editing/…) revert to idle if no update arrives. */
    private fun scheduleBusyReset() {
        busyReset?.let { handler.removeCallbacks(it) }
        busyReset = null
        if (machine.currentReaction in BUSY_REACTIONS) {
            val runnable = Runnable {
                machine.onReaction("idle")
                applyReactionState()
            }
            busyReset = runnable
            handler.postDelayed(runnable, BUSY_TIMEOUT_MS)
        }
    }

    private fun nextRoamDelay(): Long =
        if (width == 0) 5_000L else Random.nextLong(6_000L, 18_000L)

    private fun scheduleRoam(delayMs: Long) {
        cancelRoam()
        if (!roamEnabled) return
        val runnable = Runnable { startWalk() }
        scheduledRoam = runnable
        handler.postDelayed(runnable, delayMs)
    }

    private fun cancelRoam() {
        scheduledRoam?.let { handler.removeCallbacks(it) }
        scheduledRoam = null
    }

    private fun cancelWalk() {
        activeWalk?.let { petView.removeCallbacks(it) }
        activeWalk?.let { bubbleView.removeCallbacks(it) }
        activeWalk = null
    }

    private fun startWalk() {
        if (!roamEnabled || machine.currentReaction != "idle") return
        val maxOffset = maxHorizontalOffset()
        if (maxOffset < dp(20f)) {
            scheduleRoam(nextRoamDelay())
            return
        }
        // Travel to the opposite side so the pet visibly goes and comes back.
        val goingRight = petView.translationX <= 0f
        val magnitude = maxOffset * (0.6f + 0.4f * Random.nextFloat())
        walkTo(if (goingRight) magnitude else -magnitude)
    }

    private fun maxHorizontalOffset(): Float =
        ((width - petView.measuredWidth) / 2f).coerceAtLeast(0f)

    private fun walkTo(targetOffset: Float) {
        val startOffset = petView.translationX
        val distance = abs(targetOffset - startOffset)
        if (distance < dp(8f)) {
            finishWalk()
            return
        }
        val goingRight = targetOffset > startOffset
        petView.spriteState = spriteStateFor(if (goingRight) "running-right" else "running-left")

        val speedPxPerMs = dpf(0.06f).coerceAtLeast(0.02f)
        val durationMs = (distance / speedPxPerMs).toLong().coerceIn(350L, 9_000L)
        val startAt = SystemClock.uptimeMillis()
        val step = object : Runnable {
            override fun run() {
                val t = ((SystemClock.uptimeMillis() - startAt).toFloat() / durationMs).coerceIn(0f, 1f)
                setPetOffset(startOffset + (targetOffset - startOffset) * t)
                if (t < 1f) {
                    petView.postOnAnimation(this)
                } else {
                    finishWalk()
                }
            }
        }
        activeWalk = step
        petView.postOnAnimation(step)
    }

    private fun finishWalk() {
        cancelWalk()
        petView.spriteState = currentSprite()
        if (roamEnabled && machine.currentReaction == "idle") scheduleRoam(nextRoamDelay())
    }

    private fun setPetOffset(offset: Float) {
        val limit = maxHorizontalOffset()
        val clamped = offset.coerceIn(-limit, limit)
        petView.translationX = clamped
        bubbleView.translationX = clamped
    }

    /** Animates the pet horizontally (no sprite/roam side effects). */
    private fun movePetTo(targetOffset: Float, durationMs: Long) {
        cancelWalk()
        val startOffset = petView.translationX
        val limit = maxHorizontalOffset()
        val target = targetOffset.coerceIn(-limit, limit)
        val startAt = SystemClock.uptimeMillis()
        val step = object : Runnable {
            override fun run() {
                val t = ((SystemClock.uptimeMillis() - startAt).toFloat() / durationMs).coerceIn(0f, 1f)
                setPetOffset(startOffset + (target - startOffset) * t)
                if (t < 1f) {
                    petView.postOnAnimation(this)
                } else {
                    activeWalk = null
                }
            }
        }
        activeWalk = step
        petView.postOnAnimation(step)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (roamEnabled) scheduleRoam(3_000L)
    }

    override fun onDetachedFromWindow() {
        cancelRoam()
        cancelWalk()
        mediaHide?.let { handler.removeCallbacks(it) }
        super.onDetachedFromWindow()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = MeasureSpec.getSize(heightMeasureSpec)
        setMeasuredDimension(
            resolveSize(width, widthMeasureSpec),
            resolveSize(height, heightMeasureSpec),
        )

        val petWidth = (minOf(width, height) * petScale).toInt()
        val petHeight = petWidth * SPRITE_FRAME_HEIGHT / SPRITE_FRAME_WIDTH
        petView.measure(
            MeasureSpec.makeMeasureSpec(petWidth, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(petHeight, MeasureSpec.EXACTLY),
        )
        bubbleView.measure(
            MeasureSpec.makeMeasureSpec(width, MeasureSpec.AT_MOST),
            MeasureSpec.makeMeasureSpec(height, MeasureSpec.AT_MOST),
        )
        if (mediaActive) {
            val leftAreaWidth = (width - petWidth).coerceAtLeast(width / 2)
            val drawable = mediaView.drawable
            val intrinsicWidth = drawable?.intrinsicWidth ?: 0
            val intrinsicHeight = drawable?.intrinsicHeight ?: 0
            if (intrinsicWidth > 0 && intrinsicHeight > 0) {
                val maxWidth = leftAreaWidth * 0.98f
                val maxHeight = height * 0.9f
                val aspect = intrinsicWidth.toFloat() / intrinsicHeight
                var targetWidth = maxWidth
                var targetHeight = targetWidth / aspect
                if (targetHeight > maxHeight) {
                    targetHeight = maxHeight
                    targetWidth = targetHeight * aspect
                }
                mediaView.measure(
                    MeasureSpec.makeMeasureSpec(targetWidth.toInt(), MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(targetHeight.toInt(), MeasureSpec.EXACTLY),
                )
            } else {
                mediaView.measure(
                    MeasureSpec.makeMeasureSpec((leftAreaWidth * 0.98f).toInt(), MeasureSpec.AT_MOST),
                    MeasureSpec.makeMeasureSpec((height * 0.9f).toInt(), MeasureSpec.AT_MOST),
                )
            }
        } else {
            mediaView.measure(
                MeasureSpec.makeMeasureSpec((width * 0.9f).toInt(), MeasureSpec.AT_MOST),
                MeasureSpec.makeMeasureSpec((height * 0.6f).toInt(), MeasureSpec.AT_MOST),
            )
        }
        if (mediaActive) {
            val leftAreaWidth = (width - petWidth).coerceAtLeast(width / 2)
            formView.measure(
                MeasureSpec.makeMeasureSpec((leftAreaWidth * 0.98f).toInt(), MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec((height * 0.9f).toInt(), MeasureSpec.EXACTLY),
            )
        } else {
            formView.measure(
                MeasureSpec.makeMeasureSpec((width * 0.86f).toInt(), MeasureSpec.AT_MOST),
                MeasureSpec.makeMeasureSpec((height * 0.6f).toInt(), MeasureSpec.AT_MOST),
            )
        }
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val width = r - l
        val height = b - t

        val petWidth = petView.measuredWidth
        val petHeight = petView.measuredHeight
        val petLeft = (width - petWidth) / 2
        val petTop = height - petHeight
        petView.layout(petLeft, petTop, petLeft + petWidth, petTop + petHeight)

        if (mediaActive && mediaView.visibility == VISIBLE && mediaView.measuredWidth > 0) {
            val leftAreaWidth = (width - petWidth).coerceAtLeast(1)
            val mw = mediaView.measuredWidth
            val mh = mediaView.measuredHeight
            val mediaLeft = ((leftAreaWidth - mw) / 2).coerceAtLeast(0)
            val mediaTop = ((height - mh) / 2).coerceAtLeast(0)
            mediaView.layout(mediaLeft, mediaTop, mediaLeft + mw, mediaTop + mh)
        }

        if (mediaActive) {
            val leftAreaWidth = (width - petWidth).coerceAtLeast(1)
            if (mediaView.visibility == VISIBLE && mediaView.measuredWidth > 0) {
                val mw = mediaView.measuredWidth
                val mh = mediaView.measuredHeight
                val mediaLeft = ((leftAreaWidth - mw) / 2).coerceAtLeast(0)
                val mediaTop = ((height - mh) / 2).coerceAtLeast(0)
                mediaView.layout(mediaLeft, mediaTop, mediaLeft + mw, mediaTop + mh)
            }
            if (formView.visibility == VISIBLE && formView.measuredWidth > 0) {
                val fw = formView.measuredWidth
                val fh = formView.measuredHeight
                val fLeft = ((leftAreaWidth - fw) / 2).coerceAtLeast(0)
                val fTop = ((height - fh) / 2).coerceAtLeast(0)
                formView.layout(fLeft, fTop, fLeft + fw, fTop + fh)
            }
        }

        if (bubbleView.visibility == VISIBLE && bubbleView.measuredWidth > 0) {
            val bw = bubbleView.measuredWidth
            val bh = bubbleView.measuredHeight
            val bLeft = ((width - bw) / 2).coerceAtLeast(0)
            val bTop = (petTop - bh - dp(2f)).coerceAtLeast(0)
            bubbleView.layout(bLeft, bTop, bLeft + bw, bTop + bh)
        }
    }

    private fun dp(value: Float): Int = (value * resources.displayMetrics.density).toInt()

    private fun dpf(value: Float): Float = value * resources.displayMetrics.density

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                touchDownX = event.x
                draggingPet = false
                longPressFired = false
                handler.postDelayed(longPressRunnable, 500L)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (!draggingPet && abs(event.x - touchDownX) > dp(6f)) {
                    draggingPet = true
                    handler.removeCallbacks(longPressRunnable)
                    cancelRoam()
                    cancelWalk()
                }
                if (draggingPet) {
                    movePetToTouch(event.x)
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                handler.removeCallbacks(longPressRunnable)
                if (draggingPet) {
                    if (roamEnabled && !mediaActive && machine.currentReaction == "idle") {
                        scheduleRoam(nextRoamDelay())
                    }
                } else if (!longPressFired) {
                    onTap?.invoke()
                }
                draggingPet = false
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                handler.removeCallbacks(longPressRunnable)
                draggingPet = false
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    /** Moves the pet horizontally so it follows the finger. */
    private fun movePetToTouch(x: Float) {
        val limit = maxHorizontalOffset()
        val baseLeft = (width - petView.measuredWidth) / 2f
        val offset = x - baseLeft - petView.measuredWidth / 2f
        setPetOffset(offset.coerceIn(-limit, limit))
    }

    private companion object {
        const val BUSY_TIMEOUT_MS = 45_000L
    }
}
