package com.rootapp.location

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices

/** Registers/removes a single "food spot" geofence that triggers [GeofenceReceiver]. */
class GeofenceManager(private val context: Context) {
    private val client = LocationServices.getGeofencingClient(context.applicationContext)

    private val pendingIntent: PendingIntent by lazy {
        val intent = Intent(context.applicationContext, GeofenceReceiver::class.java)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)
        PendingIntent.getBroadcast(context.applicationContext, 0, intent, flags)
    }

    @SuppressLint("MissingPermission") // caller ensures location permission is granted
    fun registerFoodSpot(lat: Double, lng: Double, radiusMeters: Float = 150f, onResult: (Boolean) -> Unit = {}) {
        val geofence = Geofence.Builder()
            .setRequestId(FOOD_SPOT)
            .setCircularRegion(lat, lng, radiusMeters)
            .setExpirationDuration(Geofence.NEVER_EXPIRE)
            .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER)
            .build()
        val request = GeofencingRequest.Builder()
            .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
            .addGeofence(geofence)
            .build()
        client.addGeofences(request, pendingIntent)
            .addOnSuccessListener { Log.d("Geofence", "registered at $lat,$lng"); onResult(true) }
            .addOnFailureListener { Log.w("Geofence", "register failed: ${it.message}"); onResult(false) }
    }

    @SuppressLint("MissingPermission")
    fun registerNearby(places: List<NearbyPlaces.Place>, onResult: (Boolean) -> Unit = {}) {
        if (places.isEmpty()) { onResult(false); return }
        val fences = places.take(5).mapIndexed { i, p ->
            Geofence.Builder()
                .setRequestId("nearby_$i")
                .setCircularRegion(p.lat, p.lng, 120f)
                .setExpirationDuration(Geofence.NEVER_EXPIRE)
                .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER)
                .build()
        }
        val request = GeofencingRequest.Builder()
            .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
            .addGeofences(fences)
            .build()
        client.addGeofences(request, pendingIntent)
            .addOnSuccessListener { Log.d("Geofence", "registered ${fences.size} nearby"); onResult(true) }
            .addOnFailureListener { Log.w("Geofence", "nearby register failed: ${it.message}"); onResult(false) }
    }

    fun clear() = client.removeGeofences(listOf(FOOD_SPOT))

    companion object {
        private const val FOOD_SPOT = "food_spot"
    }
}
