package com.rootapp.data

/**
 * A cosmetic skin for the animated sky, earned through season progress. DEFAULT is always
 * available; the rest unlock at rising Effort-Point totals within a season. Purely visual - no
 * pay-to-win, and it reinforces Root's signature sky.
 */
enum class SkyTheme(val key: String, val label: String, val unlockPoints: Int, val blurb: String) {
    DEFAULT("default", "Classic", 0, "The everyday sky"),
    GOLDEN_HOUR("golden", "Golden Hour", 100, "A warm golden wash"),
    STARFIELD("starfield", "Starfield", 250, "Extra stars, day and night"),
    METEOR("meteor", "Meteor Shower", 450, "Streaking meteors"),
    AURORA("aurora", "Aurora", 700, "Shimmering northern lights"),
    COSMOS("cosmos", "Cosmos", 900, "A galaxy of asteroids and planets"),
    ;

    companion object {
        fun fromKey(k: String?): SkyTheme = entries.firstOrNull { it.key == k } ?: DEFAULT
        fun all(): List<SkyTheme> = entries.toList()
    }
}
