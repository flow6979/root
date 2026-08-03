package com.rootapp

import com.rootapp.shield.NudgeCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NudgeCalculatorTest {

    @Test fun `late night adds a sleep estimate`() {
        val n = NudgeCalculator.compute("Instagram", sessionMin = 30, todayMin = 60, hour = 23)
        assertTrue(n.sleep != null)
        assertTrue(n.sleep!!.contains("min"))
        assertTrue(n.consequence.lowercase().contains("sleep"))
    }

    @Test fun `daytime has no sleep line`() {
        val n = NudgeCalculator.compute("YouTube", sessionMin = 20, todayMin = 40, hour = 14)
        assertNull(n.sleep)
    }

    @Test fun `projection uses the larger of today and session`() {
        // 70 min today -> 70*7/60 = 8.16 -> rounds to 8.0 hours
        val n = NudgeCalculator.compute("TikTok", sessionMin = 10, todayMin = 70, hour = 12)
        assertTrue(n.projection.contains("8 hours") || n.projection.contains("8.0"))
        assertTrue(n.title.contains("TikTok"))
    }

    @Test fun `body includes the AI line first when provided`() {
        val n = NudgeCalculator.compute("Instagram", 15, 15, 13)
        val body = n.body("Set it down for now.")
        assertTrue(body.startsWith("Set it down for now."))
        assertTrue(body.contains("At this pace"))
    }

    @Test fun `body works without an AI line`() {
        val n = NudgeCalculator.compute("Instagram", 15, 15, 13)
        assertEquals(false, n.body(null).isBlank())
    }
}
