package com.rootapp.data

import kotlin.math.sqrt

/**
 * Ranks memory snippets by *meaning* rather than shared words: cosine similarity between each
 * item's embedding and the query embedding, blended with recency. Requires a minimum similarity
 * so unrelated memory is never injected. Pure + unit-testable (vectors are passed in).
 */
object SemanticRetriever {

    fun cosine(a: FloatArray, b: FloatArray): Double {
        if (a.isEmpty() || a.size != b.size) return 0.0
        var dot = 0.0
        var na = 0.0
        var nb = 0.0
        for (i in a.indices) {
            dot += a[i] * b[i]
            na += a[i] * a[i]
            nb += b[i] * b[i]
        }
        if (na == 0.0 || nb == 0.0) return 0.0
        return dot / (sqrt(na) * sqrt(nb))
    }

    /**
     * Top-[k] items by 0.7 * similarity + 0.3 * recency, keeping only those with similarity
     * >= [minSim]. [vectors] is aligned to [items]; a null vector (failed embed) is skipped.
     * De-dupes identical snippets.
     */
    fun select(
        items: List<MemoryRetriever.Item>,
        vectors: List<FloatArray?>,
        query: FloatArray,
        k: Int = 4,
        now: Long,
        minSim: Double = 0.55,
    ): List<MemoryRetriever.Item> {
        if (items.isEmpty() || query.isEmpty()) return emptyList()
        val seen = HashSet<String>()
        val scored = items.mapIndexedNotNull { i, item ->
            val vec = vectors.getOrNull(i) ?: return@mapIndexedNotNull null
            val key = item.text.trim().lowercase()
            if (!seen.add(key)) return@mapIndexedNotNull null
            val sim = cosine(vec, query)
            if (sim < minSim) return@mapIndexedNotNull null
            val ageDays = ((now - item.ts).coerceAtLeast(0)) / 86_400_000.0
            val recency = 1.0 / (1.0 + ageDays)
            item to (0.7 * sim + 0.3 * recency)
        }
        return scored.sortedByDescending { it.second }.take(k).map { it.first }
    }
}
