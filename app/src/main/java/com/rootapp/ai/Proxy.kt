package com.rootapp.ai

import com.rootapp.BuildConfig

/**
 * Backend proxy config (see /proxy). When [enabled], the app routes AI calls through our
 * Cloudflare Worker, which holds the real Groq/ElevenLabs keys, so the APK ships none.
 * The base URL is public (not a secret); the app token is a light gate.
 */
object Proxy {
    val baseUrl: String get() = BuildConfig.PROXY_BASE_URL.trimEnd('/')
    val enabled: Boolean get() = baseUrl.isNotBlank()
    private val appToken: String get() = BuildConfig.PROXY_APP_TOKEN

    /** Header to attach to every proxied request (empty if no app token is configured). */
    fun headers(): Map<String, String> =
        if (appToken.isNotBlank()) mapOf("x-root-key" to appToken) else emptyMap()
}
