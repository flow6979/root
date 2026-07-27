package com.rootapp.ui.common

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.rootapp.ui.theme.LocalRootPalette
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * The celestial companion. Breathes gently. When [interactive], you can drag it around
 * (it springs back) and tap it to make it bounce.
 */
@Composable
fun Orb(size: Dp = 64.dp, interactive: Boolean = false, modifier: Modifier = Modifier) {
    val palette = LocalRootPalette.current
    val transition = rememberInfiniteTransition(label = "orb")
    val scale by transition.animateFloat(
        initialValue = 1f, targetValue = 1.06f,
        animationSpec = infiniteRepeatable(tween(2500), RepeatMode.Reverse), label = "breathe",
    )
    val glowAlpha by transition.animateFloat(
        initialValue = 0.35f, targetValue = 0.6f,
        animationSpec = infiniteRepeatable(tween(2500), RepeatMode.Reverse), label = "glow",
    )

    val scope = rememberCoroutineScope()
    val offsetX = remember { Animatable(0f) }
    val offsetY = remember { Animatable(0f) }
    val tapScale = remember { Animatable(1f) }

    var mod = modifier.size(size)
    if (interactive) {
        mod = mod
            .offset { IntOffset(offsetX.value.roundToInt(), offsetY.value.roundToInt()) }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDrag = { change, delta ->
                        change.consume()
                        scope.launch { offsetX.snapTo(offsetX.value + delta.x) }
                        scope.launch { offsetY.snapTo(offsetY.value + delta.y) }
                    },
                    onDragEnd = {
                        scope.launch { offsetX.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy)) }
                        scope.launch { offsetY.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy)) }
                    },
                )
            }
            .pointerInput(Unit) {
                detectTapGestures {
                    scope.launch {
                        tapScale.animateTo(1.28f, tween(110))
                        tapScale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
                    }
                }
            }
    }

    Canvas(modifier = mod) {
        val r = (this.size.minDimension / 2f) * scale * tapScale.value
        val center = Offset(this.size.width / 2f, this.size.height / 2f)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(palette.orbGlow.copy(alpha = glowAlpha), Color.Transparent),
                center = center, radius = r * 2.1f,
            ),
            radius = r * 2.1f, center = center,
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color.White, palette.orb),
                center = Offset(center.x - r * 0.25f, center.y - r * 0.3f), radius = r * 1.4f,
            ),
            radius = r, center = center,
        )
    }
}
