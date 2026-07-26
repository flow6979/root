package com.rootapp.ai

/** Abstraction over the LLM provider so screens/ViewModels never depend on Groq directly. */
interface LlmClient {
    /** Send the full message list, return the assistant reply text. Throws on failure. */
    suspend fun complete(messages: List<ChatMessage>): String
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
