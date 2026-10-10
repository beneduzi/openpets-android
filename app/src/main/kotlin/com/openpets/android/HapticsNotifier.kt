package com.openpets.android

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator

/** Short haptic pulse accompanying notifications. */
class HapticsNotifier(context: Context) {

    private val vibrator: Vibrator? = try {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    } catch (_: Exception) {
        null
    }

    fun vibrate() {
        val v = vibrator ?: return
        if (!v.hasVibrator()) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                v.vibrate(VibrationEffect.createOneShot(70, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                v.vibrate(70)
            }
        } catch (_: Exception) {
        }
    }
}
