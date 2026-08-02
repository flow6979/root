package com.rootapp.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

/**
 * Google Gemini chat client (Generative Language API).
 *
 * Used when the user supplies their own Gemini API key in Settings, so their AI runs on
 * their own free quota. It implements the same [LlmClient] contract as [GroqClient], so
 * every screen and ViewModel works unchanged regardless of which provider is active.
 *
 * ChatMessage roles map to Gemini as: system -> system_instruction, assistant -> "model",
 * user -> "user".
 */
class GeminiClient(
    private val apiKey: String,
    private val model: String = "gemini-1.5-flash",
    private val baseUrl: String = "https://generativelanguage.googleapis.com/v1beta",
    private val http: OkHttpClient = OkHttpClient(),
) : LlmClient {

    private val json = Json { ignoreUnknownKeys = true }
    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    override suspend fun complete(messages: List<ChatMessage>): String = withContext(Dispatchers.IO) {
        require(apiKey.isNotBlank()) { "Gemini API key is missing. Add it in You -> AI." }

        val system = messages.filter { it.role == "system" }.joinToString("\n") { it.content }
        val turns = messages.filter { it.role != "system" }

        val payload = buildJsonObject {
            if (system.isNotBlank()) {
                put(
                    "system_instruction",
                    buildJsonObject {
                        put("parts", buildJsonArray { add(buildJsonObject { put("text", system) }) })
                    },
                )
            }
            put(
                "contents",
                buildJsonArray {
                    turns.forEach { m ->
                        add(
                            buildJsonObject {
                                put("role", if (m.role == "assistant") "model" else "user")
                                put("parts", buildJsonArray { add(buildJsonObject { put("text", m.content) }) })
                            },
                        )
                    }
                },
            )
            put(
                "generationConfig",
                buildJsonObject { put("temperature", 0.7); put("maxOutputTokens", 512) },
            )
        }

        // Key goes in the query string per the Generative Language API.
        val url = "$baseUrl/models/$model:generateContent?key=$apiKey"
        val request = Request.Builder()
            .url(url)
            .post(json.encodeToString(JsonObject.serializer(), payload).toRequestBody(jsonMedia))
            .build()

        http.newCall(request).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw IOException("Gemini HTTP ${resp.code}: ${text.take(300)}")
            json.parseToJsonElement(text).jsonObject["candidates"]?.jsonArray
                ?.firstOrNull()?.jsonObject?.get("content")?.jsonObject
                ?.get("parts")?.jsonArray?.firstOrNull()?.jsonObject
                ?.get("text")?.jsonPrimitive?.content?.trim()
                ?: throw IOException("Gemini returned no text")
        }
    }
}
