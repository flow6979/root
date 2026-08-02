package com.rootapp.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.LocationManager
import android.util.Log
import androidx.core.location.LocationManagerCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import kotlin.math.roundToInt
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

    /** The widest radius we search (see [findFoodSpots]); used only for user-facing copy. */
    const val WIDEST_RADIUS_M = 8000

    /**
     * User-facing status when a search returns nothing, e.g. "No eating spots found within 8km".
     * Pure string helper so it can be unit-tested without Android. Uses a plain hyphen, no em-dash.
     */
    fun noResultsMessage(widestRadiusM: Int = WIDEST_RADIUS_M): String {
        val km = (widestRadiusM / 1000.0).let {
            // Show a whole number when it is one (8.0 -> "8"), else one decimal.
            if (it == it.roundToInt().toDouble()) it.roundToInt().toString() else String.format("%.1f", it)
        }
        return "No eating spots found within ${km}km"
    }
}

/**
 * Obtains a device location for the "find eating spots" flow.
 *
 * Root cause of the old bug: it relied on FusedLocationProviderClient.lastLocation, which is
 * frequently null on real devices (fresh boot, no recent GPS consumer, battery-saver). We now do
 * an ACTIVE fetch via getCurrentLocation(PRIORITY_HIGH_ACCURACY), which powers up the sensors and
 * returns a fresh fix, and only fall back to lastLocation if that yields nothing.
 */
object LocationFetcher {
    private const val TAG = "NearbyPlaces"

    /** Simple result so the UI can render the right status without catching exceptions itself. */
    sealed interface Result {
        data class Ok(val lat: Double, val lng: Double) : Result
        /** Location services (GPS/network) are turned OFF at the OS level. */
        object ServicesOff : Result
        /** Permission missing (should be handled before calling, but guarded anyway). */
        object PermissionDenied : Result
        /** Fetch ran but produced no usable fix within the timeout. */
        object NoFix : Result
    }

    /**
     * Actively fetches a location. Must be called only after fine/coarse permission is granted.
     * [timeoutMs] guards against the UI hanging if the sensors never return a fix.
     */
    @SuppressLint("MissingPermission") // caller guarantees permission (guarded again below)
    suspend fun fetch(context: Context, timeoutMs: Long = 12_000): Result {
        if (!hasPermission(context)) {
            Log.w(TAG, "fetch aborted: location permission not granted")
            return Result.PermissionDenied
        }
        if (!servicesEnabled(context)) {
            Log.w(TAG, "fetch aborted: OS location services are OFF")
            return Result.ServicesOff
        }

        val client = LocationServices.getFusedLocationProviderClient(context.applicationContext)
        val cts = CancellationTokenSource()

        val fix = withTimeoutOrNull(timeoutMs) {
            // 1) Active high-accuracy fetch - powers up sensors, works even with no cached fix.
            val current = runCatching {
                suspendCancellableCoroutine<android.location.Location?> { cont ->
                    client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.token)
                        .addOnSuccessListener { cont.resume(it) }
                        .addOnFailureListener { e ->
                            Log.w(TAG, "getCurrentLocation failed: ${e.message}")
                            cont.resume(null)
                        }
                    cont.invokeOnCancellation { cts.cancel() }
                }
            }.getOrNull()
            if (current != null) return@withTimeoutOrNull current

            // 2) Fallback to the last cached fix if the active fetch came back null.
            Log.d(TAG, "getCurrentLocation null, falling back to lastLocation")
            runCatching {
                suspendCancellableCoroutine<android.location.Location?> { cont ->
                    client.lastLocation
                        .addOnSuccessListener { cont.resume(it) }
                        .addOnFailureListener { e ->
                            Log.w(TAG, "lastLocation failed: ${e.message}")
                            cont.resume(null)
                        }
                }
            }.getOrNull()
        }

        cts.cancel()
        return if (fix != null) {
            Log.d(TAG, "got fix: ${fix.latitude},${fix.longitude}")
            Result.Ok(fix.latitude, fix.longitude)
        } else {
            Log.w(TAG, "no fix obtained within ${timeoutMs}ms")
            Result.NoFix
        }
    }

    private fun hasPermission(context: Context): Boolean {
        val fine = androidx.core.content.ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.ACCESS_FINE_LOCATION,
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        val coarse = androidx.core.content.ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.ACCESS_COARSE_LOCATION,
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    private fun servicesEnabled(context: Context): Boolean {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return false
        return LocationManagerCompat.isLocationEnabled(lm)
    }
}
