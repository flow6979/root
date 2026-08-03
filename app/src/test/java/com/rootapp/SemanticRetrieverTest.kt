package com.rootapp

import com.rootapp.data.MemoryRetriever.Item
import com.rootapp.data.SemanticRetriever
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SemanticRetrieverTest {
    private val now = 1_000_000_000_000L
    private val day = 86_400_000L

    // Simple orthogonal-ish 3D vectors standing in for embeddings.
    private val sleepVec = floatArrayOf(1f, 0f, 0f)
    private val foodVec = floatArrayOf(0f, 1f, 0f)
    private val examVec = floatArrayOf(0f, 0f, 1f)

    @Test fun `cosine of identical vectors is 1`() {
        assertEquals(1.0, SemanticRetriever.cosine(sleepVec, sleepVec), 1e-6)
    }

    @Test fun `cosine of orthogonal vectors is 0`() {
        assertEquals(0.0, SemanticRetriever.cosine(sleepVec, foodVec), 1e-6)
    }

    @Test fun `ranks the semantically closest item first`() {
        val items = listOf(
            Item("sleep note", now - day),
            Item("food note", now - day),
            Item("exam note", now - day),
        )
        val vectors = listOf<FloatArray?>(sleepVec, foodVec, examVec)
        // Query leans mostly toward sleep.
        val query = floatArrayOf(0.9f, 0.1f, 0f)
        val out = SemanticRetriever.select(items, vectors, query, k = 1, now = now)
        assertEquals("sleep note", out.first().text)
    }

    @Test fun `filters out items below the similarity floor`() {
        val items = listOf(Item("food note", now))
        val vectors = listOf<FloatArray?>(foodVec)
        val out = SemanticRetriever.select(items, vectors, sleepVec, k = 3, now = now)
        assertTrue(out.isEmpty())
    }

    @Test fun `skips items with a null vector`() {
        val items = listOf(Item("a", now), Item("b", now))
        val vectors = listOf(sleepVec, null)
        val out = SemanticRetriever.select(items, vectors, sleepVec, k = 5, now = now)
        assertEquals(1, out.size)
        assertEquals("a", out.first().text)
    }
}
