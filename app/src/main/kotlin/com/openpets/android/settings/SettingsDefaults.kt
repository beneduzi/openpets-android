package com.openpets.android.settings

import com.openpets.android.protocol.DEFAULT_WAITING_ANIMATION_MS

object SettingsDefaults {
    const val IPC_PORT = 18420
    const val REMOTE_PORT = 18421
    const val WS_PORT = 18423
    const val BIND_ADDRESS = "0.0.0.0"
    const val PET = "tux"
    const val WALKABOUT_MODE = "roam"
    const val WALKABOUT_SPEED = 1.0f
    const val VOCAB_LANG = "en"
    const val ANKI_HOST = ""
    const val WAITING_DURATION_MS = DEFAULT_WAITING_ANIMATION_MS
    const val IPC_ENABLED = true
    const val REMOTE_ENABLED = true
    const val WS_ENABLED = false
    const val HOST_STATUS_ENABLED = false
    const val REQUIRE_LEASE = false
    const val SOUND_ENABLED = true
    const val HAPTICS_ENABLED = true
    const val TTS_ENABLED = true
    const val NOTIFY_ENABLED = true
    const val AUTO_START_ON_BOOT = true
}

enum class ResetScope { PET, APPEARANCE, CONNECTIONS, COMPANION, STARTUP, ALL }

fun preferenceKeys(scope: ResetScope): Set<String> {
    val pet = setOf("selectedPetId", "reactionOverrides", "waitingDurationMs", "walkaboutMode", "walkaboutSpeed", "hostStatusEnabled")
    val appearance = setOf("soundEnabled", "hapticsEnabled", "ttsEnabled", "notifyEnabled", "customSoundUri")
    // AppSettings ("openpets") keys only. Water/Routine/Calendar resets go through their
    // controllers so alarms reconcile; their store files are not touched here.
    val connections = setOf("ipcEnabled", "remoteEnabled", "wsEnabled", "bindAddress", "ipcPort", "remotePort", "wsPort", "requireLease")
    val companion = setOf("launchGreeting", "vocabTargetLang", "ankiHost")
    val startup = setOf("autoStartOnBoot")
    return when (scope) {
        ResetScope.PET -> pet
        ResetScope.APPEARANCE -> appearance
        ResetScope.CONNECTIONS -> connections
        ResetScope.COMPANION -> companion
        ResetScope.STARTUP -> startup
        ResetScope.ALL -> pet + appearance + connections + companion + startup
    }
}
