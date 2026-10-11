package com.openpets.android.settings.ui

enum class SettingsDestination {
    PETS, ANIMATIONS, WALKABOUT, HOST_STATUS_PENDING, TIMER, REMINDERS, DEADLINES, FOCUS,
    CALENDAR, CHECKLIST, WATER, MOOD, ROUTINE, BREATHE, VIRTUAL_PET, FUN, VOCABULARY,
    VOICE, RESOURCES, COMMANDS, SOUND_OUTPUT, SERVER_SETTINGS, START_ON_BOOT, ABOUT
}

sealed class Row(open val id: Long) {
    data class SectionHeader(val title: String, override val id: Long = "section:$title".hashCode().toLong()) : Row(id)
    data class Navigation(
        val title: String, val summary: String?, val destination: SettingsDestination,
        override val id: Long = "${destination.name}:$title".hashCode().toLong(),
    ) : Row(id)
    data class Info(
        val title: String, val value: String, val destination: SettingsDestination? = null,
        override val id: Long = "info:$title".hashCode().toLong(),
    ) : Row(id)
}

fun overviewRows(selectedPetName: String, deviceIp: String?): List<Row> = buildList {
    fun section(title: String) { add(Row.SectionHeader(title)) }
    fun nav(id: String, title: String, destination: SettingsDestination, summary: String? = null) {
        add(Row.Navigation(title, summary, destination, id.hashCode().toLong()))
    }
    section("Pet")
    nav("current-pet", "Current pet", SettingsDestination.PETS, selectedPetName)
    nav("animations", "Animations", SettingsDestination.ANIMATIONS)
    nav("walkabout", "Walkabout", SettingsDestination.WALKABOUT)
    nav("host-status", "Show host status", SettingsDestination.HOST_STATUS_PENDING)
    section("Appearance & output")
    nav("sound-output", "Sound & output", SettingsDestination.SOUND_OUTPUT)
    section("Companion features")
    section("Productivity")
    nav("timer", "Timer", SettingsDestination.TIMER)
    nav("focus", "Focus", SettingsDestination.FOCUS)
    nav("reminders", "Reminders", SettingsDestination.REMINDERS)
    nav("deadlines", "Deadlines", SettingsDestination.DEADLINES)
    nav("calendar", "Calendar", SettingsDestination.CALENDAR)
    nav("checklist", "Launch checklist", SettingsDestination.CHECKLIST)
    section("Wellbeing")
    nav("water", "Water reminder", SettingsDestination.WATER)
    nav("mood", "Mood", SettingsDestination.MOOD)
    nav("routine", "Routine", SettingsDestination.ROUTINE)
    nav("breathe", "Breathe", SettingsDestination.BREATHE)
    section("Play & learning")
    nav("virtual-pet", "Virtual pet", SettingsDestination.VIRTUAL_PET)
    nav("fun", "Fun", SettingsDestination.FUN)
    nav("vocabulary", "Vocabulary", SettingsDestination.VOCABULARY)
    nav("voice", "Voice input", SettingsDestination.VOICE)
    section("Connections & security")
    add(Row.Info("Device IP", deviceIp ?: "Unavailable", id = "device-ip".hashCode().toLong()))
    nav("server-settings", "Server settings", SettingsDestination.SERVER_SETTINGS)
    section("Advanced & diagnostics")
    nav("start-on-boot", "Start on boot", SettingsDestination.START_ON_BOOT)
    nav("resources", "System resources", SettingsDestination.RESOURCES)
    nav("commands", "Plugin commands", SettingsDestination.COMMANDS)
    section("About")
    nav("about", "About OpenPets", SettingsDestination.ABOUT)
}
