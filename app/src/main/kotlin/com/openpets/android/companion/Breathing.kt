package com.openpets.android.companion

enum class BreathPhase { INHALE, HOLD_IN, EXHALE, HOLD_OUT, DONE }

data class BreathingPattern(
    val id: String,
    val label: String,
    val inhaleSec: Int,
    val holdInSec: Int,
    val exhaleSec: Int,
    val holdOutSec: Int,
) {
    companion object {
        val BOX = BreathingPattern("box", "Box breathing", 4, 4, 4, 4)
        val RELAX_478 = BreathingPattern("478", "4-7-8", 4, 7, 8, 0)
        val all = listOf(BOX, RELAX_478)
        fun byId(id: String): BreathingPattern = all.firstOrNull { it.id == id } ?: BOX
    }
}

data class BreathSnapshot(
    val active: Boolean,
    val patternId: String?,
    val phase: BreathPhase,
    val phaseRemainingSec: Int,
    val cyclesCompleted: Int,
    val sessionRemainingMs: Long,
)
