package com.rootapp.ui.theme

/**
 * The five sky bands that drive the whole UI colour (see docs/DECISIONS.md D10).
 * Kept as pure Kotlin (no Android/Compose deps) so it is unit-testable on the JVM.
 */
enum class TimeOfDay { MIDNIGHT, DAWN, DAY, DUSK, NIGHT }

object Sky {

    /**
     * Map a 24-hour clock hour to its sky band.
     *  00–04 midnight · 05–07 dawn · 08–16 day · 17–19 dusk · 20–23 night
     * Negative or >23 inputs are normalised so callers can pass raw values safely.
     */
    fun fromHour(hour: Int): TimeOfDay {
        val h = ((hour % 24) + 24) % 24
        return when (h) {
            in 0..4 -> TimeOfDay.MIDNIGHT
            in 5..7 -> TimeOfDay.DAWN
            in 8..16 -> TimeOfDay.DAY
            in 17..19 -> TimeOfDay.DUSK
            else -> TimeOfDay.NIGHT
        }
    }

    /** The companion orb renders as a moon (with phases) at night; otherwise a sun. */
    fun isMoon(t: TimeOfDay): Boolean = t == TimeOfDay.NIGHT || t == TimeOfDay.MIDNIGHT
}
