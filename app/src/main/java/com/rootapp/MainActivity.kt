package com.rootapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import com.rootapp.data.SettingsStore
import com.rootapp.data.SupabaseRepository
import com.rootapp.ui.nav.RootScaffold
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Anonymous sign-in + honour any server-granted premium (auto-expires per premium_until).
        lifecycleScope.launch {
            val repo = SupabaseRepository(applicationContext)
            repo.ensureSession()
            if (repo.isPremiumFromServer()) {
                SettingsStore(applicationContext).premium = true
            }
        }
        setContent {
            RootScaffold()
        }
    }
}
