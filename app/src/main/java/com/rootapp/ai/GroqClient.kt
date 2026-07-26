package com.rootapp.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

/**
 * Groq chat client. Groq exposes an OpenAI-compatible endpoint and (per D19) does not
 * train on submitted data, so it is our default for the sensitive reflection sessions.
 * baseUrl is injectable so tests can point at a MockWebServer.
 */
class GroqClient(
    private val apiKey: String,
    private val model: String = "llama-3.1-8b-instant",
    private val baseUrl: String = "https://api.groq.com/openai/v1/",
    private val http: OkHttpClient = OkHttpClient(),
) : LlmClient {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    override suspend fun complete(messages: List<ChatMessage>): String = withContext(Dispatchers.IO) {
        require(apiKey.isNotBlank()) { "GROQ_API_KEY is missing. Set it in local.properties." }

        val payload = ChatRequest(
            model = model,
            messages = messages.map { Msg(it.role, it.content) },
        )
        val body = json.encodeToString(ChatRequest.serializer(), payload).toRequestBody(jsonMedia)
        val url = baseUrl.trimEnd('/') + "/chat/completions"
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $apiKey")
            .post(body)
            .build()

        http.newCall(request).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                throw IOException("Groq HTTP ${resp.code}: ${text.take(300)}")
            }
            val parsed = json.decodeFromString(ChatResponse.serializer(), text)
            parsed.choices.firstOrNull()?.message?.content?.trim()
                ?: throw IOException("Groq returned no choices")
        }
    }

    // ---- wire format ----
    @Serializable
    private data class ChatRequest(
        val model: String,
        val messages: List<Msg>,
        val temperature: Double = 0.7,
        @SerialName("max_tokens") val maxTokens: Int = 512,
    )

    @Serializable
    private data class Msg(val role: String, val content: String)

    @Serializable
    private data class ChatResponse(val choices: List<Choice> = emptyList())

    @Serializable
    private data class Choice(val message: Msg)
}
