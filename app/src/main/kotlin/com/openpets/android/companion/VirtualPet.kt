package com.openpets.android.companion

data class PetStats(
    val satiety: Int,
    val happiness: Int,
    val energy: Int,
    val updatedAtMs: Long,
)

enum class PetMood { HAPPY, CONTENT, TIRED, HUNGRY, EXCITED }
