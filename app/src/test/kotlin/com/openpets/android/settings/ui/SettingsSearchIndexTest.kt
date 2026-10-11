package com.openpets.android.settings.ui

import org.junit.Assert.*
import org.junit.Test

class SettingsSearchIndexTest {
    @Test fun searchesNestedDestinationsAndAliases(){
        val resolve:(Int)->String={id->when(id){com.openpets.android.R.string.settings_ipc_port->"IPC port";com.openpets.android.R.string.settings_search_ipc_port_summary->"Port for local IPC commands";com.openpets.android.R.string.settings_overview_server->"Server settings";com.openpets.android.R.string.ui_notification_sound->"Notification sound";com.openpets.android.R.string.settings_search_appearance_summary->"Sound vibration speech";com.openpets.android.R.string.ui_appearance_title->"Appearance";com.openpets.android.R.string.ui_timer->"Timer";com.openpets.android.R.string.settings_search_timer_summary->"Start and manage a timer";com.openpets.android.R.string.settings_overview_productivity->"Productivity";else->"entry"}}
        val port=searchSettings("IPC port",resolve=resolve).single();assertEquals(SettingsDestination.SERVER_SETTINGS,port.destination);assertEquals("ipcPort",port.targetKey);assertEquals(com.openpets.android.R.string.settings_search_breadcrumb_servers,port.breadcrumbRes)
        assertTrue(searchSettings("tts",resolve=resolve).any{it.destination==SettingsDestination.SOUND_OUTPUT})
        assertTrue(searchSettings("timer",resolve=resolve).any{it.destination==SettingsDestination.TIMER})
    }
    @Test fun searchIndexDoesNotExposeSensitiveOrUserSpecificValues(){
        val entries=settingsSearchIndex();assertTrue(entries.none{it.aliases.any{alias->alias.contains("token",true)}})
        assertFalse(entries.any{it.destination==SettingsDestination.PETS&&it.targetKey!=null||it.titleRes==com.openpets.android.R.string.ui_greeting})
    }
}
