package com.goncalocosta.lumen.home

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import kotlin.random.Random

internal data class StarPoint(
    val xFrac: Float,
    val yFrac: Float,
    val radiusPx: Float,
    val alpha: Float,
    val warm: Boolean
)

internal val StarField: List<StarPoint> by lazy {
    val rng = Random(4219)
    List(210) {
        val y = rng.nextFloat() * 0.66f
        val fadeByHorizon = (1f - (y / 0.68f)).coerceIn(0.15f, 1f)
        StarPoint(
            xFrac = rng.nextFloat(),
            yFrac = y,
            radiusPx = if (rng.nextFloat() > 0.88f) 2.2f else if (rng.nextFloat() > 0.55f) 1.4f else 0.9f,
            alpha = (0.25f + rng.nextFloat() * 0.70f) * fadeByHorizon,
            warm = rng.nextFloat() > 0.72f
        )
    }
}

/**
 * Recreates the atmospheric starry night sky, warm amber twilight horizon glow,
 * and layered desert hills using `drawWithCache` so all Paths and Brushes are allocated
 * once and cached on the GPU for locked 60fps performance.
 *
 * The offscreen compositing layer makes HWUI rasterise the scene once into a texture and
 * simply composite it on later frames, instead of re-drawing ~220 primitives and several
 * full-screen gradients every time the dock highlight animates.
 */
@Composable
fun StarryTwilightBackground(modifier: Modifier = Modifier) {
    Spacer(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithCache {
                val w = size.width
                val h = size.height

                val skyBrush = Brush.verticalGradient(
                    colorStops = arrayOf(
                        0.00f to Color(0xFF0A1622),
                        0.32f to Color(0xFF152D3D),
                        0.58f to Color(0xFF234150),
                        0.74f to Color(0xFF4C4E4D),
                        0.86f to Color(0xFF2B2219),
                        1.00f to Color(0xFF0E0B08)
                    )
                )

                val leftSunsetBrush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFFD99B66).copy(alpha = 0.52f),
                        Color(0xFF9E6B43).copy(alpha = 0.30f),
                        Color(0xFF4A3B32).copy(alpha = 0.12f),
                        Color.Transparent
                    ),
                    center = Offset(w * 0.18f, h * 0.72f),
                    radius = w * 0.62f
                )

                val centerSunsetBrush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFFB57C4F).copy(alpha = 0.32f),
                        Color(0xFF6B4C35).copy(alpha = 0.16f),
                        Color.Transparent
                    ),
                    center = Offset(w * 0.48f, h * 0.75f),
                    radius = w * 0.48f
                )

                val backHill = Path().apply {
                    moveTo(0f, h * 0.71f)
                    cubicTo(w * 0.18f, h * 0.69f, w * 0.36f, h * 0.74f, w * 0.58f, h * 0.73f)
                    cubicTo(w * 0.78f, h * 0.72f, w * 0.90f, h * 0.69f, w, h * 0.68f)
                    lineTo(w, h)
                    lineTo(0f, h)
                    close()
                }
                val backHillBrush = Brush.verticalGradient(
                    colors = listOf(Color(0xFF3E2D1E), Color(0xFF21170F), Color(0xFF0E0A07)),
                    startY = h * 0.68f,
                    endY = h
                )

                val midDune = Path().apply {
                    moveTo(0f, h * 0.74f)
                    cubicTo(w * 0.14f, h * 0.71f, w * 0.28f, h * 0.77f, w * 0.46f, h * 0.83f)
                    cubicTo(w * 0.64f, h * 0.89f, w * 0.82f, h * 0.80f, w, h * 0.76f)
                    lineTo(w, h)
                    lineTo(0f, h)
                    close()
                }
                val midDuneBrush = Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF6B4C30).copy(alpha = 0.85f),
                        Color(0xFF3D2B1C).copy(alpha = 0.92f),
                        Color(0xFF1A130D)
                    ),
                    start = Offset(w * 0.10f, h * 0.72f),
                    end = Offset(w * 0.75f, h * 0.96f)
                )

                val ridgeHighlight = Path().apply {
                    moveTo(w * 0.15f, h * 0.74f)
                    quadraticTo(w * 0.26f, h * 0.82f, w * 0.37f, h * 0.98f)
                    lineTo(w * 0.43f, h * 0.98f)
                    quadraticTo(w * 0.30f, h * 0.81f, w * 0.18f, h * 0.74f)
                    close()
                }
                val ridgeBrush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF9C7248).copy(alpha = 0.25f),
                        Color(0xFF7A5634).copy(alpha = 0.15f),
                        Color.Transparent
                    ),
                    startY = h * 0.74f,
                    endY = h * 0.98f
                )

                val frontSlope = Path().apply {
                    moveTo(0f, h * 0.86f)
                    quadraticTo(w * 0.22f, h * 0.91f, w * 0.45f, h * 0.96f)
                    quadraticTo(w * 0.72f, h * 0.89f, w, h * 0.87f)
                    lineTo(w, h)
                    lineTo(0f, h)
                    close()
                }
                val frontSlopeBrush = Brush.verticalGradient(
                    colors = listOf(Color(0xFF1F160E).copy(alpha = 0.90f), Color(0xFF0A0806)),
                    startY = h * 0.86f,
                    endY = h
                )

                onDrawBehind {
                    drawRect(brush = skyBrush)
                    drawRect(brush = leftSunsetBrush)
                    drawRect(brush = centerSunsetBrush)

                    for (star in StarField) {
                        val color = if (star.warm) Color(0xFFFFF3E0) else Color(0xFFE0F7FA)
                        drawCircle(
                            color = color.copy(alpha = star.alpha),
                            radius = star.radiusPx,
                            center = Offset(w * star.xFrac, h * star.yFrac)
                        )
                    }

                    drawPath(path = backHill, brush = backHillBrush)
                    drawPath(path = midDune, brush = midDuneBrush)
                    drawPath(path = ridgeHighlight, brush = ridgeBrush)
                    drawPath(path = frontSlope, brush = frontSlopeBrush)
                }
            }
    )
}
