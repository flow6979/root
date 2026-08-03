package com.rootapp.shield

import com.rootapp.ai.ChatMessage
import com.rootapp.ai.LlmClient
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The human line at the top of a nudge: one short, warm sentence from the AI, with a curated
 * offline fallback so a background nudge never depends on the network.
 */
object NudgeContent {

    private val fallback = listOf(
        "This feed will still be here later - the evening won't.",
        "You opened it out of habit, not hunger. You're allowed to close it.",
        "The scroll never ends on its own. You get to be the one who stops it.",
        "One more won't be the one. Be kind to future-you and set it down.",
        "Your attention is the most valuable thing you own. Spend it on purpose.",
        "Nothing here needs you right now. Something out there might.",
    )

    suspend fun aiLine(llm: LlmClient, appLabel: String, sessionMin: Int, hour: Int): String {
        val late = hour >= 23 || hour < 5
        val prompt = "You are a caring friend. Write ONE short sentence (max 18 words) gently " +
            "nudging me to stop scrolling $appLabel after $sessionMin minutes" +
            (if (late) " late at night" else "") +
            ". Warm, not preachy. No emojis, no hashtags, no quotation marks."
        val ai = withTimeoutOrNull(6000) {
            runCatching {
                llm.complete(listOf(ChatMessage.user(prompt)))
                    .trim().trim('"').lineSequence().firstOrNull { it.isNotBlank() }?.take(140)
            }.getOrNull()
        }
        return ai?.takeIf { it.isNotBlank() } ?: fallback[(sessionMin / 5).coerceAtLeast(0) % fallback.size]
    }
}
