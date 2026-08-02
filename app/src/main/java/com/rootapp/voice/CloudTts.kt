package com.rootapp.voice

import android.content.Context
import android.media.MediaPlayer
import android.util.Log
import com.rootapp.BuildConfig
import com.rootapp.ai.Proxy
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
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * Voice engine with a fallback chain of "agents":
 *   1. ElevenLabs (natural, needs a key)
 *   2. Google Translate TTS (free, no key; short text)
 *   3. (caller) system TextToSpeech
 * play() returns true if any cloud agent produced audio; false -> caller uses system TTS.
 */
object CloudTts {
    private val http = OkHttpClient.Builder()
        .callTimeout(15, TimeUnit.SECONDS)
        .connectTimeout(8, TimeUnit.SECONDS)
        .build()
    private val json = Json { ignoreUnknownKeys = true }
    private val jsonMedia = "application/json".toMediaType()
    private var player: MediaPlayer? = null

    // ElevenLabs "Sarah" - mature, reassuring (free-tier allowed).
    private const val ELEVEN_VOICE = "EXAVITQu4vr4xnSDxMaL"

    /** We always have at least the free StreamElements agent, so cloud is "available". */
    val configured: Boolean get() = true

    suspend fun play(context: Context, text: String, onDone: () -> Unit = {}): Boolean = withContext(Dispatchers.IO) {
        if (text.isBlank()) return@withContext false
        // Agent 1: our proxy's ElevenLabs voice (no key in the app)
        if (Proxy.enabled) {
            fetchProxy(text)?.let { return@withContext playBytes(context, it, onDone) }
        }
        // Agent 2: ElevenLabs directly (dev builds with a baked key)
        if (BuildConfig.ELEVENLABS_API_KEY.isNotBlank()) {
            fetchEleven(text)?.let { return@withContext playBytes(context, it, onDone) }
        }
        // Agent 3: Google Translate TTS (free, no key)
        fetchGoogle(text)?.let { return@withContext playBytes(context, it, onDone) }
        false
    }

    private fun fetchProxy(text: String): ByteArray? {
        val payload = buildJsonObject { put("text", text.take(2500)) }
        val req = Request.Builder()
            .url(Proxy.baseUrl + "/tts")
            .apply { Proxy.headers().forEach { (k, v) -> addHeader(k, v) } }
            .post(json.encodeToString(JsonObject.serializer(), payload).toRequestBody(jsonMedia))
            .build()
        return runCatching {
            http.newCall(req).execute().use { r ->
                if (r.isSuccessful) r.body?.bytes() else { Log.w("CloudTts", "proxy-tts ${r.code}"); null }
            }
        }.getOrElse { Log.w("CloudTts", "proxy-tts failed: ${it.message}"); null }
    }

    private fun fetchEleven(text: String): ByteArray? {
        val key = BuildConfig.ELEVENLABS_API_KEY
        val body: JsonObject = buildJsonObject {
            put("text", text.take(2500)); put("model_id", "eleven_multilingual_v2")
        }
        val req = Request.Builder()
            .url("https://api.elevenlabs.io/v1/text-to-speech/$ELEVEN_VOICE")
            .addHeader("xi-api-key", key).addHeader("Accept", "audio/mpeg")
            .post(json.encodeToString(JsonObject.serializer(), body).toRequestBody(jsonMedia))
            .build()
        return runCatching {
            http.newCall(req).execute().use { r ->
                if (r.isSuccessful) r.body?.bytes() else { Log.w("CloudTts", "eleven ${r.code}"); null }
            }
        }.getOrElse { Log.w("CloudTts", "eleven failed: ${it.message}"); null }
    }

    private fun fetchGoogle(text: String): ByteArray? {
        // Free, no key. Limited to short text, so this fallback reads the opening ~200 chars.
        val q = URLEncoder.encode(text.take(200), "UTF-8")
        val req = Request.Builder()
            .url("https://translate.google.com/translate_tts?ie=UTF-8&tl=en&client=tw-ob&q=$q")
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 14)")
            .get().build()
        return runCatching {
            http.newCall(req).execute().use { r ->
                if (r.isSuccessful) r.body?.bytes() else { Log.w("CloudTts", "google-tts ${r.code}"); null }
            }
        }.getOrElse { Log.w("CloudTts", "google-tts failed: ${it.message}"); null }
    }

    private suspend fun playBytes(context: Context, bytes: ByteArray, onDone: () -> Unit): Boolean {
        if (bytes.isEmpty()) return false
        val f = File(context.cacheDir, "tts_${System.nanoTime()}.mp3")
        f.writeBytes(bytes)
        return withContext(Dispatchers.Main) {
            runCatching {
                stop()
                player = MediaPlayer().apply {
                    setDataSource(f.absolutePath)
                    setOnCompletionListener { onDone(); runCatching { it.release() }; if (player === it) player = null }
                    prepare(); start()
                }
                true
            }.getOrElse { Log.w("CloudTts", "play failed: ${it.message}"); false }
        }
    }

    fun stop() {
        runCatching { player?.stop(); player?.release() }
        player = null
    }
}
