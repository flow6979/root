package com.rootapp.data

/** Pure streak maths, so it is unit-testable without Android. */
object Streak {
    /**
     * New streak given the previous streak and the last check-in day.
     * - first ever check-in -> 1
     * - already checked in today -> unchanged (min 1)
     * - checked in yesterday -> +1
     * - any older gap -> reset to 1
     */
    fun update(prevStreak: Int, lastDay: Long?, today: Long): Int = when {
        lastDay == null -> 1
        lastDay == today -> prevStreak.coerceAtLeast(1)
        lastDay == today - 1 -> prevStreak + 1
        else -> 1
    }
}
