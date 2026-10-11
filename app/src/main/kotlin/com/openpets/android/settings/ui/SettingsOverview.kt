package com.openpets.android.settings.ui

import androidx.annotation.StringRes
import com.openpets.android.R

enum class SettingsDestination { PETS, ANIMATIONS, WALKABOUT, HOST_STATUS_PENDING, TIMER, REMINDERS, DEADLINES, FOCUS, CALENDAR, CHECKLIST, WATER, MOOD, ROUTINE, BREATHE, VIRTUAL_PET, FUN, VOCABULARY, VOICE, RESOURCES, COMMANDS, SOUND_OUTPUT, SERVER_SETTINGS, START_ON_BOOT, ABOUT, RESET_PREFERENCES }

sealed class Row(open val id: Long) {
    data class SectionHeader(@StringRes val titleRes: Int, override val id: Long = titleRes.toLong()) : Row(id)
    data class Navigation(@StringRes val titleRes: Int, @StringRes val summaryRes: Int?, val destination: SettingsDestination, override val id: Long = "${destination.name}:$titleRes".hashCode().toLong(), @StringRes val breadcrumbRes:Int?=null, val summaryValue:String?=null) : Row(id)
    data class Info(@StringRes val titleRes: Int, val value: String, val destination: SettingsDestination? = null, override val id: Long = "info:$titleRes".hashCode().toLong()) : Row(id)
}

fun overviewRows(selectedPetName: String, deviceIp: String?): List<Row> = buildList {
    fun section(@StringRes title: Int) { add(Row.SectionHeader(title)) }
    fun nav(id: String, @StringRes title: Int, destination: SettingsDestination, summary: String? = null) {
        add(Row.Navigation(title, null, destination, id.hashCode().toLong(),summaryValue=summary))
    }
    section(R.string.ui_reset_scopes)
    nav("current-pet",R.string.ui_current_pet,SettingsDestination.PETS,selectedPetName)
    nav("animations",R.string.ui_animations,SettingsDestination.ANIMATIONS)
    nav("walkabout",R.string.ui_walkabout,SettingsDestination.WALKABOUT)
    nav("host-status",R.string.ui_host_status,SettingsDestination.HOST_STATUS_PENDING)
    section(R.string.ui_appearance_title); nav("sound-output",R.string.ui_appearance_title,SettingsDestination.SOUND_OUTPUT)
    section(R.string.settings_overview_companion);section(R.string.settings_overview_productivity)
    nav("timer",R.string.ui_timer,SettingsDestination.TIMER);nav("focus",R.string.ui_focus,SettingsDestination.FOCUS);nav("reminders",R.string.settings_reminders_title,SettingsDestination.REMINDERS);nav("deadlines",R.string.settings_deadlines_title,SettingsDestination.DEADLINES);nav("calendar",R.string.ui_calendar,SettingsDestination.CALENDAR);nav("checklist",R.string.settings_checklist_title,SettingsDestination.CHECKLIST)
    section(R.string.settings_overview_wellbeing);nav("water",R.string.ui_water,SettingsDestination.WATER);nav("mood",R.string.ui_mood,SettingsDestination.MOOD);nav("routine",R.string.ui_routine,SettingsDestination.ROUTINE);nav("breathe",R.string.ui_breathe,SettingsDestination.BREATHE)
    section(R.string.settings_overview_play);nav("virtual-pet",R.string.ui_virtual_pet,SettingsDestination.VIRTUAL_PET);nav("fun",R.string.ui_fun,SettingsDestination.FUN);nav("vocabulary",R.string.ui_vocabulary,SettingsDestination.VOCABULARY);nav("voice",R.string.ui_voice,SettingsDestination.VOICE)
    section(R.string.ui_connections);add(Row.Info(R.string.settings_overview_device_ip,deviceIp.orEmpty(),id="device-ip".hashCode().toLong()));nav("server-settings",R.string.settings_overview_server,SettingsDestination.SERVER_SETTINGS)
    section(R.string.ui_startup);nav("start-on-boot",R.string.settings_autostart,SettingsDestination.START_ON_BOOT);nav("resources",R.string.ui_resources,SettingsDestination.RESOURCES);nav("commands",R.string.ui_plugin_commands,SettingsDestination.COMMANDS);nav("reset-preferences",R.string.ui_reset_preferences,SettingsDestination.RESET_PREFERENCES)
    section(R.string.settings_overview_about);nav("about",R.string.settings_about_title,SettingsDestination.ABOUT)
}
