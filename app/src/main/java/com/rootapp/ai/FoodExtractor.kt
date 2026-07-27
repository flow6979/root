package com.rootapp.ai

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Detects a food mention in a message so reflections can auto-log meals to Moments. */
object FoodExtractor {
    data class Mention(val food: String, val healthy: Boolean)

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun extract(llm: LlmClient, message: String): Mention? {
        val prompt = """
            From this message, if the person mentions eating, ordering, or about to eat a specific
            food, reply ONLY with compact JSON: {"food":"short name","healthy":true or false}.
            If no food is mentioned, reply exactly {}.
            Message: "$message"
        """.trimIndent()
        return runCatching {
            val raw = llm.complete(
                listOf(
                    ChatMessage.system("You extract food mentions. Reply with JSON only, nothing else."),
                    ChatMessage.user(prompt),
                ),
            ).trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
            val obj = json.parseToJsonElement(raw).jsonObject
            val food = obj["food"]?.jsonPrimitive?.content?.trim().orEmpty()
            if (food.isBlank()) null
            else Mention(food, obj["healthy"]?.jsonPrimitive?.booleanOrNull ?: true)
        }.getOrNull()
    }
}
