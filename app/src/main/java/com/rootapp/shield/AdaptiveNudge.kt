package com.rootapp.shield

import kotlin.math.roundToInt

/**
 * Makes nudges just-in-time and self-tuning. Two levers, both pure + unit-testable:
 *
 * 1. WHEN to first nudge in a session - earlier in the person's historically risky hours, later
 *    in their calm ones (learned from [riskByHour], a per-hour count of past overuse nudges).
 * 2. HOW OFTEN to repeat - if nudges are mostly heeded, keep engaging; if mostly ignored, back
 *    off so Root never becomes a nag.
 *
 * With no history yet, both fall back to the fixed defaults, so behaviour is sane from install.
 */
object AdaptiveNudge {

    /** Fraction [0,1] of how risky [hour] is relative to the person's peak hour. */
    fun risk(hour: Int, riskByHour: IntArray): Double {
        val peak = riskByHour.maxOrNull() ?: 0
        if (peak <= 0 || hour !in 0..23) return 0.0
        return riskByHour[hour].toDouble() / peak
    }

    /** Minutes into a session before the first nudge: [tight] in peak hours, [loose] in calm ones. */
    fun firstNudgeMin(hour: Int, riskByHour: IntArray, base: Int = 15, tight: Int = 7, loose: Int = 25): Int {
        val peak = riskByHour.maxOrNull() ?: 0
        if (peak <= 0 || hour !in 0..23) return base
        val r = risk(hour, riskByHour)
        return (loose - r * (loose - tight)).roundToInt().coerceIn(tight, loose)
    }

    /** Gap before the next repeat nudge, widened when nudges are being ignored (anti-nag). */
    fun repeatNudgeMin(heeded: Int, shown: Int, base: Int = 20, min: Int = 15, max: Int = 40): Int {
        if (shown < 3) return base
        val rate = (heeded.toDouble() / shown).coerceIn(0.0, 1.0)
        return (max - rate * (max - min)).roundToInt().coerceIn(min, max)
    }

    /** The person's riskiest hour of day so far, or null with no history. */
    fun peakHour(riskByHour: IntArray): Int? {
        val peak = riskByHour.maxOrNull() ?: 0
        if (peak <= 0) return null
        return riskByHour.indexOfFirst { it == peak }
    }
}
