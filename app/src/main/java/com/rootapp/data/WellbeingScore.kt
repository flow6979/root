package com.rootapp.data

import kotlin.math.roundToInt

/**
 * Richer, weighted, explainable wellbeing score (0..100). Pure Kotlin (no Android imports) so it is
 * fully unit testable.
 *
 * Unlike [Scores.overall], which is a flat average of whatever sub-scores exist, this combines
 * several signals with curated weights and RENORMALISES over only the signals actually present -
 * so missing data is never penalised, it just shifts weight onto what we do know.
 *
 * Signals (each contributes a 0..100 sub-score):
 * - Mood trend   : average of recent mood check-ins (each 0..4) mapped to 0..100.
 * - Eating       : weighted meal-health via [MealHealth.aggregate] over logged meal labels.
 * - Screen time  : less is better vs a daily target (see [SCREEN_TARGET_MIN]).
 * - Consistency  : logging streak, saturating at [STREAK_TARGET] days.
 * - Reflection   : reflection engagement from count + recency of remembered notes.
 *
 * All sub-score maths live here as pure helpers so they can be tested in isolation.
 */
object WellbeingScore {
    /** One weighted component of the overall score, surfaced in the "how it works" dialog. */
    data class Component(val label: String, val subScore: Int, val weight: Double)

    /** Overall 0..100 (null when no signal is present) plus the per-signal [components]. */
    data class Result(val overall: Int?, val components: List<Component>)

    /**
     * Raw, Android-free inputs. Callers gather these from LocalStore / UsageStatsReader and pass
     * plain values so this stays pure. Any field may be null/empty when the signal is unavailable.
     */
    data class Inputs(
        /** Recent mood check-ins, each 0..4 (oldest-first not required). Empty = no mood signal. */
        val recentMoods: List<Int> = emptyList(),
        /** Logged meal labels, oldest-first. Empty = no eating signal. */
        val foodLabels: List<String> = emptyList(),
        /** Daily-average screen-time in minutes, or null when Usage access is not granted. */
        val screenDailyAvgMin: Int? = null,
        /** Current check-in streak in days (0 = none yet). */
        val streakDays: Int = 0,
        /** Number of reflection notes remembered. 0 = no reflection signal. */
        val reflectionCount: Int = 0,
        /**
         * Whole days since the most recent reflection note, or null when there are none. 0 = today.
         * Drives the recency half of the reflection sub-score.
         */
        val daysSinceLastReflection: Int? = null,
    )

    // ---- curated weights (relative; renormalised over present signals) ----
    private const val W_MOOD = 0.30
    private const val W_EATING = 0.25
    private const val W_SCREEN = 0.20
    private const val W_CONSISTENCY = 0.15
    private const val W_REFLECTION = 0.10

    // ---- tunables ----
    /** Daily screen-time (min) at/under which screen sub-score is a perfect 100. */
    const val SCREEN_GOOD_MIN = 60
    /** Daily screen-time (min) at/over which screen sub-score bottoms out at 0. */
    const val SCREEN_TARGET_MIN = 300
    /** Streak length (days) that earns a full consistency sub-score. */
    const val STREAK_TARGET = 7
    /** Reflection count that earns a full "engagement" half of the reflection sub-score. */
    const val REFLECTION_TARGET = 5
    /** Days since last reflection beyond which the "recency" half decays to 0. */
    const val REFLECTION_STALE_DAYS = 14

    /** Mood sub-score: average of moods (0..4) mapped to 0..100. null when empty. */
    fun moodSub(moods: List<Int>): Int? =
        if (moods.isEmpty()) null
        else (moods.map { it.coerceIn(0, 4) }.average() / 4.0 * 100).roundToInt().coerceIn(0, 100)

    /** Eating sub-score: recency-weighted [MealHealth] aggregate over labels. null when empty. */
    fun eatingSub(foodLabels: List<String>): Int? =
        MealHealth.aggregate(foodLabels.map { MealHealth.scoreMeal(it).score })

    /**
     * Screen sub-score: less is better. At/under [SCREEN_GOOD_MIN] -> 100, at/over
     * [SCREEN_TARGET_MIN] -> 0, linear in between. null when screen data is unavailable.
     */
    fun screenSub(dailyAvgMin: Int?): Int? {
        if (dailyAvgMin == null) return null
        val m = dailyAvgMin.coerceAtLeast(0)
        if (m <= SCREEN_GOOD_MIN) return 100
        if (m >= SCREEN_TARGET_MIN) return 0
        val span = (SCREEN_TARGET_MIN - SCREEN_GOOD_MIN).toDouble()
        return (100.0 * (SCREEN_TARGET_MIN - m) / span).roundToInt().coerceIn(0, 100)
    }

    /**
     * Consistency sub-score from the check-in [streakDays], saturating at [STREAK_TARGET]. null when
     * the user has no streak yet (nothing to reward, and we do not want to punish absence).
     */
    fun consistencySub(streakDays: Int): Int? {
        if (streakDays <= 0) return null
        return (100.0 * streakDays.coerceAtMost(STREAK_TARGET) / STREAK_TARGET).roundToInt().coerceIn(0, 100)
    }

    /**
     * Reflection sub-score = engagement (count vs [REFLECTION_TARGET]) blended with recency (how
     * fresh the last note is, decaying over [REFLECTION_STALE_DAYS]). null when there are no notes.
     */
    fun reflectionSub(count: Int, daysSinceLast: Int?): Int? {
        if (count <= 0) return null
        val engagement = 100.0 * count.coerceAtMost(REFLECTION_TARGET) / REFLECTION_TARGET
        val recency = when {
            daysSinceLast == null -> 0.0
            daysSinceLast <= 0 -> 100.0
            daysSinceLast >= REFLECTION_STALE_DAYS -> 0.0
            else -> 100.0 * (REFLECTION_STALE_DAYS - daysSinceLast) / REFLECTION_STALE_DAYS
        }
        return ((engagement * 0.5) + (recency * 0.5)).roundToInt().coerceIn(0, 100)
    }

    /**
     * Combine every available signal into an explainable [Result]. Present signals keep their
     * relative curated weights, renormalised so they sum to 1 - so the overall is a fair weighted
     * average of just what we can measure. Returns overall = null (with an empty component list)
     * when nothing has been logged yet.
     */
    fun compute(inputs: Inputs): Result {
        val raw = listOf(
            Triple("Mood", moodSub(inputs.recentMoods), W_MOOD),
            Triple("Eating", eatingSub(inputs.foodLabels), W_EATING),
            Triple("Screen time", screenSub(inputs.screenDailyAvgMin), W_SCREEN),
            Triple("Consistency", consistencySub(inputs.streakDays), W_CONSISTENCY),
            Triple("Reflection", reflectionSub(inputs.reflectionCount, inputs.daysSinceLastReflection), W_REFLECTION),
        )
        val present = raw.mapNotNull { (label, sub, w) -> if (sub == null) null else Triple(label, sub, w) }
        if (present.isEmpty()) return Result(null, emptyList())

        val weightTotal = present.sumOf { it.third }
        val components = present.map { (label, sub, w) -> Component(label, sub, w / weightTotal) }
        val overall = components.sumOf { it.subScore * it.weight }.roundToInt().coerceIn(0, 100)
        return Result(overall, components)
    }
}
