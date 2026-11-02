package com.goncalocosta.lumen.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import kotlinx.coroutines.launch

internal const val NOW_PLAYING_KEY = "now_playing"
private val NowPlayingPillShape = RoundedCornerShape(24.dp)

@Composable
fun NowPlayingPill(
    media: MediaTrackInfo,
    focus: FocusRegistry,
    canFocus: Boolean,
    onNavigateDown: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val progress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var isFocused by remember { mutableStateOf(false) }

    AnimatedVisibility(
        visible = media.isActive,
        enter = fadeIn(tween(200)) + scaleIn(tween(200), initialScale = 0.92f),
        exit = fadeOut(tween(160)) + scaleOut(tween(160), targetScale = 0.92f),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .padding(top = 8.dp)
                .height(48.dp)
                .widthIn(min = 160.dp, max = 280.dp)
                .focusRequester(focus.of(NOW_PLAYING_KEY))
                .focusProperties { this.canFocus = canFocus }
                .onFocusChanged {
                    isFocused = it.isFocused
                    if (it.isFocused) focus.onFocused(NOW_PLAYING_KEY)
                    scope.launch { progress.animateTo(if (it.isFocused) 1f else 0f, tween(160)) }
                }
                .onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionDown) {
                        onNavigateDown()
                        true
                    } else false
                }
                .tvClickable(
                    onClick = { MediaSessionTracker.togglePlayPause(context) },
                    onLongClick = { MediaSessionTracker.openCurrentApp(context) }
                )
                .graphicsLayer {
                    val s = 1f + 0.04f * progress.value
                    scaleX = s
                    scaleY = s
                }
                .clip(NowPlayingPillShape)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = 0.14f + 0.16f * progress.value),
                            Color.White.copy(alpha = 0.05f + 0.10f * progress.value)
                        )
                    )
                )
                .border(
                    width = 1.dp,
                    color = Color.White.copy(alpha = 0.20f + 0.40f * progress.value),
                    shape = NowPlayingPillShape
                )
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (media.artwork != null) {
                Image(
                    bitmap = media.artwork,
                    contentDescription = null,
                    modifier = Modifier
                        .size(30.dp)
                        .clip(RoundedCornerShape(6.dp)),
                    contentScale = ContentScale.Crop
                )
                Spacer(Modifier.width(8.dp))
            } else {
                EqualizerBars(
                    isPlaying = media.isPlaying,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(10.dp))
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = media.title ?: "Now Playing",
                    color = if (isFocused) GlassWhite else Color.White.copy(alpha = 0.92f),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!media.artist.isNullOrBlank()) {
                    Text(
                        text = media.artist,
                        color = if (isFocused) GlassSubtleText else Color.White.copy(alpha = 0.60f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(Modifier.width(8.dp))

            // Play / Pause mini indicator glyph
            Canvas(modifier = Modifier.size(14.dp)) {
                if (media.isPlaying) {
                    drawPauseGlyph(Color.White.copy(alpha = 0.85f))
                } else {
                    drawPlayGlyph(Color.White.copy(alpha = 0.85f))
                }
            }
        }
    }
}

@Composable
internal fun EqualizerBars(
    isPlaying: Boolean,
    modifier: Modifier = Modifier
) {
    // Paused bars are static: no infinite transition, so no per-frame redraws.
    if (isPlaying) AnimatedEqualizerBars(modifier) else StaticEqualizerBars(modifier)
}

@Composable
private fun AnimatedEqualizerBars(modifier: Modifier) {
    val transition = rememberInfiniteTransition(label = "equalizer")

    val h1 by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(420, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "eq1"
    )

    val h2 by transition.animateFloat(
        initialValue = 0.85f,
        targetValue = 0.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(540, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "eq2"
    )

    val h3 by transition.animateFloat(
        initialValue = 0.45f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(360, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "eq3"
    )

    Canvas(modifier = modifier) { drawEqualizerBars(h1, h2, h3) }
}

@Composable
private fun StaticEqualizerBars(modifier: Modifier) {
    Canvas(modifier = modifier) { drawEqualizerBars(0.35f, 0.65f, 0.45f) }
}

private fun DrawScope.drawEqualizerBars(f1: Float, f2: Float, f3: Float) {
    val w = size.width
    val h = size.height
    val barW = w * 0.20f
    val gap = w * 0.16f
    val color = Color.White.copy(alpha = 0.90f)
    val corner = CornerRadius(barW / 2f, barW / 2f)

    for ((i, f) in floatArrayOf(f1, f2, f3).withIndex()) {
        val barH = h * f
        drawRoundRect(
            color = color,
            topLeft = Offset((barW + gap) * i, h - barH),
            size = Size(barW, barH),
            cornerRadius = corner
        )
    }
}
