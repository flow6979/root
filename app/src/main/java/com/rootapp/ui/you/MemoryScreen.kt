package com.rootapp.ui.you

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
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
import com.rootapp.data.LocalStore
import com.rootapp.ui.theme.LocalRootPalette
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Lets the person see and control everything Root "remembers" about them: the evolving profile
 * the friend maintains and the per-session takeaways. They can delete a single takeaway or wipe
 * everything. Transparency + control build the trust the memory feature depends on.
 */
@Composable
fun MemoryScreen(modifier: Modifier = Modifier) {
    val palette = LocalRootPalette.current
    val context = LocalContext.current
    val store = remember { LocalStore(context) }
    var refresh by remember { mutableIntStateOf(0) }
    val profile = remember(refresh) { store.userProfile() }
    val takeaways = remember(refresh) { store.takeaways().reversed() }
    var confirmClear by remember { mutableStateOf(false) }
    val dateFmt = remember { SimpleDateFormat("d MMM", Locale.getDefault()) }

    Column(
        modifier.fillMaxWidth().verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text("What Root remembers", fontSize = 24.sp, fontWeight = FontWeight.SemiBold, color = palette.onSurface)
        Text("Everything here stays on your phone. You are in control.", fontSize = 12.sp, color = palette.dim)
        Spacer(Modifier.height(16.dp))

        Section("PROFILE")
        Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = palette.surface)) {
            Column(Modifier.padding(16.dp)) {
                if (profile.isBlank()) {
                    Text("Nothing yet. As you talk with Root, it builds a short picture of your goals and what helps you.",
                        fontSize = 13.sp, color = palette.dim)
                } else {
                    Text(profile, fontSize = 14.sp, color = palette.onSurface)
                }
            }
        }
        Spacer(Modifier.height(16.dp))

        Section("SESSION TAKEAWAYS")
        if (takeaways.isEmpty()) {
            Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = palette.surface)) {
                Text("No takeaways yet. Each reflection leaves one behind.",
                    fontSize = 13.sp, color = palette.dim, modifier = Modifier.padding(16.dp))
            }
        } else {
            takeaways.forEach { t ->
                Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = palette.surface)) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(dateFmt.format(Date(t.timestamp)), fontSize = 11.sp, color = palette.dim)
                            if (t.concern.isNotBlank()) {
                                Spacer(Modifier.height(2.dp))
                                Text(t.concern, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = palette.onSurface)
                            }
                            if (t.intention.isNotBlank()) {
                                Spacer(Modifier.height(2.dp))
                                Text("Wanted to: ${t.intention}", fontSize = 13.sp, color = palette.dim)
                            }
                        }
                        TextButton(onClick = { store.removeTakeaway(t.timestamp); refresh++ }) {
                            Text("Delete", color = palette.accent, fontSize = 13.sp)
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
        Spacer(Modifier.height(16.dp))

        OutlinedButton(onClick = { confirmClear = true }, modifier = Modifier.fillMaxWidth()) {
            Text("Clear all memory", fontWeight = FontWeight.SemiBold, color = palette.accent)
        }
        Spacer(Modifier.height(24.dp))
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear all memory?") },
            text = { Text("Root will forget your profile and every takeaway. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = { store.clearMemory(); confirmClear = false; refresh++ }) {
                    Text("Clear", color = palette.accent, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun Section(t: String) {
    Text(t, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = LocalRootPalette.current.dim)
    Spacer(Modifier.height(8.dp))
}
