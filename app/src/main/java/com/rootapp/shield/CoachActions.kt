package com.rootapp.shield

import android.content.Context
import com.rootapp.ai.CoachAction
import com.rootapp.data.LocalStore
import com.rootapp.data.SettingsStore

/**
 * Executes a [CoachAction] against the real app: starts focus, sets the budget, logs a meal, or
 * sets bedtime. Values are clamped to sane ranges so a bad model number can't do harm. Returns a
 * short, human confirmation to show back in the chat, or null if it couldn't act.
 */
object CoachActions {
    fun execute(context: Context, action: CoachAction): String? = when (action) {
        is CoachAction.StartFocus -> {
            val mins = action.minutes.coerceIn(5, 120)
            FocusSession.init(context)
            FocusSession.start(mins)
            "Focus started for $mins min. Time-sink apps are paused."
        }

        is CoachAction.SetBudget -> {
            val mins = action.minutes.coerceIn(15, 600)
            SettingsStore(context).screenBudgetMin = mins
            "Daily screen budget set to ${budgetLabel(mins)}."
        }

        is CoachAction.LogMeal -> {
            val food = action.food.trim().take(80)
            if (food.isBlank()) {
                null
            } else {
                LocalStore(context).addFood(food, action.healthy, System.currentTimeMillis())
                "Logged \"$food\" to Moments."
            }
        }

        is CoachAction.SetBedtime -> {
            val hour = action.hour.coerceIn(0, 23)
            val s = SettingsStore(context)
            s.bedtimeHour = hour
            s.windDownEnabled = true
            WindDown.apply(context)
            "Bedtime set to ${WindDown.bedtimeLabel(hour)}. I'll nudge you 15 min before."
        }
    }

    private fun budgetLabel(min: Int): String {
        val h = min / 60
        val m = min % 60
        return when {
            h > 0 && m > 0 -> "${h}h ${m}m"
            h > 0 -> "${h}h"
            else -> "${m}m"
        }
    }
}
