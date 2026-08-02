package com.rootapp.shield

/**
 * Pure, framework-free insight math for the Shield screen.
 *
 * Everything here takes plain data (already read from Android by [UsageStatsReader]) and turns it
 * into the one or two "hero" lines the user actually cares about: late-night use, this-week vs
 * last-week trend, and a motivating "you could get back ~X" framing. Kept Android-free so it stays
 * unit-testable and cheap to reason about.
 */
object ShieldInsights {

    /** A single foreground stretch of one app, in epoch millis. Half-open [start, end). */
    data class Interval(val start: Long, val end: Long)

    /** The distilled result the UI renders. Any field may be null when the data can't support it. */
    data class Insight(
        /** Minutes of foreground use that fell after the late-night cutoff, summed over the window. */
        val lateNightMinutes: Int,
        /** Percent change of this-week total vs last-week total, or null when last week is unknown/zero. */
        val weekOverWeekPercent: Int?,
        /** The single biggest time-sink app label, or null when none stands out. */
        val topSinkLabel: String?,
        /** Minutes on the top time-sink app over the window. */
        val topSinkMinutes: Int,
        /** Daily average minutes across the visible window. */
        val dailyAverageMinutes: Int,
    )

    private const val MS_PER_MIN = 60_000L
    private const val LATE_NIGHT_HOUR = 23 // 11pm

    /**
     * Minutes of foreground time that land after [cutoffHour] (local wall-clock) across [intervals].
     *
     * We only need a per-interval "how much of this stretch was after 11pm" figure, so the day's
     * midnight is supplied by the caller (it read the real timezone). An interval that spans the
     * cutoff is clipped; one entirely before it contributes zero. Negative/zero-length intervals and
     * anything before [dayStartMillis] are ignored so malformed event pairs can't skew the number.
     */
    fun lateNightMinutes(
        intervals: List<Interval>,
        dayStartMillis: Long,
        cutoffHour: Int = LATE_NIGHT_HOUR,
    ): Int {
        val cutoff = dayStartMillis + cutoffHour * 60L * MS_PER_MIN
        var ms = 0L
        for (iv in intervals) {
            if (iv.end <= iv.start) continue
            val from = maxOf(iv.start, cutoff)
            val to = iv.end
            if (to > from) ms += (to - from)
        }
        return (ms / MS_PER_MIN).toInt()
    }

    /**
     * Percent change of [thisWeekMinutes] against [lastWeekMinutes]. Positive = using the phone more.
     * Returns null when last week is zero/unknown so the UI can hide the trend instead of dividing by
     * zero or showing a meaningless "+100%".
     */
    fun weekOverWeekPercent(thisWeekMinutes: Int, lastWeekMinutes: Int): Int? {
        if (lastWeekMinutes <= 0) return null
        val delta = thisWeekMinutes - lastWeekMinutes
        return Math.round(delta * 100f / lastWeekMinutes)
    }

    /**
     * A short, human "you could get back ~X" line based on the daily average. We frame realistically:
     * trimming a slice of a heavy day, not a fantasy of zero screen time. Returns null when the week
     * is already light, so we never nag someone who's doing fine.
     */
    fun reclaimFraming(dailyAverageMinutes: Int): String? {
        if (dailyAverageMinutes < 90) return null
        // Aim to shave roughly a third of the daily load - an honest, reachable win.
        val perDay = dailyAverageMinutes / 3
        val perWeekHours = (perDay * 7) / 60
        return if (perWeekHours >= 1) {
            "Trim a third and you'd get back about ${perWeekHours}h a week."
        } else {
            "Trim a third and you'd get back about ${perDay}m a day."
        }
    }

    /** Compact "+18% vs last week" / "-9% vs last week" phrase, or null when trend is unknown. */
    fun trendPhrase(weekOverWeekPercent: Int?): String? {
        val p = weekOverWeekPercent ?: return null
        if (p == 0) return "About the same as last week."
        val sign = if (p > 0) "+" else "-"
        return "$sign${Math.abs(p)}% vs last week."
    }

    /**
     * Build the one/two hero lines the Shield screen shows above the gentle-pause switch.
     * Returns an empty list when there's nothing honest to say (e.g. no usage granted yet), so the
     * caller can simply not render the block. Never more than two lines - the screen stays calm.
     */
    fun heroLines(insight: Insight): List<String> {
        val lines = mutableListOf<String>()

        // Line 1: the sharpest real problem we can name. Late-night use wins because it's the one
        // most tied to the app's purpose (focus + better sleep); trend is the fallback.
        if (insight.lateNightMinutes >= 20) {
            val ln = UsageStatsReader.fmt(insight.lateNightMinutes)
            lines += "You spent about $ln on your phone after 11pm this week."
        } else {
            val trend = trendPhrase(insight.weekOverWeekPercent)
            if (trend != null && insight.weekOverWeekPercent != 0) lines += trend
        }

        // Line 2: what to do about it - name the sink, then the reachable win.
        val reclaim = reclaimFraming(insight.dailyAverageMinutes)
        val sink = insight.topSinkLabel
        when {
            sink != null && reclaim != null ->
                lines += "${sink} leads the pack (${UsageStatsReader.fmt(insight.topSinkMinutes)}). $reclaim"
            sink != null ->
                lines += "${sink} leads the pack at ${UsageStatsReader.fmt(insight.topSinkMinutes)}."
            reclaim != null ->
                lines += reclaim
        }

        return lines.take(2)
    }
}
