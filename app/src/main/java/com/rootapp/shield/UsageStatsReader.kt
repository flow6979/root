package com.rootapp.shield

import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import java.util.Calendar

/** Reads real screen-time from UsageStatsManager for the Shield insights. */
object UsageStatsReader {
    data class DayUsage(val label: String, val minutes: Int)
    data class AppUsage(val label: String, val minutes: Int)

    private val dayLetters = listOf("S", "M", "T", "W", "T", "F", "S")

    /** Foreground minutes for each of the last 7 days (oldest first). */
    fun lastSevenDays(context: Context): List<DayUsage> {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val out = mutableListOf<DayUsage>()
        for (i in 6 downTo 0) {
            val cal = Calendar.getInstance()
            cal.add(Calendar.DAY_OF_YEAR, -i)
            cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
            val start = cal.timeInMillis
            val end = start + 24L * 60 * 60 * 1000
            val stats = usm.queryAndAggregateUsageStats(start, end)
            val totalMs = stats.values.sumOf { it.totalTimeInForeground }
            out += DayUsage(dayLetters[cal.get(Calendar.DAY_OF_WEEK) - 1], (totalMs / 60000).toInt())
        }
        return out
    }

    fun weeklyTotalMinutes(days: List<DayUsage>): Int = days.sumOf { it.minutes }
    fun dailyAverageMinutes(days: List<DayUsage>): Int = if (days.isEmpty()) 0 else weeklyTotalMinutes(days) / days.size

    private val ignoredHints = listOf(
        "launcher", "systemui", "com.android.settings", "inputmethod",
        "com.google.android.gms", "permissioncontroller", "com.android.vending",
    )
    private fun isSystemish(pkg: String) = ignoredHints.any { pkg.contains(it) }

    /** Top real foreground apps over the last 7 days (excludes our app, launchers, system UI). */
    fun topApps(context: Context, limit: Int = 3): List<AppUsage> {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val pm = context.packageManager
        val end = System.currentTimeMillis()
        val start = end - 7L * 24 * 60 * 60 * 1000
        return usm.queryAndAggregateUsageStats(start, end).values
            .filter { it.totalTimeInForeground > 60000 && it.packageName != context.packageName && !isSystemish(it.packageName) }
            .sortedByDescending { it.totalTimeInForeground }
            .take(limit)
            .map { AppUsage(appLabel(pm, it.packageName), (it.totalTimeInForeground / 60000).toInt()) }
    }

    private fun appLabel(pm: PackageManager, pkg: String): String = runCatching {
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    }.getOrDefault(pkg.substringAfterLast('.').replaceFirstChar { it.uppercase() })

    fun fmt(minutes: Int): String {
        val h = minutes / 60; val m = minutes % 60
        return if (h > 0) "${h}h ${m}m" else "${m}m"
    }

    /** A richer, real "Root's read" from the data (2-3 sentences). */
    fun read(days: List<DayUsage>, top: List<AppUsage>): String {
        if (days.all { it.minutes == 0 }) return "Grant Usage access and Root will read your real screen time here."
        val avg = dailyAverageMinutes(days)
        val busiest = days.maxByOrNull { it.minutes }
        val sb = StringBuilder("About ${fmt(avg)} a day on your phone this week.")
        val t1 = top.getOrNull(0); val t2 = top.getOrNull(1)
        if (t1 != null) {
            sb.append(" Most of it in ${t1.label} (${fmt(t1.minutes)})")
            if (t2 != null) sb.append(", then ${t2.label}")
            sb.append(".")
        }
        if (busiest != null && busiest.minutes > 0) {
            sb.append(" Your heaviest day hit ${fmt(busiest.minutes)}.")
        }
        if (avg >= 180) sb.append(" That's a lot of hours you could get back.")
        else if (avg in 1 until 90) sb.append(" That's a light, healthy week. Nice.")
        return sb.toString()
    }
}
