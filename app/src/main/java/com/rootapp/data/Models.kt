package com.rootapp.data

import kotlinx.serialization.Serializable

/** A daily mood check-in. mood is 0..4 (sad -> energised). epochDay = LocalDate.toEpochDay(). */
@Serializable
data class MoodEntry(val epochDay: Long, val mood: Int, val timestamp: Long)

/** A logged meal. healthy = user/AI flagged it as a good choice. */
@Serializable
data class FoodEntry(val timestamp: Long, val label: String, val healthy: Boolean)

/**
 * A short structured takeaway distilled from one reflection session: the user's main [concern]
 * and the single [intention] they landed on. Kept tiny (both trimmed) so Home can surface it as a
 * gentle, actionable nudge. [timestamp] is when the session ended.
 */
@Serializable
data class SessionTakeaway(val timestamp: Long, val concern: String, val intention: String)
