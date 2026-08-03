package com.rootapp.ui.shield

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.NightsStay
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.SmartDisplay
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
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
import com.rootapp.data.SettingsStore
import com.rootapp.di.AppModule
import com.rootapp.shield.BatteryOpt
import com.rootapp.shield.FocusSession
import com.rootapp.shield.InterruptOverlay
import com.rootapp.shield.MonitoredApps
import com.rootapp.shield.NudgeCalculator
import com.rootapp.shield.NudgeContent
import com.rootapp.shield.Nudges
import com.rootapp.shield.ShieldInsights
import com.rootapp.shield.ShieldPermissions
import com.rootapp.shield.UsageStatsReader
import com.rootapp.shield.UsageWatcherService
import com.rootapp.shield.WindDown
import com.rootapp.ui.common.GlassCard
import com.rootapp.ui.common.IconTile
import com.rootapp.ui.common.ScoreRing
import com.rootapp.ui.common.SectionLabel
import com.rootapp.ui.common.enterUp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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

    // Overuse-nudge state.
    val settings = remember { SettingsStore(context) }
    val scope = rememberCoroutineScope()
    var nudges by remember { mutableStateOf(settings.overuseNudges) }
    var notifGranted by remember(permRefresh) { mutableStateOf(Nudges.canPost(context)) }
    val needsNotif = android.os.Build.VERSION.SDK_INT >= 33 && !notifGranted
    var windDown by remember { mutableStateOf(settings.windDownEnabled) }
    var bedtime by remember { mutableIntStateOf(settings.bedtimeHour.let { if (it < 12) it + 24 else it }.coerceIn(20, 25)) }

    // Budget + focus state.
    var budget by remember { mutableIntStateOf(settings.screenBudgetMin) }
    val todayTotal = remember(permRefresh) { if (hasUsage) UsageStatsReader.todayTotalMinutes(context) else 0 }
    val focusEnd by FocusSession.endAt.collectAsStateWithLifecycle()
    var nowTick by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(focusEnd) {
        while (focusEnd > System.currentTimeMillis()) { nowTick = System.currentTimeMillis(); delay(1000) }
        nowTick = System.currentTimeMillis()
    }
    val focusActive = focusEnd > nowTick
    val focusRemainingSec = ((focusEnd - nowTick) / 1000L).coerceAtLeast(0L)
    fun startFocus(min: Int) {
        FocusSession.start(min)
        if (ready) UsageWatcherService.start(context) // ensure the blocker is running
    }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        notifGranted = Nudges.canPost(context)
    }
    fun previewNudge() {
        scope.launch {
            val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
            val n = NudgeCalculator.compute("Instagram", sessionMin = 22, todayMin = 65, hour = hour)
            val ai = NudgeContent.aiLine(AppModule.llmClient, "Instagram", 22, hour)
            Nudges.post(context, n.title, n.body(ai))
        }
    }

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

        // ---- daily budget ----
        SectionLabel("Daily budget")
        Spacer(Modifier.height(8.dp))
        GlassCard(Modifier.enterUp(90)) {
            if (budget <= 0) {
                Text("Set a daily screen-time budget", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = palette.onSurface)
                Text("Root nudges you at 80% and when you pass it.", fontSize = 12.sp, color = palette.dim)
                Spacer(Modifier.height(12.dp))
                OutlinedButton(onClick = { budget = 120; settings.screenBudgetMin = 120 }, modifier = Modifier.fillMaxWidth()) {
                    Text("Set a 2h budget")
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        SectionLabel("Today")
                        Spacer(Modifier.height(6.dp))
                        Text("${UsageStatsReader.fmt(todayTotal)} of ${UsageStatsReader.fmt(budget)}",
                            fontSize = 18.sp, fontWeight = FontWeight.Bold, color = palette.onSurface)
                        Text(
                            if (todayTotal >= budget) "Over budget" else "${UsageStatsReader.fmt(budget - todayTotal)} left",
                            fontSize = 12.sp,
                            color = if (todayTotal >= budget) Color(0xFFD0563F) else palette.dim,
                        )
                    }
                    ScoreRing(((todayTotal * 100) / budget).coerceIn(0, 100), "used", size = 78.dp, stroke = 9.dp)
                }
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Budget", fontSize = 14.sp, color = palette.onSurface, modifier = Modifier.weight(1f))
                    OutlinedButton(onClick = { budget = (budget - 30).coerceAtLeast(30); settings.screenBudgetMin = budget }) { Text("-30m") }
                    Text(UsageStatsReader.fmt(budget), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = palette.accent, modifier = Modifier.padding(horizontal = 12.dp))
                    OutlinedButton(onClick = { budget = (budget + 30).coerceAtMost(720); settings.screenBudgetMin = budget }) { Text("+30m") }
                }
                Spacer(Modifier.height(6.dp))
                TextButton(onClick = { budget = 0; settings.screenBudgetMin = 0 }) { Text("Turn off", color = palette.accent) }
            }
        }
        Spacer(Modifier.height(18.dp))

        // ---- focus session ----
        SectionLabel("Focus")
        Spacer(Modifier.height(8.dp))
        GlassCard(Modifier.enterUp(120)) {
            if (!focusActive) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconTile(Icons.Rounded.Timer)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Focus session", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = palette.onSurface)
                        Text("Time-sink apps are paused while you focus.", fontSize = 12.sp, color = palette.dim)
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = { startFocus(25) }, modifier = Modifier.weight(1f)) { Text("25 min") }
                    OutlinedButton(onClick = { startFocus(50) }, modifier = Modifier.weight(1f)) { Text("50 min") }
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconTile(Icons.Rounded.Timer)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Focusing", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = palette.accent)
                        Text("Time-sink apps are paused.", fontSize = 12.sp, color = palette.dim)
                    }
                    Text(
                        "%d:%02d".format(focusRemainingSec / 60, focusRemainingSec % 60),
                        fontSize = 20.sp, fontWeight = FontWeight.Bold, color = palette.onSurface,
                    )
                }
                Spacer(Modifier.height(12.dp))
                OutlinedButton(onClick = { FocusSession.end() }, modifier = Modifier.fillMaxWidth()) { Text("End focus") }
            }
        }
        Spacer(Modifier.height(18.dp))

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
                if (running && !BatteryOpt.isExempt(context)) {
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = { runCatching { context.startActivity(BatteryOpt.settingsIntent()) } },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Keep Root running (battery)") }
                }
            }
        }
        Spacer(Modifier.height(18.dp))

        // ---- overuse nudges ----
        SectionLabel("Overuse nudges")
        Spacer(Modifier.height(8.dp))
        GlassCard(Modifier.enterUp(170)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconTile(Icons.Rounded.NotificationsActive)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("Nudge me when I overuse", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = palette.onSurface)
                    Text("A kind heads-up with a real projection after a long stretch.", fontSize = 12.sp, color = palette.dim)
                }
                Switch(checked = nudges, onCheckedChange = {
                    nudges = it; settings.overuseNudges = it
                    if (it && needsNotif) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                })
            }
            if (nudges && needsNotif) {
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = { notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Turn on notifications") }
            }
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = { if (needsNotif) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) else previewNudge() },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Preview a nudge") }
        }
        Spacer(Modifier.height(18.dp))

        // ---- wind-down ----
        SectionLabel("Wind-down")
        Spacer(Modifier.height(8.dp))
        GlassCard(Modifier.enterUp(185)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconTile(Icons.Rounded.Bedtime)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("Wind-down reminder", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = palette.onSurface)
                    Text("A nudge 15 min before bedtime to set the phone down.", fontSize = 12.sp, color = palette.dim)
                }
                Switch(checked = windDown, onCheckedChange = {
                    windDown = it; settings.windDownEnabled = it
                    if (it && needsNotif) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    WindDown.apply(context)
                })
            }
            if (windDown) {
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Bedtime", fontSize = 14.sp, color = palette.onSurface, modifier = Modifier.weight(1f))
                    OutlinedButton(onClick = {
                        bedtime = (bedtime - 1).coerceAtLeast(20); settings.bedtimeHour = bedtime; WindDown.apply(context)
                    }) { Text("-") }
                    Text(
                        WindDown.bedtimeLabel(bedtime % 24),
                        fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = palette.accent,
                        modifier = Modifier.padding(horizontal = 14.dp),
                    )
                    OutlinedButton(onClick = {
                        bedtime = (bedtime + 1).coerceAtMost(25); settings.bedtimeHour = bedtime; WindDown.apply(context)
                    }) { Text("+") }
                }
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
