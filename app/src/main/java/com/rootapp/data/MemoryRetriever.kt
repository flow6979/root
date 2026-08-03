package com.rootapp.data

/**
 * Lightweight, on-device retrieval for the friend's cross-session memory. No embeddings/backend:
 * it scores stored memory snippets against the current message by word overlap blended with
 * recency, so the AI recalls the *relevant* past, not just the most recent. Pure + unit-testable.
 */
object MemoryRetriever {
    data class Item(val text: String, val ts: Long)

    private val stop = setOf(
        "the", "a", "an", "and", "or", "but", "to", "of", "in", "on", "for", "with", "i", "im",
        "you", "it", "is", "am", "are", "was", "were", "be", "my", "me", "we", "so", "at", "that",
        "this", "have", "has", "had", "do", "did", "just", "about", "was", "not", "no", "yes",
    )

    private fun tokens(s: String): Set<String> =
        s.lowercase().split(Regex("[^a-z0-9]+")).filter { it.length > 2 && it !in stop }.toSet()

    /**
     * Top-[k] items. With a [query], ranks by word overlap (0.7) + recency (0.3) and requires some
     * overlap; without one, purely by recency. De-dupes identical snippets.
     */
    fun select(items: List<Item>, query: String?, k: Int = 4, now: Long): List<Item> {
        if (items.isEmpty()) return emptyList()
        val qTokens = query?.let { tokens(it) } ?: emptySet()
        val seen = HashSet<String>()
        val scored = items.mapNotNull { item ->
            val key = item.text.trim().lowercase()
            if (!seen.add(key)) return@mapNotNull null
            val ageDays = ((now - item.ts).coerceAtLeast(0)) / 86_400_000.0
            val recency = 1.0 / (1.0 + ageDays)
            if (qTokens.isEmpty()) {
                item to recency
            } else {
                val overlap = tokens(item.text).count { it in qTokens }.toDouble() / qTokens.size
                if (overlap <= 0.0) null else item to (0.7 * overlap + 0.3 * recency)
            }
        }
        return scored.sortedByDescending { it.second }.take(k).map { it.first }
    }
}
