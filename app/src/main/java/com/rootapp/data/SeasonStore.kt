package com.rootapp.data

import android.content.Context
import java.time.LocalDate

/**
 * Seasons are fixed 4-week (28-day) blocks. Effort Points accumulate within the current season
 * and reset when it rolls; unlocked sky themes are permanent once earned. Everything is local -
 * the reward for showing up, no backend required.
 */
class SeasonStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("root_season", Context.MODE_PRIVATE)

    fun seasonId(today: Long = LocalDate.now().toEpochDay()): Int =
        (((today - EPOCH0).coerceAtLeast(0)) / 28L).toInt()

    fun seasonNumber(): Int = seasonId() + 1

    /** Reset the running total when a new season starts (keeps unlocks + selection). */
    private fun rollIfNeeded() {
        val cur = seasonId()
        if (prefs.getInt(SEASON, -1) != cur) {
            prefs.edit().putInt(SEASON, cur).putInt(POINTS, 0).apply()
        }
    }

    fun points(): Int {
        rollIfNeeded()
        return prefs.getInt(POINTS, 0)
    }

    /** Add [n] season points; returns any themes newly unlocked by crossing their threshold. */
    fun addPoints(n: Int): List<SkyTheme> {
        if (n <= 0) return emptyList()
        rollIfNeeded()
        val total = prefs.getInt(POINTS, 0) + n
        val unlocked = unlockedKeys().toMutableSet()
        val newly = mutableListOf<SkyTheme>()
        SkyTheme.all().forEach { t ->
            if (t.unlockPoints in 1..total && unlocked.add(t.key)) newly += t
        }
        prefs.edit().putInt(POINTS, total).putString(UNLOCKED, unlocked.joinToString(",")).apply()
        return newly
    }

    fun unlockedKeys(): Set<String> =
        (prefs.getString(UNLOCKED, "") ?: "").split(",").filter { it.isNotBlank() }.toSet() + "default"

    fun isUnlocked(theme: SkyTheme): Boolean = theme.unlockPoints == 0 || theme.key in unlockedKeys()

    fun selectedTheme(): SkyTheme = SkyTheme.fromKey(prefs.getString(SELECTED, "default"))
    fun setSelected(key: String) = prefs.edit().putString(SELECTED, key).apply()

    /** Days remaining in the current season (1..28). */
    fun daysLeft(today: Long = LocalDate.now().toEpochDay()): Int {
        val start = EPOCH0 + seasonId(today).toLong() * 28L
        return (28L - (today - start)).toInt().coerceIn(1, 28)
    }

    companion object {
        // A Monday, so seasons align with the Monday league resets.
        private val EPOCH0 = LocalDate.of(2026, 1, 5).toEpochDay()
        private const val SEASON = "season_id"
        private const val POINTS = "season_points"
        private const val UNLOCKED = "unlocked_themes"
        private const val SELECTED = "selected_theme"
    }
}
