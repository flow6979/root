package com.rootapp.ui.shield

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.NightsStay
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.SmartDisplay
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rootapp.shield.InterruptOverlay
import com.rootapp.shield.MonitoredApps
import com.rootapp.shield.ShieldInsights
import com.rootapp.shield.ShieldPermissions
import com.rootapp.shield.UsageStatsReader
import com.rootapp.shield.UsageWatcherService
import com.rootapp.ui.common.GlassCard
import com.rootapp.ui.common.IconTile
import com.rootapp.ui.common.SectionLabel
import com.rootapp.ui.common.enterUp
import com.rootapp.ui.theme.LocalRootPalette

/** Shield = the focus tab: see screen time, one gentle pause, and the apps to pause. */
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

    val heroLines = remember(permRefresh, hasUsage, days, topApps) {
        if (!hasUsage) emptyList() else {
            val top = topApps.firstOrNull()
            ShieldInsights.heroLines(
                ShieldInsights.Insight(
                    lateNightMinutes = UsageStatsReader.lateNightMinutesLastWeek(context),
                    weekOverWeekPercent = ShieldInsights.weekOverWeekPercent(
                        UsageStatsReader.thisWeekMinutes(context),
                        UsageStatsReader.lastWeekMinutes(context),
                    ),
                    topSinkLabel = top?.label,
                    topSinkMinutes = top?.minutes ?: 0,
                    dailyAverageMinutes = UsageStatsReader.dailyAverageMinutes(days),
                ),
            )
        }
    }

    val monitored = remember { MonitoredApps(context) }
    var igOn by remember { mutableStateOf(monitored.isMonitored("com.instagram.android")) }
    var ytOn by remember { mutableStateOf(monitored.isMonitored("com.google.android.youtube")) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 8.dp),
    ) {
        Spacer(Modifier.height(10.dp))
        Text("Shield", fontSize = 26.sp, fontWeight = FontWeight.SemiBold, color = palette.onSurface)
        Text("Your focus, today", fontSize = 13.sp, color = palette.dim)
        Spacer(Modifier.height(18.dp))

        // ---- screen-time hero ----
        GlassCard(Modifier.enterUp(0)) {
            SectionLabel("Screen time this week")
            Spacer(Modifier.height(6.dp))
            Text(if (hasUsage) dailyAvg else "--", fontSize = 44.sp, fontWeight = FontWeight.Bold, color = palette.onSurface)
            Text(if (hasUsage) "a day on average" else "Grant Usage access to see this", fontSize = 12.sp, color = palette.dim)
            Spacer(Modifier.height(16.dp))
            WeeklyBars(days, palette.accent, palette.accentSoft)
            if (hasUsage) {
                Spacer(Modifier.height(16.dp))
                Row {
                    Icon(Icons.Rounded.Insights, null, tint = palette.accent, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(aiRead, fontSize = 13.sp, color = palette.onSurface, modifier = Modifier.weight(1f))
                }
            }
        }
        Spacer(Modifier.height(14.dp))

        // ---- hero insight ----
        if (heroLines.isNotEmpty()) {
            GlassCard(Modifier.enterUp(80), padding = 16.dp) {
                Row {
                    IconTile(Icons.Rounded.NightsStay)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        heroLines.forEachIndexed { i, line ->
                            if (i > 0) Spacer(Modifier.height(6.dp))
                            Text(
                                line, fontSize = 13.sp,
                                fontWeight = if (i == 0) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (i == 0) palette.onSurface else palette.dim,
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(18.dp))
        }

        // ---- the gentle pause ----
        SectionLabel("The gentle pause")
        Spacer(Modifier.height(8.dp))
        GlassCard(Modifier.enterUp(140)) {
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
                    IconTile(Icons.Rounded.Shield)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(if (running) "On" else "Off", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = palette.onSurface)
                        Text("A calm full-screen pause when you open a paused app.", fontSize = 12.sp, color = palette.dim)
                    }
                    Switch(checked = running, onCheckedChange = {
                        if (it) UsageWatcherService.start(context) else UsageWatcherService.stop(context)
                    })
                }
                Spacer(Modifier.height(14.dp))
                OutlinedButton(
                    onClick = { InterruptOverlay(context).showForApp(appLabel = "Instagram", strict = true, onPause = {}, onProceed = {}) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Preview the pause") }
            }
        }
        Spacer(Modifier.height(18.dp))

        // ---- apps to pause ----
        SectionLabel("Apps to pause")
        Spacer(Modifier.height(8.dp))
        GlassCard(Modifier.enterUp(200)) {
            Text("The apps that pull you in the most.", fontSize = 12.sp, color = palette.dim)
            Spacer(Modifier.height(14.dp))
            AppToggle("Instagram", "Reels, endless feed", Icons.Rounded.PhotoCamera, igOn) { igOn = it; monitored.toggle("com.instagram.android", it) }
            Spacer(Modifier.height(16.dp))
            AppToggle("YouTube", "Shorts, autoplay", Icons.Rounded.SmartDisplay, ytOn) { ytOn = it; monitored.toggle("com.google.android.youtube", it) }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun AppToggle(name: String, detail: String, icon: ImageVector, checked: Boolean, onChange: (Boolean) -> Unit) {
    val palette = LocalRootPalette.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconTile(icon)
        Spacer(Modifier.width(14.dp))
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
    Canvas(Modifier.fillMaxWidth().height(90.dp)) {
        val n = values.size
        val gap = 10.dp.toPx()
        val w = (size.width - gap * (n - 1)) / n
        val radius = CornerRadius(w / 2.4f, w / 2.4f)
        values.forEachIndexed { i, v ->
            val x = i * (w + gap)
            val barH = (size.height * (v.toFloat() / max)).coerceAtLeast(w * 0.4f)
            drawRoundRect(color = track, topLeft = Offset(x, 0f), size = Size(w, size.height), cornerRadius = radius)
            drawRoundRect(color = bar, topLeft = Offset(x, size.height - barH), size = Size(w, barH), cornerRadius = radius)
        }
    }
}
