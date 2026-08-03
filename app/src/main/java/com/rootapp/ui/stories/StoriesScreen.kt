package com.rootapp.ui.stories

import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Icon
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.rootapp.voice.CloudTts
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rootapp.ai.StoryGenerator
import com.rootapp.data.Classics
import com.rootapp.data.SettingsStore
import com.rootapp.di.AppModule

private const val TOTAL = 5
private val NIGHT_TOP = Color(0xFF0A1730)
private val NIGHT_MID = Color(0xFF16294B)
private val NIGHT_BOT = Color(0xFF060C18)
private val AMBER = Color(0xFFF0C987)

@Composable
fun StoriesScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val premium = remember { SettingsStore(context).premium }
    val tts = remember { TextToSpeech(context) {} }
    DisposableEffect(Unit) { onDispose { tts.stop(); tts.shutdown() } }
    var mode by remember { mutableStateOf("You") }
    LaunchedEffect(mode) { tts.stop() }

    Box(modifier.fillMaxSize().background(Brush.verticalGradient(listOf(NIGHT_TOP, NIGHT_MID, NIGHT_BOT)))) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(top = 12.dp, start = 16.dp, end = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ToggleChip("For you", mode == "You") { mode = "You" }
                ToggleChip("Classics", mode == "Classics") { mode = "Classics" }
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (mode == "You") AiStories(premium, tts) else ClassicsSection(premium, tts)
            }
        }
    }
}

@Composable
private fun ToggleChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(100))
            .background(if (selected) AMBER else Color(0x22FFFFFF))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(label, color = if (selected) NIGHT_TOP else Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun AiStories(premium: Boolean, tts: TextToSpeech) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val stories = remember { mutableStateListOf<StoryGenerator.Story>() }
    var index by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(false) }
    var speaking by remember { mutableStateOf(false) }
    var refreshKey by remember { mutableIntStateOf(0) }

    LaunchedEffect(index, refreshKey) {
        tts.stop(); speaking = false
        if (index < TOTAL && index >= stories.size) {
            loading = true
            stories.add(StoryGenerator.generate(AppModule.llmClient, index))
            loading = false
        }
    }

    Box(Modifier.fillMaxSize()) {
        if (index >= TOTAL) {
            EndingState(onReadMore = { index = 0; stories.clear(); refreshKey++ })
            return@Box
        }

        val transition = rememberInfiniteTransition(label = "moon")
        val scale by transition.animateFloat(1f, 1.05f, infiniteRepeatable(tween(3500), RepeatMode.Reverse), label = "s")
        Canvas(Modifier.fillMaxSize()) {
            val r = 46f * scale
            val c = Offset(size.width * 0.76f, size.height * 0.16f)
            drawCircle(Brush.radialGradient(listOf(Color(0x66AEC4EE), Color.Transparent), c, r * 2.4f), r * 2.4f, c)
            drawCircle(Brush.radialGradient(listOf(Color.White, Color(0xFFC9D7EE)), Offset(c.x - r * 0.3f, c.y - r * 0.3f), r * 1.3f), r, c)
        }

        Row(
            Modifier.fillMaxWidth().padding(top = 10.dp, start = 20.dp, end = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            repeat(TOTAL) { i ->
                Box(Modifier.weight(1f).height(3.dp).clip(RoundedCornerShape(3.dp))
                    .background(if (i <= index) Color.White else Color(0x40FFFFFF)))
            }
        }

        if (loading || index >= stories.size) {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = AMBER)
                Spacer(Modifier.height(14.dp))
                Text("Writing you a story…", color = Color(0xFFAEBCD4), fontSize = 13.sp)
            }
            return@Box
        }

        val s = stories[index]
        Column(
            Modifier.align(Alignment.BottomStart)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xF2040810))))
                .padding(22.dp),
        ) {
            Text("Story ${index + 1} of $TOTAL", color = Color(0xFFAEBCD4), fontSize = 12.sp)
            Spacer(Modifier.height(10.dp))
            Text(s.kicker.uppercase(), color = AMBER, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(s.title, color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            Text(
                s.body, color = Color(0xFFD6E0F0), fontSize = 14.sp,
                modifier = Modifier.heightIn(max = 240.dp).verticalScroll(rememberScrollState()),
            )
            Spacer(Modifier.height(14.dp))
            ListenPill(premium, speaking) {
                if (!premium) {
                    Toast.makeText(context, "Story audio is a premium feature", Toast.LENGTH_SHORT).show()
                } else if (speaking) {
                    CloudTts.stop(); tts.stop(); speaking = false
                } else {
                    speaking = true
                    scope.launch {
                        val ok = if (CloudTts.configured) CloudTts.play(context, s.body) { speaking = false } else false
                        if (!ok) speakCalm(tts, s.body, "story-$index")
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(100)).background(AMBER).padding(13.dp).clickable { index++ },
                contentAlignment = Alignment.Center) {
                Text(if (index == TOTAL - 1) "Finish" else "Next story", color = NIGHT_TOP, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun ClassicsSection(premium: Boolean, tts: TextToSpeech) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selected by remember { mutableStateOf<Classics.Work?>(null) }
    var speaking by remember { mutableStateOf(false) }
    val w = selected

    var shown by remember { mutableStateOf(Classics.random(6)) }
    if (w == null) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
            Text("Read by the greats", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text("Short excerpts from public-domain poets.", color = Color(0xFFAEBCD4), fontSize = 13.sp)
            Spacer(Modifier.height(16.dp))
            shown.forEach { work ->
                Box(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                        .background(Color(0x1AFFFFFF)).clickable { selected = work }.padding(16.dp),
                ) {
                    Column {
                        Text(work.title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        Text("${work.author} · ${work.kind}", color = AMBER, fontSize = 12.sp)
                    }
                }
                Spacer(Modifier.height(10.dp))
            }
            Spacer(Modifier.height(6.dp))
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(100)).background(Color(0x22FFFFFF))
                    .clickable { shown = Classics.random(6) }.padding(vertical = 13.dp),
                contentAlignment = Alignment.Center,
            ) { Text("More", color = AMBER, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
            Spacer(Modifier.height(16.dp))
        }
    } else {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
            Text("← Back", color = AMBER, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable { tts.stop(); speaking = false; selected = null })
            Spacer(Modifier.height(18.dp))
            Text(w.title, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text(w.author, color = AMBER, fontSize = 13.sp)
            Spacer(Modifier.height(18.dp))
            Text(w.excerpt, color = Color(0xFFD6E0F0), fontSize = 17.sp, lineHeight = 26.sp)
            Spacer(Modifier.height(10.dp))
            Text("Excerpt · public domain", color = Color(0xFF8FA0BB), fontSize = 11.sp)
            Spacer(Modifier.height(20.dp))
            ListenPill(premium, speaking) {
                if (!premium) {
                    Toast.makeText(context, "Audio is a premium feature", Toast.LENGTH_SHORT).show()
                } else if (speaking) {
                    CloudTts.stop(); tts.stop(); speaking = false
                } else {
                    speaking = true
                    val line = "${w.title}, by ${w.author}. ${w.excerpt}"
                    scope.launch {
                        val ok = if (CloudTts.configured) CloudTts.play(context, line) { speaking = false } else false
                        if (!ok) speakCalm(tts, line, "classic-${w.title}")
                    }
                }
            }
        }
    }
}

@Composable
private fun ListenPill(premium: Boolean, speaking: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(100)).background(Color(0x33000000))
            .clickable(onClick = onClick).padding(12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (speaking) Icons.Rounded.Stop else Icons.Rounded.Headphones,
            contentDescription = null, tint = AMBER, modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(if (speaking) "Stop" else "Listen", color = AMBER, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

private fun speakCalm(tts: TextToSpeech, text: String, id: String) {
    tts.language = java.util.Locale.getDefault()
    tts.setPitch(0.85f)
    tts.setSpeechRate(0.85f)
    tts.voices?.firstOrNull {
        it.locale.language == "en" && !it.isNetworkConnectionRequired &&
            it.quality >= android.speech.tts.Voice.QUALITY_HIGH
    }?.let { tts.voice = it }
    tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, id)
}

@Composable
private fun EndingState(onReadMore: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(30.dp),
        verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("🌿", fontSize = 52.sp)
        Spacer(Modifier.height(14.dp))
        Text("That's enough for today.", color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Text(
            "Honestly, this is a good place to stop. Go live your real one, text a friend, step outside, or just breathe.",
            color = Color(0xFFC4D3EA), fontSize = 14.sp, textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        Box(
            Modifier.clip(RoundedCornerShape(100)).background(Color(0x22FFFFFF))
                .clickable(onClick = onReadMore).padding(horizontal = 22.dp, vertical = 12.dp),
        ) { Text("Read a few more", color = AMBER, fontWeight = FontWeight.SemiBold) }
        Spacer(Modifier.height(8.dp))
        Text("(but tomorrow-you will thank you for stopping)", color = Color(0xFF8FA0BB), fontSize = 11.sp)
    }
}
