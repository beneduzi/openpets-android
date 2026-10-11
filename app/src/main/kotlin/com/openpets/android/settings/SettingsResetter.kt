package com.openpets.android.settings

import com.openpets.android.companion.CalendarController
import com.openpets.android.companion.RoutineController
import com.openpets.android.companion.WaterController

/** Explicit preference-value map; keys are intersected with the scope allow-list before writing. */
fun preferenceResetValues(scope:ResetScope):Map<String,Any?> = linkedMapOf(
    "selectedPetId" to SettingsDefaults.PET,
    "reactionOverrides" to "{}",
    "waitingDurationMs" to SettingsDefaults.WAITING_DURATION_MS,
    "walkaboutMode" to SettingsDefaults.WALKABOUT_MODE,
    "walkaboutSpeed" to SettingsDefaults.WALKABOUT_SPEED,
    "hostStatusEnabled" to SettingsDefaults.HOST_STATUS_ENABLED,
    "soundEnabled" to SettingsDefaults.SOUND_ENABLED,
    "hapticsEnabled" to SettingsDefaults.HAPTICS_ENABLED,
    "ttsEnabled" to SettingsDefaults.TTS_ENABLED,
    "notifyEnabled" to SettingsDefaults.NOTIFY_ENABLED,
    "customSoundUri" to null,
    "ipcEnabled" to SettingsDefaults.IPC_ENABLED,
    "remoteEnabled" to SettingsDefaults.REMOTE_ENABLED,
    "wsEnabled" to SettingsDefaults.WS_ENABLED,
    "bindAddress" to SettingsDefaults.BIND_ADDRESS,
    "ipcPort" to SettingsDefaults.IPC_PORT,
    "remotePort" to SettingsDefaults.REMOTE_PORT,
    "wsPort" to SettingsDefaults.WS_PORT,
    "requireLease" to SettingsDefaults.REQUIRE_LEASE,
    "launchGreeting" to "",
    "vocabTargetLang" to SettingsDefaults.VOCAB_LANG,
    "ankiHost" to SettingsDefaults.ANKI_HOST,
    "autoStartOnBoot" to SettingsDefaults.AUTO_START_ON_BOOT,
).filterKeys(preferenceKeys(scope)::contains)

/** Pure helper used to prove reset scopes cannot write credentials or unrelated preferences. */
fun applyPreferenceReset(target:MutableMap<String,Any?>,scope:ResetScope){
    val values=preferenceResetValues(scope)
    require(values.keys==preferenceKeys(scope))
    values.forEach{(key,value)->if(value==null)target.remove(key)else target[key]=value}
}

class SettingsResetter(
    private val settings:AppSettings,
    private val water:WaterController?=null,
    private val routine:RoutineController?=null,
    private val calendar:CalendarController?=null,
){
    fun apply(scope:ResetScope){
        settings.applyResetValues(preferenceResetValues(scope))
        if(scope==ResetScope.COMPANION||scope==ResetScope.ALL){
            water?.update(false,60,9,21)
            routine?.update(true,9,0,true,20,0)
            calendar?.setEnabled(true)
        }
    }
}
