package com.openpets.android.settings.ui

import androidx.annotation.StringRes
import com.openpets.android.R

data class SettingsSearchEntry(@StringRes val titleRes:Int,@StringRes val summaryRes:Int,@StringRes val breadcrumbRes:Int,val destination:SettingsDestination,val targetKey:String?=null,val aliases:Set<String> = emptySet())

/** Explicit allow-list; only static labels and aliases are indexed. */
fun settingsSearchIndex():List<SettingsSearchEntry> = listOf(
    SettingsSearchEntry(R.string.ui_current_pet,R.string.settings_search_pet_summary,R.string.settings_search_breadcrumb_pet,SettingsDestination.PETS,aliases=setOf("animal","catalog")),
    SettingsSearchEntry(R.string.settings_search_animations,R.string.settings_search_animations_summary,R.string.settings_search_breadcrumb_pet,SettingsDestination.ANIMATIONS,aliases=setOf("animation","waiting duration")),
    SettingsSearchEntry(R.string.settings_search_walkabout_mode,R.string.settings_search_walkabout_summary,R.string.settings_search_breadcrumb_pet,SettingsDestination.WALKABOUT,"walkaboutMode",setOf("roam","patrol","follow")),
    SettingsSearchEntry(R.string.settings_search_walkabout_speed,R.string.settings_search_walkabout_summary,R.string.settings_search_breadcrumb_pet,SettingsDestination.WALKABOUT,"walkaboutSpeed"),
    SettingsSearchEntry(R.string.settings_search_host_status,R.string.settings_search_host_summary,R.string.settings_search_breadcrumb_pet,SettingsDestination.HOST_STATUS_PENDING,"hostStatusEnabled"),
    SettingsSearchEntry(R.string.ui_notification_sound,R.string.settings_search_appearance_summary,R.string.settings_search_breadcrumb_appearance,SettingsDestination.SOUND_OUTPUT,aliases=setOf("vibration","haptics","tts","speech")),
    SettingsSearchEntry(R.string.settings_search_sound_file,R.string.settings_search_sound_file_summary,R.string.settings_search_breadcrumb_appearance,SettingsDestination.SOUND_OUTPUT,"soundFile"),
    SettingsSearchEntry(R.string.settings_search_haptics,R.string.settings_search_appearance_summary,R.string.settings_search_breadcrumb_appearance,SettingsDestination.SOUND_OUTPUT,"hapticsEnabled"),
    SettingsSearchEntry(R.string.settings_search_tts,R.string.settings_search_appearance_summary,R.string.settings_search_breadcrumb_appearance,SettingsDestination.SOUND_OUTPUT,"ttsEnabled"),
    SettingsSearchEntry(R.string.settings_search_notifications,R.string.settings_search_appearance_summary,R.string.settings_search_breadcrumb_appearance,SettingsDestination.SOUND_OUTPUT,"notifyEnabled"),
    SettingsSearchEntry(R.string.ui_timer,R.string.settings_search_timer_summary,R.string.settings_search_breadcrumb_productivity,SettingsDestination.TIMER,aliases=setOf("countdown")),
    SettingsSearchEntry(R.string.ui_focus,R.string.settings_search_focus_summary,R.string.settings_search_breadcrumb_productivity,SettingsDestination.FOCUS,aliases=setOf("pomodoro")),
    SettingsSearchEntry(R.string.settings_reminders_title,R.string.settings_search_reminders_summary,R.string.settings_search_breadcrumb_productivity,SettingsDestination.REMINDERS),
    SettingsSearchEntry(R.string.settings_deadlines_title,R.string.settings_search_deadlines_summary,R.string.settings_search_breadcrumb_productivity,SettingsDestination.DEADLINES),
    SettingsSearchEntry(R.string.ui_calendar,R.string.settings_search_calendar_summary,R.string.settings_search_breadcrumb_productivity,SettingsDestination.CALENDAR,aliases=setOf("events")),
    SettingsSearchEntry(R.string.settings_checklist_title,R.string.settings_search_checklist_summary,R.string.settings_search_breadcrumb_productivity,SettingsDestination.CHECKLIST,aliases=setOf("checklist")),
    SettingsSearchEntry(R.string.ui_water,R.string.settings_search_water_summary,R.string.settings_search_breadcrumb_wellbeing,SettingsDestination.WATER),
    SettingsSearchEntry(R.string.ui_mood,R.string.settings_search_mood_summary,R.string.settings_search_breadcrumb_wellbeing,SettingsDestination.MOOD),
    SettingsSearchEntry(R.string.ui_routine,R.string.settings_search_routine_summary,R.string.settings_search_breadcrumb_wellbeing,SettingsDestination.ROUTINE),
    SettingsSearchEntry(R.string.ui_breathe,R.string.settings_search_breathe_summary,R.string.settings_search_breadcrumb_wellbeing,SettingsDestination.BREATHE),
    SettingsSearchEntry(R.string.ui_virtual_pet,R.string.settings_search_virtual_pet_summary,R.string.settings_search_breadcrumb_play,SettingsDestination.VIRTUAL_PET),
    SettingsSearchEntry(R.string.ui_fun,R.string.settings_search_fun_summary,R.string.settings_search_breadcrumb_play,SettingsDestination.FUN),
    SettingsSearchEntry(R.string.ui_vocabulary,R.string.settings_search_vocabulary_summary,R.string.settings_search_breadcrumb_play,SettingsDestination.VOCABULARY,aliases=setOf("anki","dictionary")),
    SettingsSearchEntry(R.string.ui_voice,R.string.settings_search_voice_summary,R.string.settings_search_breadcrumb_play,SettingsDestination.VOICE,aliases=setOf("microphone","speech to text")),
    SettingsSearchEntry(R.string.settings_connections_bind,R.string.settings_search_bind_summary,R.string.settings_search_breadcrumb_servers,SettingsDestination.SERVER_SETTINGS, "bind",setOf("all interfaces")),
    SettingsSearchEntry(R.string.settings_ipc_port,R.string.settings_search_ipc_port_summary,R.string.settings_search_breadcrumb_servers,SettingsDestination.SERVER_SETTINGS,"ipcPort",setOf("ipc","local server")),
    SettingsSearchEntry(R.string.settings_search_ipc_enabled,R.string.settings_search_ipc_enabled_summary,R.string.settings_search_breadcrumb_servers,SettingsDestination.SERVER_SETTINGS,"ipcEnabled",setOf("ipc")),
    SettingsSearchEntry(R.string.settings_remote_port,R.string.settings_search_remote_port_summary,R.string.settings_search_breadcrumb_servers,SettingsDestination.SERVER_SETTINGS,"remotePort",setOf("remote")),
    SettingsSearchEntry(R.string.settings_search_remote_enabled,R.string.settings_search_remote_enabled_summary,R.string.settings_search_breadcrumb_servers,SettingsDestination.SERVER_SETTINGS,"remoteEnabled"),
    SettingsSearchEntry(R.string.settings_ws_port,R.string.settings_search_ws_port_summary,R.string.settings_search_breadcrumb_servers,SettingsDestination.SERVER_SETTINGS,"wsPort",setOf("websocket","ws")),
    SettingsSearchEntry(R.string.settings_search_ws_enabled,R.string.settings_search_ws_enabled_summary,R.string.settings_search_breadcrumb_servers,SettingsDestination.SERVER_SETTINGS,"wsEnabled",setOf("websocket","ws")),
    SettingsSearchEntry(R.string.settings_search_pairing,R.string.settings_search_pairing_summary,R.string.settings_search_breadcrumb_security,SettingsDestination.SERVER_SETTINGS,"requireLease",setOf("lease")),
    SettingsSearchEntry(R.string.settings_connections_ipc_token,R.string.settings_search_token_summary,R.string.settings_search_breadcrumb_security,SettingsDestination.SERVER_SETTINGS,"editIpc",setOf("ipc secret")),
    SettingsSearchEntry(R.string.settings_connections_remote_token,R.string.settings_search_token_summary,R.string.settings_search_breadcrumb_security,SettingsDestination.SERVER_SETTINGS,"editRemote",setOf("remote secret")),
    SettingsSearchEntry(R.string.settings_autostart,R.string.settings_search_startup_summary,R.string.settings_search_breadcrumb_advanced,SettingsDestination.START_ON_BOOT,aliases=setOf("startup")),
    SettingsSearchEntry(R.string.ui_resources,R.string.settings_search_resources_summary,R.string.settings_search_breadcrumb_advanced,SettingsDestination.RESOURCES),
    SettingsSearchEntry(R.string.ui_plugin_commands,R.string.settings_search_commands_summary,R.string.settings_search_breadcrumb_advanced,SettingsDestination.COMMANDS),
    SettingsSearchEntry(R.string.ui_reset_preferences,R.string.settings_reset_all_detail,R.string.settings_search_breadcrumb_advanced,SettingsDestination.RESET_PREFERENCES),
    SettingsSearchEntry(R.string.settings_about_title,R.string.settings_search_about_summary,R.string.settings_overview_about,SettingsDestination.ABOUT),
)

fun searchSettings(query:String,index:List<SettingsSearchEntry> = settingsSearchIndex(),resolve:(Int)->String):List<SettingsSearchEntry>{
    val terms=query.trim().lowercase().split(Regex("\\s+")).filter(String::isNotBlank)
    if(terms.isEmpty())return emptyList()
    return index.filter{entry->val haystack=listOf(resolve(entry.titleRes),resolve(entry.summaryRes),resolve(entry.breadcrumbRes),*entry.aliases.toTypedArray()).joinToString(" ").lowercase();terms.all(haystack::contains)}
}
