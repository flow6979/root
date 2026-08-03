package com.rootapp.ai

/**
 * Turns text into vectors for semantic memory retrieval. Kept separate from [LlmClient] so a
 * provider can offer chat, embeddings, or both. When no embedder is available (no user key),
 * memory retrieval falls back to the lexical [com.rootapp.data.MemoryRetriever].
 */
interface Embedder {
    /** Stable provider+model id; used to invalidate cached vectors when the model changes. */
    val id: String

    /** Embed each text, returning one vector per input in the same order. Throws on failure. */
    suspend fun embed(texts: List<String>): List<FloatArray>
}
