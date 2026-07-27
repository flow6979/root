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

    /** Premium entitlement. Currently flipped by a test unlock; later set by Play Billing. */
    var premium: Boolean
        get() = prefs.getBoolean(PREMIUM, false)
        set(v) = prefs.edit().putBoolean(PREMIUM, v).apply()

    var userName: String
        get() = prefs.getString(USER_NAME, "Vaibhav") ?: "Vaibhav"
        set(v) = prefs.edit().putString(USER_NAME, v).apply()

    companion object {
        private const val MINIMALIST = "minimalist"
        private const val PERSONALITY = "personality"
        private const val ONBOARDED = "onboarded"
        private const val USER_NAME = "user_name"
        private const val PREMIUM = "premium"
    }
}
