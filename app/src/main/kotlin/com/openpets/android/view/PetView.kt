package com.openpets.android.view

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.os.SystemClock
import android.util.AttributeSet
import android.view.View
import com.openpets.android.pet.PetPackage
import com.openpets.android.protocol.SpriteState
import com.openpets.android.protocol.spriteStateFor

/**
 * Draws the animated pet from a spritesheet (192x208 frames, 8x9 grid).
 *
 * Animation is time-based and self-invalidating. Non-looping states stop on the
 * last frame and fire [onAnimationComplete] exactly once.
 */
class PetView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val srcRect = Rect()
    private val dstRect = RectF()

    private var spritesheet: Bitmap? = null
    private var frameWidth = 0
    private var frameHeight = 0
    private var columns = 1

    /** Called once when a non-looping animation finishes. */
    var onAnimationComplete: (() -> Unit)? = null

    var spriteState: SpriteState = spriteStateFor("idle")
        set(value) {
            if (field.id != value.id) {
                field = value
                animationStartMs = SystemClock.uptimeMillis()
                animationDone = false
                invalidate()
            }
        }

    private var animationStartMs = 0L
    private var animationDone = false

    fun bind(petPackage: PetPackage) {
        spritesheet = petPackage.spritesheet
        frameWidth = petPackage.frameWidth
        frameHeight = petPackage.frameHeight
        columns = petPackage.columns
        animationStartMs = SystemClock.uptimeMillis()
        animationDone = false
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val bitmap = spritesheet ?: return
        if (frameWidth <= 0 || frameHeight <= 0 || width == 0 || height == 0) return

        val state = spriteState
        val perFrameMs = state.durationMs.toDouble() / state.frames
        val elapsed = SystemClock.uptimeMillis() - animationStartMs
        val iterations = state.iterations

        val frameIndex: Int
        var finished = false
        if (iterations == null) {
            frameIndex = ((elapsed / perFrameMs).toInt()).floorMod(state.frames)
        } else {
            val totalMs = (perFrameMs * state.frames * iterations).toLong()
            if (elapsed >= totalMs) {
                frameIndex = state.frames - 1
                finished = true
            } else {
                frameIndex = ((elapsed / perFrameMs).toInt()).floorMod(state.frames)
            }
        }

        val row = state.row.coerceIn(0, rows() - 1)
        val col = frameIndex.coerceIn(0, columns - 1)
        srcRect.set(
            col * frameWidth,
            row * frameHeight,
            (col + 1) * frameWidth,
            (row + 1) * frameHeight,
        )

        val scale = minOf(width.toFloat() / frameWidth, height.toFloat() / frameHeight)
        val drawWidth = frameWidth * scale
        val drawHeight = frameHeight * scale
        val left = (width - drawWidth) / 2f
        val top = height - drawHeight
        dstRect.set(left, top, left + drawWidth, top + drawHeight)
        canvas.drawBitmap(bitmap, srcRect, dstRect, paint)

        if (!finished) {
            postInvalidateOnAnimation()
        } else if (!animationDone) {
            animationDone = true
            val callback = onAnimationComplete
            if (callback != null) post { callback() }
        }
    }

    private fun rows(): Int = (spritesheet?.height ?: frameHeight) / frameHeight
}

private fun Int.floorMod(modulo: Int): Int = ((this % modulo) + modulo) % modulo
