package com.rootapp.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.time.LocalDate

/** A weekly themed goal: do [action] [target] times this week for [bonus] Effort Points. */
data class Challenge(
    val key: String,
    val title: String,
    val desc: String,
    val action: EPAction,
    val target: Int,
    val bonus: Int,
)

/**
 * The weekly challenge. The same challenge shows for everyone in a given week (picked by week
 * index), progress is read from the local action counts, and completing it grants bonus EP +
 * a badge, once per week. All local.
 */
object Challenges {
    private val CATALOG = listOf(
        Challenge("focus3", "Find your focus", "Run 3 focus sessions this week", EPAction.FOCUS, 3, 30),
        Challenge("checkin5", "Check in often", "Log your mood on 5 days", EPAction.MOOD, 5, 30),
        Challenge("meals5", "Mind your meals", "Log 5 meals this week", EPAction.MEAL, 5, 25),
        Challenge("nudge5", "Answer the nudge", "Put the phone down after 5 nudges", EPAction.NUDGE_HEEDED, 5, 25),
        Challenge("reflect3", "Talk it out", "Have 3 reflection sessions", EPAction.REFLECTION, 3, 30),
    )

    private fun weekIndex(): Int = (Leaderboard.weekStartDate().toEpochDay() / 7L).toInt()

    fun current(): Challenge = CATALOG[((weekIndex() % CATALOG.size) + CATALOG.size) % CATALOG.size]

    fun progress(context: Context): Int {
        val ws = Leaderboard.weekStartDate().toEpochDay()
        return LeaderboardStore(context).actionCountThisWeek(current().action.name, ws).coerceAtMost(current().target)
    }

    fun isComplete(context: Context): Boolean = progress(context) >= current().target

    /** If complete and not yet claimed this week, grant the bonus + badge. Returns true if claimed now. */
    fun checkAndClaim(context: Context, scope: CoroutineScope): Boolean {
        val ch = current()
        if (progress(context) < ch.target) return false
        val store = ChallengeStore(context)
        if (!store.claim(Leaderboard.weekStartString(), ch.key)) return false
        LeaderboardStore(context).addBonus(ch.bonus, LocalDate.now().toEpochDay())
        SeasonStore(context).addPoints(ch.bonus)
        BadgeStore(context).award("challenge")
        scope.launch { Leaderboard.syncScore(context) }
        return true
    }
}

/** Tracks which weekly challenges have been claimed (so the bonus is granted once). */
class ChallengeStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("root_challenges", Context.MODE_PRIVATE)

    fun isClaimed(week: String, key: String): Boolean = prefs.getBoolean("$week|$key", false)

    /** Mark claimed; returns true only if it wasn't already claimed. */
    fun claim(week: String, key: String): Boolean {
        val id = "$week|$key"
        if (prefs.getBoolean(id, false)) return false
        prefs.edit().putBoolean(id, true).apply()
        return true
    }
}
