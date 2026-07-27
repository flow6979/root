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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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

private data class Story(val kicker: String, val title: String, val body: String)

private val STORIES = listOf(
    Story("Someone like you", "The night Aman deleted the app at 3am",
        "He'd told himself “five minutes.” Two hours later the ceiling was still blue-lit and grey. What changed wasn't willpower — it was one small rule he made with himself the next morning…"),
    Story("A small idea", "What if bored was allowed?",
        "Boredom isn't the enemy. It's the quiet your mind needs to actually rest. Tonight, try letting one dull moment just… be dull."),
    Story("Before bed", "The 4-7-8 breath",
        "Breathe in for 4, hold for 7, out for 8. Three rounds. It tells your body the day is over, better than any last scroll ever could."),
    Story("Someone like you", "Priya's one-tap rule",
        "She couldn't quit the feed cold. So she made a deal: before opening it, text one real friend first. Half the time, she never opened the feed."),
    Story("Tomorrow", "Pick one small thing",
        "Not a resolution. One tiny thing for tomorrow-you: a glass of water by the bed, a walk before noon. Small is what actually sticks."),
)

private val NIGHT_TOP = Color(0xFF0A1730)
private val NIGHT_MID = Color(0xFF16294B)
private val NIGHT_BOT = Color(0xFF060C18)
private val AMBER = Color(0xFFF0C987)

@Composable
fun StoriesScreen(modifier: Modifier = Modifier) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val premium = remember { com.rootapp.data.SettingsStore(context).premium }
    var index by remember { mutableIntStateOf(0) }
    val total = STORIES.size

    Box(
        modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(NIGHT_TOP, NIGHT_MID, NIGHT_BOT))),
    ) {
        if (index >= total) {
            EndingState(onClose = { index = 0 })
            return@Box
        }

        // breathing moon
        val transition = rememberInfiniteTransition(label = "moon")
        val scale by transition.animateFloat(
            1f, 1.05f, infiniteRepeatable(tween(3500), RepeatMode.Reverse), label = "s",
        )
        Canvas(Modifier.fillMaxSize()) {
            val r = 46f * scale
            val c = Offset(size.width * 0.76f, size.height * 0.20f)
            drawCircle(Brush.radialGradient(
                listOf(Color(0x66AEC4EE), Color.Transparent), c, r * 2.4f), r * 2.4f, c)
            drawCircle(Brush.radialGradient(
                listOf(Color.White, Color(0xFFC9D7EE)), Offset(c.x - r * 0.3f, c.y - r * 0.3f), r * 1.3f), r, c)
        }

        // segment progress
        Row(
            Modifier.fillMaxWidth().padding(top = 14.dp, start = 20.dp, end = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            repeat(total) { i ->
                Box(
                    Modifier.weight(1f).height(3.dp).clip(RoundedCornerShape(3.dp))
                        .background(if (i <= index) Color.White else Color(0x40FFFFFF)),
                )
            }
        }

        // story copy over a bottom scrim
        Column(
            Modifier.align(Alignment.BottomStart)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xF2040810))))
                .padding(22.dp)
                .clickable { index++ },
        ) {
            val s = STORIES[index]
            Text("Story ${index + 1} of $total", color = Color(0xFFAEBCD4), fontSize = 12.sp)
            Spacer(Modifier.height(10.dp))
            Text(s.kicker.uppercase(), color = AMBER, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(s.title, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            Text(s.body, color = Color(0xFFD6E0F0), fontSize = 14.sp)
            Spacer(Modifier.height(16.dp))
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(100))
                    .background(Color(0x33000000)).padding(12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (premium) "🎧 Listen" else "🎧 Listen instead · Premium",
                    color = AMBER, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                "tap when you're ready — no rush",
                color = Color(0xFFAEBCD4), fontSize = 11.sp,
                modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun EndingState(onClose: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(30.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("🌿", fontSize = 52.sp)
        Spacer(Modifier.height(14.dp))
        Text("That's enough for today.", color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Text(
            "You read 5 stories. Now go live your real one — text a friend, step outside, or just breathe. I'll be here tomorrow.",
            color = Color(0xFFC4D3EA), fontSize = 14.sp, textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onClose, colors = ButtonDefaults.buttonColors(containerColor = Color.White)) {
            Text("Start over", color = NIGHT_TOP, fontWeight = FontWeight.SemiBold)
        }
    }
}
