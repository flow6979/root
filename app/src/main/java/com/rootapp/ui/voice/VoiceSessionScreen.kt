package com.rootapp.ui.voice

import android.Manifest
import android.content.pm.PackageManager
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rootapp.ai.GroqTranscriber
import com.rootapp.di.AppModule
import com.rootapp.data.LocalStore
import com.rootapp.data.SettingsStore
import com.rootapp.ui.common.Orb
import com.rootapp.ui.reflection.ReflectionVMFactory
import com.rootapp.ui.reflection.ReflectionViewModel
import com.rootapp.ui.theme.LocalRootPalette
import com.rootapp.voice.VoiceRecorder
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

/** One-on-one voice session: talk, Root replies aloud. Or tap "type instead" to switch to text. */
@Composable
fun VoiceSessionScreen(userName: String, onExit: () -> Unit, onTypeInstead: () -> Unit = {}) {
    val palette = LocalRootPalette.current
    val context = LocalContext.current
    val store = remember { LocalStore(context) }
    val bgScope = rememberCoroutineScope()
    val pastMemory = remember { store.recentMemory().joinToString("; ") }
    val tone = remember { SettingsStore(context).personality }
    val vm: ReflectionViewModel = viewModel(
        factory = ReflectionVMFactory(AppModule.llmClient, userName, pastMemory, tone, onUserMessage = { store.remember(it) }),
    )
    val state by vm.state.collectAsStateWithLifecycle()

    val recorder = remember { VoiceRecorder(context) }
    var recording by remember { mutableStateOf(false) }
    var transcribing by remember { mutableStateOf(false) }
    var lastSpoken by remember { mutableIntStateOf(0) }
    val tts = remember { TextToSpeech(context) {} }
    DisposableEffect(Unit) { onDispose { tts.stop(); tts.shutdown(); recorder.stop(); com.rootapp.voice.CloudTts.stop() } }

    val recordPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) { if (recorder.start()) recording = true }
        else Toast.makeText(context, "Microphone permission needed", Toast.LENGTH_SHORT).show()
    }

    // Speak each new assistant reply aloud (calm).
    LaunchedEffect(state.visible.size, state.sending) {
        if (!state.sending) {
            val last = state.visible.lastOrNull()
            if (last?.role == "assistant" && state.visible.size > lastSpoken) {
                lastSpoken = state.visible.size
                // Prefer the natural cloud voice; fall back to system TTS.
                val played = if (com.rootapp.voice.CloudTts.configured) com.rootapp.voice.CloudTts.play(context, last.content) else false
                if (!played) {
                    tts.language = Locale.getDefault(); tts.setPitch(0.85f); tts.setSpeechRate(0.85f)
                    tts.voices?.firstOrNull {
                        it.locale.language == "en" && !it.isNetworkConnectionRequired &&
                            it.quality >= android.speech.tts.Voice.QUALITY_HIGH
                    }?.let { tts.voice = it }
                    tts.speak(last.content, TextToSpeech.QUEUE_FLUSH, null, "v-${state.visible.size}")
                }
            }
        }
    }

    fun stopAndSend() {
        if (!recording) return
        recording = false
        val f = recorder.stop() ?: return
        transcribing = true
        bgScope.launch {
            val t = GroqTranscriber.transcribe(f)
            transcribing = false
            if (!t.isNullOrBlank()) vm.send(t)
            else Toast.makeText(context, "Didn't catch that.", Toast.LENGTH_SHORT).show()
        }
    }
    // Auto-stop on silence.
    LaunchedEffect(recording) {
        if (recording) {
            var spoke = false; var sil = 0L; var tot = 0L
            while (recording) {
                delay(200); tot += 200
                val amp = recorder.amplitude()
                if (amp > 1800) { spoke = true; sil = 0 } else if (spoke) sil += 200
                if (spoke && sil >= 1400) { stopAndSend(); break }
                if (tot >= 25000) { stopAndSend(); break }
            }
        }
    }
    fun onMic() {
        if (recording) stopAndSend()
        else {
            val has = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
            if (has) { if (recorder.start()) recording = true } else recordPerm.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    val latestReply = state.visible.lastOrNull { it.role == "assistant" }?.content ?: ""
    val status = when {
        transcribing || state.sending -> "Thinking..."
        recording -> "Listening..."
        else -> "Tap to talk"
    }
    val onAccent = if (palette.dark) Color(0xFF06101F) else Color.White
    val micBg = if (recording) Color(0xFFD0563F) else palette.accent

    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(palette.bg1, palette.bg2)))) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Talk to Root", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = palette.onSurface,
                modifier = Modifier.weight(1f).padding(start = 8.dp))
            IconButton(onClick = onExit) { Icon(Icons.Filled.Close, contentDescription = "Close", tint = palette.onSurface) }
        }

        Column(
            Modifier.fillMaxSize().padding(horizontal = 32.dp, vertical = 80.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Orb(size = 110.dp, interactive = true)
            Spacer(Modifier.height(28.dp))
            Text(status, fontSize = 14.sp, color = palette.dim)
            Spacer(Modifier.height(18.dp))
            Text(
                latestReply.ifBlank { "Say what's on your mind. I'm listening." },
                fontSize = 17.sp, color = palette.onSurface, textAlign = TextAlign.Center,
                fontWeight = FontWeight.Medium,
            )
        }

        Column(
            Modifier.fillMaxWidth().padding(bottom = 40.dp).align(Alignment.BottomCenter),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier.size(78.dp).clip(CircleShape).background(micBg)
                    .clickable(enabled = !state.sending && !transcribing) { onMic() },
                contentAlignment = Alignment.Center,
            ) {
                if (transcribing || state.sending) {
                    CircularProgressIndicator(color = onAccent, strokeWidth = 3.dp, modifier = Modifier.size(30.dp))
                } else {
                    Icon(
                        if (recording) Icons.Filled.Stop else Icons.Filled.Mic,
                        contentDescription = "Talk", tint = onAccent, modifier = Modifier.size(34.dp),
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            androidx.compose.material3.TextButton(onClick = onTypeInstead) {
                Text("Prefer to type?", color = palette.accent, fontSize = 13.sp)
            }
        }
    }
}
