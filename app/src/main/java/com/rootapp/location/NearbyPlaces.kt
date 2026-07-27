package com.rootapp.location

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLEncoder
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Finds nearby eating spots via the free OpenStreetMap Overpass API (no key needed). */
object NearbyPlaces {
    /** kind: fast_food | restaurant | cafe | food_court. */
    data class Place(val name: String, val lat: Double, val lng: Double, val distanceM: Int, val kind: String) {
        val isJunk: Boolean get() = kind == "fast_food" || kind == "food_court"
        val healthLabel: String get() = when (kind) {
            "fast_food", "food_court" -> "fast food"
            "cafe" -> "cafe"
            else -> "restaurant"
        }
    }

    private val http = OkHttpClient()
    private val json = Json { ignoreUnknownKeys = true }
    private val endpoints = listOf(
        "https://overpass-api.de/api/interpreter",
        "https://overpass.kumi.systems/api/interpreter",
        "https://maps.mail.ru/osm/tools/overpass/api/interpreter",
    )
    private val formMedia = "application/x-www-form-urlencoded".toMediaType()

    /** Searches at [radiusM]; if nothing, widens to 3km, then 8km. */
    suspend fun findFoodSpots(lat: Double, lng: Double, radiusM: Int = 1500): List<Place> =
        withContext(Dispatchers.IO) {
            for (r in listOf(radiusM, 3000, 8000)) {
                val found = query(lat, lng, r)
                if (found.isNotEmpty()) return@withContext found
            }
            emptyList()
        }

    private fun query(lat: Double, lng: Double, radiusM: Int): List<Place> {
        // nwr = nodes + ways + relations; "out center" gives a point for ways/relations too.
        val q = "[out:json][timeout:25];nwr(around:$radiusM,$lat,$lng)[amenity~\"fast_food|restaurant|cafe|food_court\"];out center 50;"
        val body = ("data=" + URLEncoder.encode(q, "UTF-8")).toRequestBody(formMedia)
        for (endpoint in endpoints) {
            val req = Request.Builder()
                .url(endpoint)
                .header("User-Agent", "RootApp/1.0 (digital-wellbeing app)")
                .header("Accept", "application/json")
                .post(body)
                .build()
            val result = runCatching {
                http.newCall(req).execute().use { r ->
                    if (!r.isSuccessful) return@use null
                    val els = json.parseToJsonElement(r.body?.string().orEmpty())
                        .jsonObject["elements"]?.jsonArray ?: return@use emptyList<Place>()
                    els.mapNotNull { e ->
                        val o = e.jsonObject
                        val center = o["center"]?.jsonObject
                        val plat = (o["lat"] ?: center?.get("lat"))?.jsonPrimitive?.content?.toDoubleOrNull() ?: return@mapNotNull null
                        val plng = (o["lon"] ?: center?.get("lon"))?.jsonPrimitive?.content?.toDoubleOrNull() ?: return@mapNotNull null
                        val tags = o["tags"]?.jsonObject
                        val name = tags?.get("name")?.jsonPrimitive?.content ?: return@mapNotNull null
                        val kind = tags["amenity"]?.jsonPrimitive?.content ?: "restaurant"
                        Place(name, plat, plng, haversineM(lat, lng, plat, plng), kind)
                    }.sortedBy { it.distanceM }
                }
            }.getOrNull()
            if (result != null) return result
        }
        return emptyList()
    }

    private fun haversineM(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Int {
        val r = 6371000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2) * sin(dLon / 2)
        return (r * 2 * atan2(sqrt(a), sqrt(1 - a))).toInt()
    }
}
