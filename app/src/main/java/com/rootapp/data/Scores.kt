package com.rootapp.data

import kotlin.math.roundToInt

/** Deterministic wellbeing sub-scores (0..100). Pure -> unit-testable. */
object Scores {
    /** Eating: share of healthy meals. null when nothing logged. */
    fun eating(healthy: Int, junk: Int): Int? {
        val total = healthy + junk
        if (total == 0) return null
        return (100.0 * healthy / total).roundToInt()
    }

    /** Mood: average of recent moods (each 0..4) mapped to 0..100. */
    fun mood(values: List<Int>): Int? =
        if (values.isEmpty()) null else (values.average() / 4.0 * 100).roundToInt()

    /** Screen time: lower is better. 0 min -> 100, 240+ min -> 0. */
    fun screen(dailyAvgMin: Int): Int? =
        if (dailyAvgMin <= 0) null else (100.0 - dailyAvgMin / 240.0 * 100).coerceIn(0.0, 100.0).roundToInt()

    /** Overall wellbeing: average of whatever sub-scores are available. */
    fun overall(subs: List<Int>): Int? =
        if (subs.isEmpty()) null else subs.average().roundToInt()

    fun label(score: Int): String = when {
        score >= 80 -> "Great"
        score >= 60 -> "Good"
        score >= 40 -> "Okay"
        else -> "Needs care"
    }
}
