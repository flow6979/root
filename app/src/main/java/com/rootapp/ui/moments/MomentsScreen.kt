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
import androidx.compose.foundation.clickable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
    var nearbyList by remember { mutableStateOf<List<NearbyPlaces.Place>>(emptyList()) }

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
                            watchStatus = "No eating spots within ~400m right now."
                            nearbyList = emptyList()
                        } else {
                            geofence.registerNearby(places)
                            nearest = places.first()
                            nearbyList = places.take(5)
                            watchStatus = "Watching the ${minOf(places.size, 5)} closest. I'll nudge you near one."
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

    // Voice food logging (premium) via Groq Whisper. No Google dependency.
    val voiceRecorder = remember { com.rootapp.voice.VoiceRecorder(context) }
    var foodRecording by remember { mutableStateOf(false) }
    var foodSaving by remember { mutableStateOf(false) }
    fun logSpoken(text: String) {
        val junk = listOf("burger", "pizza", "fries", "soda", "coke", "chips", "candy", "fried", "ice cream", "donut")
        val healthy = junk.none { text.lowercase().contains(it) }
        store.addFood(text, healthy, System.currentTimeMillis())
        refreshFoods()
        scope.launch { supabase.pushFood(text, healthy) }
        Track.event(Events.FOOD_LOGGED, mapOf("healthy" to healthy, "source" to "voice"))
    }
    val recordPermFood = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) { if (voiceRecorder.start()) foodRecording = true }
        else Toast.makeText(context, "Microphone permission needed", Toast.LENGTH_SHORT).show()
    }
    fun stopFoodAndSave() {
        if (!foodRecording) return
        foodRecording = false
        val f = voiceRecorder.stop() ?: return
        foodSaving = true
        scope.launch {
            val text = com.rootapp.ai.GroqTranscriber.transcribe(f)
            if (text.isNullOrBlank()) {
                foodSaving = false
                Toast.makeText(context, "Didn't catch that.", Toast.LENGTH_SHORT).show()
                return@launch
            }
            // Pull out just the food (e.g. "I ate pizza today" -> pizza, unhealthy).
            val m = com.rootapp.ai.FoodExtractor.extract(com.rootapp.di.AppModule.llmClient, text)
            foodSaving = false
            if (m != null) {
                store.addFood(m.food, m.healthy, System.currentTimeMillis())
                refreshFoods()
                supabase.pushFood(m.food, m.healthy)
                Track.event(Events.FOOD_LOGGED, mapOf("healthy" to m.healthy, "source" to "voice"))
            } else {
                logSpoken(text) // fallback: store what we heard
            }
        }
    }
    val logByVoice = {
        if (foodRecording) {
            stopFoodAndSave()
        } else {
            val hasPerm = ContextCompat.checkSelfPermission(
                context, Manifest.permission.RECORD_AUDIO,
            ) == PackageManager.PERMISSION_GRANTED
            if (hasPerm) { if (voiceRecorder.start()) foodRecording = true }
            else recordPermFood.launch(Manifest.permission.RECORD_AUDIO)
        }
        Unit
    }
    LaunchedEffect(foodRecording) {
        if (foodRecording) {
            var spoke = false; var silenceMs = 0L; var totalMs = 0L
            while (foodRecording) {
                kotlinx.coroutines.delay(200)
                totalMs += 200
                val amp = voiceRecorder.amplitude()
                if (amp > 1800) { spoke = true; silenceMs = 0 } else if (spoke) silenceMs += 200
                if (spoke && silenceMs >= 1400) { stopFoodAndSave(); break }
                if (totalMs >= 20000) { stopFoodAndSave(); break }
            }
        }
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
                    if (nearest != null) "Nearest: ${nearest!!.name}, ${nearest!!.distanceM}m away."
                    else "Root finds restaurants and fast-food within about 400m of you, and nudges you when you walk near one.",
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
                if (nearbyList.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    nearbyList.forEach { p ->
                        Text("• ${p.name} · ${p.distanceM}m", fontSize = 12.sp, color = palette.onSurface,
                            modifier = Modifier.padding(vertical = 2.dp))
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))

        // ---- eating score ----
        val healthyCount = foods.count { it.healthy }
        val junkCount = foods.count { !it.healthy }
        val eatingScore = com.rootapp.data.Scores.eating(healthyCount, junkCount)
        var showDetails by remember { mutableStateOf(false) }
        var details by remember { mutableStateOf<String?>(null) }
        var loadingDetails by remember { mutableStateOf(false) }
        Card(shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = palette.surface),
            modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Eating score", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = palette.onSurface)
                        Text(
                            if (eatingScore == null) "Log meals to see your score"
                            else "${com.rootapp.data.Scores.label(eatingScore)} · $healthyCount healthy, $junkCount junk",
                            fontSize = 12.sp, color = palette.dim,
                        )
                    }
                    Text(eatingScore?.let { "$it" } ?: "—", fontSize = 30.sp,
                        fontWeight = FontWeight.Bold, color = palette.accent)
                }
                if (eatingScore != null) {
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = {
                            showDetails = true; loadingDetails = true; details = null
                            scope.launch {
                                val data = "Meals logged: " + foods.joinToString { "${it.label} (${if (it.healthy) "healthy" else "junk"})" }
                                details = com.rootapp.ai.InsightAnalyzer.analyze(
                                    com.rootapp.di.AppModule.llmClient,
                                    "Eating score of $eatingScore/100. Give each food a rough healthiness weight and explain the score",
                                    data,
                                )
                                loadingDetails = false
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("How is this calculated?") }
                }
            }
        }
        Spacer(Modifier.height(16.dp))

        if (showDetails) {
            AlertDialog(
                onDismissRequest = { showDetails = false },
                confirmButton = { TextButton(onClick = { showDetails = false }) { Text("Close") } },
                title = { Text("Your eating score") },
                text = {
                    if (loadingDetails) Text("Working it out…")
                    else Text(details ?: "Couldn't load details.")
                },
            )
        }

        Text("YOUR MEALS", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = palette.dim)
        Spacer(Modifier.height(8.dp))
        Card(shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = palette.surface),
            modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                if (foods.isEmpty()) {
                    Text("Nothing logged yet. Everything you log is kept, so you can look back.",
                        fontSize = 13.sp, color = palette.dim)
                } else {
                    // foods is newest-first; group into day sections (Today / Yesterday / date).
                    foods.groupBy { dayLabel(it.timestamp) }.forEach { (day, items) ->
                        Text(day, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = palette.dim)
                        Spacer(Modifier.height(8.dp))
                        items.forEach { f ->
                            FoodRow(f, onDelete = { store.removeFood(f.timestamp); refreshFoods() })
                            Spacer(Modifier.height(12.dp))
                        }
                        Spacer(Modifier.height(4.dp))
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
        ) {
            Text(
                when {
                    !premium -> "🎤 Log by voice (Premium)"
                    foodSaving -> "Saving…"
                    foodRecording -> "⏹ Stop & save"
                    else -> "🎤 Log by voice"
                },
            )
        }
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

private fun dayLabel(ts: Long): String {
    val d = java.time.Instant.ofEpochMilli(ts).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
    return when (java.time.temporal.ChronoUnit.DAYS.between(d, java.time.LocalDate.now())) {
        0L -> "Today"
        1L -> "Yesterday"
        else -> d.format(java.time.format.DateTimeFormatter.ofPattern("MMM d"))
    }
}

@Composable
private fun FoodRow(f: FoodEntry, onDelete: () -> Unit) {
    val palette = LocalRootPalette.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(if (f.healthy) "🥗" else "🍔", fontSize = 20.sp)
        Column(Modifier.padding(start = 12.dp).weight(1f)) {
            Text(f.label.ifBlank { "Meal" }, fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold, color = palette.onSurface)
            val time = java.time.Instant.ofEpochMilli(f.timestamp).atZone(java.time.ZoneId.systemDefault())
                .toLocalTime().format(java.time.format.DateTimeFormatter.ofPattern("h:mm a"))
            Text("$time · ${if (f.healthy) "healthy" else "junk"}", fontSize = 12.sp, color = palette.dim)
        }
        Text("✕", color = palette.dim, fontSize = 16.sp,
            modifier = Modifier.clickable { onDelete() }.padding(8.dp))
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
