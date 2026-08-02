package com.rootapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import com.rootapp.data.SupabaseRepository
import com.rootapp.ui.nav.RootScaffold
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Refresh the session if already signed in (the login screen handles first sign-in / guest).
        lifecycleScope.launch {
            val repo = SupabaseRepository(applicationContext)
            if (repo.loggedIn) repo.ensureSession()
        }
        setContent {
            RootScaffold()
        }
    }
}
