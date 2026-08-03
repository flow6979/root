package com.rootapp.shield

/**
 * Turns "you've been on X for a while" into an honest, specific nudge: a pace projection, a
 * late-night sleep estimate, and a gentle consequence. Pure Kotlin (no Android) so it is unit
 * testable.
 *
 * Responsible framing: we only state well-established wellbeing effects (sleep timing, eye
 * strain, focus, mood, sedentary time). We deliberately do NOT invent disease diagnoses.
 */
object NudgeCalculator {

    data class Nudge(
        val title: String,
        val projection: String,
        val sleep: String?,
        val consequence: String,
    ) {
        /** Assemble the full notification body; [aiLine] (if any) leads. */
        fun body(aiLine: String? = null): String {
            val parts = mutableListOf<String>()
            if (!aiLine.isNullOrBlank()) parts += aiLine.trim()
            parts += projection
            sleep?.let { parts += it }
            parts += consequence
            return parts.joinToString("\n\n")
        }
    }

    fun compute(appLabel: String, sessionMin: Int, todayMin: Int, hour: Int, bedtimeHour: Int = 23): Nudge {
        val late = hour >= bedtimeHour || hour < 5
        val today = maxOf(todayMin, sessionMin)
        val weekly = roundHalf(today * 7 / 60.0)

        val title = "Still on $appLabel"
        val projection = "You're at ${fmt(today)} today, $sessionMin in one sitting. " +
            "At this pace that's about ${trimNum(weekly)} hours a week."

        val sleep = if (late) {
            val loss = (sessionMin + 20).coerceIn(20, 120)
            "It's late. Screen light holds off the melatonin that makes you drowsy, so real sleep " +
                "could slip about $loss min tonight."
        } else {
            null
        }

        val consequence = when {
            late -> "Short, late sleep is closely tied to lower mood and focus the next day."
            sessionMin >= 30 -> "Long unbroken feeds tire your eyes and scatter your focus for up to an hour after."
            today >= 120 -> "Hours in the feed is time your body isn't moving, resting, or connecting - the things that actually lift mood."
            else -> "Each pull-to-refresh trains your attention to chase the next hit. A short pause resets it."
        }
        return Nudge(title, projection, sleep, consequence)
    }

    private fun roundHalf(x: Double): Double = Math.round(x * 2.0) / 2.0

    private fun trimNum(d: Double): String = if (d % 1.0 == 0.0) d.toInt().toString() else d.toString()

    private fun fmt(minutes: Int): String {
        val h = minutes / 60
        val m = minutes % 60
        return if (h > 0) "${h}h ${m}m" else "${m}m"
    }
}
