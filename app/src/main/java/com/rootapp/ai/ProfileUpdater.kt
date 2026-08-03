package com.rootapp.ai

import kotlinx.coroutines.withTimeoutOrNull

/**
 * Maintains a short, evolving profile of the person so the friend has real continuity across
 * sessions. After a session, the LLM folds the conversation into the existing profile. Best-effort:
 * returns the current profile unchanged if the model fails or the session was empty.
 */
object ProfileUpdater {
    suspend fun update(llm: LlmClient, current: String, convo: List<ChatMessage>): String {
        val transcript = convo.filter { it.role != "system" }
            .joinToString("\n") { "${it.role}: ${it.content}" }
            .take(3000)
        if (transcript.isBlank()) return current
        val prompt = """
            You keep a SHORT profile of a person, for a caring wellbeing coach. Update the profile
            using the new conversation. Keep it to at most 6 concise bullet points across: their
            goals, recurring struggles or triggers, what has actually helped them, and key context
            (sleep, work, relationships). Merge - don't just append. Output ONLY the bullet points,
            no preamble.

            Current profile:
            ${current.ifBlank { "(none yet)" }}

            New conversation:
            $transcript
        """.trimIndent()
        val out = withTimeoutOrNull(8000) {
            runCatching { llm.complete(listOf(ChatMessage.user(prompt))).trim() }.getOrNull()
        }
        return out?.takeIf { it.isNotBlank() }?.take(900) ?: current
    }
}
