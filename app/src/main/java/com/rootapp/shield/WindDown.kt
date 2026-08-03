package com.rootapp.shield

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.rootapp.data.SettingsStore
import java.util.Calendar

/**
 * Nightly wind-down reminder. Schedules an inexact daily alarm ~15 min before the user's bedtime
 * (no exact-alarm permission needed). Re-armed on every app launch so it survives normally; a
 * reboot clears it until the app is next opened (acceptable for a gentle reminder).
 */
object WindDown {
    private const val REQ = 7001

    private fun pending(ctx: Context): PendingIntent = PendingIntent.getBroadcast(
        ctx, REQ, Intent(ctx, WindDownReceiver::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /** Apply the current settings: schedule if enabled, cancel if not. Safe to call any time. */
    fun apply(ctx: Context) {
        val s = SettingsStore(ctx)
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        if (!s.windDownEnabled) { am.cancel(pending(ctx)); return }
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, s.bedtimeHour); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            add(Calendar.MINUTE, -15)
            if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1)
        }
        am.setInexactRepeating(AlarmManager.RTC_WAKEUP, cal.timeInMillis, AlarmManager.INTERVAL_DAY, pending(ctx))
    }

    fun bedtimeLabel(hour24: Int): String {
        val h12 = ((hour24 + 11) % 12) + 1
        val ampm = if (hour24 % 24 < 12) "AM" else "PM"
        return "$h12:00 $ampm"
    }
}

/** Fires at wind-down time and posts the reminder (if still enabled). */
class WindDownReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!SettingsStore(context).windDownEnabled) return
        Nudges.post(
            context,
            "Time to wind down",
            "Lights soon. Screens right now make sleep come slower - the light holds back melatonin. " +
                "Try setting the phone down in the next 15 minutes and let your eyes rest.",
        )
    }
}
