package com.rootapp

import android.app.Application
import com.rootapp.analytics.PostHogAnalytics
import com.rootapp.analytics.Track
import com.rootapp.di.AppModule
import io.sentry.android.core.SentryAndroid
import java.util.UUID

/** Application entry point. Kept minimal; DI lives in com.rootapp.di.AppModule. */
class RootApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppModule.init(this)
        com.rootapp.shield.FocusSession.init(this)

        // Crash reporting (only if a DSN is configured).
        if (BuildConfig.SENTRY_DSN.isNotBlank()) {
            runCatching { SentryAndroid.init(this) { it.dsn = BuildConfig.SENTRY_DSN } }
        }
        // Product analytics (only if a project key is configured). Anonymous per-install id.
        if (BuildConfig.POSTHOG_KEY.isNotBlank()) {
            Track.impl = PostHogAnalytics(BuildConfig.POSTHOG_HOST, BuildConfig.POSTHOG_KEY, anonId())
        }
    }

    private fun anonId(): String {
        val p = getSharedPreferences("analytics", MODE_PRIVATE)
        return p.getString("aid", null) ?: UUID.randomUUID().toString().also { p.edit().putString("aid", it).apply() }
    }
}
