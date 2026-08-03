package com.rootapp.shield

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * A running focus (Pomodoro) block. While active, the watcher fully pauses time-sink apps.
 * In-memory (single process shared by UI + service); a process death simply ends the session.
 */
object FocusSession {
    private val _endAt = MutableStateFlow(0L)
    val endAt: StateFlow<Long> = _endAt.asStateFlow()

    val active: Boolean get() = _endAt.value > System.currentTimeMillis()

    fun remainingMin(): Int =
        ((_endAt.value - System.currentTimeMillis() + 59_999L) / 60000L).toInt().coerceAtLeast(0)

    fun start(minutes: Int) { _endAt.value = System.currentTimeMillis() + minutes * 60000L }
    fun end() { _endAt.value = 0L }
}
