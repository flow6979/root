package com.rootapp

import com.rootapp.data.MemoryRetriever
import com.rootapp.data.MemoryRetriever.Item
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryRetrieverTest {
    private val now = 1_000_000_000_000L
    private val day = 86_400_000L

    @Test fun `query ranks relevant snippets first`() {
        val items = listOf(
            Item("They were concerned about sleep and staying up late scrolling.", now - day),
            Item("They wanted to eat fewer takeaways and more home food.", now - day),
            Item("They felt anxious about exams.", now - day),
        )
        val out = MemoryRetriever.select(items, query = "I can't sleep, up late again on my phone", k = 2, now = now)
        assertTrue(out.isNotEmpty())
        assertTrue(out.first().text.contains("sleep"))
    }

    @Test fun `no query falls back to recency`() {
        val items = listOf(
            Item("older note", now - 5 * day),
            Item("newer note", now - 1 * day),
        )
        val out = MemoryRetriever.select(items, query = null, k = 1, now = now)
        assertEquals("newer note", out.first().text)
    }

    @Test fun `irrelevant query returns nothing`() {
        val items = listOf(Item("They wanted to walk more.", now - day))
        val out = MemoryRetriever.select(items, query = "purple giraffe telescope", k = 3, now = now)
        assertTrue(out.isEmpty())
    }

    @Test fun `dedupes identical snippets`() {
        val items = listOf(Item("same thing", now), Item("same thing", now - day))
        assertEquals(1, MemoryRetriever.select(items, query = null, k = 5, now = now).size)
    }
}
