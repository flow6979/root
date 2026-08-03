package com.rootapp.data

import android.content.Context
import com.rootapp.ai.ChatMessage
import com.rootapp.ai.LlmClient
import com.rootapp.shield.ShieldInsights
import com.rootapp.shield.UsageStatsReader
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDate

/** Aggregates the last 7 days across Root's signals into a "Your week" recap. */
object WeeklyReview {

    data class Data(
        val screenThisWeekMin: Int,
        val screenLastWeekMin: Int,
        val topApp: String?,
        val topAppMin: Int,
        val lateNightMin: Int,
        val sleepNights: Int,
        val avgSleepMin: Int?,
        val bedtimeConsistency: Int?,
        val healthyMeals: Int,
        val junkMeals: Int,
        val eatingScore: Int?,
        val moodScore: Int?,
        val pausesTaken: Int,
    )

    fun build(context: Context): Data {
        val nights = UsageStatsReader.weeklyNights(context).filterNotNull()
        val store = LocalStore(context)
        val weekAgo = System.currentTimeMillis() - 7L * 86_400_000
        val foods = store.foods().filter { it.timestamp >= weekAgo }
        val todayEpoch = LocalDate.now().toEpochDay()
        val moods = store.moods().filter { it.epochDay >= todayEpoch - 6 }.map { it.mood }
        return Data(
            screenThisWeekMin = UsageStatsReader.thisWeekMinutes(context),
            screenLastWeekMin = UsageStatsReader.lastWeekMinutes(context),
            topApp = UsageStatsReader.topApps(context, 1).firstOrNull()?.label,
            topAppMin = UsageStatsReader.topApps(context, 1).firstOrNull()?.minutes ?: 0,
            lateNightMin = UsageStatsReader.lateNightMinutesLastWeek(context),
            sleepNights = nights.size,
            avgSleepMin = if (nights.isEmpty()) null else nights.sumOf { it.minutes } / nights.size,
            bedtimeConsistency = UsageStatsReader.bedtimeConsistency(context),
            healthyMeals = foods.count { it.healthy },
            junkMeals = foods.count { !it.healthy },
            eatingScore = Scores.eatingWeighted(foods.map { it.label }),
            moodScore = if (moods.isEmpty()) null else (moods.average() / 4.0 * 100).toInt(),
            pausesTaken = store.interruptPaused(),
        )
    }

    /** Screen-time week-over-week trend (negative = down/good), null if no prior week. */
    fun trendPct(d: Data): Int? = ShieldInsights.weekOverWeekPercent(d.screenThisWeekMin, d.screenLastWeekMin)

    fun headline(d: Data): String {
        val t = trendPct(d)
        return when {
            t == null -> "Here's your week with Root."
            t <= -10 -> "Your screen time is down ${-t}% this week. Well done."
            t >= 10 -> "Screen time is up $t% this week. Let's ease it back."
            else -> "A steady week. Small steps from here."
        }
    }

    suspend fun focusLine(llm: LlmClient, d: Data): String {
        val ctx = "screen ${d.screenThisWeekMin}m this week (was ${d.screenLastWeekMin}), top ${d.topApp ?: "n/a"}, " +
            "late-night ${d.lateNightMin}m, sleep avg ${d.avgSleepMin ?: "n/a"}m consistency ${d.bedtimeConsistency ?: "n/a"}, " +
            "meals ${d.healthyMeals} healthy/${d.junkMeals} junk, mood ${d.moodScore ?: "n/a"}/100"
        val prompt = "From this week's wellbeing data, suggest ONE specific, kind focus for next week " +
            "in 18 words or fewer. Warm, not preachy. No emojis, no quotes. Data: $ctx"
        val ai = withTimeoutOrNull(6000) {
            runCatching {
                llm.complete(listOf(ChatMessage.user(prompt)))
                    .trim().trim('"').lineSequence().firstOrNull { it.isNotBlank() }?.take(140)
            }.getOrNull()
        }
        return ai?.takeIf { it.isNotBlank() } ?: defaultFocus(d)
    }

    private fun defaultFocus(d: Data): String = when {
        d.lateNightMin >= 120 -> "Aim for a screen-free 30 minutes before bed a few nights next week."
        (trendPct(d) ?: 0) > 0 -> "Pick one time-sink app and set a small daily limit."
        d.junkMeals > d.healthyMeals -> "Add one vegetable or fruit to a meal each day."
        else -> "Keep the rhythm going - one small healthy choice a day."
    }
}
