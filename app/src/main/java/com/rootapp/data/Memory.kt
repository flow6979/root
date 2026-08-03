package com.rootapp.data

import android.content.Context

/**
 * The friend's memory: distilled reflection takeaways (long-term) plus recent things the user
 * said, retrieved by relevance to the current message (see [MemoryRetriever]).
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

    /** A few most-recent memory snippets to seed the system prompt at session start. */
    fun base(context: Context): String = format(
        MemoryRetriever.select(items(LocalStore(context)), query = null, k = 4, now = System.currentTimeMillis()),
    )

    /** Memory most relevant to [query], for injecting just before a model turn. */
    fun relevant(context: Context, query: String): String = format(
        MemoryRetriever.select(items(LocalStore(context)), query = query, k = 4, now = System.currentTimeMillis()),
    )

    private fun format(items: List<MemoryRetriever.Item>): String =
        items.joinToString("\n") { "- ${it.text}" }
}
