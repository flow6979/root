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

/** Speech-to-text via Groq Whisper (no Google dependency; reuses our Groq key). */
object GroqTranscriber {
    private val http = OkHttpClient()
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun transcribe(file: File): String? = withContext(Dispatchers.IO) {
        val key = BuildConfig.GROQ_API_KEY
        if (key.isBlank() || !file.exists() || file.length() == 0L) return@withContext null
        val body = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("model", "whisper-large-v3-turbo")
            .addFormDataPart("response_format", "json")
            .addFormDataPart("file", file.name, file.asRequestBody("audio/mp4".toMediaType()))
            .build()
        val req = Request.Builder()
            .url("https://api.groq.com/openai/v1/audio/transcriptions")
            .addHeader("Authorization", "Bearer $key")
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
