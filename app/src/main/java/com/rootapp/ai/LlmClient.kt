package com.rootapp.ai

/** Abstraction over the LLM provider so screens/ViewModels never depend on Groq directly. */
interface LlmClient {
    /** Send the full message list, return the assistant reply text. Throws on failure. */
    suspend fun complete(messages: List<ChatMessage>): String
}

/**
 * Tries [primary] first; if it throws (e.g. a bad user Gemini key, quota, no network), falls
 * back to [secondary] so the app's AI never hard-fails on a misconfigured provider.
 */
class FallbackLlmClient(
    private val primary: LlmClient,
    private val secondary: LlmClient,
) : LlmClient {
    override suspend fun complete(messages: List<ChatMessage>): String =
        try {
            primary.complete(messages)
        } catch (t: Throwable) {
            android.util.Log.w("Llm", "primary provider failed, using fallback: ${t.message}")
            secondary.complete(messages)
        }
}

/** Deterministic client for tests and offline previews. */
class FakeLlmClient(private val reply: String = "I hear you.") : LlmClient {
    var lastMessages: List<ChatMessage>? = null
        private set
    override suspend fun complete(messages: List<ChatMessage>): String {
        lastMessages = messages
        return reply
    }
}
