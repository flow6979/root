package com.rootapp

import com.rootapp.data.WellbeingScore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WellbeingScoreTest {

    // ---- sub-scores ----

    @Test fun `mood sub is null for empty and mapped otherwise`() {
        assertNull(WellbeingScore.moodSub(emptyList()))
        assertEquals(100, WellbeingScore.moodSub(listOf(4, 4)))
        assertEquals(0, WellbeingScore.moodSub(listOf(0, 0)))
        assertEquals(50, WellbeingScore.moodSub(listOf(2, 2)))
    }

    @Test fun `screen sub rewards less usage`() {
        assertNull(WellbeingScore.screenSub(null))
        assertEquals(100, WellbeingScore.screenSub(30))
        assertEquals(0, WellbeingScore.screenSub(400))
        val mid = WellbeingScore.screenSub(180)!!
        assertTrue(mid in 1..99)
        // more screen time -> lower score
        assertTrue(WellbeingScore.screenSub(120)!! > WellbeingScore.screenSub(240)!!)
    }

    @Test fun `consistency saturates at target`() {
        assertNull(WellbeingScore.consistencySub(0))
        assertEquals(100, WellbeingScore.consistencySub(WellbeingScore.STREAK_TARGET))
        assertEquals(100, WellbeingScore.consistencySub(WellbeingScore.STREAK_TARGET + 5))
        assertTrue(WellbeingScore.consistencySub(1)!! < 100)
    }

    @Test fun `reflection blends count and recency`() {
        assertNull(WellbeingScore.reflectionSub(0, null))
        // full engagement + fresh -> 100
        assertEquals(100, WellbeingScore.reflectionSub(WellbeingScore.REFLECTION_TARGET, 0))
        // fresh but only one note -> below full
        assertTrue(WellbeingScore.reflectionSub(1, 0)!! < 100)
        // many notes but stale -> engagement half only
        assertTrue(WellbeingScore.reflectionSub(WellbeingScore.REFLECTION_TARGET, WellbeingScore.REFLECTION_STALE_DAYS)!! in 40..60)
    }

    @Test fun `eating sub delegates to meal health`() {
        assertNull(WellbeingScore.eatingSub(emptyList()))
        assertTrue(WellbeingScore.eatingSub(listOf("salad", "grilled chicken"))!! >= 75)
        assertTrue(WellbeingScore.eatingSub(listOf("pizza", "coke"))!! <= 40)
    }

    // ---- compute ----

    @Test fun `no data yields null overall and empty breakdown`() {
        val r = WellbeingScore.compute(WellbeingScore.Inputs())
        assertNull(r.overall)
        assertTrue(r.components.isEmpty())
    }

    @Test fun `all-good inputs score high`() {
        val r = WellbeingScore.compute(
            WellbeingScore.Inputs(
                recentMoods = listOf(4, 4, 4),
                foodLabels = listOf("salad", "grilled chicken", "dal"),
                screenDailyAvgMin = 40,
                streakDays = 10,
                reflectionCount = 6,
                daysSinceLastReflection = 0,
            ),
        )
        assertTrue("expected high, got ${r.overall}", r.overall!! >= 85)
        assertEquals(5, r.components.size)
    }

    @Test fun `screen-heavy drags the score down`() {
        val base = WellbeingScore.Inputs(
            recentMoods = listOf(3, 3),
            foodLabels = listOf("rice", "sandwich"),
            streakDays = 3,
        )
        val light = WellbeingScore.compute(base.copy(screenDailyAvgMin = 30)).overall!!
        val heavy = WellbeingScore.compute(base.copy(screenDailyAvgMin = 500)).overall!!
        assertTrue("heavy ($heavy) should be < light ($light)", heavy < light)
    }

    @Test fun `missing signals are renormalised not punished`() {
        // Only mood present -> overall equals the mood sub-score (single weight -> 1.0).
        val moodOnly = WellbeingScore.compute(WellbeingScore.Inputs(recentMoods = listOf(4, 4)))
        assertEquals(1, moodOnly.components.size)
        assertEquals(100, moodOnly.overall)
        assertEquals(1.0, moodOnly.components.first().weight, 1e-9)
    }

    @Test fun `component weights sum to one over present signals`() {
        val r = WellbeingScore.compute(
            WellbeingScore.Inputs(
                recentMoods = listOf(2),
                foodLabels = listOf("pizza"),
                screenDailyAvgMin = 120,
            ),
        )
        assertEquals(3, r.components.size)
        assertEquals(1.0, r.components.sumOf { it.weight }, 1e-9)
    }

    @Test fun `all-junk all-bad inputs score low`() {
        val r = WellbeingScore.compute(
            WellbeingScore.Inputs(
                recentMoods = listOf(0, 0),
                foodLabels = listOf("pizza", "coke", "fries"),
                screenDailyAvgMin = 500,
                streakDays = 0,
                reflectionCount = 0,
            ),
        )
        assertTrue("expected low, got ${r.overall}", r.overall!! <= 25)
    }
}
