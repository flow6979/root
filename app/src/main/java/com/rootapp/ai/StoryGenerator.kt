package com.rootapp.ai

/** Generates a longer, narrative story via the LLM for the Stories tab. */
object StoryGenerator {

    data class Story(val kicker: String, val title: String, val body: String)

    private val kickers = listOf("A short story", "Someone like you", "Before bed", "A small idea", "Tonight")

    private val fallback = Story(
        "A short story",
        "The quiet after",
        "It was almost 2am when Meera finally put the phone face down. Not because she wanted to, " +
            "but because her thumb ached. The room felt loud in its silence. For the first time in hours " +
            "she heard the fan, the traffic far below, her own breathing. She thought she would feel bored. " +
            "Instead she felt something closer to relief, like setting down a bag she didn't know was heavy. " +
            "The next morning she made one tiny rule, and kept it.",
    )

    suspend fun generate(llm: LlmClient, index: Int): Story {
        val kicker = kickers[index % kickers.size]
        val prompt = """
            Write a short story of about 250 to 350 words for someone trying to break free from
            phone and short-video addiction. Give it a real narrative arc: a relatable character,
            a turning point or twist, a small climax, and a quiet, hopeful ending. Warm and human,
            not preachy. No dashes. No clichés.

            Format exactly:
            First line: a title of 2 to 5 words.
            Then a blank line.
            Then the story.
        """.trimIndent()
        return runCatching {
            val text = llm.complete(
                listOf(
                    ChatMessage.system("You are a gifted short-story writer. Keep it vivid and concise."),
                    ChatMessage.user(prompt),
                ),
            ).trim()
            val lines = text.lines()
            val title = lines.firstOrNull()?.trim()?.trim('"', '#', '*', ' ')?.ifBlank { "A short story" } ?: "A short story"
            val body = lines.drop(1).joinToString("\n").trim().ifBlank { fallback.body }
            Story(kicker, title, body)
        }.getOrDefault(fallback)
    }
}
