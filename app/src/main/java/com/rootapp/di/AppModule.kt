package com.rootapp.di

import com.rootapp.BuildConfig
import com.rootapp.ai.FakeLlmClient
import com.rootapp.ai.GroqClient
import com.rootapp.ai.LlmClient

/**
 * Tiny manual DI. If no GROQ_API_KEY is configured we fall back to a friendly
 * offline client so the app still runs in demo builds.
 */
object AppModule {
    val llmClient: LlmClient by lazy {
        val key = BuildConfig.GROQ_API_KEY
        if (key.isBlank()) {
            FakeLlmClient(
                "I'm running in offline demo mode right now, but I'm still here. " +
                    "What's on your mind?",
            )
        } else {
            GroqClient(apiKey = key)
        }
    }
}
