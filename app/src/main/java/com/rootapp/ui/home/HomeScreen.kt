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
import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.rememberCoroutineScope
import com.rootapp.analytics.Events
import com.rootapp.analytics.Track
import com.rootapp.data.LocalStore
import com.rootapp.data.SupabaseRepository
import com.rootapp.ui.theme.LocalRootPalette
import kotlinx.coroutines.launch
import java.time.LocalDate
import kotlin.math.roundToInt

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
    // Daily-average screen-time in minutes, read once (needs Usage access). null when not granted.
    val screenDailyAvgMin = remember {
        if (com.rootapp.shield.ShieldPermissions.hasUsageAccess(context)) {
            com.rootapp.shield.UsageStatsReader.dailyAverageMinutes(
                com.rootapp.shield.UsageStatsReader.lastSevenDays(context),
            )
        } else null
    }
    // Richer, weighted, explainable wellbeing score. Depends on selectedMood + streak so it
    // recomputes right after a check-in. Only signals actually present are weighted (missing data
    // is renormalised away, never penalised).
    val wellbeingResult = run {
        selectedMood
        com.rootapp.data.WellbeingScore.compute(
            com.rootapp.data.WellbeingScore.Inputs(
                recentMoods = store.moods().takeLast(7).map { it.mood },
                foodLabels = store.foods().map { it.label },
                screenDailyAvgMin = screenDailyAvgMin,
                streakDays = streak,
                reflectionCount = store.memory().size,
            ),
        )
    }
    val wellbeing = wellbeingResult.overall

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
                        Text(
                            "It's a weighted blend of what we can measure right now, each out of 100. " +
                                "We only weight the signals you have data for:",
                            fontSize = 13.sp,
                        )
                        Spacer(Modifier.height(10.dp))
                        if (wellbeingResult.components.isEmpty()) {
                            Text("Nothing logged yet - check in, log a meal, or reflect to start your score.", fontSize = 13.sp)
                        } else {
                            wellbeingResult.components.forEach { c ->
                                val pct = (c.weight * 100).roundToInt()
                                Text("• ${c.label}: ${c.subScore}  (weight $pct%)", fontSize = 13.sp)
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "Overall: ${wellbeing?.let { "$it/100" } ?: "not enough data yet"}. " +
                                "As you check in, log meals, reflect, and grant Usage access, it gets more accurate.",
                            fontSize = 13.sp,
                        )
                    }
                },
            )
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = palette.surface),
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(moodPrompt, fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold, color = palette.onSurface)
                Text("One tap. Helps Root learn your rhythm.", fontSize = 12.sp, color = palette.dim)
                Spacer(Modifier.height(14.dp))
                // Clean 1-5 segmented check-in. One tap saves the mood (still index 0..4) and keeps
                // the wellbeing score + mood state in sync. Intentional and low-emoji by design.
                MoodSelector(
                    selected = selectedMood,
                    onSelect = { i ->
                        streak = store.addMood(i, today, System.currentTimeMillis())
                        selectedMood = i
                        Track.event(Events.MOOD_LOGGED)
                        scope.launch { supabase.pushMood(today, i) }
                    },
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // ---- "For you": data-driven insight cards ----
        val insights = remember(selectedMood, streak) {
            val top = if (com.rootapp.shield.ShieldPermissions.hasUsageAccess(context)) {
                com.rootapp.shield.UsageStatsReader.topApps(context, limit = 1).firstOrNull()
            } else null
            com.rootapp.data.Insights.build(
                com.rootapp.data.Insights.Inputs(
                    screenDailyAvgMin = screenDailyAvgMin,
                    topAppLabel = top?.label,
                    topAppMinutes = top?.minutes ?: 0,
                    foodLabels = store.foods().map { it.label },
                    recentMoods = store.moods().takeLast(7).map { it.mood },
                    latestIntention = store.latestTakeaway()?.intention,
                ),
            )
        }
        if (insights.isNotEmpty()) {
            Text("For you", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = palette.onSurface)
            Spacer(Modifier.height(10.dp))
            insights.forEach { card ->
                InsightCardView(card)
                Spacer(Modifier.height(10.dp))
            }
            Spacer(Modifier.height(10.dp))
        }

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

/**
 * A clean 1-5 segmented mood check-in. Five labelled pills (Low .. Great) map to mood index 0..4.
 * The chosen pill is filled with the accent; the rest stay quiet. One tap = one save. Intentional
 * and calm - a deliberate move away from the old raw-emoji row.
 */
@Composable
private fun MoodSelector(
    selected: Int?,
    onSelect: (Int) -> Unit,
) {
    val palette = LocalRootPalette.current
    val labels = listOf("Low", "Meh", "Okay", "Good", "Great")
    val onAccent = if (palette.dark) androidx.compose.ui.graphics.Color(0xFF06101F) else androidx.compose.ui.graphics.Color.White
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        labels.forEachIndexed { i, label ->
            val chosen = selected == i
            androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .weight(1f)
                    .height(38.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (chosen) palette.accent else palette.accentSoft)
                    .clickable { onSelect(i) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    fontSize = 12.sp,
                    fontWeight = if (chosen) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (chosen) onAccent else palette.onSurface,
                )
            }
        }
    }
}

/** A single glassy "For you" insight: title, observation, and a gentle suggestion line. */
@Composable
private fun InsightCardView(card: com.rootapp.data.Insights.InsightCard) {
    val palette = LocalRootPalette.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = palette.surface),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(card.title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = palette.onSurface)
            Spacer(Modifier.height(4.dp))
            Text(card.body, fontSize = 13.sp, color = palette.onSurface)
            Spacer(Modifier.height(6.dp))
            Text(card.suggestion, fontSize = 13.sp, color = palette.accent)
        }
    }
}
