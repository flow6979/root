package com.rootapp.shield

import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Estimates sleep WITHOUT a wearable: your sleep is the longest stretch of no phone use across the
 * night. We take the foreground intervals in a night window (e.g. 8pm to 11am) and find the biggest
 * inactivity gap. Pure Kotlin so it is unit-testable.
 */
object SleepEstimator {

    data class Night(val startMs: Long, val endMs: Long) {
        val minutes: Int get() = ((endMs - startMs) / 60000L).toInt()
    }

    /**
     * Longest no-usage gap inside [windowStart, windowEnd]. Returns null if the biggest gap is
     * shorter than [minSleepMin] (not enough signal to call it sleep).
     */
    fun estimate(
        intervals: List<ShieldInsights.Interval>,
        windowStart: Long,
        windowEnd: Long,
        minSleepMin: Int = 180,
    ): Night? {
        if (windowEnd <= windowStart) return null
        val clipped = intervals
            .filter { it.end > windowStart && it.start < windowEnd }
            .map { ShieldInsights.Interval(maxOf(it.start, windowStart), minOf(it.end, windowEnd)) }
            .sortedBy { it.start }
        var cursor = windowStart
        var best: Night? = null
        fun consider(from: Long, to: Long) {
            if (to > from) {
                val g = Night(from, to)
                if (best == null || g.minutes > best!!.minutes) best = g
            }
        }
        for (iv in clipped) {
            consider(cursor, iv.start)
            cursor = maxOf(cursor, iv.end)
        }
        consider(cursor, windowEnd)
        return best?.takeIf { it.minutes >= minSleepMin }
    }

    /**
     * Bedtime consistency 0-100 from sleep-start minute-of-day values. Times before noon are treated
     * as after midnight (wrapped +24h) so 23:30 and 00:30 read as close, not opposite.
     * Tighter spread = higher score. Needs at least 2 nights.
     */
    fun consistency(bedtimeMinOfDay: List<Int>): Int? {
        if (bedtimeMinOfDay.size < 2) return null
        val norm = bedtimeMinOfDay.map { if (it < 720) it + 1440 else it }
        val mean = norm.average()
        val variance = norm.sumOf { (it - mean) * (it - mean) } / norm.size
        val sd = sqrt(variance)
        // 0 min spread -> 100; 120 min spread -> 0.
        return (100 - (sd * 100.0 / 120.0)).roundToInt().coerceIn(0, 100)
    }

    fun fmt(minutes: Int): String {
        val h = minutes / 60
        val m = minutes % 60
        return if (h > 0) "${h}h ${m}m" else "${m}m"
    }
}
