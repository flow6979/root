package com.rootapp.data

import android.content.Context

/** Small persisted settings (appearance, friend personality, onboarding state). */
class SettingsStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("root_settings", Context.MODE_PRIVATE)

    var minimalist: Boolean
        get() = prefs.getBoolean(MINIMALIST, false)
        set(v) = prefs.edit().putBoolean(MINIMALIST, v).apply()

    var personality: String
        get() = prefs.getString(PERSONALITY, "Gentle") ?: "Gentle"
        set(v) = prefs.edit().putString(PERSONALITY, v).apply()

    var onboarded: Boolean
        get() = prefs.getBoolean(ONBOARDED, false)
        set(v) = prefs.edit().putBoolean(ONBOARDED, v).apply()

    /** All features are free for everyone. Kept as a constant so feature gates stay unlocked. */
    val premium: Boolean get() = true

    /**
     * Optional user-supplied Gemini API key. When set, AI generation runs on the user's own
     * Gemini quota instead of Root's built-in free engine. Blank = use the default engine.
     */
    var geminiApiKey: String
        get() = prefs.getString(GEMINI_KEY, "") ?: ""
        set(v) = prefs.edit().putString(GEMINI_KEY, v.trim()).apply()

    var userName: String
        get() = prefs.getString(USER_NAME, "Vaibhav") ?: "Vaibhav"
        set(v) = prefs.edit().putString(USER_NAME, v).apply()

    /** Fire a gentle notification when a monitored app is used for a long stretch. Default on. */
    var overuseNudges: Boolean
        get() = prefs.getBoolean(OVERUSE_NUDGES, true)
        set(v) = prefs.edit().putBoolean(OVERUSE_NUDGES, v).apply()

    /** Nightly wind-down reminder. Opt-in. bedtimeHour is 24h (default 23 = 11pm). */
    var windDownEnabled: Boolean
        get() = prefs.getBoolean(WIND_DOWN, false)
        set(v) = prefs.edit().putBoolean(WIND_DOWN, v).apply()
    var bedtimeHour: Int
        get() = prefs.getInt(BEDTIME_HOUR, 23)
        set(v) = prefs.edit().putInt(BEDTIME_HOUR, v.coerceIn(19, 26) % 24).apply()

    /** Daily screen-time budget in minutes. 0 = off. */
    var screenBudgetMin: Int
        get() = prefs.getInt(SCREEN_BUDGET, 0)
        set(v) = prefs.edit().putInt(SCREEN_BUDGET, v.coerceIn(0, 720)).apply()

    /** Daily step goal. */
    var stepGoal: Int
        get() = prefs.getInt(STEP_GOAL, 6000)
        set(v) = prefs.edit().putInt(STEP_GOAL, v.coerceIn(1000, 30000)).apply()

    companion object {
        private const val MINIMALIST = "minimalist"
        private const val PERSONALITY = "personality"
        private const val ONBOARDED = "onboarded"
        private const val USER_NAME = "user_name"
        private const val GEMINI_KEY = "gemini_api_key"
        private const val OVERUSE_NUDGES = "overuse_nudges"
        private const val WIND_DOWN = "wind_down_enabled"
        private const val BEDTIME_HOUR = "bedtime_hour"
        private const val SCREEN_BUDGET = "screen_budget_min"
        private const val STEP_GOAL = "step_goal"
    }
}
