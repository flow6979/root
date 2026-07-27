package com.rootapp.voice

import android.content.Context
import android.media.MediaPlayer
import android.util.Log
import com.rootapp.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Natural cloud voice via ElevenLabs (free tier). Falls back to system TTS when no key.
 * A soothing default voice; plays through MediaPlayer.
 */
object CloudTts {
    private val http = OkHttpClient.Builder().callTimeout(30, TimeUnit.SECONDS).build()
    private val json = Json { ignoreUnknownKeys = true }
    private val jsonMedia = "application/json".toMediaType()
    private var player: MediaPlayer? = null

    // "Rachel" - a calm, warm default voice.
    private const val VOICE_ID = "21m00Tcm4TlvDq8ikWAM"

    val configured: Boolean get() = BuildConfig.ELEVENLABS_API_KEY.isNotBlank()

    /** Fetches + plays audio. Returns true if it played; false to signal fallback. */
    suspend fun play(context: Context, text: String, onDone: () -> Unit = {}): Boolean = withContext(Dispatchers.IO) {
        val key = BuildConfig.ELEVENLABS_API_KEY
        if (key.isBlank() || text.isBlank()) return@withContext false
        val body: JsonObject = buildJsonObject {
            put("text", text.take(2500))
            put("model_id", "eleven_multilingual_v2")
        }
        val req = Request.Builder()
            .url("https://api.elevenlabs.io/v1/text-to-speech/$VOICE_ID")
            .addHeader("xi-api-key", key)
            .addHeader("Accept", "audio/mpeg")
            .post(json.encodeToString(JsonObject.serializer(), body).toRequestBody(jsonMedia))
            .build()
        runCatching {
            http.newCall(req).execute().use { r ->
                if (!r.isSuccessful) { Log.w("CloudTts", "http ${r.code}"); return@use false }
                val bytes = r.body?.bytes() ?: return@use false
                val f = File(context.cacheDir, "tts_${System.nanoTime()}.mp3")
                f.writeBytes(bytes)
                withContext(Dispatchers.Main) {
                    stop()
                    player = MediaPlayer().apply {
                        setDataSource(f.absolutePath)
                        setOnCompletionListener { onDone(); runCatching { it.release() }; if (player === it) player = null }
                        prepare()
                        start()
                    }
                }
                true
            }
        }.getOrElse { Log.w("CloudTts", "play failed: ${it.message}"); false }
    }

    fun stop() {
        runCatching { player?.stop(); player?.release() }
        player = null
    }
}
