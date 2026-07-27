package com.rootapp.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rootapp.data.SupabaseRepository
import com.rootapp.ui.common.Orb
import com.rootapp.ui.theme.LocalRootPalette
import kotlinx.coroutines.launch

@Composable
fun AuthScreen(onAuthed: () -> Unit) {
    val palette = LocalRootPalette.current
    val context = LocalContext.current
    val repo = remember { SupabaseRepository(context) }
    val scope = rememberCoroutineScope()

    var isSignUp by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    Column(
        Modifier.fillMaxSize()
            .background(Brush.verticalGradient(listOf(palette.bg1, palette.bg2)))
            .padding(28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Orb(size = 76.dp)
        Spacer(Modifier.height(18.dp))
        Text("Welcome to Root", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = palette.onSurface)
        Text(if (isSignUp) "Create your account" else "Sign in to continue",
            fontSize = 13.sp, color = palette.dim)
        Spacer(Modifier.height(24.dp))

        OutlinedTextField(
            value = email, onValueChange = { email = it; message = null },
            label = { Text("Email") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = password, onValueChange = { password = it; message = null },
            label = { Text("Password") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(18.dp))

        Button(
            onClick = {
                if (email.isBlank() || password.length < 6) {
                    message = "Enter an email and a password (6+ characters)."
                    return@Button
                }
                loading = true; message = null
                scope.launch {
                    val r = if (isSignUp) repo.signUp(email, password) else repo.signIn(email, password)
                    loading = false
                    when (r) {
                        is SupabaseRepository.AuthResult.Success -> onAuthed()
                        is SupabaseRepository.AuthResult.NeedsConfirmation ->
                            message = "Check your email to confirm, then sign in."
                        is SupabaseRepository.AuthResult.Error -> message = r.message
                    }
                }
            },
            enabled = !loading,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(if (isSignUp) "Create account" else "Sign in", fontWeight = FontWeight.SemiBold) }

        TextButton(onClick = { isSignUp = !isSignUp; message = null }) {
            Text(if (isSignUp) "Have an account? Sign in" else "New here? Create an account",
                color = palette.accent)
        }

        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = { loading = true; scope.launch { val ok = repo.guestSignIn(); loading = false; if (ok) onAuthed() else message = "Couldn't start. Check your connection." } },
            enabled = !loading,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Continue as guest") }

        Spacer(Modifier.height(14.dp))
        if (loading) CircularProgressIndicator(color = palette.accent)
        message?.let {
            Text(it, color = palette.accent, fontSize = 13.sp, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth())
        }
    }
}
