package com.openpets.android.companion

data class Deadline(val id: String, val title: String, val dueAtMs: Long, val completed: Boolean = false)
