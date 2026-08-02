package com.rootapp.ai

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Pulls the meals a person mentions out of free-form text (typed or spoken) so they can be
 * auto-logged to Moments. A single utterance like "I ate pizza and pasta today" yields TWO
 * meals, each classified healthy/unhealthy independently.
 */
object FoodExtractor {
    /** One extracted meal: a short food name plus whether it counts as a healthy choice. */
    data class Meal(val food: String, val healthy: Boolean)

    private val json = Json { ignoreUnknownKeys = true }

    /** Junk keywords: any item containing one of these is classified unhealthy. */
    private val junkKeywords = listOf(
        "pizza", "burger", "fries", "soda", "coke", "cola", "chips", "candy", "fried",
        "ice cream", "icecream", "donut", "cake", "chocolate", "noodles", "maggi",
        "samosa", "sugary", "pastry", "pasta",
    )

    /** Filler words/phrases stripped before splitting a sentence into individual foods. */
    private val fillers = listOf(
        "i ate", "i had", "today", "some", "with", "a", "an",
    )

    /**
     * Extract every meal mentioned in [message]. Primary path asks [llm] for a strict JSON array;
     * if that yields nothing (LLM off, malformed reply, empty), falls back to a deterministic,
     * offline parser. Never throws - returns an empty list when no food is found.
     */
    suspend fun extract(llm: LlmClient, message: String): List<Meal> {
        val fromLlm = runCatching { extractViaLlm(llm, message) }.getOrNull().orEmpty()
        if (fromLlm.isNotEmpty()) return fromLlm
        return parse(message)
    }

    private suspend fun extractViaLlm(llm: LlmClient, message: String): List<Meal> {
        val prompt = """
            From this message, list every distinct food the person mentions eating, ordering, or
            about to eat. Reply ONLY with a compact JSON array where each element is
            {"food":"short name","healthy":true or false}. Mark healthy false for junk/fast food,
            sugary or fried items; true for whole/nutritious foods. If no food is mentioned, reply
            exactly [].
            Message: "$message"
        """.trimIndent()
        val raw = llm.complete(
            listOf(
                ChatMessage.system("You extract food mentions. Reply with a JSON array only, nothing else."),
                ChatMessage.user(prompt),
            ),
        ).trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()

        // Accept either a JSON array or a single JSON object for robustness.
        val element = json.parseToJsonElement(raw)
        val objects = when {
            raw.startsWith("[") -> element.jsonArray.map { it.jsonObject }
            raw.startsWith("{") -> listOf(element.jsonObject)
            else -> emptyList()
        }
        return objects.mapNotNull { obj ->
            val food = obj["food"]?.jsonPrimitive?.content?.trim().orEmpty()
            if (food.isBlank()) {
                null
            } else {
                val healthy = obj["healthy"]?.jsonPrimitive?.booleanOrNull ?: !isJunk(food)
                Meal(food, healthy)
            }
        }
    }

    /**
     * Deterministic, offline parser. Lowercases, strips filler words, splits on commas / "and" /
     * "&" / "plus", trims, drops empties, and classifies each item by junk keywords. Pure logic
     * with no Android or network dependency so it is unit-testable.
     */
    fun parse(message: String): List<Meal> {
        // Normalise separators to commas so a single split handles them all.
        var text = message.lowercase()
        text = text.replace("&", ",")
            .replace(Regex("\\bplus\\b"), ",")
            .replace(Regex("\\band\\b"), ",")

        return text.split(",")
            .map { stripFillers(it).trim() }
            .filter { it.isNotBlank() }
            .map { Meal(it, !isJunk(it)) }
    }

    private fun stripFillers(item: String): String {
        var s = " ${item.trim()} "
        for (f in fillers) {
            s = s.replace(" $f ", " ")
        }
        return s.trim()
    }

    private fun isJunk(item: String): Boolean {
        val lower = item.lowercase()
        return junkKeywords.any { lower.contains(it) }
    }
}
