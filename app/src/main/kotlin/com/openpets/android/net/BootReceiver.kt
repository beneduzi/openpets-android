package com.openpets.android.net

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.openpets.android.settings.AppSettings

/** Restarts the companion servers after device boot when enabled. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != "android.intent.action.QUICKBOOT_POWERON"
        ) {
            return
        }
        val settings = AppSettings(context)
        if (!settings.autoStartOnBoot) return
        if (!settings.ipcEnabled && !settings.remoteEnabled) return
        OpenPetsService.start(context)
    }
}
