package com.rootapp.data

/**
 * Turns the user's OWN real data into a few concrete, gentle "For you" cards for Home. Pure Kotlin
 * (no Android imports) so the whole thing is unit-testable and cheap to run on the main thread.
 *
 * Design principles (from docs/CLAUDE.md - friend, not warden):
 * - Only ever speak from data we actually have. If a signal is missing, we SKIP that card rather
 *   than invent or guess. Empty input -> empty list (Home simply hides the section).
 * - Each card is one observation ([body]) plus one small, doable [suggestion]. Never a lecture.
 * - No shaming language, no dashes in copy, plain hyphens only.
 *
 * The builder is deterministic and ranked: it collects every card whose signal fired, orders them
 * by how actionable they are, and returns at most [MAX_CARDS].
 */
object Insights {
    /** One Home insight: a short [title], an observation [body], and one gentle [suggestion]. */
    data class InsightCard(val title: String, val body: String, val suggestion: String)

    /**
     * Android-free inputs. Callers gather these from LocalStore / UsageStatsReader and pass plain
     * values so this stays pure. Any field may be null/empty when the signal is unavailable.
     */
    data class Inputs(
        /** Daily-average screen-time in minutes, or null when Usage access is not granted. */
        val screenDailyAvgMin: Int? = null,
        /** Label of the single most-used app this week (e.g. "Instagram"), or null. */
        val topAppLabel: String? = null,
        /** Minutes on the top app this week, for phrasing. 0 when unknown. */
        val topAppMinutes: Int = 0,
        /** Logged meal labels, oldest-first. Empty = no eating signal. */
        val foodLabels: List<String> = emptyList(),
        /** Recent mood check-ins, each 0..4, oldest-first. Empty = no mood signal. */
        val recentMoods: List<Int> = emptyList(),
        /** The single intention from the most recent reflection session, or null/blank. */
        val latestIntention: String? = null,
    )

    /** Screen-time (daily avg, min) at/over which we gently flag it. */
    const val SCREEN_HEAVY_MIN = 180
    /** How many of the most recent meals we scan for a junk streak. */
    const val JUNK_WINDOW = 4
    /** A meal at/under this [MealHealth] score counts as "junk" for the streak signal. */
    const val JUNK_THRESHOLD = 30
    /** Consecutive junk meals (within the window) that trip the junk-streak card. */
    const val JUNK_STREAK_MIN = 2
    /** Max cards Home shows at once. */
    const val MAX_CARDS = 3

    /**
     * Build up to [MAX_CARDS] insight cards from real data. Cards are collected in priority order
     * (most actionable first) and each is included only if its underlying signal is present.
     */
    fun build(inputs: Inputs): List<InsightCard> {
        val cards = mutableListOf<InsightCard>()

        intentionCard(inputs.latestIntention)?.let { cards += it }
        junkStreakCard(inputs.foodLabels)?.let { cards += it }
        screenCard(inputs.screenDailyAvgMin, inputs.topAppLabel, inputs.topAppMinutes)?.let { cards += it }
        moodCard(inputs.recentMoods)?.let { cards += it }

        return cards.take(MAX_CARDS)
    }

    /**
     * Turn the intention the user set last session into a follow-through nudge. Skipped when there
     * is no intention on record. This closes the loop so reflection sessions "lead somewhere".
     */
    fun intentionCard(latestIntention: String?): InsightCard? {
        val intention = latestIntention?.trim().orEmpty()
        if (intention.isEmpty()) return null
        return InsightCard(
            title = "Your last intention",
            body = "Last time you said you wanted to $intention.",
            suggestion = "One small step toward it today counts. What's the smallest version?",
        )
    }

    /**
     * Flag a run of junk meals among the most recent [JUNK_WINDOW]. Uses [MealHealth] scores so it
     * stays consistent with the wellbeing score. Skipped when there is no recent junk streak.
     */
    fun junkStreakCard(foodLabels: List<String>): InsightCard? {
        if (foodLabels.isEmpty()) return null
        val recent = foodLabels.takeLast(JUNK_WINDOW)
        // Count the trailing run of junk meals (most recent first).
        var streak = 0
        for (label in recent.asReversed()) {
            if (MealHealth.scoreMeal(label).score <= JUNK_THRESHOLD) streak++ else break
        }
        if (streak < JUNK_STREAK_MIN) return null
        return InsightCard(
            title = "A few heavy meals in a row",
            body = "Your last $streak logged meals were on the heavier side.",
            suggestion = "No guilt. Maybe make the next one greens or fruit - it evens out fast.",
        )
    }

    /**
     * Flag heavy phone use, naming the top app when we know it. Skipped when screen data is absent
     * or usage is already light (nothing to nudge). This is the concrete, data-driven insight.
     */
    fun screenCard(dailyAvgMin: Int?, topAppLabel: String?, topAppMinutes: Int): InsightCard? {
        if (dailyAvgMin == null || dailyAvgMin < SCREEN_HEAVY_MIN) return null
        val app = topAppLabel?.trim().orEmpty()
        val body = if (app.isNotEmpty()) {
            "You are averaging ${fmt(dailyAvgMin)} a day on your phone, most of it in $app" +
                (if (topAppMinutes > 0) " (${fmt(topAppMinutes)} this week)." else ".")
        } else {
            "You are averaging ${fmt(dailyAvgMin)} a day on your phone this week."
        }
        val suggestion = if (app.isNotEmpty()) {
            "Try one screen-free stretch today, even 20 minutes away from $app."
        } else {
            "Try one screen-free stretch today, even 20 minutes helps."
        }
        return InsightCard(title = "Screen time is running high", body = body, suggestion = suggestion)
    }

    /**
     * Surface a dipping mood trend: recent check-ins clearly lower than earlier ones. Needs at
     * least 3 moods and a real downward move; otherwise skipped (we do not manufacture concern).
     */
    fun moodCard(recentMoods: List<Int>): InsightCard? {
        if (recentMoods.size < 3) return null
        val half = recentMoods.size / 2
        val earlier = recentMoods.take(half)
        val later = recentMoods.takeLast(recentMoods.size - half)
        if (earlier.isEmpty() || later.isEmpty()) return null
        val earlierAvg = earlier.average()
        val laterAvg = later.average()
        if (laterAvg >= earlierAvg - 0.75) return null
        return InsightCard(
            title = "Your mood has dipped a little",
            body = "Your recent check-ins are lower than they were earlier this week.",
            suggestion = "Be kind to yourself today. A short walk or a talk with Root can help.",
        )
    }

    private fun fmt(minutes: Int): String {
        val h = minutes / 60
        val m = minutes % 60
        return if (h > 0) "${h}h ${m}m" else "${m}m"
    }

    // ---- takeaway extraction (pure parser for the reflection-session AI output) ----

    /** A parsed takeaway: the user's main [concern] and one [intention]. Either may be blank. */
    data class Takeaway(val concern: String, val intention: String)

    /**
     * Parse the LLM's session-takeaway reply into a [Takeaway]. We ask the model to reply in the
     * exact shape "Concern: ... | Intention: ...", but real models drift, so this is lenient:
     * it accepts labelled lines OR a single "concern | intention" split, trims, strips surrounding
     * quotes, and caps each field so nothing huge is stored. Pure -> unit-testable. Never throws.
     */
    fun parseTakeaway(raw: String, maxLen: Int = 120): Takeaway {
        val text = raw.trim()
        if (text.isEmpty()) return Takeaway("", "")

        var concern = ""
        var intention = ""

        // Labelled form: look for "Concern:" / "Intention:" anywhere, line by line.
        text.lineSequence().forEach { line ->
            val l = line.trim()
            val lower = l.lowercase()
            when {
                lower.startsWith("concern:") -> concern = l.substringAfter(":").trim()
                lower.startsWith("intention:") -> intention = l.substringAfter(":").trim()
            }
        }

        // Fallback: single "concern | intention" line.
        if (concern.isEmpty() && intention.isEmpty() && text.contains("|")) {
            val parts = text.split("|").map { it.trim() }
            concern = parts.getOrElse(0) { "" }
            intention = parts.getOrElse(1) { "" }
        }

        return Takeaway(clean(concern, maxLen), clean(intention, maxLen))
    }

    private fun clean(s: String, maxLen: Int): String {
        val trimmed = s.trim().trim('"', '\'', '.').trim()
        return if (trimmed.length > maxLen) trimmed.take(maxLen).trim() else trimmed
    }
}
