package com.rootapp.data

import android.content.Context
import com.rootapp.shield.ShieldPermissions
import com.rootapp.shield.UsageStatsReader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * Facade for the wellbeing leaderboard. Awards Effort Points for healthy actions (capped locally),
 * then syncs the week's total plus the current wellbeing score to Supabase. Everything is
 * best-effort: with no backend or network, points still accrue locally and sync later.
 */
object Leaderboard {

    /** Monday of the week containing [today]. */
    fun weekStartDate(today: LocalDate = LocalDate.now()): LocalDate =
        today.minusDays(((today.dayOfWeek.value + 6) % 7).toLong())

    private fun weekStartEpochDay(): Long = weekStartDate().toEpochDay()
    fun weekStartString(): String = weekStartDate().toString()

    /** Award [action] and, if any points landed, add to season progress and push the weekly total. */
    fun record(context: Context, scope: CoroutineScope, action: EPAction) {
        val awarded = LeaderboardStore(context).record(action, LocalDate.now().toEpochDay())
        if (awarded > 0) {
            SeasonStore(context).addPoints(awarded) // unlock cosmetic sky themes over the season
            scope.launch { syncScore(context) }
        }
    }

    /** Push this week's EP total + current wellbeing score to the backend. */
    suspend fun syncScore(context: Context) {
        val repo = SupabaseRepository(context)
        if (!repo.configured) return
        val weekly = LeaderboardStore(context).weeklyEffort(weekStartEpochDay())
        repo.submitScore(weekStartString(), weekly, currentWellbeing(context))
    }

    /** The current overall wellbeing score (0-100), or null if there isn't enough data yet. */
    private fun currentWellbeing(context: Context): Int? {
        val store = LocalStore(context)
        val screenAvg = if (ShieldPermissions.hasUsageAccess(context)) {
            UsageStatsReader.dailyAverageMinutes(UsageStatsReader.lastSevenDays(context))
        } else {
            null
        }
        return WellbeingScore.compute(
            WellbeingScore.Inputs(
                recentMoods = store.moods().takeLast(7).map { it.mood },
                foodLabels = store.foods().map { it.label },
                screenDailyAvgMin = screenAvg,
                streakDays = store.streak(),
                reflectionCount = store.memory().size,
            ),
        ).overall
    }
}
