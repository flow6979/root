package com.rootapp.shield

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.provider.Settings

/**
 * Battery optimizations let the OS kill our foreground service, silently stopping protection.
 * We check the exemption and (via a Play-safe settings screen, no special permission) let the user
 * whitelist Root so it keeps running.
 */
object BatteryOpt {
    fun isExempt(ctx: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
        val pm = ctx.getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(ctx.packageName)
    }

    /** Opens the system list where the user can set Root to "not optimized". */
    fun settingsIntent(): Intent =
        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
