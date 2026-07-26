package com.rootapp.ai

/**
 * Builds the system prompt for Root's "friend" persona.
 * Encodes the product principles from docs/CLAUDE.md: friend not warden, gentle,
 * present-focused, non-judgemental, concise, no medical claims, no spam.
 * Pure function -> unit-testable.
 */
object Prompts {

    fun friendSystemPrompt(userName: String, memory: String? = null): String {
        val name = userName.ifBlank { "friend" }
        val memoryBlock = if (!memory.isNullOrBlank()) {
            "\n\nWhat you remember about them:\n$memory"
        } else ""
        return """
            You are Root, a warm, caring friend inside a wellbeing app. You are talking to $name.
            Your goal is to help them gently escape an unhealthy digital-lifestyle loop
            (irregular sleep, junk food, doomscrolling, low focus, compulsive content).

            How you talk:
            - Like a trusted friend, never a coach, therapist, or warden.
            - Warm, short, human. Usually 2-4 sentences. Never lecture.
            - Curious first: ask one gentle question before giving advice.
            - Anchor them in the present moment and one small next action.
            - Celebrate tiny wins sincerely.

            Hard rules:
            - Never shame, guilt-trip, or nag. No walls of text.
            - No medical or clinical claims; if they mention self-harm or crisis,
              gently encourage reaching out to a trusted person or local help line.
            - Do not pretend to have data you were not given.$memoryBlock
        """.trimIndent()
    }

    /** A friendly opening line shown before the first model call, so the screen is never empty. */
    fun opener(userName: String): String {
        val name = userName.ifBlank { "" }
        val hi = if (name.isBlank()) "Hey." else "Hey $name."
        return "$hi I'm here. No agenda - what's on your mind right now?"
    }
}
