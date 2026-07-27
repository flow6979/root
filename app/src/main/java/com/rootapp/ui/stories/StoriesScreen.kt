package com.rootapp.ui.stories

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
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rootapp.ai.StoryGenerator
import com.rootapp.di.AppModule

private const val TOTAL = 5
private val NIGHT_TOP = Color(0xFF0A1730)
private val NIGHT_MID = Color(0xFF16294B)
private val NIGHT_BOT = Color(0xFF060C18)
private val AMBER = Color(0xFFF0C987)

@Composable
fun StoriesScreen(modifier: Modifier = Modifier) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val premium = remember { com.rootapp.data.SettingsStore(context).premium }
    val stories = remember { mutableStateListOf<StoryGenerator.Story>() }
    var index by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(false) }
    var speaking by remember { mutableStateOf(false) }
    val tts = remember { TextToSpeech(context) {} }
    DisposableEffect(Unit) { onDispose { tts.stop(); tts.shutdown() } }

    // Generate the current story on demand (AI). Finite: TOTAL per day.
    LaunchedEffect(index) {
        tts.stop(); speaking = false
        if (index < TOTAL && index >= stories.size) {
            loading = true
            stories.add(StoryGenerator.generate(AppModule.llmClient, index))
            loading = false
        }
    }

    Box(
        modifier.fillMaxSize().background(Brush.verticalGradient(listOf(NIGHT_TOP, NIGHT_MID, NIGHT_BOT))),
    ) {
        if (index >= TOTAL) { EndingState(onClose = { index = 0; stories.clear() }); return@Box }

        val transition = rememberInfiniteTransition(label = "moon")
        val scale by transition.animateFloat(1f, 1.05f, infiniteRepeatable(tween(3500), RepeatMode.Reverse), label = "s")
        Canvas(Modifier.fillMaxSize()) {
            val r = 46f * scale
            val c = Offset(size.width * 0.76f, size.height * 0.18f)
            drawCircle(Brush.radialGradient(listOf(Color(0x66AEC4EE), Color.Transparent), c, r * 2.4f), r * 2.4f, c)
            drawCircle(Brush.radialGradient(listOf(Color.White, Color(0xFFC9D7EE)), Offset(c.x - r * 0.3f, c.y - r * 0.3f), r * 1.3f), r, c)
        }

        Row(
            Modifier.fillMaxWidth().padding(top = 14.dp, start = 20.dp, end = 20.dp),
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
                modifier = Modifier.heightIn(max = 260.dp).verticalScroll(rememberScrollState()),
            )
            Spacer(Modifier.height(14.dp))
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(100)).background(Color(0x33000000))
                    .clickable {
                        if (!premium) {
                            Toast.makeText(context, "Story audio is a premium feature", Toast.LENGTH_SHORT).show()
                        } else if (speaking) {
                            tts.stop(); speaking = false
                        } else {
                            tts.language = java.util.Locale.getDefault()
                            tts.setPitch(0.9f)
                            tts.setSpeechRate(0.88f)
                            tts.speak(s.body, TextToSpeech.QUEUE_FLUSH, null, "story-$index")
                            speaking = true
                        }
                    }
                    .padding(12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    when {
                        !premium -> "🎧 Listen · Premium"
                        speaking -> "⏹ Stop"
                        else -> "🎧 Listen"
                    },
                    color = AMBER, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                )
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
private fun EndingState(onClose: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(30.dp),
        verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("🌿", fontSize = 52.sp)
        Spacer(Modifier.height(14.dp))
        Text("That's enough for today.", color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Text(
            "Go live your real one. Text a friend, step outside, or just breathe.",
            color = Color(0xFFC4D3EA), fontSize = 14.sp, textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onClose, colors = ButtonDefaults.buttonColors(containerColor = Color.White)) {
            Text("Read more tomorrow", color = NIGHT_TOP, fontWeight = FontWeight.SemiBold)
        }
    }
}
