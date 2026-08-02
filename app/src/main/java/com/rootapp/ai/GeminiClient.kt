package com.rootapp.ai

import android.util.Log
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
 * Google Gemini chat client (Generative Language API), used when the user supplies their own
 * key in You -> AI. Implements the same [LlmClient] contract as [GroqClient].
 *
 * Robustness: newly-created API keys often only have access to the 2.x models (1.5 is retired),
 * and different keys expose different names, so we try a small list of current models and use
 * the first that answers. Field names are camelCase per the REST API.
 */
class GeminiClient(
    private val apiKey: String,
    private val models: List<String> = listOf("gemini-2.0-flash", "gemini-2.5-flash", "gemini-1.5-flash"),
    private val baseUrl: String = "https://generativelanguage.googleapis.com/v1beta",
    private val http: OkHttpClient = OkHttpClient(),
) : LlmClient {

    private val json = Json { ignoreUnknownKeys = true }
    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    override suspend fun complete(messages: List<ChatMessage>): String = withContext(Dispatchers.IO) {
        require(apiKey.isNotBlank()) { "Gemini API key is missing. Add it in You -> AI." }

        val system = messages.filter { it.role == "system" }.joinToString("\n") { it.content }
        // Gemini requires the conversation to START with a user turn (unlike OpenAI/Groq, which
        // accept an assistant opener). Drop any leading assistant turns, else it 400s every time.
        val turns = messages.filter { it.role != "system" }.dropWhile { it.role == "assistant" }

        val payload = buildJsonObject {
            if (system.isNotBlank()) {
                put(
                    "systemInstruction",
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
        val bodyStr = json.encodeToString(JsonObject.serializer(), payload)

        var lastError = "Gemini request failed"
        for (model in models) {
            val request = Request.Builder()
                .url("$baseUrl/models/$model:generateContent?key=$apiKey")
                .post(bodyStr.toRequestBody(jsonMedia))
                .build()
            val outcome = runCatching {
                http.newCall(request).execute().use { resp ->
                    val text = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) {
                        lastError = "Gemini ($model) HTTP ${resp.code}: ${text.take(200)}"
                        Log.w("GeminiClient", lastError)
                        // 404 = model not available for this key -> try the next model.
                        // Other codes (bad key, quota) won't be fixed by another model.
                        if (resp.code == 404 || resp.code == 400) return@use null
                        throw IOException(lastError)
                    }
                    json.parseToJsonElement(text).jsonObject["candidates"]?.jsonArray
                        ?.firstOrNull()?.jsonObject?.get("content")?.jsonObject
                        ?.get("parts")?.jsonArray?.firstOrNull()?.jsonObject
                        ?.get("text")?.jsonPrimitive?.content?.trim()
                        ?: throw IOException("Gemini returned no text: ${text.take(200)}")
                }
            }.getOrElse { throw it }
            if (outcome != null) return@withContext outcome
        }
        throw IOException(lastError)
    }
}
