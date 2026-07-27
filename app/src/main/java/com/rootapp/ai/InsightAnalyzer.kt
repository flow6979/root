package com.rootapp.ai

/** Turns the user's collected data into a warm, specific analysis via the LLM. */
object InsightAnalyzer {
    suspend fun analyze(llm: LlmClient, topic: String, data: String): String {
        val prompt = """
            You are Root, a warm wellbeing companion. Based on the data below about "$topic",
            write a short, specific, encouraging analysis in 2 to 3 short paragraphs: what you
            notice, what is going well, and one small, doable suggestion. Talk like a caring
            friend, not a report. No dashes.

            Data:
            $data
        """.trimIndent()
        return runCatching {
            llm.complete(
                listOf(
                    ChatMessage.system("You are an insightful, kind wellbeing coach. Be specific and warm."),
                    ChatMessage.user(prompt),
                ),
            )
        }.getOrElse { "Couldn't generate the analysis right now. Try again in a moment." }
    }
}
