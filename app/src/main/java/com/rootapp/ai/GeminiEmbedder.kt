package com.rootapp.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.float
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
 * Google Gemini embeddings (Generative Language API, text-embedding-004), used when the user
 * supplies their own key. Batches all texts into a single request. Same key as [GeminiClient].
 */
class GeminiEmbedder(
    private val apiKey: String,
    private val model: String = "text-embedding-004",
    private val baseUrl: String = "https://generativelanguage.googleapis.com/v1beta",
    private val http: OkHttpClient = OkHttpClient(),
) : Embedder {

    override val id: String = "gemini:$model"

    private val json = Json { ignoreUnknownKeys = true }
    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    override suspend fun embed(texts: List<String>): List<FloatArray> = withContext(Dispatchers.IO) {
        require(apiKey.isNotBlank()) { "Gemini API key is missing." }
        if (texts.isEmpty()) return@withContext emptyList()

        val payload = buildJsonObject {
            put(
                "requests",
                buildJsonArray {
                    texts.forEach { t ->
                        add(
                            buildJsonObject {
                                put("model", "models/$model")
                                put(
                                    "content",
                                    buildJsonObject {
                                        put("parts", buildJsonArray { add(buildJsonObject { put("text", t) }) })
                                    },
                                )
                            },
                        )
                    }
                },
            )
        }
        val bodyStr = json.encodeToString(JsonObject.serializer(), payload)
        val request = Request.Builder()
            .url("$baseUrl/models/$model:batchEmbedContents?key=$apiKey")
            .post(bodyStr.toRequestBody(jsonMedia))
            .build()

        http.newCall(request).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw IOException("Gemini embed HTTP ${resp.code}: ${text.take(200)}")
            val arr = json.parseToJsonElement(text).jsonObject["embeddings"]?.jsonArray
                ?: throw IOException("Gemini embed: no embeddings in ${text.take(200)}")
            arr.map { e ->
                val values = e.jsonObject["values"]?.jsonArray
                    ?: throw IOException("Gemini embed: missing values")
                FloatArray(values.size) { i -> values[i].jsonPrimitive.float }
            }
        }
    }
}
