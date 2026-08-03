package com.rootapp.shield

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * A running focus (Pomodoro) block. While active, the watcher fully pauses time-sink apps.
 * Backed by SharedPreferences (via [init] from RootApp) so a process death doesn't silently
 * unblock apps mid-session; the end time is restored on next launch.
 */
object FocusSession {
    private var prefs: SharedPreferences? = null
    private val _endAt = MutableStateFlow(0L)
    val endAt: StateFlow<Long> = _endAt.asStateFlow()

    fun init(ctx: Context) {
        val p = ctx.applicationContext.getSharedPreferences("focus", Context.MODE_PRIVATE)
        prefs = p
        _endAt.value = p.getLong("end", 0L)
    }

    val active: Boolean get() = _endAt.value > System.currentTimeMillis()

    fun remainingMin(): Int =
        ((_endAt.value - System.currentTimeMillis() + 59_999L) / 60000L).toInt().coerceAtLeast(0)

    fun start(minutes: Int) {
        val end = System.currentTimeMillis() + minutes * 60000L
        _endAt.value = end
        prefs?.edit()?.putLong("end", end)?.apply()
    }

    fun end() {
        _endAt.value = 0L
        prefs?.edit()?.putLong("end", 0L)?.apply()
    }
}
