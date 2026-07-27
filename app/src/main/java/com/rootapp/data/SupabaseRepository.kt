package com.rootapp.data

import android.content.Context
import android.util.Log
import com.rootapp.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Lightweight Supabase client over OkHttp (no extra SDK). Handles anonymous auth
 * (persisted refresh token so the same anon user sticks) and RLS-scoped inserts.
 * Everything is best-effort: if unconfigured or offline, it no-ops so the app still works.
 */
class SupabaseRepository(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("supabase", Context.MODE_PRIVATE)
    private val http = OkHttpClient()
    private val json = Json { ignoreUnknownKeys = true }
    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    private val baseUrl = BuildConfig.SUPABASE_URL.trimEnd('/')
    private val anonKey = BuildConfig.SUPABASE_ANON_KEY
    val configured: Boolean get() = baseUrl.isNotBlank() && anonKey.isNotBlank()

    /** Ensure we have a valid access token (refresh or anonymous sign-in). Returns null on failure. */
    suspend fun ensureSession(): String? = withContext(Dispatchers.IO) {
        if (!configured) return@withContext null
        val refresh = prefs.getString(REFRESH, null)
        val tokens = if (refresh != null) refresh(refresh) ?: signInAnonymously() else signInAnonymously()
        tokens?.let {
            prefs.edit()
                .putString(ACCESS, it.access)
                .putString(REFRESH, it.refresh)
                .putString(USER_ID, it.userId)
                .apply()
        }
        tokens?.access
    }

    val userId: String? get() = prefs.getString(USER_ID, null)

    // ---- pushes (fire-and-forget; caller launches on a coroutine) ----
    suspend fun pushMood(epochDay: Long, mood: Int) =
        insert("moods", buildJsonObject { put("epoch_day", epochDay); put("mood", mood) })

    suspend fun pushFood(label: String, healthy: Boolean) =
        insert("foods", buildJsonObject { put("label", label); put("healthy", healthy) })

    suspend fun pushReflection(messageCount: Int) =
        insert("reflections", buildJsonObject { put("message_count", messageCount) })

    private suspend fun insert(table: String, body: JsonObject): Boolean = withContext(Dispatchers.IO) {
        val token = ensureSession() ?: return@withContext false
        val req = Request.Builder()
            .url("$baseUrl/rest/v1/$table")
            .addHeader("apikey", anonKey)
            .addHeader("Authorization", "Bearer $token")
            .addHeader("Prefer", "return=minimal")
            .post(json.encodeToString(JsonObject.serializer(), body).toRequestBody(jsonMedia))
            .build()
        runCatching {
            http.newCall(req).execute().use { r ->
                if (!r.isSuccessful) Log.w("Supabase", "insert $table -> ${r.code}: ${r.body?.string()?.take(160)}")
                r.isSuccessful
            }
        }.getOrElse { Log.w("Supabase", "insert $table failed: ${it.message}"); false }
    }

    // ---- auth ----
    private data class Tokens(val access: String, val refresh: String, val userId: String)

    private fun signInAnonymously(): Tokens? {
        val req = Request.Builder()
            .url("$baseUrl/auth/v1/signup")
            .addHeader("apikey", anonKey)
            .post("{}".toRequestBody(jsonMedia))
            .build()
        return authCall(req, "anon sign-in")
    }

    private fun refresh(refreshToken: String): Tokens? {
        val body = buildJsonObject { put("refresh_token", refreshToken) }
        val req = Request.Builder()
            .url("$baseUrl/auth/v1/token?grant_type=refresh_token")
            .addHeader("apikey", anonKey)
            .post(json.encodeToString(JsonObject.serializer(), body).toRequestBody(jsonMedia))
            .build()
        return authCall(req, "refresh")
    }

    private fun authCall(req: Request, label: String): Tokens? = runCatching {
        http.newCall(req).execute().use { r ->
            val text = r.body?.string().orEmpty()
            if (!r.isSuccessful) { Log.w("Supabase", "$label -> ${r.code}: ${text.take(160)}"); return null }
            val obj = json.parseToJsonElement(text).jsonObject
            val access = obj["access_token"]?.jsonPrimitive?.content ?: return null
            val refresh = obj["refresh_token"]?.jsonPrimitive?.content ?: ""
            val uid = obj["user"]?.jsonObject?.get("id")?.jsonPrimitive?.content ?: ""
            Tokens(access, refresh, uid)
        }
    }.getOrElse { Log.w("Supabase", "$label failed: ${it.message}"); null }

    companion object {
        private const val ACCESS = "access_token"
        private const val REFRESH = "refresh_token"
        private const val USER_ID = "user_id"
    }
}
