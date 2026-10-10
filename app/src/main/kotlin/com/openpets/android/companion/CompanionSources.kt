package com.openpets.android.companion

/** Sources that already issue dedicated companion notifications. */
object CompanionSources {
    val ALL: Set<String> = setOf(
        "timer",
        "focus",
        "reminder",
        "water",
        "routine",
        "calendar",
        "mood",
        "virtualpet",
        "resources",
        "breathing",
    )
}
