package com.rootapp.data

import android.content.Context
import com.rootapp.ai.Embedder
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * On-device cache of text embeddings so we don't re-embed the same stored memory every turn.
 * Keyed by embedder id + text, persisted as JSON in SharedPreferences. On each call it embeds
 * only the texts it hasn't seen, prunes anything no longer in the working set (and any vectors
 * from a different embedder), and returns vectors aligned to the input order.
 */
class EmbeddingStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("root_embeddings", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = MapSerializer(String.serializer(), ListSerializer(Float.serializer()))

    private fun load(): MutableMap<String, List<Float>> {
        val s = prefs.getString(CACHE, null)
        return if (s.isNullOrBlank()) mutableMapOf()
        else runCatching { json.decodeFromString(serializer, s).toMutableMap() }.getOrDefault(mutableMapOf())
    }

    private fun save(map: Map<String, List<Float>>) {
        prefs.edit().putString(CACHE, json.encodeToString(serializer, map)).apply()
    }

    private fun keyFor(embedderId: String, text: String): String = "$embedderId$text"

    /**
     * Vectors for [texts], aligned by index. Embeds only the uncached ones via [embedder], then
     * prunes the cache to exactly this working set so it can't grow unbounded. Throws if the
     * embedder call fails (callers treat that as "fall back to lexical").
     */
    suspend fun vectorsFor(embedder: Embedder, texts: List<String>): List<FloatArray> {
        if (texts.isEmpty()) return emptyList()
        val cache = load()
        val missing = texts.filter { keyFor(embedder.id, it) !in cache }.distinct()
        if (missing.isNotEmpty()) {
            val fresh = embedder.embed(missing)
            missing.forEachIndexed { i, t ->
                fresh.getOrNull(i)?.let { cache[keyFor(embedder.id, t)] = it.toList() }
            }
        }
        // Prune to only the current working set for this embedder.
        val keep = texts.map { keyFor(embedder.id, it) }.toSet()
        val pruned = cache.filterKeys { it in keep }
        save(pruned)
        return texts.map { (pruned[keyFor(embedder.id, it)] ?: emptyList()).toFloatArray() }
    }

    companion object {
        private const val CACHE = "vectors"
    }
}
