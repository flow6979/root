package com.rootapp

import com.rootapp.shield.AdaptiveNudge
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptiveNudgeTest {

    private fun risk(vararg pairs: Pair<Int, Int>): IntArray {
        val a = IntArray(24)
        pairs.forEach { (h, v) -> a[h] = v }
        return a
    }

    @Test fun `no history falls back to base thresholds`() {
        val empty = IntArray(24)
        assertEquals(15, AdaptiveNudge.firstNudgeMin(23, empty, base = 15))
        assertEquals(20, AdaptiveNudge.repeatNudgeMin(heeded = 0, shown = 0, base = 20))
        assertNull(AdaptiveNudge.peakHour(empty))
    }

    @Test fun `nudges earlier in the peak hour, later in a calm hour`() {
        val r = risk(23 to 10, 9 to 1) // 11pm very risky, 9am barely
        val peakMin = AdaptiveNudge.firstNudgeMin(23, r, tight = 7, loose = 25)
        val calmMin = AdaptiveNudge.firstNudgeMin(9, r, tight = 7, loose = 25)
        assertEquals(7, peakMin)
        assertTrue("calm hour should nudge later than peak", calmMin > peakMin)
    }

    @Test fun `backs off when nudges are ignored, stays tight when heeded`() {
        val ignored = AdaptiveNudge.repeatNudgeMin(heeded = 0, shown = 10, min = 15, max = 40)
        val working = AdaptiveNudge.repeatNudgeMin(heeded = 10, shown = 10, min = 15, max = 40)
        assertEquals(40, ignored)
        assertEquals(15, working)
        assertTrue(ignored > working)
    }

    @Test fun `peakHour is the busiest hour`() {
        assertEquals(23, AdaptiveNudge.peakHour(risk(8 to 3, 23 to 9, 12 to 5)))
    }
}
