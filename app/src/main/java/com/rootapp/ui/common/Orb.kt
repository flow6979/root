package com.rootapp.ui.common

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.rootapp.ui.theme.LocalRootPalette

/**
 * The celestial companion. Breathes gently; renders as a soft glowing disc.
 * (Cloud shading + true lunar phases are a later polish - see docs/DECISIONS.md D11.)
 */
@Composable
fun Orb(size: Dp = 64.dp, modifier: Modifier = Modifier) {
    val palette = LocalRootPalette.current
    val transition = rememberInfiniteTransition(label = "orb")
    val scale by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(tween(2500), RepeatMode.Reverse),
        label = "breathe",
    )
    val glowAlpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.6f,
        animationSpec = infiniteRepeatable(tween(2500), RepeatMode.Reverse),
        label = "glow",
    )

    Canvas(modifier = modifier.size(size)) {
        val r = (this.size.minDimension / 2f) * scale
        val center = Offset(this.size.width / 2f, this.size.height / 2f)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(palette.orbGlow.copy(alpha = glowAlpha), Color.Transparent),
                center = center,
                radius = r * 2.1f,
            ),
            radius = r * 2.1f,
            center = center,
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color.White, palette.orb),
                center = Offset(center.x - r * 0.25f, center.y - r * 0.3f),
                radius = r * 1.4f,
            ),
            radius = r,
            center = center,
        )
    }
}
