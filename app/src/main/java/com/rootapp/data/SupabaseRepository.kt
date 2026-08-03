package com.rootapp.data

import android.content.Context
import android.util.Log
import com.rootapp.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.time.OffsetDateTime
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
    val loggedIn: Boolean get() = prefs.getString(REFRESH, null) != null
    val email: String? get() = prefs.getString(EMAIL, null)
    val isGuest: Boolean get() = prefs.getString(KIND, null) == "anon"

    sealed class AuthResult {
        object Success : AuthResult()
        object NeedsConfirmation : AuthResult()
        data class Error(val message: String) : AuthResult()
    }

    suspend fun signIn(emailAddr: String, password: String): AuthResult = withContext(Dispatchers.IO) {
        if (!configured) return@withContext AuthResult.Error("Not configured")
        val body = buildJsonObject { put("email", emailAddr.trim()); put("password", password) }
        val req = Request.Builder()
            .url("$baseUrl/auth/v1/token?grant_type=password")
            .addHeader("apikey", anonKey)
            .post(json.encodeToString(JsonObject.serializer(), body).toRequestBody(jsonMedia))
            .build()
        toResult(authCallResult(req, "sign-in"), "email")
    }

    suspend fun signUp(emailAddr: String, password: String, name: String = ""): AuthResult = withContext(Dispatchers.IO) {
        if (!configured) return@withContext AuthResult.Error("Not configured")
        val body = buildJsonObject {
            put("email", emailAddr.trim())
            put("password", password)
            if (name.isNotBlank()) put("data", buildJsonObject { put("full_name", name.trim()) })
        }
        // Ask Supabase to land the email-confirmation link on a real page (not the project's
        // default Site URL, which is localhost:3000 out of the box). This URL must also be listed
        // under Supabase Auth -> URL Configuration -> Redirect URLs for it to take effect.
        val redirect = java.net.URLEncoder.encode(EMAIL_REDIRECT, "UTF-8")
        val req = Request.Builder()
            .url("$baseUrl/auth/v1/signup?redirect_to=$redirect")
            .addHeader("apikey", anonKey)
            .post(json.encodeToString(JsonObject.serializer(), body).toRequestBody(jsonMedia))
            .build()
        toResult(authCallResult(req, "sign-up"), "email")
    }

    suspend fun guestSignIn(): Boolean = withContext(Dispatchers.IO) {
        val t = signInAnonymously() ?: return@withContext false
        store(t, "anon"); true
    }

    fun signOut() { prefs.edit().clear().apply() }

    private fun toResult(call: AuthCall, kind: String): AuthResult = when (call) {
        is AuthCall.Ok -> { store(call.tokens, kind); AuthResult.Success }
        is AuthCall.Fail -> if (call.needsConfirmation) AuthResult.NeedsConfirmation else AuthResult.Error(call.message)
    }

    private fun store(t: Tokens, kind: String) {
        val e = prefs.edit()
        e.putString(ACCESS, t.access); e.putString(REFRESH, t.refresh)
        e.putString(USER_ID, t.userId); e.putString(KIND, kind)
        if (t.email.isNullOrBlank()) e.remove(EMAIL) else e.putString(EMAIL, t.email)
        if (t.name.isNullOrBlank()) e.remove(NAME) else e.putString(NAME, t.name)
        e.apply()
    }

    /** Display name from the signed-in account's metadata, if any. */
    val displayName: String? get() = prefs.getString(NAME, null)?.ifBlank { null }

    private data class Tokens(
        val access: String,
        val refresh: String,
        val userId: String,
        val email: String? = null,
        val name: String? = null,
    )

    private fun signInAnonymously(): Tokens? {
        val req = Request.Builder()
            .url("$baseUrl/auth/v1/signup")
            .addHeader("apikey", anonKey)
            .post("{}".toRequestBody(jsonMedia))
            .build()
        return (authCallResult(req, "anon sign-in") as? AuthCall.Ok)?.tokens
    }

    private fun refresh(refreshToken: String): Tokens? {
        val body = buildJsonObject { put("refresh_token", refreshToken) }
        val req = Request.Builder()
            .url("$baseUrl/auth/v1/token?grant_type=refresh_token")
            .addHeader("apikey", anonKey)
            .post(json.encodeToString(JsonObject.serializer(), body).toRequestBody(jsonMedia))
            .build()
        return (authCallResult(req, "refresh") as? AuthCall.Ok)?.tokens
    }

    private sealed class AuthCall {
        data class Ok(val tokens: Tokens) : AuthCall()
        data class Fail(val message: String, val needsConfirmation: Boolean = false) : AuthCall()
    }

    private fun authCallResult(req: Request, label: String): AuthCall = runCatching {
        http.newCall(req).execute().use { r ->
            val text = r.body?.string().orEmpty()
            val obj = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull()
            if (r.isSuccessful) {
                val access = obj?.get("access_token")?.jsonPrimitive?.content
                if (access != null) {
                    val refresh = obj["refresh_token"]?.jsonPrimitive?.content ?: ""
                    val user = obj["user"]?.jsonObject
                    val uid = user?.get("id")?.jsonPrimitive?.content ?: ""
                    val em = user?.get("email")?.jsonPrimitive?.content
                    val meta = user?.get("user_metadata")?.jsonObject
                    val nm = (meta?.get("full_name") ?: meta?.get("name"))?.jsonPrimitive?.content
                    AuthCall.Ok(Tokens(access, refresh, uid, em?.ifBlank { null }, nm?.ifBlank { null }))
                } else {
                    AuthCall.Fail("Check your email to confirm your account.", needsConfirmation = true)
                }
            } else {
                val msg = obj?.get("msg")?.jsonPrimitive?.content
                    ?: obj?.get("error_description")?.jsonPrimitive?.content
                    ?: obj?.get("error")?.jsonPrimitive?.content
                    ?: "Something went wrong (${r.code})"
                Log.w("Supabase", "$label -> ${r.code}: ${text.take(160)}")
                AuthCall.Fail(msg)
            }
        }
    }.getOrElse { Log.w("Supabase", "$label failed: ${it.message}"); AuthCall.Fail(it.message ?: "Network error") }

    companion object {
        private const val ACCESS = "access_token"
        private const val REFRESH = "refresh_token"
        private const val USER_ID = "user_id"
        private const val EMAIL = "email"
        private const val NAME = "display_name"
        private const val KIND = "kind"
        /** Where the email-confirmation link lands after verifying (must be allow-listed in Supabase). */
        private const val EMAIL_REDIRECT = "https://github.com/flow6979/root"
    }
}
