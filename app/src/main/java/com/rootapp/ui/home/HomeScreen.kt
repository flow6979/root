package com.rootapp.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.foundation.clickable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.rememberCoroutineScope
import com.rootapp.analytics.Events
import com.rootapp.analytics.Track
import com.rootapp.data.LocalStore
import com.rootapp.data.SupabaseRepository
import com.rootapp.ui.common.Orb
import com.rootapp.ui.theme.LocalRootPalette
import kotlinx.coroutines.launch
import java.time.LocalDate

@Composable
fun HomeScreen(
    userName: String,
    onStartReflection: () -> Unit,
    onTalk: () -> Unit = onStartReflection,
    modifier: Modifier = Modifier,
) {
    val palette = LocalRootPalette.current
    val greetingName = userName.ifBlank { "there" }
    val context = LocalContext.current
    val store = remember { LocalStore(context) }
    val supabase = remember { SupabaseRepository(context) }
    val scope = rememberCoroutineScope()
    val today = remember { LocalDate.now().toEpochDay() }
    var streak by remember { mutableIntStateOf(store.streak()) }
    var selectedMood by remember { mutableStateOf(store.todaysMood(today)) }
    val streakText = if (streak > 0) "Day $streak" else "Let's start today"
    val hour = remember { java.time.LocalTime.now().hour }
    val moodPrompt = remember(hour) {
        when {
            hour < 11 -> "How did you sleep?"
            hour < 17 -> "How's your day going?"
            hour < 22 -> "How's your evening?"
            else -> "Winding down. How do you feel?"
        }
    }
    // Wellbeing score from all signals we have. Screen time read once (needs Usage access).
    val screenScore = remember {
        if (com.rootapp.shield.ShieldPermissions.hasUsageAccess(context)) {
            com.rootapp.data.Scores.screen(
                com.rootapp.shield.UsageStatsReader.dailyAverageMinutes(com.rootapp.shield.UsageStatsReader.lastSevenDays(context)),
            )
        } else null
    }
    // Depends on selectedMood so it recomputes right after a check-in.
    val moodScore = run { selectedMood; com.rootapp.data.Scores.mood(store.moods().takeLast(7).map { it.mood }) }
    val foods = store.foods()
    val eatingScore = com.rootapp.data.Scores.eating(foods.count { it.healthy }, foods.count { !it.healthy })
    val wellbeing = com.rootapp.data.Scores.overall(listOfNotNull(moodScore, eatingScore, screenScore))

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp),
    ) {
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Hey, $greetingName",
            fontSize = 26.sp,
            fontWeight = FontWeight.SemiBold,
            color = palette.onSurface,
        )
        Text(
            text = streakText,
            fontSize = 13.sp,
            color = palette.dim,
        )
        Spacer(Modifier.height(16.dp))

        // ---- wellbeing score ----
        var showScoreInfo by remember { mutableStateOf(false) }
        Card(
            modifier = Modifier.fillMaxWidth().clickable { showScoreInfo = true },
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = palette.surface),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Wellbeing score", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = palette.onSurface)
                    Text(
                        if (wellbeing == null) "Check in and log to build your score"
                        else com.rootapp.data.Scores.label(wellbeing) + " · tap to see how",
                        fontSize = 12.sp, color = palette.dim,
                    )
                }
                Text(wellbeing?.let { "$it" } ?: "—", fontSize = 34.sp, fontWeight = FontWeight.Bold, color = palette.accent)
            }
        }
        Spacer(Modifier.height(14.dp))

        if (showScoreInfo) {
            AlertDialog(
                onDismissRequest = { showScoreInfo = false },
                confirmButton = { TextButton(onClick = { showScoreInfo = false }) { Text("Got it") } },
                title = { Text("How your score works") },
                text = {
                    Column {
                        Text("It's the average of what we can measure right now, each out of 100:", fontSize = 13.sp)
                        Spacer(Modifier.height(10.dp))
                        Text("• Mood: ${moodScore?.let { "$it" } ?: "—"}  (from your check-ins)", fontSize = 13.sp)
                        Text("• Eating: ${eatingScore?.let { "$it" } ?: "—"}  (share of healthy meals)", fontSize = 13.sp)
                        Text("• Screen time: ${screenScore?.let { "$it" } ?: "—"}  (less is better)", fontSize = 13.sp)
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "Overall: ${wellbeing?.let { "$it/100" } ?: "not enough data yet"}. " +
                                "As you check in, log meals, and grant Usage access, it gets more accurate.",
                            fontSize = 13.sp,
                        )
                    }
                },
            )
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(26.dp),
            colors = CardDefaults.cardColors(containerColor = palette.surface),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 22.dp, horizontal = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Orb(size = 72.dp, interactive = true)
                Spacer(Modifier.height(14.dp))
                Text(
                    text = "Good to see you. Want to talk?",
                    fontSize = 15.sp,
                    color = palette.onSurface,
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = palette.surface),
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(moodPrompt, fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold, color = palette.onSurface)
                Text("One tap. Helps Root learn your rhythm.", fontSize = 12.sp, color = palette.dim)
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround,
                ) {
                    listOf("😔", "😐", "🙂", "😌", "⚡️").forEachIndexed { i, emoji ->
                        Text(
                            text = emoji,
                            fontSize = 26.sp,
                            modifier = Modifier
                                .alpha(if (selectedMood == null || selectedMood == i) 1f else 0.35f)
                                .clickable {
                                    streak = store.addMood(i, today, System.currentTimeMillis())
                                    selectedMood = i
                                    Track.event(Events.MOOD_LOGGED)
                                    scope.launch { supabase.pushMood(today, i) }
                                },
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        Button(
            onClick = onStartReflection,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Start a reflection session", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(10.dp))
        androidx.compose.material3.OutlinedButton(
            onClick = onTalk,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("🎤 Talk to Root", fontSize = 15.sp, color = palette.accent) }
        Spacer(Modifier.height(8.dp))
        Text(
            text = "5 min · just talk, I'll listen",
            fontSize = 12.sp,
            color = palette.dim,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
