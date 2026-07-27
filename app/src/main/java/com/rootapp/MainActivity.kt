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
        // Best-effort anonymous sign-in so cloud sync has a user (no-op if unconfigured).
        lifecycleScope.launch { SupabaseRepository(applicationContext).ensureSession() }
        setContent {
            RootScaffold()
        }
    }
}
