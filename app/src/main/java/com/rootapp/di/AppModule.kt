package com.rootapp.di

import android.content.Context
import com.rootapp.BuildConfig
import com.rootapp.ai.FakeLlmClient
import com.rootapp.ai.GeminiClient
import com.rootapp.ai.GroqClient
import com.rootapp.ai.LlmClient
import com.rootapp.data.SettingsStore

/**
 * Tiny manual DI for the AI provider.
 *
 * [llmClient] is a getter (not a cached val) so it re-reads Settings on every access and
 * reflects the user's latest choice. Provider selection order:
 *   1. the user's own Gemini key (You -> AI), if set  -> GeminiClient (their quota)
 *   2. else Root's built-in free engine (Groq)         -> GroqClient
 *   3. else a friendly offline demo client
 *
 * Future: this is where a registry of providers (OpenAI, Anthropic, ...) would live, each
 * chosen the same way from a user-supplied key.
 */
object AppModule {
    private var appContext: Context? = null

    fun init(context: Context) { appContext = context.applicationContext }

    private fun userGeminiKey(): String =
        appContext?.let { SettingsStore(it).geminiApiKey }?.trim().orEmpty()

    val llmClient: LlmClient
        get() {
            val gemini = userGeminiKey()
            if (gemini.isNotBlank()) return GeminiClient(apiKey = gemini)
            val groq = BuildConfig.GROQ_API_KEY
            return if (groq.isBlank()) {
                FakeLlmClient(
                    "I'm running in offline demo mode right now, but I'm still here. " +
                        "What's on your mind?",
                )
            } else {
                GroqClient(apiKey = groq)
            }
        }

    /** Short label of the active provider, for display in Settings. */
    fun activeProviderLabel(): String = when {
        userGeminiKey().isNotBlank() -> "Your Gemini key"
        BuildConfig.GROQ_API_KEY.isNotBlank() -> "Root's free engine"
        else -> "Offline demo"
    }
}
