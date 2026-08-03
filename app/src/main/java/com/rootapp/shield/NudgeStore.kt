package com.rootapp.shield

import android.content.Context

/**
 * On-device store for the adaptive-nudge learning: a per-hour count of overuse nudges (the
 * person's risk-by-hour signal) plus how many nudges were shown vs heeded (left the app soon
 * after). Feeds [AdaptiveNudge]. All local; nothing leaves the phone.
 */
class NudgeStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("root_nudge_learn", Context.MODE_PRIVATE)

    fun riskByHour(): IntArray {
        val arr = IntArray(24)
        prefs.getString(RISK, null)?.split(",")?.forEachIndexed { i, v ->
            if (i < 24) arr[i] = v.toIntOrNull() ?: 0
        }
        return arr
    }

    /** Record that a nudge fired at [hour]: bumps that hour's risk and the shown counter. */
    fun recordNudge(hour: Int) {
        val arr = riskByHour()
        if (hour in 0..23) arr[hour] = arr[hour] + 1
        prefs.edit()
            .putString(RISK, arr.joinToString(","))
            .putInt(SHOWN, shown() + 1)
            .apply()
    }

    /** Record that the person left the app shortly after a nudge (it worked). */
    fun recordHeeded() = prefs.edit().putInt(HEEDED, heeded() + 1).apply()

    fun shown(): Int = prefs.getInt(SHOWN, 0)
    fun heeded(): Int = prefs.getInt(HEEDED, 0)

    companion object {
        private const val RISK = "risk_by_hour"
        private const val SHOWN = "nudges_shown"
        private const val HEEDED = "nudges_heeded"
    }
}
