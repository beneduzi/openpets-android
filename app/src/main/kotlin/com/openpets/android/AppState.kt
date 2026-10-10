package com.openpets.android

/** Tracks whether the companion UI is currently in the foreground. */
object AppState {
    @Volatile
    var foreground: Boolean = false
}
