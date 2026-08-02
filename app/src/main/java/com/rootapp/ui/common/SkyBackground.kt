package com.rootapp.ui.common

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.rootapp.ui.theme.LocalRootPalette
import java.util.Calendar
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/**
 * Living sky behind every screen. The sun / moon sits on a real arc for the current time -
 * it rises low at dawn, peaks at midday, sets at dusk, and the moon crosses through the night -
 * so as time passes through the day the body visibly climbs and falls. Clouds drift by day,
 * stars twinkle at night, and the whole thing breathes gently. Minimalist mode keeps only a
 * faint disc so the black-and-white theme stays clean.
 */
@Composable
fun SkyBackground(hour: Int, minimalist: Boolean, modifier: Modifier = Modifier) {
    val palette = LocalRootPalette.current
    val minute = remember(hour) { Calendar.getInstance().get(Calendar.MINUTE) }
    val minutesOfDay = (((hour % 24) + 24) % 24) * 60 + minute
    val isDay = minutesOfDay in 300..1140 // 05:00 - 19:00 the sun is up; otherwise the moon

    val transition = rememberInfiniteTransition(label = "sky")
    val drift by transition.animateFloat(
        0f, 1f, infiniteRepeatable(tween(60000, easing = LinearEasing), RepeatMode.Restart), label = "drift",
    )
    val twinkle by transition.animateFloat(
        0f, 1f, infiniteRepeatable(tween(3200), RepeatMode.Reverse), label = "twinkle",
    )
    val bob by transition.animateFloat(
        -1f, 1f, infiniteRepeatable(tween(5200), RepeatMode.Reverse), label = "bob",
    )

    // Deterministic star field (fixed positions so it doesn't jump on recomposition).
    val stars = remember {
        var seed = 20260802
        fun rnd(): Float { seed = (seed * 1103515245 + 12345) and 0x7fffffff; return (seed % 1000) / 1000f }
        List(70) { Triple(rnd(), rnd() * 0.78f, rnd()) } // x-fraction, y-fraction (most of the sky), phase
    }

    Canvas(modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        drawRect(Brush.verticalGradient(listOf(palette.bg1, palette.bg2)))

        if (!minimalist) {
            // Twinkling stars (only when the palette says the sky is dark enough).
            if (palette.starsAlpha > 0f) {
                stars.forEach { (fx, fy, ph) ->
                    val a = palette.starsAlpha * (0.35f + 0.65f * abs(sin(((twinkle + ph) * 2f * PI).toFloat())))
                    drawCircle(Color.White.copy(alpha = a.coerceIn(0f, 1f)), 0.7f + ph * 1.7f, Offset(fx * w, fy * h))
                }
            }
            // Slow-drifting clouds by day.
            if (isDay) {
                listOf(
                    Triple(0.12f, 0.20f, 0.55f),
                    Triple(0.52f, 0.13f, 0.32f),
                    Triple(0.80f, 0.28f, 0.46f),
                ).forEach { (baseX, fy, speed) ->
                    val x = (((baseX + drift * speed) % 1.35f) - 0.2f) * w
                    drawCloud(x, fy * h, (0.10f + speed * 0.05f) * w, Color.White.copy(alpha = 0.16f))
                }
            }
        }

        // ---- the sun / moon on its time-of-day arc ----
        val p = if (isDay) {
            (minutesOfDay - 300) / 840f
        } else {
            val m = if (minutesOfDay >= 1140) minutesOfDay else minutesOfDay + 1440
            (m - 1140) / 600f
        }.coerceIn(0f, 1f)
        // Big, real-sky arc: rises low on the left, sweeps high across the top, sets low on the right.
        val cx = (0.12f + 0.76f * p) * w
        val altitude = sin((p * PI).toFloat()) // 0 at horizon, 1 at peak
        val cy = (0.90f - 0.78f * altitude) * h + bob * 5f
        val r = minOf(w, h) * 0.072f

        // soft glow
        drawCircle(
            Brush.radialGradient(
                listOf(palette.orbGlow.copy(alpha = if (minimalist) 0.10f else 0.42f), Color.Transparent),
                center = Offset(cx, cy), radius = r * 3.2f,
            ),
            radius = r * 3.2f, center = Offset(cx, cy),
        )
        // the disc (warm sun / cool moon comes straight from the time-driven palette)
        drawCircle(
            Brush.radialGradient(
                listOf(Color.White, palette.orb),
                center = Offset(cx - r * 0.25f, cy - r * 0.3f), radius = r * 1.5f,
            ),
            radius = r, center = Offset(cx, cy),
        )
        // faint craters give the night disc a moon feel
        if (!isDay && !minimalist) {
            val crater = palette.orbGlow.copy(alpha = 0.25f)
            drawCircle(crater, r * 0.16f, Offset(cx + r * 0.28f, cy - r * 0.18f))
            drawCircle(crater, r * 0.10f, Offset(cx - r * 0.30f, cy + r * 0.16f))
            drawCircle(crater, r * 0.12f, Offset(cx + r * 0.06f, cy + r * 0.34f))
        }
    }
}

private fun DrawScope.drawCloud(x: Float, y: Float, s: Float, color: Color) {
    drawCircle(color, s * 0.50f, Offset(x, y))
    drawCircle(color, s * 0.66f, Offset(x + s * 0.5f, y + s * 0.08f))
    drawCircle(color, s * 0.50f, Offset(x + s * 1.0f, y))
    drawCircle(color, s * 0.42f, Offset(x + s * 0.5f, y - s * 0.18f))
}
