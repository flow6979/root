package com.rootapp.shield

/**
 * Decides whether to show the interrupt. Pure + unit-testable.
 * Rule: interrupt only when a monitored app is foreground AND the per-app cooldown
 * has elapsed (so we nudge once, then leave them alone for a while - friend, not nag).
 */
object InterruptPolicy {
    const val DEFAULT_COOLDOWN_MS: Long = 30_000L

    fun shouldInterrupt(
        current: String?,
        monitored: Set<String>,
        now: Long,
        lastShownAt: Long?,
        cooldownMs: Long = DEFAULT_COOLDOWN_MS,
    ): Boolean {
        if (current == null || current !in monitored) return false
        if (lastShownAt == null) return true
        return now - lastShownAt >= cooldownMs
    }
}
