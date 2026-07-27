package com.rootapp

import com.rootapp.data.Scores
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScoresTest {
    @Test fun `eating is percent healthy`() {
        assertEquals(100, Scores.eating(3, 0))
        assertEquals(50, Scores.eating(2, 2))
        assertNull(Scores.eating(0, 0))
    }

    @Test fun `mood maps 0-4 to 0-100`() {
        assertEquals(100, Scores.mood(listOf(4, 4)))
        assertEquals(50, Scores.mood(listOf(2)))
        assertNull(Scores.mood(emptyList()))
    }

    @Test fun `screen rewards less time`() {
        assertNull(Scores.screen(0))
        assertEquals(50, Scores.screen(120))
        assertTrue((Scores.screen(240) ?: 0) <= 1)
    }

    @Test fun `overall averages available subscores`() {
        assertEquals(60, Scores.overall(listOf(40, 80)))
        assertNull(Scores.overall(emptyList()))
    }
}
