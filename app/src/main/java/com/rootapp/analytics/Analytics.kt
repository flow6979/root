package com.rootapp.analytics

import android.util.Log

/**
 * Product analytics abstraction. IMPORTANT (docs/ANALYTICS.md): only aggregate,
 * non-content events - never reflection text or logged mood *content*.
 * Default impl logs to Logcat; swap in a PostHog impl behind a key for production.
 */
interface Analytics {
    fun track(event: String, props: Map<String, Any?> = emptyMap())
}

class LogcatAnalytics : Analytics {
    override fun track(event: String, props: Map<String, Any?>) {
        Log.d("Analytics", if (props.isEmpty()) event else "$event $props")
    }
}

/** Captures events for tests. */
class FakeAnalytics : Analytics {
    val events = mutableListOf<String>()
    override fun track(event: String, props: Map<String, Any?>) { events += event }
}

/** Global, swappable holder so any layer (incl. the service) can emit events. */
object Track {
    @Volatile var impl: Analytics = LogcatAnalytics()
    fun event(name: String, props: Map<String, Any?> = emptyMap()) = impl.track(name, props)
}

/** Canonical event names - the PMF signals (see docs/ANALYTICS.md). */
object Events {
    const val APP_OPEN = "app_open"
    const val MOOD_LOGGED = "mood_logged"
    const val REFLECTION_STARTED = "reflection_started"
    const val REFLECTION_MESSAGE_SENT = "reflection_message_sent"
    const val INTERRUPT_SHOWN = "interrupt_shown"
    const val INTERRUPT_PAUSED = "interrupt_paused"
    const val INTERRUPT_OPENED_ANYWAY = "interrupt_opened_anyway"
    const val STORY_READ = "story_read"
    const val STORIES_FINISHED = "stories_finished_for_day"
    const val FOOD_LOGGED = "food_logged"
    const val PROTECTION_ENABLED = "protection_enabled"
}
