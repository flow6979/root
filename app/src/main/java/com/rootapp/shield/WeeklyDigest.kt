package com.rootapp.shield

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.util.Calendar

/** Schedules a weekly "Your week with Root is ready" notification for Sunday evening. */
object WeeklyDigest {
    private const val REQ = 7003

    private fun pending(ctx: Context): PendingIntent = PendingIntent.getBroadcast(
        ctx, REQ, Intent(ctx, WeeklyDigestReceiver::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    fun schedule(ctx: Context) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 18); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            while (get(Calendar.DAY_OF_WEEK) != Calendar.SUNDAY || timeInMillis <= System.currentTimeMillis()) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }
        am.setInexactRepeating(AlarmManager.RTC_WAKEUP, cal.timeInMillis, 7L * AlarmManager.INTERVAL_DAY, pending(ctx))
    }
}

class WeeklyDigestReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Nudges.post(
            context,
            "Your week with Root is ready",
            "Tap to open Root and see your 7-day recap - screen time, sleep, meals and mood - plus one gentle focus for next week.",
        )
    }
}
