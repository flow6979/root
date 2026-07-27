package com.rootapp

import com.rootapp.shield.ForegroundAppDetector
import com.rootapp.shield.InterruptPolicy
import com.rootapp.shield.UsageEventLite
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ShieldLogicTest {

    // ---- ForegroundAppDetector ----
    @Test fun `picks the most recent foreground package`() {
        val events = listOf(
            UsageEventLite("com.a", 100, true),
            UsageEventLite("com.b", 300, true),
            UsageEventLite("com.c", 200, true),
            UsageEventLite("com.b", 400, false), // background, ignored
        )
        assertEquals("com.b", ForegroundAppDetector.latestForegroundPackage(events))
    }

    @Test fun `null when no foreground events`() {
        assertNull(ForegroundAppDetector.latestForegroundPackage(emptyList()))
        assertNull(ForegroundAppDetector.latestForegroundPackage(
            listOf(UsageEventLite("com.a", 1, false)),
        ))
    }

    // ---- InterruptPolicy ----
    private val monitored = setOf("com.instagram.android", "com.google.android.youtube")

    @Test fun `interrupts a monitored app on first sight`() {
        assertTrue(InterruptPolicy.shouldInterrupt("com.instagram.android", monitored, 1_000, null))
    }

    @Test fun `ignores non-monitored and null`() {
        assertFalse(InterruptPolicy.shouldInterrupt("com.rootapp", monitored, 1_000, null))
        assertFalse(InterruptPolicy.shouldInterrupt(null, monitored, 1_000, null))
    }

    @Test fun `respects cooldown so it does not nag`() {
        val now = 100_000L
        // shown 10s ago, cooldown 30s -> no
        assertFalse(InterruptPolicy.shouldInterrupt("com.instagram.android", monitored, now, now - 10_000))
        // shown 31s ago -> yes
        assertTrue(InterruptPolicy.shouldInterrupt("com.instagram.android", monitored, now, now - 31_000))
    }
}
