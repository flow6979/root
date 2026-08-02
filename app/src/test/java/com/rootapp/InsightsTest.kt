package com.rootapp

import com.rootapp.data.Insights
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InsightsTest {

    // ---- individual card signals ----

    @Test fun `intention card only when an intention exists`() {
        assertNull(Insights.intentionCard(null))
        assertNull(Insights.intentionCard("   "))
        val c = Insights.intentionCard("sleep before midnight")
        assertNotNull(c)
        assertTrue(c!!.body.contains("sleep before midnight"))
    }

    @Test fun `junk streak fires on a trailing run of heavy meals`() {
        // two junk meals at the end -> fires
        val c = Insights.junkStreakCard(listOf("salad", "pizza", "coke"))
        assertNotNull(c)
        assertTrue(c!!.body.contains("2"))
    }

    @Test fun `junk streak skipped when the latest meal is healthy`() {
        // trailing meal is healthy, so the run is 0
        assertNull(Insights.junkStreakCard(listOf("pizza", "coke", "salad")))
    }

    @Test fun `junk streak skipped when nothing logged`() {
        assertNull(Insights.junkStreakCard(emptyList()))
    }

    @Test fun `screen card fires only above the heavy threshold and names the app`() {
        assertNull(Insights.screenCard(null, "Instagram", 400))
        assertNull(Insights.screenCard(90, "Instagram", 400))
        val c = Insights.screenCard(220, "Instagram", 400)
        assertNotNull(c)
        assertTrue(c!!.body.contains("Instagram"))
        assertTrue(c.suggestion.contains("Instagram"))
    }

    @Test fun `screen card works without a top app label`() {
        val c = Insights.screenCard(220, null, 0)
        assertNotNull(c)
        assertTrue(c!!.body.contains("phone"))
    }

    @Test fun `mood card fires on a clear dip and skips a steady trend`() {
        // earlier high, later low -> dip
        assertNotNull(Insights.moodCard(listOf(4, 4, 1, 1)))
        // steady -> no card
        assertNull(Insights.moodCard(listOf(3, 3, 3, 3)))
        // too few points -> no card
        assertNull(Insights.moodCard(listOf(4, 1)))
    }

    // ---- build orchestration ----

    @Test fun `empty inputs produce no cards`() {
        assertTrue(Insights.build(Insights.Inputs()).isEmpty())
    }

    @Test fun `build caps at MAX_CARDS`() {
        val cards = Insights.build(
            Insights.Inputs(
                screenDailyAvgMin = 300,
                topAppLabel = "Instagram",
                topAppMinutes = 500,
                foodLabels = listOf("pizza", "coke"),
                recentMoods = listOf(4, 4, 1, 1),
                latestIntention = "sleep earlier",
            ),
        )
        assertTrue(cards.size <= Insights.MAX_CARDS)
        // intention is highest priority, so it leads
        assertEquals("Your last intention", cards.first().title)
    }

    @Test fun `build only includes cards whose signal is present`() {
        val cards = Insights.build(Insights.Inputs(latestIntention = "drink more water"))
        assertEquals(1, cards.size)
        assertEquals("Your last intention", cards.first().title)
    }

    // ---- takeaway parsing ----

    @Test fun `parses labelled concern and intention`() {
        val t = Insights.parseTakeaway("Concern: doomscrolling late\nIntention: sleep before midnight")
        assertEquals("doomscrolling late", t.concern)
        assertEquals("sleep before midnight", t.intention)
    }

    @Test fun `parses a single pipe separated line`() {
        val t = Insights.parseTakeaway("feeling burned out | take one real break")
        assertEquals("feeling burned out", t.concern)
        assertEquals("take one real break", t.intention)
    }

    @Test fun `blank reply yields blank takeaway`() {
        val t = Insights.parseTakeaway("   ")
        assertEquals("", t.concern)
        assertEquals("", t.intention)
    }

    @Test fun `takeaway fields are trimmed capped and dequoted`() {
        val long = "x".repeat(200)
        val t = Insights.parseTakeaway("Concern: \"$long\"\nIntention: \"do it.\"")
        assertTrue(t.concern.length <= 120)
        assertEquals("do it", t.intention)
    }
}
