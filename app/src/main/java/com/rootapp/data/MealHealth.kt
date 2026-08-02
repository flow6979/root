package com.rootapp.data

import com.rootapp.ai.ChatMessage
import com.rootapp.ai.LlmClient
import kotlin.math.roundToInt

/**
 * Robust weighted meal-health scoring. Pure Kotlin (no Android imports) so it is fully unit
 * testable. Every meal gets a numeric health weight 0..100 plus a short human reason, and a list
 * of meals aggregates into an overall eating score 0..100.
 *
 * The deterministic keyword path is the source of truth. An optional LLM-enrichment hook can
 * refine foods the keyword map does not recognise, but the app works entirely offline without it.
 */
object MealHealth {
    /** A single scored meal: [score] is 0..100 (higher = healthier), [reason] is a short phrase. */
    data class MealScore(val score: Int, val reason: String)

    /** One keyword rule: substrings that match, the weight to assign, and a human reason. */
    private data class Rule(val keywords: List<String>, val score: Int, val reason: String)

    // Curated knowledge map. Ordered high -> low; the FIRST matching rule wins, so more specific /
    // stronger signals should come earlier. Scores are chosen mid-band per tier for headroom.
    private val rules = listOf(
        // --- Low (junk / fried / sugary): 10..30 ---
        Rule(listOf("soda", "cola", "coke", "sugary"), 12, "sugary drink with empty calories"),
        Rule(listOf("candy", "chocolate", "cake", "donut", "doughnut", "pastry", "ice cream", "icecream"), 15, "sugary treat, low on nutrients"),
        Rule(listOf("pizza", "burger"), 22, "fast food, high in refined carbs and fat"),
        Rule(listOf("fries", "fried", "chips", "samosa"), 20, "fried and high in refined carbs"),
        // --- Medium (staples / mixed): 45..65 ---
        Rule(listOf("noodles", "maggi", "pasta"), 45, "refined carbs - fine in moderation"),
        Rule(listOf("sandwich", "wrap"), 55, "a balanced staple, depends on the filling"),
        Rule(listOf("rice", "roti", "bread", "chapati"), 55, "a carb staple - pair it with veg or protein"),
        // --- High (whole / nutritious): 75..95 ---
        Rule(listOf("salad", "leafy", "spinach", "kale"), 92, "leafy greens, rich in fibre and nutrients"),
        Rule(listOf("veg", "vegetable", "broccoli"), 88, "vegetables, high in fibre and vitamins"),
        Rule(listOf("fruit", "apple", "banana", "berry", "berries"), 85, "fruit, naturally rich in fibre and vitamins"),
        Rule(listOf("grilled", "boiled", "steamed"), 82, "lightly cooked, keeps nutrients intact"),
        Rule(listOf("dal", "lentil", "beans"), 85, "plant protein and fibre"),
        Rule(listOf("eggs", "egg"), 80, "good source of protein"),
        Rule(listOf("oats", "oatmeal"), 82, "whole grain, keeps you full"),
        Rule(listOf("yogurt", "yoghurt", "curd"), 78, "protein and gut-friendly"),
    )

    private const val DEFAULT_SCORE = 55
    private const val DEFAULT_REASON = "mixed - logged as a general meal"

    /**
     * Score a single [food] string deterministically. Case-insensitive substring match against the
     * curated knowledge map; the first matching rule wins. Unknown foods get a sensible default.
     */
    fun scoreMeal(food: String): MealScore {
        val lower = food.lowercase().trim()
        if (lower.isEmpty()) return MealScore(DEFAULT_SCORE, DEFAULT_REASON)
        val rule = rules.firstOrNull { r -> r.keywords.any { lower.contains(it) } }
        return if (rule != null) MealScore(rule.score, rule.reason)
        else MealScore(DEFAULT_SCORE, DEFAULT_REASON)
    }

    /**
     * Recency-weighted average of per-meal [scores], 0..100. More recent meals (later in the list)
     * carry more weight so the score reflects current habits. Returns null when the list is empty.
     *
     * Weight for index i (0-based, oldest first) is (i + 1), giving a linear recency ramp.
     */
    fun aggregate(scores: List<Int>): Int? {
        if (scores.isEmpty()) return null
        var weightedSum = 0.0
        var weightTotal = 0.0
        scores.forEachIndexed { i, s ->
            val w = (i + 1).toDouble()
            weightedSum += s.coerceIn(0, 100) * w
            weightTotal += w
        }
        return (weightedSum / weightTotal).roundToInt().coerceIn(0, 100)
    }

    /**
     * Optional LLM-enrichment hook. Refines only foods the keyword map did not recognise (i.e. that
     * fell back to the default), returning refined [MealScore]s keyed by the original food string.
     * The deterministic path remains the source of truth - if the LLM is off or replies oddly this
     * returns an empty map and callers keep the deterministic scores. Never throws.
     */
    suspend fun enrichUnknown(llm: LlmClient, foods: List<String>): Map<String, MealScore> {
        val unknown = foods.filter { scoreMeal(it).let { m -> m.score == DEFAULT_SCORE && m.reason == DEFAULT_REASON } }
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
        if (unknown.isEmpty()) return emptyMap()

        val prompt = buildString {
            appendLine("For each food, give a health weight 0-100 (higher = healthier) and a short reason (<= 6 words).")
            appendLine("Reply ONLY with lines formatted exactly: food | score | reason")
            appendLine("Foods:")
            unknown.forEach { appendLine("- $it") }
        }
        val raw = runCatching {
            llm.complete(
                listOf(
                    ChatMessage.system("You are a nutrition scorer. Reply only with 'food | score | reason' lines."),
                    ChatMessage.user(prompt),
                ),
            )
        }.getOrNull().orEmpty()

        return parseEnrichment(raw, unknown)
    }

    /**
     * Parse the LLM enrichment reply ("food | score | reason" per line) into scores keyed by the
     * matching input food. Pure and lenient: skips malformed lines, clamps scores, ignores foods it
     * cannot map back to an input. Exposed for unit testing.
     */
    fun parseEnrichment(raw: String, unknown: List<String>): Map<String, MealScore> {
        val out = mutableMapOf<String, MealScore>()
        raw.lineSequence().forEach { line ->
            val parts = line.split("|").map { it.trim() }
            if (parts.size < 3) return@forEach
            val food = parts[0]
            val score = parts[1].filter { it.isDigit() }.toIntOrNull() ?: return@forEach
            val reason = parts[2].ifBlank { DEFAULT_REASON }
            val match = unknown.firstOrNull { it.equals(food, ignoreCase = true) }
                ?: unknown.firstOrNull { it.contains(food, ignoreCase = true) || food.contains(it, ignoreCase = true) }
            if (match != null && food.isNotBlank()) {
                out[match] = MealScore(score.coerceIn(0, 100), reason)
            }
        }
        return out
    }
}
