package com.rootapp.analytics

import android.util.Log
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

/**
 * Minimal PostHog capture over HTTP (no SDK). Fire-and-forget so it never blocks the UI, and it
 * only ever sends aggregate event names + non-content props (see docs/ANALYTICS.md).
 */
class PostHogAnalytics(
    private val host: String,
    private val apiKey: String,
    private val distinctId: String,
    private val http: OkHttpClient = OkHttpClient(),
) : Analytics {
    private val json = Json { encodeDefaults = true }
    private val media = "application/json; charset=utf-8".toMediaType()

    override fun track(event: String, props: Map<String, Any?>) {
        val payload: JsonObject = buildJsonObject {
            put("api_key", apiKey)
            put("event", event)
            put("distinct_id", distinctId)
            put(
                "properties",
                buildJsonObject { props.forEach { (k, v) -> put(k, v?.toString() ?: "") } },
            )
        }
        val req = Request.Builder()
            .url("${host.trimEnd('/')}/capture/")
            .post(json.encodeToString(JsonObject.serializer(), payload).toRequestBody(media))
            .build()
        runCatching {
            http.newCall(req).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) { Log.w("PostHog", "send failed: ${e.message}") }
                override fun onResponse(call: Call, response: okhttp3.Response) { response.close() }
            })
        }
    }
}
