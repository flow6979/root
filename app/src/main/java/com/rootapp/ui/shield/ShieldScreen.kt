package com.rootapp.ui.shield

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import com.rootapp.data.LocalStore
import kotlinx.coroutines.launch
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rootapp.data.SettingsStore
import com.rootapp.shield.InterruptOverlay
import com.rootapp.shield.MonitoredApps
import com.rootapp.shield.ShieldPermissions
import com.rootapp.shield.UsageStatsReader
import com.rootapp.shield.UsageWatcherService
import com.rootapp.ui.theme.LocalRootPalette
import com.rootapp.ui.theme.PremiumAccent

@Composable
fun ShieldScreen(modifier: Modifier = Modifier) {
    val palette = LocalRootPalette.current
    val context = LocalContext.current
    val running by UsageWatcherService.running.collectAsStateWithLifecycle()
    val premium = remember { SettingsStore(context).premium }

    var permRefresh by remember { mutableIntStateOf(0) }
    // Re-check permissions whenever we return to this screen (user grants them in Settings).
    LifecycleResumeEffect(Unit) {
        permRefresh++
        onPauseOrDispose { }
    }
    val hasUsage = remember(permRefresh) { ShieldPermissions.hasUsageAccess(context) }
    val hasOverlay = remember(permRefresh) { ShieldPermissions.hasOverlay(context) }
    val ready = hasUsage && hasOverlay

    // real screen-time data (empty until Usage access is granted)
    val days = remember(permRefresh, hasUsage) { if (hasUsage) UsageStatsReader.lastSevenDays(context) else emptyList() }
    val topApps = remember(permRefresh, hasUsage) { if (hasUsage) UsageStatsReader.topApps(context) else emptyList() }
    val dailyAvg = UsageStatsReader.fmt(UsageStatsReader.dailyAverageMinutes(days))
    val aiRead = UsageStatsReader.read(days, topApps)

    val store = remember { LocalStore(context) }
    val scope = rememberCoroutineScope()
    var analysisTopic by remember { mutableStateOf<String?>(null) }
    var analysisText by remember { mutableStateOf<String?>(null) }
    var analyzing by remember { mutableStateOf(false) }
    fun runAnalysis(topic: String, data: String) {
        analysisTopic = topic; analyzing = true; analysisText = null
        scope.launch {
            analysisText = com.rootapp.ai.InsightAnalyzer.analyze(com.rootapp.di.AppModule.llmClient, topic, data)
            analyzing = false
        }
    }

    val monitored = remember { MonitoredApps(context) }
    var igOn by remember { mutableStateOf(monitored.isMonitored("com.instagram.android")) }
    var ytOn by remember { mutableStateOf(monitored.isMonitored("com.google.android.youtube")) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text("Shield", fontSize = 24.sp, fontWeight = FontWeight.SemiBold, color = palette.onSurface)
        Text("Your week, at a glance", fontSize = 12.sp, color = palette.dim)
        Spacer(Modifier.height(16.dp))

        // ---- Root's read (AI analysis) ----
        Card(shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = palette.surface),
            modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(15.dp)) {
                Text("🧠 Root's read", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = palette.accent)
                Spacer(Modifier.height(6.dp))
                Text(
                    aiRead,
                    fontSize = 13.sp, color = palette.onSurface,
                )
                Spacer(Modifier.height(12.dp))
                Text("Dig deeper", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = palette.dim)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val moods = store.moods()
                    val foods = store.foods()
                    OutlinedButton(
                        onClick = {
                            runAnalysis(
                                "Screen time",
                                "Daily average: $dailyAvg. Top apps: " +
                                    topApps.joinToString { "${it.label} ${UsageStatsReader.fmt(it.minutes)}" },
                            )
                        },
                        modifier = Modifier.weight(1f),
                    ) { Text("Screen", fontSize = 12.sp) }
                    OutlinedButton(
                        onClick = {
                            runAnalysis(
                                "Habits",
                                "Mood check-ins: ${moods.size}. Meals logged: " +
                                    "${foods.count { it.healthy }} healthy, ${foods.count { !it.healthy }} junk. " +
                                    "Recent meals: ${foods.takeLast(5).joinToString { it.label }}",
                            )
                        },
                        modifier = Modifier.weight(1f),
                    ) { Text("Habits", fontSize = 12.sp) }
                    OutlinedButton(
                        onClick = {
                            val mem = store.recentMemory(10)
                            runAnalysis(
                                "What you've shared",
                                if (mem.isEmpty()) "No reflections yet." else "Things they've said: ${mem.joinToString("; ")}",
                            )
                        },
                        modifier = Modifier.weight(1f),
                    ) { Text("You", fontSize = 12.sp) }
                }
            }
        }
        Spacer(Modifier.height(14.dp))

        // ---- deep analysis result ----
        if (analyzing || analysisText != null) {
            Card(shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = palette.surface),
                modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(analysisTopic ?: "Analysis", fontSize = 13.sp,
                        fontWeight = FontWeight.Bold, color = palette.accent)
                    Spacer(Modifier.height(8.dp))
                    if (analyzing) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.height(18.dp), color = palette.accent)
                            Spacer(Modifier.width(10.dp))
                            Text("Thinking it through…", fontSize = 13.sp, color = palette.dim)
                        }
                    } else {
                        Text(analysisText ?: "", fontSize = 13.sp, color = palette.onSurface)
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
        }

        // ---- habits graphs ----
        val moods = store.moods()
        val foods = store.foods()
        Text("HABITS", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = palette.dim)
        Spacer(Modifier.height(8.dp))
        Card(shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = palette.surface),
            modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("Mood (recent)", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = palette.onSurface)
                Spacer(Modifier.height(8.dp))
                MoodMiniBars(moods.takeLast(7).map { it.mood }, palette.accent, palette.accentSoft, palette.dim)
                Spacer(Modifier.height(16.dp))
                Text("Meals", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = palette.onSurface)
                Spacer(Modifier.height(8.dp))
                FoodRatioBar(foods.count { it.healthy }, foods.count { !it.healthy }, palette.dim)
            }
        }
        Spacer(Modifier.height(14.dp))

        // ---- screen time (bars) ----
        Text("SCREEN TIME", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = palette.dim)
        Spacer(Modifier.height(8.dp))
        Card(shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = palette.surface),
            modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text(if (hasUsage) dailyAvg else "—", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = palette.onSurface)
                Text(if (hasUsage) "daily average this week" else "grant Usage access to see this",
                    fontSize = 12.sp, color = palette.dim)
                Spacer(Modifier.height(12.dp))
                WeeklyBars(days, palette.accent, palette.accentSoft)
            }
        }
        Spacer(Modifier.height(14.dp))

        // ---- Protection ----
        Text("PROTECTION", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = palette.dim)
        Spacer(Modifier.height(8.dp))
        Card(shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = palette.surface),
            modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                if (!ready) {
                    Text(
                        "Root needs two permissions to gently interrupt junk apps:",
                        fontSize = 13.sp, color = palette.onSurface,
                    )
                    Spacer(Modifier.height(10.dp))
                    if (!hasUsage) {
                        OutlinedButton(onClick = { context.startActivity(ShieldPermissions.usageAccessIntent()) },
                            modifier = Modifier.fillMaxWidth()) { Text("Grant Usage access") }
                        Spacer(Modifier.height(8.dp))
                    }
                    if (!hasOverlay) {
                        OutlinedButton(onClick = { context.startActivity(ShieldPermissions.overlayIntent(context)) },
                            modifier = Modifier.fillMaxWidth()) { Text("Allow display over other apps") }
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(if (running) "Protection is on" else "Protection is off",
                                fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = palette.onSurface)
                            Text("I'll pause you when you open a monitored app.",
                                fontSize = 12.sp, color = palette.dim)
                        }
                        Switch(checked = running, onCheckedChange = {
                            if (it) UsageWatcherService.start(context) else UsageWatcherService.stop(context)
                        })
                    }
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = {
                            InterruptOverlay(context).showForApp(
                                appLabel = "Instagram", strict = premium, onPause = {}, onProceed = {},
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Test interrupt now") }
                }
            }
        }
        Spacer(Modifier.height(14.dp))

        // ---- monitored apps ----
        Text("APPS I'LL INTERRUPT", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = palette.dim)
        Spacer(Modifier.height(8.dp))
        Card(shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = palette.surface),
            modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                AppToggle("Instagram", "Reels · 1h 20m", igOn) {
                    igOn = it; monitored.toggle("com.instagram.android", it)
                }
                Spacer(Modifier.height(14.dp))
                AppToggle("YouTube", "Shorts · 48m", ytOn) {
                    ytOn = it; monitored.toggle("com.google.android.youtube", it)
                }
            }
        }
        Spacer(Modifier.height(14.dp))

        // ---- strict mode (premium) ----
        Card(shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = palette.surface),
            modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                if (premium) {
                    Text("🛡️ Strict Mode on", fontSize = 13.sp,
                        fontWeight = FontWeight.Bold, color = palette.accent)
                    Spacer(Modifier.height(6.dp))
                    Text("No \"open anyway\". The pause can't be skipped.",
                        fontSize = 13.sp, color = palette.dim)
                } else {
                    Text("🔒 Strict Mode (Premium)", fontSize = 13.sp,
                        fontWeight = FontWeight.Bold, color = PremiumAccent)
                    Spacer(Modifier.height(6.dp))
                    Text("Removes the \"open anyway\" button. Unlock in You.",
                        fontSize = 13.sp, color = palette.dim)
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun AppToggle(name: String, detail: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val palette = LocalRootPalette.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = palette.onSurface)
            Text(detail, fontSize = 12.sp, color = palette.dim)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun MoodMiniBars(values: List<Int>, bar: Color, track: Color, dim: Color) {
    if (values.isEmpty()) {
        Text("No check-ins yet. Tap a mood on Home.", fontSize = 12.sp, color = dim); return
    }
    Row(
        Modifier.fillMaxWidth().height(50.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        values.forEach { v ->
            val frac = ((v + 1).coerceIn(1, 5)) / 5f
            Box(
                Modifier.weight(1f).fillMaxHeight(frac).clip(RoundedCornerShape(4.dp)).background(bar),
            )
        }
        repeat(7 - values.size) { Box(Modifier.weight(1f).fillMaxHeight(0.12f).clip(RoundedCornerShape(4.dp)).background(track)) }
    }
}

@Composable
private fun FoodRatioBar(healthy: Int, junk: Int, dim: Color) {
    if (healthy + junk == 0) { Text("No meals logged yet.", fontSize = 12.sp, color = dim); return }
    Column {
        Row(Modifier.fillMaxWidth().height(14.dp).clip(RoundedCornerShape(7.dp))) {
            if (healthy > 0) Box(Modifier.weight(healthy.toFloat()).fillMaxHeight().background(Color(0xFF5FCF9E)))
            if (junk > 0) Box(Modifier.weight(junk.toFloat()).fillMaxHeight().background(Color(0xFFE0954A)))
        }
        Spacer(Modifier.height(6.dp))
        Text("$healthy healthy · $junk junk", fontSize = 12.sp, color = dim)
    }
}

@Composable
private fun WeeklyBars(days: List<UsageStatsReader.DayUsage>, bar: Color, track: Color) {
    val values = if (days.isEmpty()) List(7) { 0 } else days.map { it.minutes }
    val max = (values.maxOrNull() ?: 0).coerceAtLeast(1)
    Canvas(Modifier.fillMaxWidth().height(96.dp)) {
        val n = values.size
        val gap = 10.dp.toPx()
        val w = (size.width - gap * (n - 1)) / n
        values.forEachIndexed { i, v ->
            val x = i * (w + gap)
            val barH = size.height * (v.toFloat() / max)
            drawRoundRect(
                color = track, topLeft = Offset(x, 0f), size = Size(w, size.height),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f, 6f),
            )
            if (barH > 0f) drawRoundRect(
                color = bar, topLeft = Offset(x, size.height - barH), size = Size(w, barH),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f, 6f),
            )
        }
    }
}
