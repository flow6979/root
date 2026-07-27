package com.rootapp

import com.rootapp.analytics.Events
import com.rootapp.analytics.FakeAnalytics
import com.rootapp.analytics.Track
import com.rootapp.data.Streak
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StreakAndAnalyticsTest {

    @Test fun `first check-in starts a streak of 1`() {
        assertEquals(1, Streak.update(prevStreak = 0, lastDay = null, today = 100))
    }

    @Test fun `consecutive day increments`() {
        assertEquals(4, Streak.update(prevStreak = 3, lastDay = 99, today = 100))
    }

    @Test fun `same day does not double-count`() {
        assertEquals(3, Streak.update(prevStreak = 3, lastDay = 100, today = 100))
    }

    @Test fun `a gap resets to 1`() {
        assertEquals(1, Streak.update(prevStreak = 9, lastDay = 90, today = 100))
    }

    @Test fun `Track routes events to the current impl`() {
        val fake = FakeAnalytics()
        Track.impl = fake
        Track.event(Events.MOOD_LOGGED)
        Track.event(Events.INTERRUPT_PAUSED)
        assertTrue(fake.events.containsAll(listOf("mood_logged", "interrupt_paused")))
    }
}
