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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.rootapp.data.SkyTheme
import com.rootapp.ui.theme.LocalRootPalette
import java.util.Calendar
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Living sky behind every screen. The sun / moon sits on a real arc for the current time -
 * it rises low at dawn, peaks at midday, sets at dusk, and the moon crosses through the night -
 * so as time passes through the day the body visibly climbs and falls. Clouds drift by day,
 * stars twinkle at night, and the whole thing breathes gently. Minimalist mode keeps only a
 * faint disc so the black-and-white theme stays clean.
 */
@Composable
fun SkyBackground(
    hour: Int,
    minimalist: Boolean,
    modifier: Modifier = Modifier,
    theme: SkyTheme = SkyTheme.DEFAULT,
) {
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
    // A shooting star: streaks briefly near the start of each long cycle, then rests.
    val shoot by transition.animateFloat(
        0f, 1f, infiniteRepeatable(tween(9000, easing = LinearEasing), RepeatMode.Restart), label = "shoot",
    )

    // Deterministic star field (fixed positions so it doesn't jump on recomposition).
    val stars = remember {
        var seed = 20260802
        fun rnd(): Float { seed = (seed * 1103515245 + 12345) and 0x7fffffff; return (seed % 1000) / 1000f }
        List(70) { Triple(rnd(), rnd() * 0.78f, rnd()) } // x-fraction, y-fraction (most of the sky), phase
    }
    // A denser field used only by the Starfield cosmetic (visible even by day).
    val bonusStars = remember {
        var seed = 991733
        fun rnd(): Float { seed = (seed * 1103515245 + 12345) and 0x7fffffff; return (seed % 1000) / 1000f }
        List(90) { Triple(rnd(), rnd() * 0.82f, rnd()) }
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
                // shooting star (brief streak at the start of the cycle)
                if (shoot < 0.11f) {
                    val p = shoot / 0.11f
                    val sx = w * (0.18f + 0.5f * p)
                    val sy = h * (0.08f + 0.2f * p)
                    val len = w * 0.13f
                    val a = ((1f - p) * 0.85f * palette.starsAlpha).coerceIn(0f, 1f)
                    drawLine(
                        Color.White.copy(alpha = a),
                        Offset(sx, sy), Offset(sx - len, sy - len * 0.45f),
                        strokeWidth = 3f, cap = StrokeCap.Round,
                    )
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

            // Subtle deep-space extras on the night sky: a tiny ringed planet + drifting asteroids.
            if (!isDay && palette.starsAlpha > 0f) {
                val u = minOf(w, h)
                drawRingedPlanet(w * 0.84f, h * 0.13f, u * 0.020f, Color(0xFFCBB68F), Color(0xFFE7DAB8), 0.30f)
                drawAsteroid(wrap(0.20f + drift * 0.30f) * w, h * 0.55f, u * 0.015f, Color.White, 0.18f)
                drawAsteroid(wrap(0.72f - drift * 0.22f) * w, h * 0.63f, u * 0.012f, Color.White, 0.14f)
            }

            // ---- earned cosmetic sky themes (Phase C) ----
            when (theme) {
                SkyTheme.GOLDEN_HOUR -> drawRect(
                    Brush.verticalGradient(
                        listOf(
                            Color(0xFFFFC46B).copy(alpha = 0.24f),
                            Color(0xFFFF8A5B).copy(alpha = 0.08f),
                            Color.Transparent,
                        ),
                    ),
                )
                SkyTheme.STARFIELD -> {
                    val floorA = maxOf(palette.starsAlpha, 0.12f)
                    bonusStars.forEach { (fx, fy, ph) ->
                        val a = floorA * (0.3f + 0.7f * abs(sin(((twinkle + ph) * 2f * PI).toFloat())))
                        drawCircle(Color.White.copy(alpha = a.coerceIn(0f, 1f)), 0.6f + ph * 1.5f, Offset(fx * w, fy * h))
                    }
                }
                SkyTheme.METEOR -> drawMeteors(shoot, w, h)
                SkyTheme.AURORA -> drawAurora(drift, w, h)
                SkyTheme.COSMOS -> {
                    val u = minOf(w, h)
                    // deep-space wash so it reads as outer space at any time of day
                    drawRect(
                        Brush.verticalGradient(
                            listOf(
                                Color(0xFF0B1030).copy(alpha = 0.60f),
                                Color(0xFF1B0E33).copy(alpha = 0.40f),
                                Color(0xFF06030F).copy(alpha = 0.30f),
                            ),
                        ),
                    )
                    drawNebula(w, h)
                    // a dense, bright starfield
                    bonusStars.forEach { (fx, fy, ph) ->
                        val a = 0.4f + 0.6f * abs(sin(((twinkle + ph) * 2f * PI).toFloat()))
                        drawCircle(Color.White.copy(alpha = (a * 0.9f).coerceIn(0f, 1f)), 0.6f + ph * 1.9f, Offset(fx * w, fy * h))
                    }
                    // a big ringed planet + drifting asteroids of different sizes
                    drawRingedPlanet(w * 0.26f, h * 0.28f, u * 0.06f, Color(0xFFC9A26B), Color(0xFFEAD9B0), 0.92f)
                    drawAsteroid(wrap(0.15f + drift * 0.40f) * w, h * 0.50f, u * 0.030f, Color(0xFF8A8FA3), 0.9f)
                    drawAsteroid(wrap(0.60f + drift * 0.26f) * w, h * 0.66f, u * 0.022f, Color(0xFF9AA0B2), 0.85f)
                    drawAsteroid(wrap(0.88f - drift * 0.30f) * w, h * 0.40f, u * 0.026f, Color(0xFF7E839A), 0.85f)
                    // an occasional comet
                    drawMeteors(shoot, w, h)
                }
                SkyTheme.DEFAULT -> {}
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

/** Aurora cosmetic: two soft, wavy ribbons of light drifting across the upper sky. */
private fun DrawScope.drawAurora(drift: Float, w: Float, h: Float) {
    val bands = listOf(
        Triple(0.20f, Color(0xFF5CE1B6), 0.0f),
        Triple(0.29f, Color(0xFF8A7CFF), 0.6f),
    )
    val steps = 26
    bands.forEach { (baseY, color, phase) ->
        val path = Path()
        val y0 = baseY * h
        val amp = h * 0.03f
        for (i in 0..steps) {
            val x = w * i / steps
            val y = y0 + amp * sin((i / steps.toFloat() * 3f + drift * 2f + phase) * PI.toFloat())
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color.copy(alpha = 0.22f), style = Stroke(width = h * 0.045f, cap = StrokeCap.Round))
    }
}

/** Keep a drifting fraction within a smooth 0..1 loop (with a little off-screen margin). */
private fun wrap(x: Float): Float = ((x % 1.1f) + 1.1f) % 1.1f - 0.05f

/** A small irregular asteroid with a couple of craters. */
private fun DrawScope.drawAsteroid(cx: Float, cy: Float, r: Float, color: Color, alpha: Float) {
    val path = Path()
    val pts = 9
    for (i in 0..pts) {
        val ang = (i.toFloat() / pts) * 2f * PI.toFloat()
        val rr = r * (0.72f + 0.30f * abs(sin((i * 41 + 7).toFloat())))
        val x = cx + rr * cos(ang)
        val y = cy + rr * sin(ang)
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    drawPath(path, color.copy(alpha = alpha))
    drawCircle(color.copy(alpha = alpha * 0.45f), r * 0.20f, Offset(cx - r * 0.22f, cy - r * 0.10f))
    drawCircle(color.copy(alpha = alpha * 0.45f), r * 0.13f, Offset(cx + r * 0.26f, cy + r * 0.22f))
}

/** A stylised ringed planet (Saturn-like): an ellipse ring with the body drawn over it. */
private fun DrawScope.drawRingedPlanet(cx: Float, cy: Float, r: Float, body: Color, ring: Color, alpha: Float) {
    val ringW = r * 2.7f
    val ringH = r * 0.85f
    drawOval(
        color = ring.copy(alpha = alpha * 0.55f),
        topLeft = Offset(cx - ringW / 2f, cy - ringH / 2f),
        size = Size(ringW, ringH),
        style = Stroke(width = r * 0.16f),
    )
    drawCircle(body.copy(alpha = alpha), r, Offset(cx, cy))
    drawCircle(Color.White.copy(alpha = alpha * 0.18f), r * 0.55f, Offset(cx - r * 0.3f, cy - r * 0.3f))
}

/** Soft nebula clouds for the Cosmos theme. */
private fun DrawScope.drawNebula(w: Float, h: Float) {
    listOf(
        Triple(0.30f, 0.32f, Color(0xFF7A4FB0)),
        Triple(0.72f, 0.52f, Color(0xFF3F6BB0)),
        Triple(0.5f, 0.8f, Color(0xFFB05587)),
    ).forEach { (fx, fy, c) ->
        val center = Offset(fx * w, fy * h)
        val radius = w * 0.55f
        drawCircle(
            Brush.radialGradient(listOf(c.copy(alpha = 0.22f), Color.Transparent), center = center, radius = radius),
            radius = radius, center = center,
        )
    }
}

/** Meteor-shower cosmetic: several staggered streaks so the sky is never still. */
private fun DrawScope.drawMeteors(shoot: Float, w: Float, h: Float) {
    listOf(0f, 0.33f, 0.66f).forEachIndexed { idx, off ->
        val ph = (shoot + off) % 1f
        if (ph < 0.25f) {
            val p = ph / 0.25f
            val sx = w * (0.1f + 0.7f * p) + idx * w * 0.05f
            val sy = h * (0.05f + 0.25f * p) + idx * h * 0.08f
            val len = w * 0.16f
            val a = ((1f - p) * 0.9f).coerceIn(0f, 1f)
            drawLine(
                Color.White.copy(alpha = a),
                Offset(sx, sy), Offset(sx - len, sy - len * 0.5f),
                strokeWidth = 3.5f, cap = StrokeCap.Round,
            )
        }
    }
}
