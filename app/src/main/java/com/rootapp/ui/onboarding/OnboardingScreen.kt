package com.rootapp.ui.onboarding

import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.rootapp.shield.ShieldPermissions
import com.rootapp.ui.common.Orb
import com.rootapp.ui.theme.LocalRootPalette

/** First-run screen: introduce Root and (optionally) grant the key permissions. */
@Composable
fun OnboardingScreen(onDone: () -> Unit) {
    val palette = LocalRootPalette.current
    val context = LocalContext.current

    var refresh by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) { refresh++; onPauseOrDispose { } }
    val hasUsage = remember(refresh) { ShieldPermissions.hasUsageAccess(context) }
    val hasOverlay = remember(refresh) { ShieldPermissions.hasOverlay(context) }

    var notifGranted by remember { mutableIntStateOf(0) }
    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { notifGranted++ }

    Column(
        Modifier.fillMaxSize()
            .background(Brush.verticalGradient(listOf(palette.bg1, palette.bg2)))
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(24.dp))
        Orb(size = 84.dp)
        Spacer(Modifier.height(20.dp))
        Text("Meet Root", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = palette.onSurface)
        Spacer(Modifier.height(8.dp))
        Text(
            "A friend who helps you catch the scroll before it starts. " +
                "Root needs a couple of permissions to do that. You're always in control.",
            fontSize = 14.sp, color = palette.dim,
        )
        Spacer(Modifier.height(24.dp))

        PermCard(
            title = "Usage access",
            why = "So Root knows when you open a junk app and can offer a pause.",
            granted = hasUsage,
            onGrant = { context.startActivity(ShieldPermissions.usageAccessIntent()) },
        )
        PermCard(
            title = "Display over other apps",
            why = "So the friendly pause can appear over the app you opened.",
            granted = hasOverlay,
            onGrant = { context.startActivity(ShieldPermissions.overlayIntent(context)) },
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            PermCard(
                title = "Notifications",
                why = "So Root can quietly run in the background and check in.",
                granted = notifGranted > 0,
                onGrant = { notifLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS) },
            )
        }

        Spacer(Modifier.height(16.dp))
        Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
            Text("I'm ready", fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(6.dp))
        Text("You can grant or change these anytime in You › Permissions.",
            fontSize = 11.sp, color = palette.dim)
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun PermCard(title: String, why: String, granted: Boolean, onGrant: () -> Unit) {
    val palette = LocalRootPalette.current
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = palette.surface),
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = palette.onSurface)
                Text(why, fontSize = 12.sp, color = palette.dim)
            }
            Spacer(Modifier.height(0.dp))
            if (granted) {
                Text("✓ Granted", color = palette.accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            } else {
                OutlinedButton(onClick = onGrant) { Text("Grant") }
            }
        }
    }
}
