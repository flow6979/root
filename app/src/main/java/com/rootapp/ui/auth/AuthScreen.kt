package com.rootapp.ui.auth

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rootapp.data.SettingsStore
import com.rootapp.data.SupabaseRepository
import com.rootapp.ui.common.SkyBackground
import com.rootapp.ui.theme.LocalRootPalette
import com.rootapp.ui.theme.RootPalette
import kotlinx.coroutines.launch
import java.util.Calendar

@Composable
fun AuthScreen(onAuthed: () -> Unit) {
    val palette = LocalRootPalette.current
    val context = LocalContext.current
    val repo = remember { SupabaseRepository(context) }
    val settings = remember { SettingsStore(context) }
    val scope = rememberCoroutineScope()
    val hour = remember { Calendar.getInstance().get(Calendar.HOUR_OF_DAY) }

    var isSignUp by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var sentTo by remember { mutableStateOf<String?>(null) } // set when a confirmation email is sent

    Box(Modifier.fillMaxSize()) {
        SkyBackground(hour = hour, minimalist = false, modifier = Modifier.fillMaxSize())
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(26.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(12.dp))

            val pending = sentTo
            if (pending != null) {
                // ---- confirmation-sent state ----
                Text("✉️", fontSize = 40.sp)
                Spacer(Modifier.height(10.dp))
                Text("Check your inbox", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = palette.onSurface)
                Spacer(Modifier.height(8.dp))
                Text(
                    "We sent a confirmation link to $pending. Tap it, then come back and log in.",
                    fontSize = 13.sp, color = palette.dim, textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(20.dp))
                Button(
                    onClick = { sentTo = null; isSignUp = false; password = ""; confirm = ""; message = null },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("I've confirmed - log in", fontWeight = FontWeight.SemiBold) }
                TextButton(onClick = { sentTo = null; message = null }) {
                    Text("Use a different email", color = palette.accent)
                }
                return@Column
            }

            // ---- title (distinct per mode) ----
            Text(
                if (isSignUp) "Create your account" else "Welcome back",
                fontSize = 24.sp, fontWeight = FontWeight.Bold, color = palette.onSurface,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                if (isSignUp) "A kinder relationship with your phone starts here."
                else "Log in to pick up right where you left off.",
                fontSize = 13.sp, color = palette.dim, textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(20.dp))

            // ---- segmented switch so the two modes are obviously different ----
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(palette.accentSoft).padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                SegPill("Log in", selected = !isSignUp, palette, Modifier.weight(1f)) { isSignUp = false; message = null }
                SegPill("Create account", selected = isSignUp, palette, Modifier.weight(1f)) { isSignUp = true; message = null }
            }
            Spacer(Modifier.height(18.dp))

            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = palette.surface),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(18.dp)) {
                    if (isSignUp) {
                        Field(name, "Your name", KeyboardType.Text) { name = it; message = null }
                        Spacer(Modifier.height(12.dp))
                    }
                    Field(email, "Email", KeyboardType.Email) { email = it; message = null }
                    Spacer(Modifier.height(12.dp))
                    Field(password, "Password", KeyboardType.Password, isPassword = true) { password = it; message = null }
                    if (isSignUp) {
                        Spacer(Modifier.height(12.dp))
                        Field(confirm, "Confirm password", KeyboardType.Password, isPassword = true) { confirm = it; message = null }
                        Spacer(Modifier.height(6.dp))
                        Text("At least 6 characters.", fontSize = 11.sp, color = palette.dim)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))

            Button(
                onClick = {
                    val err = validate(isSignUp, name, email, password, confirm)
                    if (err != null) { message = err; return@Button }
                    loading = true; message = null
                    scope.launch {
                        val r = if (isSignUp) repo.signUp(email, password, name) else repo.signIn(email, password)
                        loading = false
                        when (r) {
                            is SupabaseRepository.AuthResult.Success -> {
                                if (isSignUp && name.isNotBlank()) settings.userName = name.trim()
                                onAuthed()
                            }
                            is SupabaseRepository.AuthResult.NeedsConfirmation -> sentTo = email.trim()
                            is SupabaseRepository.AuthResult.Error -> message = r.message
                        }
                    }
                },
                enabled = !loading,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (isSignUp) "Create account" else "Log in", fontWeight = FontWeight.SemiBold) }

            Spacer(Modifier.height(14.dp))
            if (loading) CircularProgressIndicator(color = palette.accent)
            message?.let {
                Text(it, color = palette.accent, fontSize = 13.sp, textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth())
            }
            // Temporary: kept until the email flow is verified, then removed.
            Spacer(Modifier.height(8.dp))
            TextButton(
                onClick = {
                    loading = true; message = null
                    scope.launch {
                        val ok = repo.guestSignIn(); loading = false
                        if (ok) onAuthed() else message = "Couldn't start. Check your connection."
                    }
                },
                enabled = !loading,
            ) { Text("Just looking? Continue as guest", color = palette.dim, fontSize = 12.sp) }
        }
    }
}

private fun validate(isSignUp: Boolean, name: String, email: String, password: String, confirm: String): String? {
    if (isSignUp && name.isBlank()) return "What should Root call you?"
    if (!email.contains("@") || !email.contains(".")) return "Enter a valid email address."
    if (password.length < 6) return "Password needs at least 6 characters."
    if (isSignUp && password != confirm) return "Passwords don't match."
    return null
}

@Composable
private fun SegPill(text: String, selected: Boolean, palette: RootPalette, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .clip(RoundedCornerShape(11.dp))
            .background(if (selected) palette.surface else Color.Transparent)
            .clickable { onClick() }
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text, fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) palette.accent else palette.dim,
        )
    }
}

@Composable
private fun Field(value: String, label: String, type: KeyboardType, isPassword: Boolean = false, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value, onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = type),
        modifier = Modifier.fillMaxWidth(),
    )
}
