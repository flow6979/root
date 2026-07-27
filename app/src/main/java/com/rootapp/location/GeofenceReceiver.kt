package com.rootapp.location

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import com.rootapp.analytics.Events
import com.rootapp.analytics.Track
import com.rootapp.shield.InterruptOverlay

/** Fires when the user enters a watched real-world spot; shows a gentle food-choice pause. */
class GeofenceReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val event = GeofencingEvent.fromIntent(intent) ?: return
        if (event.hasError()) {
            Log.w("Geofence", "error code ${event.errorCode}")
            return
        }
        if (event.geofenceTransition == Geofence.GEOFENCE_TRANSITION_ENTER) {
            Track.event(Events.INTERRUPT_SHOWN, mapOf("source" to "geofence"))
            InterruptOverlay(context).show(
                titleText = "You're near a food spot.",
                subtitleText = "Pause 10 seconds before you decide?",
                onPause = { Track.event(Events.INTERRUPT_PAUSED, mapOf("source" to "geofence")) },
                onProceed = { Track.event(Events.INTERRUPT_OPENED_ANYWAY, mapOf("source" to "geofence")) },
            )
        }
    }
}
