package com.rootapp.ui.moments

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rootapp.analytics.Events
import com.rootapp.analytics.Track
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.rootapp.data.FoodEntry
import com.rootapp.data.LocalStore
import com.rootapp.data.SettingsStore
import com.rootapp.data.SupabaseRepository
import com.rootapp.location.GeofenceManager
import com.rootapp.location.NearbyPlaces
import com.rootapp.ui.theme.LocalRootPalette
import kotlinx.coroutines.launch

@Composable
fun MomentsScreen(modifier: Modifier = Modifier) {
    val palette = LocalRootPalette.current
    val context = LocalContext.current
    val store = remember { LocalStore(context) }
    val supabase = remember { SupabaseRepository(context) }
    val geofence = remember { GeofenceManager(context) }
    val scope = rememberCoroutineScope()
    val premium = remember { SettingsStore(context).premium }
    var foods by remember { mutableStateOf(store.foods().reversed()) }
    var showDialog by remember { mutableStateOf(false) }
    var watchStatus by remember { mutableStateOf<String?>(null) }
    var nearest by remember { mutableStateOf<NearbyPlaces.Place?>(null) }

    fun refreshFoods() { foods = store.foods().reversed() }

    // Find real eating spots nearby (OpenStreetMap) and watch them.
    @Suppress("MissingPermission")
    fun findNearby() {
        watchStatus = "Looking for eating spots nearby…"
        try {
            LocationServices.getFusedLocationProviderClient(context).lastLocation
                .addOnSuccessListener { loc ->
                    if (loc == null) { watchStatus = "No location yet. Move around and retry."; return@addOnSuccessListener }
                    scope.launch {
                        val places = NearbyPlaces.findFoodSpots(loc.latitude, loc.longitude)
                        if (places.isEmpty()) {
                            watchStatus = "No eating spots found nearby."
                        } else {
                            geofence.registerNearby(places)
                            nearest = places.first()
                            watchStatus = "Watching ${minOf(places.size, 5)} spots near you."
                        }
                    }
                }
        } catch (e: SecurityException) {
            watchStatus = "Location permission needed"
        }
    }

    val locationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> if (granted) findNearby() else watchStatus = "Location permission needed" }

    // Voice food logging (premium): speak a meal, we log it.
    val foodVoiceLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        val spoken = res.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (!spoken.isNullOrBlank()) {
            val junk = listOf("burger", "pizza", "fries", "soda", "coke", "chips", "candy", "fried", "ice cream", "donut")
            val healthy = junk.none { spoken.lowercase().contains(it) }
            store.addFood(spoken, healthy, System.currentTimeMillis())
            refreshFoods()
            scope.launch { supabase.pushFood(spoken, healthy) }
            Track.event(Events.FOOD_LOGGED, mapOf("healthy" to healthy, "source" to "voice"))
        }
    }
    val logByVoice = {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "What did you eat?")
        }
        runCatching { foodVoiceLauncher.launch(intent) }
            .onFailure { Toast.makeText(context, "No speech recognizer on this device", Toast.LENGTH_SHORT).show() }
        Unit
    }

    Column(
        modifier = modifier.fillMaxWidth().verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text("Moments", fontSize = 24.sp, fontWeight = FontWeight.SemiBold, color = palette.onSurface)
        Text("Caught in real life, gently", fontSize = 12.sp, color = palette.dim)
        Spacer(Modifier.height(16.dp))

        Card(shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = palette.accentSoft),
            modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    if (nearest != null) "📍 You're near ${nearest!!.name}" else "📍 Eating spots nearby",
                    fontSize = 14.sp, fontWeight = FontWeight.Bold, color = palette.accent,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    if (nearest != null) "${nearest!!.distanceM}m away. Want to pause before you decide?"
                    else "I'll find eating spots around you and nudge you before an impulse stop.",
                    fontSize = 13.sp, color = palette.onSurface,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = {
                        val granted = ContextCompat.checkSelfPermission(
                            context, Manifest.permission.ACCESS_FINE_LOCATION,
                        ) == PackageManager.PERMISSION_GRANTED
                        if (granted) findNearby() else locationLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Find eating spots near me") }
                watchStatus?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, fontSize = 12.sp, color = palette.dim)
                }
            }
        }
        Spacer(Modifier.height(16.dp))

        Text("TODAY'S LOG", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = palette.dim)
        Spacer(Modifier.height(8.dp))
        Card(shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = palette.surface),
            modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                if (foods.isEmpty()) {
                    Text("Nothing logged yet. Tap below to add your first meal.",
                        fontSize = 13.sp, color = palette.dim)
                } else {
                    foods.forEachIndexed { i, f ->
                        FoodRow(f)
                        if (i != foods.lastIndex) Spacer(Modifier.height(14.dp))
                    }
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        OutlinedButton(onClick = { showDialog = true }, modifier = Modifier.fillMaxWidth()) {
            Text("＋ Log what you ate")
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = {
                if (premium) logByVoice()
                else Toast.makeText(context, "Voice logging is a premium feature", Toast.LENGTH_SHORT).show()
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(if (premium) "🎤 Log by voice" else "🎤 Log by voice (Premium)") }
        Spacer(Modifier.height(24.dp))
    }

    if (showDialog) {
        LogFoodDialog(
            onDismiss = { showDialog = false },
            onSave = { label, healthy ->
                store.addFood(label, healthy, System.currentTimeMillis())
                foods = store.foods().reversed()
                Track.event(Events.FOOD_LOGGED, mapOf("healthy" to healthy))
                scope.launch { supabase.pushFood(label.ifBlank { "Meal" }, healthy) }
                showDialog = false
            },
        )
    }
}

@Composable
private fun FoodRow(f: FoodEntry) {
    val palette = LocalRootPalette.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(if (f.healthy) "🥗" else "🍔", fontSize = 20.sp)
        Spacer(Modifier.height(0.dp))
        Column(Modifier.padding(start = 12.dp).weight(1f)) {
            Text(f.label.ifBlank { "Meal" }, fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold, color = palette.onSurface)
            Text(if (f.healthy) "home / healthy" else "flagged", fontSize = 12.sp, color = palette.dim)
        }
        Text(if (f.healthy) "✓" else "⚠︎", color = if (f.healthy) palette.accent else palette.dim)
    }
}

@Composable
private fun LogFoodDialog(onDismiss: () -> Unit, onSave: (String, Boolean) -> Unit) {
    var label by remember { mutableStateOf("") }
    var healthy by remember { mutableStateOf(true) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Log a meal") },
        text = {
            Column {
                OutlinedTextField(
                    value = label, onValueChange = { label = it },
                    placeholder = { Text("What did you eat?") },
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = healthy, onClick = { healthy = true }, label = { Text("Healthy") })
                    FilterChip(selected = !healthy, onClick = { healthy = false }, label = { Text("Junk") })
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(label, healthy) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
