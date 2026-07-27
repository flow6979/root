package com.rootapp.data

import android.content.Context
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * Local-first persistence (SharedPreferences + JSON). Survives app restarts with no
 * network. When Supabase is wired (see docs/SUPABASE.md), this becomes the offline
 * cache that syncs to the cloud.
 */
class LocalStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("root_store", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    // ---- mood + streak ----
    fun moods(): List<MoodEntry> = decode(prefs.getString(MOODS, null), MoodEntry.serializer())

    fun todaysMood(todayEpochDay: Long): Int? =
        moods().lastOrNull { it.epochDay == todayEpochDay }?.mood

    /** Records a mood for today, updates the streak, returns the new streak. */
    fun addMood(mood: Int, todayEpochDay: Long, now: Long): Int {
        val list = moods().toMutableList().apply {
            add(MoodEntry(todayEpochDay, mood, now))
        }
        prefs.edit().putString(MOODS, encode(list, MoodEntry.serializer())).apply()
        val newStreak = Streak.update(streak(), lastCheckInDay(), todayEpochDay)
        prefs.edit().putInt(STREAK, newStreak).putLong(LAST_DAY, todayEpochDay).apply()
        return newStreak
    }

    fun streak(): Int = prefs.getInt(STREAK, 0)
    private fun lastCheckInDay(): Long? = if (prefs.contains(LAST_DAY)) prefs.getLong(LAST_DAY, 0) else null

    // ---- food log ----
    fun foods(): List<FoodEntry> = decode(prefs.getString(FOODS, null), FoodEntry.serializer())

    fun addFood(label: String, healthy: Boolean, now: Long) {
        val list = foods().toMutableList().apply { add(FoodEntry(now, label, healthy)) }
        prefs.edit().putString(FOODS, encode(list, FoodEntry.serializer())).apply()
    }

    fun removeFood(timestamp: Long) {
        val list = foods().filterNot { it.timestamp == timestamp }
        prefs.edit().putString(FOODS, encode(list, FoodEntry.serializer())).apply()
    }

    // ---- reflection memory (what the friend remembers across sessions) ----
    fun memory(): List<String> {
        val s = prefs.getString(MEMORY, null)
        return if (s.isNullOrBlank()) emptyList()
        else runCatching { json.decodeFromString(ListSerializer(String.serializer()), s) }.getOrDefault(emptyList())
    }

    /** Remember a thing the user said; keeps only the most recent [cap]. */
    fun remember(text: String, cap: Int = 30) {
        val clean = text.trim()
        if (clean.isEmpty()) return
        val list = (memory() + clean).takeLast(cap)
        prefs.edit().putString(MEMORY, json.encodeToString(ListSerializer(String.serializer()), list)).apply()
    }

    fun recentMemory(limit: Int = 6): List<String> = memory().takeLast(limit)

    // ---- interrupt stats ----
    fun incInterruptShown() = prefs.edit().putInt(I_SHOWN, prefs.getInt(I_SHOWN, 0) + 1).apply()
    fun incInterruptPaused() = prefs.edit().putInt(I_PAUSED, prefs.getInt(I_PAUSED, 0) + 1).apply()
    fun interruptShown(): Int = prefs.getInt(I_SHOWN, 0)
    fun interruptPaused(): Int = prefs.getInt(I_PAUSED, 0)

    private fun <T> encode(list: List<T>, ser: kotlinx.serialization.KSerializer<T>): String =
        json.encodeToString(ListSerializer(ser), list)

    private fun <T> decode(s: String?, ser: kotlinx.serialization.KSerializer<T>): List<T> =
        if (s.isNullOrBlank()) emptyList() else runCatching {
            json.decodeFromString(ListSerializer(ser), s)
        }.getOrDefault(emptyList())

    companion object {
        private const val MOODS = "moods"
        private const val FOODS = "foods"
        private const val STREAK = "streak"
        private const val LAST_DAY = "last_checkin_day"
        private const val I_SHOWN = "interrupt_shown"
        private const val I_PAUSED = "interrupt_paused"
        private const val MEMORY = "memory_msgs"
    }
}
