package com.rootapp.shield

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.rootapp.data.SettingsStore

/**
 * Reboots clear foreground services and alarms. On BOOT_COMPLETED we restore Root's background
 * pieces so protection and the wind-down reminder keep working without the user reopening the app.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        if (SettingsStore(context).protectionEnabled) {
            runCatching { UsageWatcherService.start(context) }
        }
        runCatching { WindDown.apply(context) }
        runCatching { WeeklyDigest.schedule(context) }
    }
}
