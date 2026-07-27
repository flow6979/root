package com.rootapp.ui.shield

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.runtime.mutableStateOf
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
import com.rootapp.shield.InterruptOverlay
import com.rootapp.shield.MonitoredApps
import com.rootapp.shield.ShieldPermissions
import com.rootapp.shield.UsageWatcherService
import com.rootapp.ui.theme.LocalRootPalette
import com.rootapp.ui.theme.PremiumAccent

@Composable
fun ShieldScreen(modifier: Modifier = Modifier) {
    val palette = LocalRootPalette.current
    val context = LocalContext.current
    val running by UsageWatcherService.running.collectAsStateWithLifecycle()

    var permRefresh by remember { mutableIntStateOf(0) }
    // Re-check permissions whenever we return to this screen (user grants them in Settings).
    LifecycleResumeEffect(Unit) {
        permRefresh++
        onPauseOrDispose { }
    }
    val hasUsage = remember(permRefresh) { ShieldPermissions.hasUsageAccess(context) }
    val hasOverlay = remember(permRefresh) { ShieldPermissions.hasOverlay(context) }
    val ready = hasUsage && hasOverlay

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
        Text("This week · screen time", fontSize = 12.sp, color = palette.dim)
        Spacer(Modifier.height(16.dp))

        // ---- weekly chart ----
        Card(shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = palette.surface),
            modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("3h 12m", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = palette.onSurface)
                Text("daily avg · ↓ 38m vs last week", fontSize = 12.sp, color = palette.dim)
                Spacer(Modifier.height(12.dp))
                WeeklyBars(palette.accent, palette.accentSoft)
            }
        }
        Spacer(Modifier.height(14.dp))

        // ---- Root's read (AI analysis) ----
        Card(shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = palette.surface),
            modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(15.dp)) {
                Text("🧠 Root's read", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = palette.accent)
                Spacer(Modifier.height(6.dp))
                Text(
                    "Your worst scroll window is 11pm–1am — it's costing you about 45 min of sleep. " +
                        "Thursdays spike hardest, usually after a stressful day.",
                    fontSize = 13.sp, color = palette.onSurface,
                )
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
                                appLabel = "Instagram", onPause = {}, onProceed = {},
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
                Text("🔒 Strict Mode — Premium", fontSize = 13.sp,
                    fontWeight = FontWeight.Bold, color = PremiumAccent)
                Spacer(Modifier.height(6.dp))
                Text("No “open anyway.” Timed lockouts you can't bypass.",
                    fontSize = 13.sp, color = palette.dim)
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
private fun WeeklyBars(bar: Color, track: Color) {
    val heights = listOf(0.55f, 0.78f, 0.64f, 0.90f, 0.48f, 0.70f, 0.40f)
    Canvas(Modifier.fillMaxWidth().height(96.dp)) {
        val n = heights.size
        val gap = 10.dp.toPx()
        val w = (size.width - gap * (n - 1)) / n
        heights.forEachIndexed { i, h ->
            val x = i * (w + gap)
            val barH = size.height * h
            drawRoundRect(
                color = track, topLeft = Offset(x, 0f), size = Size(w, size.height),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f, 6f),
            )
            drawRoundRect(
                color = bar, topLeft = Offset(x, size.height - barH), size = Size(w, barH),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f, 6f),
            )
        }
    }
}
