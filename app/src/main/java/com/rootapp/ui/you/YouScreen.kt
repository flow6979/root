package com.rootapp.ui.you

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.rootapp.shield.ShieldPermissions
import com.rootapp.ui.theme.LocalRootPalette
import com.rootapp.ui.theme.PremiumAccent

@Composable
fun YouScreen(
    userName: String,
    minimalist: Boolean,
    onMinimalistChange: (Boolean) -> Unit,
    personality: String,
    onPersonalityChange: (String) -> Unit,
    onLogout: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val palette = LocalRootPalette.current
    val context = LocalContext.current
    val settings = remember { com.rootapp.data.SettingsStore(context) }
    val repo = remember { com.rootapp.data.SupabaseRepository(context) }
    val accountLabel = repo.email ?: if (repo.isGuest) "Guest" else "Signed in"
    var premium by remember { mutableStateOf(settings.premium) }
    var refresh by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) { refresh++; onPauseOrDispose { } }
    val hasUsage = remember(refresh) { ShieldPermissions.hasUsageAccess(context) }
    val hasOverlay = remember(refresh) { ShieldPermissions.hasOverlay(context) }

    Column(
        modifier.fillMaxWidth().verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text("You", fontSize = 24.sp, fontWeight = FontWeight.SemiBold, color = palette.onSurface)
        Text("$accountLabel · ${if (premium) "Premium" else "Free"} plan", fontSize = 12.sp, color = palette.dim)
        Spacer(Modifier.height(16.dp))

        // Premium
        Card(shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.Transparent)) {
            Column(
                Modifier.fillMaxWidth()
                    .background(Brush.horizontalGradient(listOf(Color(0xFF2E5540), Color(0xFF3E6B52))))
                    .padding(16.dp),
            ) {
                Text(if (premium) "✨ Premium active" else "✨ Root Premium",
                    color = PremiumAccent, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(Modifier.height(6.dp))
                Text(
                    if (premium) "All features unlocked. Thank you for supporting Root."
                    else "Strict mode · unlimited AI · weekly insights · story audio",
                    color = Color(0xFFD6E6DA), fontSize = 13.sp,
                )
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = { premium = !premium; settings.premium = premium },
                    colors = ButtonDefaults.buttonColors(containerColor = PremiumAccent),
                ) {
                    Text(if (premium) "Turn off (test)" else "Unlock premium (test)",
                        color = Color(0xFF231A08), fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.height(6.dp))
                Text("Test unlock. Real purchases come with Play Billing.",
                    color = Color(0xFFB9CFC0), fontSize = 11.sp)
            }
        }
        Spacer(Modifier.height(16.dp))

        // Appearance
        Section("APPEARANCE")
        Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = palette.surface)) {
            Column(Modifier.padding(16.dp)) {
                RowText("Time-adaptive colour", "UI follows the real sky")
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Minimalist (B&W)", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = palette.onSurface)
                        Text("Pure black & white, no colour", fontSize = 12.sp, color = palette.dim)
                    }
                    Switch(checked = minimalist, onCheckedChange = onMinimalistChange)
                }
            }
        }
        Spacer(Modifier.height(16.dp))

        // Friend
        Section("YOUR FRIEND")
        Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = palette.surface)) {
            Column(Modifier.padding(16.dp)) {
                Text("Personality", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = palette.onSurface)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Gentle", "Tough-love").forEach { p ->
                        FilterChip(selected = personality == p, onClick = { onPersonalityChange(p) },
                            label = { Text(p) })
                    }
                }
                Spacer(Modifier.height(14.dp))
                RowText("Voice", "🔒 Premium")
                Spacer(Modifier.height(14.dp))
                RowText("Accountability partner", "Not set")
            }
        }
        Spacer(Modifier.height(16.dp))

        // Permissions
        Section("PERMISSIONS")
        Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = palette.surface)) {
            Column(Modifier.padding(16.dp)) {
                PermRow("Usage access", hasUsage) { context.startActivity(ShieldPermissions.usageAccessIntent()) }
                Spacer(Modifier.height(14.dp))
                PermRow("Display over other apps", hasOverlay) { context.startActivity(ShieldPermissions.overlayIntent(context)) }
            }
        }
        Spacer(Modifier.height(16.dp))

        Section("ACCOUNT")
        Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = palette.surface)) {
            Column(Modifier.padding(16.dp)) {
                RowText("Signed in as", accountLabel)
                Spacer(Modifier.height(14.dp))
                androidx.compose.material3.OutlinedButton(
                    onClick = onLogout,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Log out", fontWeight = FontWeight.SemiBold, color = palette.accent) }
            }
        }
        Spacer(Modifier.height(16.dp))

        Text("Root v0.1 · made with care", fontSize = 11.sp, color = palette.dim,
            modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun Section(t: String) {
    Text(t, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = LocalRootPalette.current.dim)
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun RowText(title: String, detail: String) {
    val palette = LocalRootPalette.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = palette.onSurface)
        }
        Text(detail, fontSize = 12.sp, color = palette.dim)
    }
}

@Composable
private fun PermRow(title: String, granted: Boolean, onGrant: () -> Unit) {
    val palette = LocalRootPalette.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = palette.onSurface,
            modifier = Modifier.weight(1f))
        if (granted) {
            Text("✓ Granted", color = palette.accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        } else {
            Text("Grant", color = palette.accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable { onGrant() }.padding(4.dp))
        }
    }
}
