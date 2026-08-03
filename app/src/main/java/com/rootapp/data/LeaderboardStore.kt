package com.rootapp.data

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/** A healthy action that earns Effort Points, with a per-day cap so it can't be farmed. */
enum class EPAction(val points: Int, val dailyCap: Int) {
    MOOD(10, 1),
    REFLECTION(20, 2),
    FOCUS(15, 3),
    NUDGE_HEEDED(5, 6),
    MEAL(5, 3),
    UNDER_BUDGET(25, 1),
    WIND_DOWN(15, 1),
}

@Serializable
data class DayEP(val ep: Int = 0, val actions: Map<String, Int> = emptyMap())

/**
 * On-device ledger of Effort Points earned per day, with per-action daily caps. The weekly total
 * (Monday-anchored) is what gets submitted to the leaderboard. Local-first: even offline, points
 * accrue and sync later. Prunes anything older than three weeks.
 */
class LeaderboardStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("root_leaderboard", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = MapSerializer(String.serializer(), DayEP.serializer())

    private fun load(): MutableMap<String, DayEP> {
        val s = prefs.getString(DAYS, null)
        return if (s.isNullOrBlank()) mutableMapOf()
        else runCatching { json.decodeFromString(serializer, s).toMutableMap() }.getOrDefault(mutableMapOf())
    }

    private fun save(map: Map<String, DayEP>) {
        prefs.edit().putString(DAYS, json.encodeToString(serializer, map)).apply()
    }

    /** Award [action] for [today] (epoch-day) if under its daily cap. Returns points actually added. */
    fun record(action: EPAction, today: Long): Int {
        val map = load()
        val key = today.toString()
        val rec = map[key] ?: DayEP()
        val count = rec.actions[action.name] ?: 0
        if (count >= action.dailyCap) return 0
        map[key] = rec.copy(
            ep = rec.ep + action.points,
            actions = rec.actions.toMutableMap().apply { put(action.name, count + 1) },
        )
        val cutoff = today - 21
        save(map.filterKeys { (it.toLongOrNull() ?: 0L) >= cutoff })
        return action.points
    }

    /** Total EP across the seven days starting at [weekStart] (epoch-day of the Monday). */
    fun weeklyEffort(weekStart: Long): Int {
        val map = load()
        var sum = 0
        for (d in weekStart until weekStart + 7) sum += map[d.toString()]?.ep ?: 0
        return sum
    }

    companion object {
        private const val DAYS = "days"
    }
}
