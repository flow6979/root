package com.rootapp

import com.rootapp.data.MealHealth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MealHealthTest {
    @Test fun `junk scores low`() {
        assertTrue(MealHealth.scoreMeal("pizza").score <= 30)
        assertTrue(MealHealth.scoreMeal("french fries").score <= 30)
        assertTrue(MealHealth.scoreMeal("chocolate cake").score <= 30)
        assertTrue(MealHealth.scoreMeal("Coke").score <= 30)
    }

    @Test fun `healthy scores high`() {
        assertTrue(MealHealth.scoreMeal("green salad").score >= 75)
        assertTrue(MealHealth.scoreMeal("grilled chicken").score >= 75)
        assertTrue(MealHealth.scoreMeal("dal").score >= 75)
        assertTrue(MealHealth.scoreMeal("oats").score >= 75)
    }

    @Test fun `unknown food gets sensible default`() {
        val m = MealHealth.scoreMeal("something obscure")
        assertEquals(55, m.score)
        assertEquals("mixed - logged as a general meal", m.reason)
    }

    @Test fun `reason is non-empty`() {
        assertTrue(MealHealth.scoreMeal("pizza").reason.isNotBlank())
        assertTrue(MealHealth.scoreMeal("salad").reason.isNotBlank())
    }

    @Test fun `aggregate is null for empty`() {
        assertNull(MealHealth.aggregate(emptyList()))
    }

    @Test fun `aggregate of identical scores is that score`() {
        assertEquals(80, MealHealth.aggregate(listOf(80, 80, 80)))
    }

    @Test fun `aggregate weights recent meals more`() {
        // oldest-first: two low then two high -> recency bias pulls above the plain mean (50)
        val recencyBiased = MealHealth.aggregate(listOf(0, 0, 100, 100))!!
        assertTrue("expected >50, got $recencyBiased", recencyBiased > 50)
        // reverse order should be symmetric and below 50
        val reversed = MealHealth.aggregate(listOf(100, 100, 0, 0))!!
        assertTrue("expected <50, got $reversed", reversed < 50)
    }

    @Test fun `aggregate clamps out-of-range inputs`() {
        val v = MealHealth.aggregate(listOf(-50, 200))!!
        assertTrue(v in 0..100)
    }

    @Test fun `parseEnrichment maps lines back to inputs and clamps`() {
        val raw = """
            quinoa bowl | 85 | whole grain and protein
            mystery drink | 150 | sugary
            garbage line without pipes
        """.trimIndent()
        val out = MealHealth.parseEnrichment(raw, listOf("quinoa bowl", "mystery drink"))
        assertEquals(85, out["quinoa bowl"]?.score)
        assertEquals(100, out["mystery drink"]?.score) // clamped from 150
    }
}
