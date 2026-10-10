package com.openpets.android.companion

data class MoodEntry(val value: Int, val note: String?, val atMs: Long)

enum class MoodLevel { LOW, OK, GOOD, GREAT }
