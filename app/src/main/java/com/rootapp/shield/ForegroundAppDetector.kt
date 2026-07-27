package com.rootapp.shield

/** A minimal, framework-free view of a usage event, so detection logic is unit-testable. */
data class UsageEventLite(
    val packageName: String,
    val timeStamp: Long,
    val toForeground: Boolean,
)

object ForegroundAppDetector {
    /** The package of the most recent "moved to foreground" event, or null if none. */
    fun latestForegroundPackage(events: List<UsageEventLite>): String? =
        events.filter { it.toForeground }.maxByOrNull { it.timeStamp }?.packageName
}
