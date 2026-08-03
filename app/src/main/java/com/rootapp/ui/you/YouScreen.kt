package com.rootapp.ui.you

import android.widget.Toast
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.rootapp.di.AppModule
import com.rootapp.shield.ShieldPermissions
import com.rootapp.ui.theme.LocalRootPalette

@Composable
fun YouScreen(
    userName: String,
    minimalist: Boolean,
    onMinimalistChange: (Boolean) -> Unit,
    personality: String,
    onPersonalityChange: (String) -> Unit,
    onLogout: () -> Unit = {},
    onOpenMemory: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val palette = LocalRootPalette.current
    val context = LocalContext.current
    val settings = remember { com.rootapp.data.SettingsStore(context) }
    val repo = remember { com.rootapp.data.SupabaseRepository(context) }
    val accountLabel = repo.email ?: if (repo.isGuest) "Guest" else "Signed in"
    var refresh by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) { refresh++; onPauseOrDispose { } }
    val hasUsage = remember(refresh) { ShieldPermissions.hasUsageAccess(context) }
    val hasOverlay = remember(refresh) { ShieldPermissions.hasOverlay(context) }

    // AI engine state
    var savedKey by remember { mutableStateOf(settings.geminiApiKey) }
    var draftKey by remember { mutableStateOf(settings.geminiApiKey) }
    var editingKey by remember { mutableStateOf(settings.geminiApiKey.isBlank()) }
    var providerLabel by remember { mutableStateOf(AppModule.activeProviderLabel()) }

    Column(
        modifier.fillMaxWidth().verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text("You", fontSize = 24.sp, fontWeight = FontWeight.SemiBold, color = palette.onSurface)
        Text("$accountLabel · all features included", fontSize = 12.sp, color = palette.dim)
        Spacer(Modifier.height(16.dp))

        // AI engine
        Section("AI")
        Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = palette.surface)) {
            Column(Modifier.padding(16.dp)) {
                Text("AI engine", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = palette.onSurface)
                Text("Now using: $providerLabel", fontSize = 12.sp, color = palette.accent)
                Spacer(Modifier.height(10.dp))
                if (!editingKey && savedKey.isNotBlank()) {
                    // Saved state: show the masked key on its own line with edit / remove.
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Your Gemini key", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = palette.onSurface)
                            Text(maskKey(savedKey), fontSize = 13.sp, color = palette.dim)
                        }
                        TextButton(onClick = { draftKey = savedKey; editingKey = true }) { Text("Edit", color = palette.accent) }
                        TextButton(onClick = {
                            savedKey = ""; draftKey = ""; settings.geminiApiKey = ""
                            editingKey = true
                            providerLabel = AppModule.activeProviderLabel()
                            Toast.makeText(context, "Back to Root's engine", Toast.LENGTH_SHORT).show()
                        }) { Text("Remove", color = palette.accent) }
                    }
                    Spacer(Modifier.height(2.dp))
                    Text("Root is using your key. Edit to change it, Remove to go back to the free engine.",
                        fontSize = 11.sp, color = palette.dim)
                } else {
                    // Editing / first-time state: the input + Save (and Cancel if a key exists).
                    Text(
                        "Optional: use your own Gemini key so AI runs on your quota. Get a free key from " +
                            "Google AI Studio (aistudio.google.com).",
                        fontSize = 12.sp, color = palette.dim,
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = draftKey,
                        onValueChange = { draftKey = it },
                        label = { Text("Gemini API key") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                settings.geminiApiKey = draftKey.trim()
                                savedKey = settings.geminiApiKey
                                draftKey = savedKey
                                providerLabel = AppModule.activeProviderLabel()
                                editingKey = savedKey.isBlank() // collapse to the saved row when a key is set
                                Toast.makeText(context, "AI engine updated", Toast.LENGTH_SHORT).show()
                            },
                            enabled = draftKey.isNotBlank(),
                        ) { Text("Save") }
                        if (savedKey.isNotBlank()) {
                            TextButton(onClick = { draftKey = savedKey; editingKey = false }) {
                                Text("Cancel", color = palette.accent)
                            }
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text("More providers (OpenAI, Anthropic) coming later. Voice transcription still uses Root's engine.",
                        fontSize = 11.sp, color = palette.dim)
                }
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
                Text("How Root talks to you", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = palette.onSurface)
                Text("Pick the coaching style that helps you most. Switch anytime; it changes every reply.",
                    fontSize = 12.sp, color = palette.dim)
                Spacer(Modifier.height(12.dp))
                PersonalityOption(
                    title = "Gentle",
                    benefit = "Warm and validating. Softens hard days, celebrates small wins, never pushes.",
                    selected = personality == "Gentle",
                ) { onPersonalityChange("Gentle") }
                Spacer(Modifier.height(8.dp))
                PersonalityOption(
                    title = "Tough-love",
                    benefit = "Honest and direct. Names the excuse, asks for one real commitment, keeps you accountable.",
                    selected = personality == "Tough-love",
                ) { onPersonalityChange("Tough-love") }
            }
        }
        Spacer(Modifier.height(16.dp))

        // Memory
        Section("MEMORY")
        Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().clickable { onOpenMemory() },
            colors = CardDefaults.cardColors(containerColor = palette.surface)) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("What Root remembers", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = palette.onSurface)
                    Text("See the profile and takeaways Root keeps, or clear them.", fontSize = 12.sp, color = palette.dim)
                }
                Text("›", fontSize = 22.sp, color = palette.accent)
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

        Text("Root v${com.rootapp.BuildConfig.VERSION_NAME} · made with care",
            fontSize = 11.sp, color = palette.dim, modifier = Modifier.fillMaxWidth())
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
private fun PersonalityOption(title: String, benefit: String, selected: Boolean, onClick: () -> Unit) {
    val palette = LocalRootPalette.current
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = if (selected) palette.accentSoft else palette.surface),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    title, fontSize = 15.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                    color = if (selected) palette.accent else palette.onSurface,
                )
                Text(benefit, fontSize = 12.sp, color = palette.dim)
            }
            if (selected) {
                Text("✓", color = palette.accent, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/** Show a key as first-4 + dots + last-4 so it's recognisable but not fully exposed on screen. */
private fun maskKey(k: String): String =
    if (k.length <= 8) "••••••••" else k.take(4) + "••••" + k.takeLast(4)

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
