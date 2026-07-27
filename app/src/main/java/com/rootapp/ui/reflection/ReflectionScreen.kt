package com.rootapp.ui.reflection

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import android.content.Intent
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Mic
import androidx.compose.runtime.DisposableEffect
import java.util.Locale
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.platform.LocalContext
import com.rootapp.ai.ChatMessage
import com.rootapp.ai.LlmClient
import com.rootapp.data.LocalStore
import com.rootapp.di.AppModule
import com.rootapp.ui.theme.LocalRootPalette

/** Builds a ReflectionViewModel with our injected LlmClient + cross-session memory. */
class ReflectionVMFactory(
    private val llm: LlmClient,
    private val userName: String,
    private val pastMemory: String,
    private val tone: String,
    private val onUserMessage: (String) -> Unit,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        ReflectionViewModel(llm, userName, pastMemory, tone, onUserMessage) as T
}

@Composable
fun ReflectionScreen(
    userName: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val store = remember { LocalStore(context) }
    val supabase = remember { com.rootapp.data.SupabaseRepository(context) }
    val bgScope = rememberCoroutineScope()
    val pastMemory = remember { store.recentMemory().joinToString("; ") }
    val tone = remember { com.rootapp.data.SettingsStore(context).personality }
    var sessionLogged by remember { mutableStateOf(false) }
    var msgCount by remember { mutableIntStateOf(0) }
    val vm: ReflectionViewModel = viewModel(
        factory = ReflectionVMFactory(
            llm = AppModule.llmClient,
            userName = userName,
            pastMemory = pastMemory,
            tone = tone,
            onUserMessage = {
                store.remember(it)
                msgCount++
                if (!sessionLogged) { sessionLogged = true; bgScope.launch { supabase.pushReflection(msgCount) } }
                // Auto-log food mentioned during the session into Moments.
                bgScope.launch {
                    val m = com.rootapp.ai.FoodExtractor.extract(AppModule.llmClient, it)
                    if (m != null) {
                        store.addFood(m.food, m.healthy, System.currentTimeMillis())
                        supabase.pushFood(m.food, m.healthy)
                    }
                }
            },
        ),
    )

    // ---- voice session (premium): speak with the friend, it replies aloud ----
    val premium = remember { com.rootapp.data.SettingsStore(context).premium }
    val tts = remember { TextToSpeech(context) {} }
    DisposableEffect(Unit) { onDispose { tts.stop(); tts.shutdown() } }
    var voiceMode by remember { mutableStateOf(false) }
    var lastSpoken by remember { mutableIntStateOf(0) }
    val speechLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        val spoken = res.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (!spoken.isNullOrBlank()) { voiceMode = true; vm.send(spoken) }
    }
    val startListening = {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Talk to Root")
        }
        runCatching { speechLauncher.launch(intent) }
            .onFailure { Toast.makeText(context, "No speech recognizer on this device", Toast.LENGTH_SHORT).show() }
        Unit
    }
    val palette = LocalRootPalette.current
    val state by vm.state.collectAsStateWithLifecycle()
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    // Keep the latest message in view.
    LaunchedEffect(state.visible.size, state.sending) {
        val count = state.visible.size + if (state.sending) 1 else 0
        if (count > 0) listState.animateScrollToItem(count - 1)
    }

    // In voice mode, speak each new assistant reply aloud.
    LaunchedEffect(state.visible.size, state.sending) {
        if (voiceMode && !state.sending) {
            val last = state.visible.lastOrNull()
            if (last?.role == "assistant" && state.visible.size > lastSpoken) {
                lastSpoken = state.visible.size
                tts.language = Locale.getDefault()
                tts.speak(last.content, TextToSpeech.QUEUE_FLUSH, null, "root-${state.visible.size}")
            }
        }
    }

    Column(modifier = modifier.fillMaxSize().padding(horizontal = 14.dp)) {
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(state.visible) { msg -> Bubble(msg) }
            if (state.sending) {
                item {
                    Row(Modifier.padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.height(16.dp))
                        Spacer(Modifier.height(1.dp))
                    }
                }
            }
        }

        state.error?.let { err ->
            Text(
                text = err,
                color = palette.accent,
                fontSize = 12.sp,
                modifier = Modifier.padding(vertical = 6.dp),
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val onAccent = if (palette.dark) Color(0xFF06101F) else Color.White
            // Mic (always visible; premium-gated action)
            Box(
                Modifier.size(48.dp).clip(CircleShape).background(palette.accent)
                    .clickable(enabled = !state.sending) {
                        if (premium) startListening()
                        else Toast.makeText(context, "Voice is a premium feature", Toast.LENGTH_SHORT).show()
                    },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.Mic, contentDescription = "Talk", tint = onAccent) }
            Spacer(Modifier.width(8.dp))
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text(if (premium) "Type or tap the mic" else "Say what's on your mind") },
                enabled = !state.sending,
            )
            Spacer(Modifier.width(8.dp))
            val canSend = input.isNotBlank() && !state.sending
            Box(
                Modifier.size(48.dp).clip(CircleShape)
                    .background(if (canSend) palette.accent else palette.surface)
                    .clickable(enabled = canSend) { voiceMode = false; vm.send(input); input = "" },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send",
                    tint = if (canSend) onAccent else palette.dim)
            }
        }
    }
}

@Composable
private fun Bubble(msg: ChatMessage) {
    val palette = LocalRootPalette.current
    val fromUser = msg.role == "user"
    Box(Modifier.fillMaxWidth()) {
        Card(
            modifier = Modifier
                .widthIn(max = 300.dp)
                .align(if (fromUser) Alignment.CenterEnd else Alignment.CenterStart),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (fromUser) palette.accent else palette.surface,
            ),
        ) {
            Text(
                text = msg.content,
                modifier = Modifier.padding(12.dp),
                fontSize = 14.sp,
                fontWeight = if (fromUser) FontWeight.Medium else FontWeight.Normal,
                color = if (fromUser) androidx.compose.ui.graphics.Color.White else palette.onSurface,
            )
        }
    }
}
