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

/** My position on the weekly leaderboard. */
data class LeaderStanding(
    val rank: Int,
    val players: Int,
    val effortPercentile: Int,
    val points: Int,
    val growthPercentile: Int,
)

/** One row on the weekly leaderboard. */
data class LeaderRow(val username: String, val points: Int, val rank: Int, val isMe: Boolean)

/** One member of my weekly division (Phase B), carrying the division's tier + size. */
data class LeagueMember(
    val username: String,
    val points: Int,
    val rank: Int,
    val isMe: Boolean,
    val tier: Int,
    val leagueSize: Int,
)

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

    // ---- leaderboard (Phase A) ----

    /** Create or update the caller's public username. Returns true on success. */
    suspend fun setUsername(name: String): Boolean = withContext(Dispatchers.IO) {
        val token = ensureSession() ?: return@withContext false
        val uid = userId ?: return@withContext false
        val body = buildJsonObject { put("user_id", uid); put("username", name.trim()) }
        val req = Request.Builder()
            .url("$baseUrl/rest/v1/profiles")
            .addHeader("apikey", anonKey)
            .addHeader("Authorization", "Bearer $token")
            .addHeader("Prefer", "resolution=merge-duplicates,return=minimal")
            .post(json.encodeToString(JsonObject.serializer(), body).toRequestBody(jsonMedia))
            .build()
        runCatching {
            http.newCall(req).execute().use { r ->
                if (!r.isSuccessful) Log.w("Supabase", "setUsername -> ${r.code}: ${r.body?.string()?.take(160)}")
                r.isSuccessful
            }
        }.getOrElse { Log.w("Supabase", "setUsername failed: ${it.message}"); false }
    }

    /** The caller's chosen username, or null if none set / offline. */
    suspend fun getUsername(): String? = withContext(Dispatchers.IO) {
        val token = ensureSession() ?: return@withContext null
        val uid = userId ?: return@withContext null
        val text = authedGet("rest/v1/profiles?select=username&user_id=eq.$uid", token) ?: return@withContext null
        runCatching {
            json.parseToJsonElement(text).jsonArray.firstOrNull()?.jsonObject
                ?.get("username")?.jsonPrimitive?.content
        }.getOrNull()
    }

    /** Submit this week's effort total + wellbeing score. [week] is the Monday date (yyyy-MM-dd). */
    suspend fun submitScore(week: String, effort: Int, wellbeing: Int?): Boolean = withContext(Dispatchers.IO) {
        val body = buildJsonObject {
            put("p_week", week)
            put("p_effort", effort)
            if (wellbeing == null) put("p_wellbeing", kotlinx.serialization.json.JsonNull) else put("p_wellbeing", wellbeing)
        }
        rpc("submit_score", body) != null
    }

    /** My rank + effort/growth percentiles for the week, or null if I have no score yet. */
    suspend fun myStanding(week: String): LeaderStanding? = withContext(Dispatchers.IO) {
        val body = buildJsonObject { put("p_week", week) }
        val text = rpc("get_my_standing", body) ?: return@withContext null
        runCatching {
            val o = json.parseToJsonElement(text).jsonArray.firstOrNull()?.jsonObject ?: return@runCatching null
            LeaderStanding(
                rank = o["my_rank"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                players = o["players"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                effortPercentile = o["effort_percentile"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                points = o["my_points"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                growthPercentile = o["growth_percentile"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
            )
        }.getOrNull()
    }

    /** The week's top players (username, points, rank, and whether it's me). */
    suspend fun leaderboard(week: String, limit: Int = 50): List<LeaderRow> = withContext(Dispatchers.IO) {
        val body = buildJsonObject { put("p_week", week); put("p_limit", limit) }
        val text = rpc("get_leaderboard", body) ?: return@withContext emptyList()
        runCatching {
            json.parseToJsonElement(text).jsonArray.map { el ->
                val o = el.jsonObject
                LeaderRow(
                    username = o["username"]?.jsonPrimitive?.content ?: "anon",
                    points = o["effort_points"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                    rank = o["rnk"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                    isMe = o["is_me"]?.jsonPrimitive?.content?.toBoolean() ?: false,
                )
            }
        }.getOrDefault(emptyList())
    }

    /** My weekly division's board (Phase B): members ranked, with tier + league size. */
    suspend fun myDivision(week: String): List<LeagueMember> = withContext(Dispatchers.IO) {
        val body = buildJsonObject { put("p_week", week) }
        val text = rpc("get_my_division", body) ?: return@withContext emptyList()
        runCatching {
            json.parseToJsonElement(text).jsonArray.map { el ->
                val o = el.jsonObject
                LeagueMember(
                    username = o["username"]?.jsonPrimitive?.content ?: "anon",
                    points = o["effort_points"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                    rank = o["rnk"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                    isMe = o["is_me"]?.jsonPrimitive?.content?.toBoolean() ?: false,
                    tier = o["tier"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                    leagueSize = o["league_size"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                )
            }
        }.getOrDefault(emptyList())
    }

    private suspend fun rpc(fn: String, body: JsonObject): String? {
        val token = ensureSession() ?: return null
        val req = Request.Builder()
            .url("$baseUrl/rest/v1/rpc/$fn")
            .addHeader("apikey", anonKey)
            .addHeader("Authorization", "Bearer $token")
            .post(json.encodeToString(JsonObject.serializer(), body).toRequestBody(jsonMedia))
            .build()
        return runCatching {
            http.newCall(req).execute().use { r ->
                val t = r.body?.string()
                if (!r.isSuccessful) { Log.w("Supabase", "rpc $fn -> ${r.code}: ${t?.take(160)}"); null } else t
            }
        }.getOrElse { Log.w("Supabase", "rpc $fn failed: ${it.message}"); null }
    }

    private fun authedGet(path: String, token: String): String? = runCatching {
        val req = Request.Builder()
            .url("$baseUrl/$path")
            .addHeader("apikey", anonKey)
            .addHeader("Authorization", "Bearer $token")
            .get()
            .build()
        http.newCall(req).execute().use { r -> if (r.isSuccessful) r.body?.string() else null }
    }.getOrNull()

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

    /**
     * Verify a signup with the 6-digit code emailed to the user (email OTP). On success this
     * returns a real session, so the user is logged straight in - no web link, no redirect. The
     * Supabase "Confirm signup" email template must include the code token ({{ .Token }}).
     */
    suspend fun verifyEmailOtp(emailAddr: String, token: String): AuthResult = withContext(Dispatchers.IO) {
        if (!configured) return@withContext AuthResult.Error("Not configured")
        val body = buildJsonObject {
            put("type", "signup")
            put("email", emailAddr.trim())
            put("token", token.trim())
        }
        val req = Request.Builder()
            .url("$baseUrl/auth/v1/verify")
            .addHeader("apikey", anonKey)
            .post(json.encodeToString(JsonObject.serializer(), body).toRequestBody(jsonMedia))
            .build()
        toResult(authCallResult(req, "verify-otp"), "email")
    }

    /** Re-send the signup confirmation code. Returns true if the request was accepted. */
    suspend fun resendSignupOtp(emailAddr: String): Boolean = withContext(Dispatchers.IO) {
        if (!configured) return@withContext false
        val body = buildJsonObject { put("type", "signup"); put("email", emailAddr.trim()) }
        val req = Request.Builder()
            .url("$baseUrl/auth/v1/resend")
            .addHeader("apikey", anonKey)
            .post(json.encodeToString(JsonObject.serializer(), body).toRequestBody(jsonMedia))
            .build()
        runCatching { http.newCall(req).execute().use { it.isSuccessful } }.getOrDefault(false)
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
