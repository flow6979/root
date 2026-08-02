package com.rootapp

import com.rootapp.shield.ShieldInsights
import com.rootapp.shield.ShieldInsights.Interval
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ShieldInsightsTest {

    private val dayStart = 0L // pretend local midnight is epoch 0 for clean arithmetic
    private val hour = 60L * 60_000L
    private fun at(h: Long) = dayStart + h * hour // millis at wall-clock hour h

    // ---- lateNightMinutes ----
    @Test fun `counts only the slice after 11pm`() {
        // 10:30pm -> 11:30pm : only the 30 min after 11pm should count.
        val intervals = listOf(Interval(at(22) + 30 * 60_000L, at(23) + 30 * 60_000L))
        assertEquals(30, ShieldInsights.lateNightMinutes(intervals, dayStart))
    }

    @Test fun `ignores intervals entirely before the cutoff`() {
        val intervals = listOf(Interval(at(9), at(10)))
        assertEquals(0, ShieldInsights.lateNightMinutes(intervals, dayStart))
    }

    @Test fun `sums multiple late-night intervals and ignores bad ones`() {
        val intervals = listOf(
            Interval(at(23), at(23) + 20 * 60_000L), // 20m after 11pm
            Interval(at(23) + 40 * 60_000L, at(23) + 55 * 60_000L), // 15m
            Interval(at(5), at(4)), // negative-length, ignored
        )
        assertEquals(35, ShieldInsights.lateNightMinutes(intervals, dayStart))
    }

    @Test fun `respects a custom cutoff hour`() {
        val intervals = listOf(Interval(at(20), at(22))) // 8pm-10pm
        // cutoff at 9pm -> only the last hour counts
        assertEquals(60, ShieldInsights.lateNightMinutes(intervals, dayStart, cutoffHour = 21))
    }

    // ---- weekOverWeekPercent ----
    @Test fun `week over week goes up and down`() {
        assertEquals(20, ShieldInsights.weekOverWeekPercent(1200, 1000))
        assertEquals(-10, ShieldInsights.weekOverWeekPercent(900, 1000))
        assertEquals(0, ShieldInsights.weekOverWeekPercent(1000, 1000))
    }

    @Test fun `week over week is null when last week unknown`() {
        assertNull(ShieldInsights.weekOverWeekPercent(1200, 0))
        assertNull(ShieldInsights.weekOverWeekPercent(1200, -5))
    }

    // ---- reclaimFraming ----
    @Test fun `no reclaim framing for a light week`() {
        assertNull(ShieldInsights.reclaimFraming(60))
        assertNull(ShieldInsights.reclaimFraming(89))
    }

    @Test fun `reclaim framing gives hours a week for heavy use`() {
        // 180m/day -> trim 60/day -> 420m/week -> 7h a week
        val f = ShieldInsights.reclaimFraming(180)!!
        assertTrue(f.contains("7h a week"))
    }

    // ---- trendPhrase ----
    @Test fun `trend phrase formats sign`() {
        assertEquals("+18% vs last week.", ShieldInsights.trendPhrase(18))
        assertEquals("-9% vs last week.", ShieldInsights.trendPhrase(-9))
        assertEquals("About the same as last week.", ShieldInsights.trendPhrase(0))
        assertNull(ShieldInsights.trendPhrase(null))
    }

    // ---- heroLines ----
    @Test fun `hero leads with late-night when significant`() {
        val insight = ShieldInsights.Insight(
            lateNightMinutes = 95,
            weekOverWeekPercent = 12,
            topSinkLabel = "Instagram",
            topSinkMinutes = 320,
            dailyAverageMinutes = 200,
        )
        val lines = ShieldInsights.heroLines(insight)
        assertEquals(2, lines.size)
        assertTrue(lines[0].contains("after 11pm"))
        assertTrue(lines[1].contains("Instagram"))
    }

    @Test fun `hero falls back to trend when late-night is small`() {
        val insight = ShieldInsights.Insight(
            lateNightMinutes = 5,
            weekOverWeekPercent = 18,
            topSinkLabel = "YouTube",
            topSinkMinutes = 200,
            dailyAverageMinutes = 120,
        )
        val lines = ShieldInsights.heroLines(insight)
        assertTrue(lines[0].contains("vs last week"))
    }

    @Test fun `hero is empty when there is nothing honest to say`() {
        val insight = ShieldInsights.Insight(
            lateNightMinutes = 0,
            weekOverWeekPercent = null,
            topSinkLabel = null,
            topSinkMinutes = 0,
            dailyAverageMinutes = 30, // light week, no reclaim
        )
        assertTrue(ShieldInsights.heroLines(insight).isEmpty())
    }

    @Test fun `hero never shows more than two lines`() {
        val insight = ShieldInsights.Insight(
            lateNightMinutes = 60,
            weekOverWeekPercent = 40,
            topSinkLabel = "TikTok",
            topSinkMinutes = 500,
            dailyAverageMinutes = 240,
        )
        assertTrue(ShieldInsights.heroLines(insight).size <= 2)
    }
}
