package com.rootapp.ui.common

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rootapp.ui.theme.LocalRootPalette
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/** One consistent frosted card. The whole app uses this so spacing + shape never drift. */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    padding: Dp = 18.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val palette = LocalRootPalette.current
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(palette.surface)
            .padding(padding),
        content = content,
    )
}

/** Small uppercase, letter-spaced section header. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        modifier = modifier,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.4.sp,
        color = LocalRootPalette.current.dim,
    )
}

/**
 * The signature element: a circular gauge (a 280 degree arc with a gap at the bottom) showing a
 * 0-100 score in the middle. Replaces flat big numbers so scores feel designed.
 */
@Composable
fun ScoreRing(
    score: Int?,
    label: String,
    modifier: Modifier = Modifier,
    size: Dp = 128.dp,
    stroke: Dp = 12.dp,
) {
    val palette = LocalRootPalette.current
    val target = ((score ?: 0).coerceIn(0, 100)) / 100f
    // Ease the ring fill (and the number count-up) in from zero whenever the score changes.
    val fill = remember(score) { Animatable(0f) }
    LaunchedEffect(score) { fill.animateTo(target, tween(1000, easing = FastOutSlowInEasing)) }
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val s = stroke.toPx()
            val arc = Size(this.size.width - s, this.size.height - s)
            val topLeft = Offset(s / 2, s / 2)
            drawArc(palette.accentSoft, 130f, 280f, false, topLeft, arc, style = Stroke(s, cap = StrokeCap.Round))
            drawArc(palette.accent, 130f, 280f * fill.value, false, topLeft, arc, style = Stroke(s, cap = StrokeCap.Round))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                if (score == null) "--" else "${(fill.value * 100).roundToInt()}",
                fontSize = 34.sp, fontWeight = FontWeight.Bold, color = palette.accent,
            )
            Text(label, fontSize = 11.sp, color = palette.dim)
        }
    }
}

/**
 * Gentle entrance: fades a composable in while easing it up a few dp. Pass a per-item [delayMs]
 * to stagger a list of cards so a screen "settles" into place. Runs once on first composition.
 */
fun Modifier.enterUp(delayMs: Int = 0): Modifier = composed {
    val p = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        if (delayMs > 0) delay(delayMs.toLong())
        p.animateTo(1f, tween(durationMillis = 480, easing = FastOutSlowInEasing))
    }
    graphicsLayer {
        alpha = p.value
        translationY = (1f - p.value) * 28.dp.toPx()
    }
}

/** A rounded tile holding a single line-icon - used for list rows and insight cards (no emoji). */
@Composable
fun IconTile(icon: ImageVector, modifier: Modifier = Modifier, size: Dp = 38.dp, tint: Color? = null) {
    val palette = LocalRootPalette.current
    Box(
        modifier.size(size).clip(RoundedCornerShape(12.dp)).background(palette.accentSoft),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint ?: palette.accent, modifier = Modifier.size(size * 0.52f))
    }
}

/** A small coloured status pill, e.g. "avoid" (warm) or "healthy" (accent). */
@Composable
fun StatChip(text: String, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.16f))
            .padding(horizontal = 9.dp, vertical = 3.dp),
    ) {
        Text(text, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = color)
    }
}
