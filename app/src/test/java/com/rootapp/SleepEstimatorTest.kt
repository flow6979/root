package com.rootapp

import com.rootapp.shield.ShieldInsights
import com.rootapp.shield.SleepEstimator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SleepEstimatorTest {

    private val h = 3_600_000L
    private val m = 60_000L

    @Test fun `finds the longest overnight gap as sleep`() {
        // Window 0..15h. Use at 0-1h and 8-9h -> biggest gap is 1h..8h = 7h.
        val ivs = listOf(ShieldInsights.Interval(0, 1 * h), ShieldInsights.Interval(8 * h, 9 * h))
        val n = SleepEstimator.estimate(ivs, 0, 15 * h)
        assertTrue(n != null)
        assertEquals(7 * 60, n!!.minutes)
        assertEquals(1 * h, n.startMs)
    }

    @Test fun `returns null when the biggest gap is too short`() {
        // Constant use, gaps under 3h.
        val ivs = (0 until 10).map { ShieldInsights.Interval(it * h, it * h + 40 * m) }
        assertNull(SleepEstimator.estimate(ivs, 0, 10 * h, minSleepMin = 180))
    }

    @Test fun `empty usage means the whole window is the gap`() {
        val n = SleepEstimator.estimate(emptyList(), 0, 8 * h)
        assertEquals(8 * 60, n!!.minutes)
    }

    @Test fun `consistency is high for similar bedtimes, low for scattered`() {
        val tight = SleepEstimator.consistency(listOf(1410, 1425, 1400, 30)) // ~23:30 and 00:30
        val loose = SleepEstimator.consistency(listOf(1320, 60, 1440 - 1, 240))
        assertTrue(tight!! > loose!!)
    }

    @Test fun `consistency needs at least two nights`() {
        assertNull(SleepEstimator.consistency(listOf(1410)))
    }
}
