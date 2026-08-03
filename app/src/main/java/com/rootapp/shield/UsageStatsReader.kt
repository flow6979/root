package com.rootapp.shield

import android.app.usage.UsageEvents
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

    /** Total foreground minutes for the 7 days ending [daysAgo] days back (0 = the last 7 days). */
    private fun weekTotalMinutes(context: Context, daysAgo: Int): Int {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
        cal.add(Calendar.DAY_OF_YEAR, -(daysAgo + 6))
        val start = cal.timeInMillis
        val end = start + 7L * 24 * 60 * 60 * 1000
        val totalMs = usm.queryAndAggregateUsageStats(start, end).values.sumOf { it.totalTimeInForeground }
        return (totalMs / 60000).toInt()
    }

    /** This week's foreground total (last 7 days). */
    fun thisWeekMinutes(context: Context): Int = weekTotalMinutes(context, 0)

    /** The prior week's foreground total (days 8-14 back), for the week-over-week trend. */
    fun lastWeekMinutes(context: Context): Int = weekTotalMinutes(context, 7)

    /**
     * Foreground intervals (per app) over the last 7 days, reconstructed from raw usage events so we
     * can measure late-night use. Each MOVE_TO_FOREGROUND is paired with the next MOVE_TO_BACKGROUND.
     * Excludes our own app so previewing the pause never counts against the user.
     */
    fun foregroundIntervals(context: Context): List<ShieldInsights.Interval> {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val end = System.currentTimeMillis()
        val start = end - 7L * 24 * 60 * 60 * 1000
        val events = usm.queryEvents(start, end)
        val e = UsageEvents.Event()
        val openedAt = HashMap<String, Long>()
        val out = mutableListOf<ShieldInsights.Interval>()
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            val pkg = e.packageName ?: continue
            if (pkg == context.packageName) continue
            when (e.eventType) {
                UsageEvents.Event.MOVE_TO_FOREGROUND -> openedAt[pkg] = e.timeStamp
                UsageEvents.Event.MOVE_TO_BACKGROUND -> {
                    val from = openedAt.remove(pkg) ?: continue
                    if (e.timeStamp > from) out += ShieldInsights.Interval(from, e.timeStamp)
                }
            }
        }
        return out
    }

    /** Local midnight (start of today) in millis - the reference point for the 11pm cutoff. */
    fun todayStartMillis(): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    /**
     * Late-night minutes over the last 7 days: for each of the 7 days we clip its intervals to the
     * 11pm cutoff for that specific day and sum. Uses [todayStartMillis] shifted back day by day so
     * the cutoff tracks real local midnights (incl. any DST shift the Calendar applies).
     */
    fun lateNightMinutesLastWeek(context: Context): Int {
        val intervals = foregroundIntervals(context)
        if (intervals.isEmpty()) return 0
        var total = 0
        for (d in 0..6) {
            val cal = Calendar.getInstance()
            cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
            cal.add(Calendar.DAY_OF_YEAR, -d)
            val dayStart = cal.timeInMillis
            val dayEnd = dayStart + 24L * 60 * 60 * 1000
            // Only intervals that touch this day, clipped to it, so we don't double-count.
            val forDay = intervals
                .filter { it.end > dayStart && it.start < dayEnd }
                .map { ShieldInsights.Interval(maxOf(it.start, dayStart), minOf(it.end, dayEnd)) }
            total += ShieldInsights.lateNightMinutes(forDay, dayStart)
        }
        return total
    }

    // ---- sleep (from the overnight usage gap) ----

    /** Night window ending at 11am [daysAgo] days back, starting 15h earlier (~8pm the day before). */
    private fun sleepWindow(daysAgo: Int): Pair<Long, Long> {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 11); cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
        cal.add(Calendar.DAY_OF_YEAR, -daysAgo)
        val end = cal.timeInMillis
        return (end - 15L * 60 * 60 * 1000) to end
    }

    fun lastNightSleep(context: Context): SleepEstimator.Night? {
        val ivs = foregroundIntervals(context)
        val (s, e) = sleepWindow(0)
        return SleepEstimator.estimate(ivs, s, e)
    }

    /** Estimated sleep for each of the last 7 nights (oldest first); null nights = not enough signal. */
    fun weeklyNights(context: Context): List<SleepEstimator.Night?> {
        val ivs = foregroundIntervals(context)
        return (6 downTo 0).map { d -> val (s, e) = sleepWindow(d); SleepEstimator.estimate(ivs, s, e) }
    }

    /** 0-100 bedtime-consistency from the last week's estimated sleep-start times. */
    fun bedtimeConsistency(context: Context): Int? {
        val bedtimes = weeklyNights(context).filterNotNull().map {
            val c = Calendar.getInstance(); c.timeInMillis = it.startMs
            c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE)
        }
        return SleepEstimator.consistency(bedtimes)
    }

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

    /** Total foreground minutes across all apps since local midnight (for the daily budget). */
    fun todayTotalMinutes(context: Context): Int {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val ms = usm.queryAndAggregateUsageStats(todayStartMillis(), System.currentTimeMillis())
            .values.filter { it.packageName != context.packageName && !isSystemish(it.packageName) }
            .sumOf { it.totalTimeInForeground }
        return (ms / 60000).toInt()
    }

    /** Foreground minutes for a single package since local midnight today. */
    fun todayForegroundMinutes(context: Context, pkg: String): Int {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val ms = usm.queryAndAggregateUsageStats(todayStartMillis(), System.currentTimeMillis())[pkg]
            ?.totalTimeInForeground ?: 0L
        return (ms / 60000).toInt()
    }

    private val knownNames = mapOf(
        "com.instagram.android" to "Instagram",
        "com.google.android.youtube" to "YouTube",
        "com.zhiliaoapp.musically" to "TikTok",
        "com.ss.android.ugc.trill" to "TikTok",
        "com.whatsapp" to "WhatsApp",
        "com.snapchat.android" to "Snapchat",
        "com.facebook.katana" to "Facebook",
        "com.twitter.android" to "X",
        "com.reddit.frontpage" to "Reddit",
        "com.netflix.mediaclient" to "Netflix",
        "com.google.android.gm" to "Gmail",
        "com.android.chrome" to "Chrome",
    )

    /** Generic package segments that should never become an app name (fixes the ".android" bug). */
    private val genericSegments = setOf("android", "app", "apps", "mobile", "com", "org", "net", "google", "free", "lite", "go")

    private fun prettyFromPackage(pkg: String): String {
        val parts = pkg.split('.').filter { it.isNotBlank() }
        val candidate = parts.lastOrNull { it.lowercase() !in genericSegments } ?: parts.lastOrNull() ?: pkg
        return candidate.replaceFirstChar { it.uppercase() }
    }

    private fun appLabel(pm: PackageManager, pkg: String): String =
        knownNames[pkg]
            ?: runCatching { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() }
                .getOrNull()?.takeIf { it.isNotBlank() && !it.equals("android", ignoreCase = true) }
            ?: prettyFromPackage(pkg)

    /** Public resolver for callers outside this object (e.g. the watcher's nudge labels). */
    fun labelOf(context: Context, pkg: String): String =
        appLabel(context.packageManager, pkg)

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
