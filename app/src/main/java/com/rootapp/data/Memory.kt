package com.rootapp.data

import android.content.Context
import com.rootapp.ai.GeminiEmbedder

/**
 * The friend's memory: distilled reflection takeaways (long-term) plus recent things the user
 * said, retrieved by relevance to the current message. Retrieval is semantic (embedding cosine
 * similarity via [SemanticRetriever]) when the user has a Gemini key; otherwise it falls back to
 * the lexical word-overlap [MemoryRetriever]. Either way the friend recalls the *relevant* past.
 */
object Memory {
    private fun items(store: LocalStore): List<MemoryRetriever.Item> {
        val out = mutableListOf<MemoryRetriever.Item>()
        store.takeaways().forEach { t ->
            val text = buildString {
                if (t.concern.isNotBlank()) append("They were concerned about ${t.concern}. ")
                if (t.intention.isNotBlank()) append("They wanted to ${t.intention}.")
            }.trim()
            if (text.isNotBlank()) out += MemoryRetriever.Item(text, t.timestamp)
        }
        // Recent raw messages have no timestamps; approximate recency by order (newest last).
        val msgs = store.memory()
        val base = System.currentTimeMillis()
        msgs.forEachIndexed { i, m ->
            out += MemoryRetriever.Item(m, base - (msgs.size - i) * 60_000L)
        }
        return out
    }

    /** A few most-recent memory snippets to seed the system prompt at session start (lexical). */
    fun base(context: Context): String = format(
        MemoryRetriever.select(items(LocalStore(context)), query = null, k = 4, now = System.currentTimeMillis()),
    )

    /**
     * Memory most relevant to [query], for injecting just before a model turn. Uses semantic
     * (embedding) retrieval when a Gemini key is set; on any failure or no key, falls back to
     * lexical retrieval so memory always works.
     */
    suspend fun relevant(context: Context, query: String): String {
        val semantic = runCatching { semanticRelevant(context, query) }.getOrNull()
        if (semantic != null) return semantic
        return format(
            MemoryRetriever.select(items(LocalStore(context)), query = query, k = 4, now = System.currentTimeMillis()),
        )
    }

    /** Returns null (so the caller falls back to lexical) when no key is set or embedding fails. */
    private suspend fun semanticRelevant(context: Context, query: String): String? {
        val key = SettingsStore(context).geminiApiKey.trim()
        if (key.isBlank()) return null
        val items = items(LocalStore(context))
        if (items.isEmpty()) return ""
        val embedder = GeminiEmbedder(apiKey = key)
        val vectors = EmbeddingStore(context).vectorsFor(embedder, items.map { it.text })
        val queryVec = embedder.embed(listOf(query)).firstOrNull() ?: return null
        return format(
            SemanticRetriever.select(items, vectors, queryVec, k = 4, now = System.currentTimeMillis()),
        )
    }

    private fun format(items: List<MemoryRetriever.Item>): String =
        items.joinToString("\n") { "- ${it.text}" }
}
