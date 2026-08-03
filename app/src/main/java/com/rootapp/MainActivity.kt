package com.rootapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import com.rootapp.data.SupabaseRepository
import com.rootapp.ui.nav.RootScaffold
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Draw edge to edge so the animated sky fills behind the status + navigation bars.
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Refresh the session if already signed in (the login screen handles first sign-in / guest).
        lifecycleScope.launch {
            val repo = SupabaseRepository(applicationContext)
            if (repo.loggedIn) repo.ensureSession()
        }
        // Re-arm the nightly wind-down reminder (alarms are cleared on reboot).
        com.rootapp.shield.WindDown.apply(applicationContext)
        setContent {
            RootScaffold()
        }
    }
}
