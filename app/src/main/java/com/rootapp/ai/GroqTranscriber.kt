package com.rootapp.ai

import com.rootapp.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File

/** Speech-to-text via Groq Whisper. Uses the backend proxy when configured, else a baked key. */
object GroqTranscriber {
    private val http = OkHttpClient()
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun transcribe(file: File): String? = withContext(Dispatchers.IO) {
        val key = BuildConfig.GROQ_API_KEY
        // Need either the proxy or a direct key, plus a real file.
        if ((!Proxy.enabled && key.isBlank()) || !file.exists() || file.length() == 0L) {
            return@withContext null
        }
        val body = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("model", "whisper-large-v3-turbo")
            .addFormDataPart("response_format", "json")
            .addFormDataPart("file", file.name, file.asRequestBody("audio/mp4".toMediaType()))
            .build()
        val url = if (Proxy.enabled) {
            Proxy.baseUrl + "/openai/v1/audio/transcriptions"
        } else {
            "https://api.groq.com/openai/v1/audio/transcriptions"
        }
        val req = Request.Builder()
            .url(url)
            .apply {
                if (Proxy.enabled) Proxy.headers().forEach { (k, v) -> addHeader(k, v) }
                else addHeader("Authorization", "Bearer $key")
            }
            .post(body)
            .build()
        runCatching {
            http.newCall(req).execute().use { r ->
                val text = r.body?.string().orEmpty()
                if (!r.isSuccessful) return@use null
                json.parseToJsonElement(text).jsonObject["text"]?.jsonPrimitive?.content?.trim()
            }
        }.getOrNull()
    }
}
