package com.rootapp.ui.home

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.DirectionsWalk
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.PhoneIphone
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rootapp.analytics.Events
import com.rootapp.analytics.Track
import com.rootapp.data.LocalStore
import com.rootapp.data.SupabaseRepository
import com.rootapp.ui.common.GlassCard
import com.rootapp.ui.common.IconTile
import com.rootapp.ui.common.ScoreRing
import com.rootapp.ui.common.SectionLabel
import com.rootapp.ui.common.enterUp
import com.rootapp.ui.theme.LocalRootPalette
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

@Composable
fun HomeScreen(
    userName: String,
    onTalk: () -> Unit,
    onOpenWeekly: () -> Unit = {},
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
    val dateLine = remember { LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, MMM d")) }
    val hour = remember { java.time.LocalTime.now().hour }
    val moodPrompt = remember(hour) {
        when {
            hour < 11 -> "How did you sleep?"
            hour < 17 -> "How's your day going?"
            hour < 22 -> "How's your evening?"
            else -> "Winding down. How do you feel?"
        }
    }
    val hasUsage = remember { com.rootapp.shield.ShieldPermissions.hasUsageAccess(context) }
    val screenDailyAvgMin = remember {
        if (hasUsage) {
            com.rootapp.shield.UsageStatsReader.dailyAverageMinutes(
                com.rootapp.shield.UsageStatsReader.lastSevenDays(context),
            )
        } else null
    }
    val lastSleep = remember { if (hasUsage) com.rootapp.shield.UsageStatsReader.lastNightSleep(context) else null }
    val sleepConsistency = remember { if (hasUsage) com.rootapp.shield.UsageStatsReader.bedtimeConsistency(context) else null }
    // Steps (on-device step counter).
    val stepsAvailable = remember { com.rootapp.shield.StepCounter.available(context) }
    var stepPerm by remember { mutableStateOf(com.rootapp.shield.StepCounter.hasPermission(context)) }
    var steps by remember { mutableStateOf<Int?>(null) }
    val stepGoal = remember { com.rootapp.data.SettingsStore(context).stepGoal }
    val stepPermLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        stepPerm = granted
        if (granted) com.rootapp.shield.StepCounter.sampleOnce(context) { steps = it }
    }
    LaunchedEffect(stepPerm) {
        if (stepsAvailable && stepPerm) com.rootapp.shield.StepCounter.sampleOnce(context) { steps = it }
    }
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
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp),
    ) {
        Spacer(Modifier.height(18.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Hey, $greetingName", fontSize = 26.sp, fontWeight = FontWeight.SemiBold, color = palette.onSurface)
                Text(dateLine, fontSize = 13.sp, color = palette.dim)
            }
            if (streak > 0) {
                Box(
                    Modifier.clip(RoundedCornerShape(20.dp)).background(palette.accentSoft)
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                ) {
                    Text("Day $streak", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = palette.accent)
                }
            }
        }
        Spacer(Modifier.height(18.dp))

        // ---- wellbeing hero (score ring) ----
        var showScoreInfo by remember { mutableStateOf(false) }
        GlassCard(Modifier.enterUp(0).clickable { showScoreInfo = true }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ScoreRing(wellbeing, "of 100", size = 104.dp, stroke = 11.dp)
                Spacer(Modifier.width(18.dp))
                Column(Modifier.weight(1f)) {
                    SectionLabel("Wellbeing")
                    Spacer(Modifier.height(6.dp))
                    Text(
                        wellbeing?.let { com.rootapp.data.Scores.label(it) } ?: "Let's start",
                        fontSize = 20.sp, fontWeight = FontWeight.Bold, color = palette.onSurface,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (wellbeing == null) "Check in and log to build it" else "Tap to see the breakdown",
                        fontSize = 12.sp, color = palette.dim,
                    )
                }
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
                            "A weighted blend of what we can measure, each out of 100. Only the " +
                                "signals you have data for are counted:",
                            fontSize = 13.sp,
                        )
                        Spacer(Modifier.height(10.dp))
                        if (wellbeingResult.components.isEmpty()) {
                            Text("Nothing logged yet - check in, log a meal, or reflect to start your score.", fontSize = 13.sp)
                        } else {
                            wellbeingResult.components.forEach { c ->
                                val pct = (c.weight * 100).roundToInt()
                                Text("- ${c.label}: ${c.subScore}  (weight $pct%)", fontSize = 13.sp)
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "Overall: ${wellbeing?.let { "$it/100" } ?: "not enough data yet"}. It sharpens " +
                                "as you check in, log meals, reflect, and grant Usage access.",
                            fontSize = 13.sp,
                        )
                    }
                },
            )
        }

        // ---- sleep (estimated from the overnight usage gap) ----
        if (lastSleep != null) {
            GlassCard(Modifier.enterUp(35)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconTile(Icons.Rounded.Bedtime)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        SectionLabel("Last night")
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "${com.rootapp.shield.SleepEstimator.fmt(lastSleep.minutes)} of sleep",
                            fontSize = 18.sp, fontWeight = FontWeight.Bold, color = palette.onSurface,
                        )
                        Text(
                            "Phone down around ${sleepClock(lastSleep.startMs)}" +
                                (sleepConsistency?.let { " - bedtime consistency $it" } ?: ""),
                            fontSize = 12.sp, color = palette.dim,
                        )
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
        }

        // ---- steps (on-device sensor) ----
        if (stepsAvailable) {
            GlassCard(Modifier.enterUp(52)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconTile(Icons.Rounded.DirectionsWalk)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        SectionLabel("Move")
                        Spacer(Modifier.height(6.dp))
                        Text(
                            if (stepPerm) "${steps ?: "--"} steps" else "Count your steps",
                            fontSize = 18.sp, fontWeight = FontWeight.Bold, color = palette.onSurface,
                        )
                        Text(
                            if (stepPerm) "goal $stepGoal" else "On-device only. Tap to enable.",
                            fontSize = 12.sp, color = palette.dim,
                        )
                    }
                    if (stepPerm && steps != null) {
                        ScoreRing((steps!! * 100 / stepGoal).coerceIn(0, 100), "of goal", size = 78.dp, stroke = 9.dp)
                    } else if (!stepPerm) {
                        OutlinedButton(onClick = { stepPermLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION) }) {
                            Text("Enable", color = palette.accent)
                        }
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
        }

        // ---- mood check-in ----
        GlassCard(Modifier.enterUp(70)) {
            Text(moodPrompt, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = palette.onSurface)
            Text("A quick check-in. It shapes today's insights.", fontSize = 12.sp, color = palette.dim)
            Spacer(Modifier.height(14.dp))
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
        Spacer(Modifier.height(14.dp))

        // ---- talk to Root (quick voice) ----
        OutlinedButton(onClick = onTalk, modifier = Modifier.fillMaxWidth().height(50.dp)) {
            Icon(Icons.Rounded.Mic, contentDescription = null, tint = palette.accent, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Talk to Root", fontSize = 15.sp, color = palette.accent)
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = "5 min - just talk, I'll listen",
            fontSize = 12.sp, color = palette.dim,
            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(18.dp))

        // ---- "For you" insight cards ----
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
            SectionLabel("For you")
            Spacer(Modifier.height(10.dp))
            insights.forEachIndexed { i, card ->
                InsightCardView(card, delayMs = 140 + i * 70)
                Spacer(Modifier.height(10.dp))
            }
            Spacer(Modifier.height(6.dp))
        }

        // ---- your week entry ----
        GlassCard(Modifier.enterUp(200).clickable { onOpenWeekly() }, padding = 16.dp) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconTile(Icons.Rounded.CalendarMonth)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("Your week with Root", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = palette.onSurface)
                    Text("A 7-day recap and a focus for next week", fontSize = 12.sp, color = palette.dim)
                }
                Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = palette.accent)
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

/** Clean 1-5 segmented check-in (Low .. Great). One tap saves the mood. No emoji. */
@Composable
private fun MoodSelector(selected: Int?, onSelect: (Int) -> Unit) {
    val palette = LocalRootPalette.current
    val labels = listOf("Low", "Meh", "Okay", "Good", "Great")
    val onAccent = if (palette.dark) Color(0xFF06101F) else Color.White
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        labels.forEachIndexed { i, label ->
            val chosen = selected == i
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp)
                    .clip(RoundedCornerShape(13.dp))
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

/** A "For you" insight: line-icon tile, title, observation, and a gentle suggestion. */
@Composable
private fun InsightCardView(card: com.rootapp.data.Insights.InsightCard, delayMs: Int = 0) {
    val palette = LocalRootPalette.current
    GlassCard(Modifier.enterUp(delayMs), padding = 16.dp) {
        Row {
            IconTile(iconForInsight(card))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(card.title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = palette.onSurface)
                Spacer(Modifier.height(4.dp))
                Text(card.body, fontSize = 13.sp, color = palette.onSurface)
                Spacer(Modifier.height(8.dp))
                Text(card.suggestion, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = palette.accent)
            }
        }
    }
}

private fun sleepClock(ms: Long): String =
    java.time.Instant.ofEpochMilli(ms).atZone(java.time.ZoneId.systemDefault())
        .toLocalTime().format(java.time.format.DateTimeFormatter.ofPattern("h:mm a"))

private fun iconForInsight(card: com.rootapp.data.Insights.InsightCard): ImageVector {
    val t = (card.title + " " + card.body).lowercase()
    return when {
        "night" in t || "11pm" in t || "sleep" in t || "late" in t -> Icons.Rounded.Bedtime
        "screen" in t || "phone" in t -> Icons.Rounded.PhoneIphone
        "meal" in t || "junk" in t || "eat" in t || "food" in t -> Icons.Rounded.Restaurant
        "mood" in t || "feel" in t -> Icons.Rounded.WbSunny
        "intention" in t || "said" in t || "want" in t -> Icons.Rounded.Bookmark
        else -> Icons.Rounded.AutoAwesome
    }
}
