package com.rootapp.data

import android.content.Context

/** A milestone the user can earn. Purely motivational; shown on a shelf in the League screen. */
data class Badge(val key: String, val title: String, val desc: String)

/** The full set of badges, in display order. */
object Badges {
    val CATALOG = listOf(
        Badge("first_checkin", "First step", "Logged your first mood"),
        Badge("focused", "In the zone", "Ran a focus session"),
        Badge("reflected", "Opened up", "Had a reflection"),
        Badge("challenge", "Challenge champ", "Completed a weekly challenge"),
        Badge("streak7", "One week strong", "Reached a 7-day streak"),
        Badge("streak30", "A month of care", "Reached a 30-day streak"),
        Badge("sky", "Sky collector", "Unlocked a new sky"),
    )
}

/** Persists which badges have been earned. */
class BadgeStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("root_badges", Context.MODE_PRIVATE)

    fun isEarned(key: String): Boolean = prefs.getBoolean(key, false)

    /** Mark earned; returns true only the first time. */
    fun award(key: String): Boolean {
        if (prefs.getBoolean(key, false)) return false
        prefs.edit().putBoolean(key, true).apply()
        return true
    }

    fun earnedCount(): Int = Badges.CATALOG.count { isEarned(it.key) }
}

/**
 * Evaluates the current state and awards any newly-earned badges. Idempotent and cheap - safe to
 * call after every scoring action. Uses lifetime-ish signals from the local stores.
 */
object Achievements {
    fun check(context: Context) {
        val store = LocalStore(context)
        val badges = BadgeStore(context)

        if (store.moods().isNotEmpty()) badges.award("first_checkin")
        if (store.memory().isNotEmpty()) badges.award("reflected")
        if (store.streak() >= 7) badges.award("streak7")
        if (store.streak() >= 30) badges.award("streak30")
        if (SeasonStore(context).unlockedKeys().size > 1) badges.award("sky")

        val ws = Leaderboard.weekStartDate().toEpochDay()
        if (LeaderboardStore(context).actionCountThisWeek(EPAction.FOCUS.name, ws) > 0) badges.award("focused")
    }
}
