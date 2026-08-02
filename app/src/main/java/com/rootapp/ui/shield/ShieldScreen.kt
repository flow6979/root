package com.rootapp.ui.shield

import androidx.compose.foundation.Canvas
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rootapp.shield.InterruptOverlay
import com.rootapp.shield.MonitoredApps
import com.rootapp.shield.ShieldPermissions
import com.rootapp.shield.UsageStatsReader
import com.rootapp.shield.UsageWatcherService
import com.rootapp.ui.theme.LocalRootPalette

/**
 * Shield = the focus tab. One job: help the user see their screen time and gently break the
 * doomscroll. Kept deliberately simple - screen-time hero, one on/off protection, and the
 * apps to pause.
 */
@Composable
fun ShieldScreen(modifier: Modifier = Modifier) {
    val palette = LocalRootPalette.current
    val context = LocalContext.current
    val running by UsageWatcherService.running.collectAsStateWithLifecycle()

    var permRefresh by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) { permRefresh++; onPauseOrDispose { } }
    val hasUsage = remember(permRefresh) { ShieldPermissions.hasUsageAccess(context) }
    val hasOverlay = remember(permRefresh) { ShieldPermissions.hasOverlay(context) }
    val ready = hasUsage && hasOverlay

    val days = remember(permRefresh, hasUsage) { if (hasUsage) UsageStatsReader.lastSevenDays(context) else emptyList() }
    val topApps = remember(permRefresh, hasUsage) { if (hasUsage) UsageStatsReader.topApps(context) else emptyList() }
    val dailyAvg = UsageStatsReader.fmt(UsageStatsReader.dailyAverageMinutes(days))
    val aiRead = UsageStatsReader.read(days, topApps)

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
        Text("Your focus, today", fontSize = 12.sp, color = palette.dim)
        Spacer(Modifier.height(16.dp))

        // ---- screen-time hero ----
        GlassCard(palette.surface) {
            Text("Screen time this week", fontSize = 12.sp, color = palette.dim)
            Text(if (hasUsage) dailyAvg else "—", fontSize = 40.sp, fontWeight = FontWeight.Bold, color = palette.onSurface)
            Text(if (hasUsage) "a day on average" else "Grant Usage access to see this",
                fontSize = 12.sp, color = palette.dim)
            Spacer(Modifier.height(14.dp))
            WeeklyBars(days, palette.accent, palette.accentSoft)
            if (hasUsage) {
                Spacer(Modifier.height(14.dp))
                Text("🧠 $aiRead", fontSize = 13.sp, color = palette.onSurface)
            }
        }
        Spacer(Modifier.height(16.dp))

        // ---- the gentle pause (core feature) ----
        Section("THE GENTLE PAUSE")
        GlassCard(palette.surface) {
            if (!ready) {
                Text("Let Root step in for a breath when you open a time-sink app. Two quick permissions:",
                    fontSize = 13.sp, color = palette.onSurface)
                Spacer(Modifier.height(12.dp))
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
                        Text(if (running) "On" else "Off", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = palette.onSurface)
                        Text("A calm full-screen pause when you open a paused app.",
                            fontSize = 12.sp, color = palette.dim)
                    }
                    Switch(checked = running, onCheckedChange = {
                        if (it) UsageWatcherService.start(context) else UsageWatcherService.stop(context)
                    })
                }
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = {
                        InterruptOverlay(context).showForApp(appLabel = "Instagram", strict = true, onPause = {}, onProceed = {})
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Preview the pause") }
            }
        }
        Spacer(Modifier.height(16.dp))

        // ---- apps to pause (doomscroll / provoking content) ----
        Section("APPS TO PAUSE")
        GlassCard(palette.surface) {
            Text("The apps that pull you in the most.", fontSize = 12.sp, color = palette.dim)
            Spacer(Modifier.height(12.dp))
            AppToggle("Instagram", "Reels, endless feed", igOn) { igOn = it; monitored.toggle("com.instagram.android", it) }
            Spacer(Modifier.height(14.dp))
            AppToggle("YouTube", "Shorts, autoplay", ytOn) { ytOn = it; monitored.toggle("com.google.android.youtube", it) }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun GlassCard(surface: Color, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = surface),
        modifier = Modifier.fillMaxWidth(),
    ) { Column(Modifier.padding(16.dp), content = content) }
}

@Composable
private fun Section(t: String) {
    Text(t, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = LocalRootPalette.current.dim)
    Spacer(Modifier.height(8.dp))
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
            drawRoundRect(color = track, topLeft = Offset(x, 0f), size = Size(w, size.height), cornerRadius = CornerRadius(6f, 6f))
            if (barH > 0f) drawRoundRect(color = bar, topLeft = Offset(x, size.height - barH), size = Size(w, barH), cornerRadius = CornerRadius(6f, 6f))
        }
    }
}
