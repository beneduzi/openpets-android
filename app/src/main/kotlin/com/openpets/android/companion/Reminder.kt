package com.openpets.android.companion

data class Reminder(
    val id: String,
    val title: String,
    val dueAtMs: Long,
    val completed: Boolean = false,
    val notified: Boolean = false,
)
