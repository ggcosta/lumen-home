package com.goncalocosta.lumen.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.random.Random

@Composable
fun AmbientScreensaver(
    visible: Boolean,
    wallpaper: WallpaperState,
    media: MediaTrackInfo,
    onWake: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var oledShiftX by remember { mutableIntStateOf(0) }
    var oledShiftY by remember { mutableIntStateOf(0) }

    LaunchedEffect(visible) {
        if (!visible) return@LaunchedEffect
        while (true) {
            now = System.currentTimeMillis()
            // Shift clock slightly every minute to prevent OLED burn-in
            oledShiftX = Random.nextInt(-16, 17)
            oledShiftY = Random.nextInt(-12, 13)
            delay(60_000L)
        }
    }

    val timeFormat = remember { android.text.format.DateFormat.getTimeFormat(context) }
    val dateFormat = remember {
        val pattern = android.text.format.DateFormat.getBestDateTimePattern(Locale.getDefault(), "EEEE, d MMMM")
        SimpleDateFormat(pattern, Locale.getDefault())
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(700)),
        exit = fadeOut(tween(400)),
        modifier = modifier.fillMaxSize()
    ) {
        // Slow Apple TV Ken Burns motion. Lives inside AnimatedVisibility so the infinite
        // transition only ticks frames while the screensaver is actually on screen.
        val transition = rememberInfiniteTransition(label = "ken-burns")
        val scale by transition.animateFloat(
            initialValue = 1.0f,
            targetValue = 1.07f,
            animationSpec = infiniteRepeatable(
                animation = tween(38_000, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "kb-scale"
        )
        val driftX by transition.animateFloat(
            initialValue = -12f,
            targetValue = 12f,
            animationSpec = infiniteRepeatable(
                animation = tween(45_000, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "kb-drift-x"
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .tvClickable(onClick = onWake)
        ) {
            // Wallpaper with subtle Ken Burns motion
            val bmp = wallpaper.image
            if (bmp != null) {
                Image(
                    bitmap = bmp,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            translationX = driftX
                        },
                    contentScale = ContentScale.Crop
                )
            } else {
                StarryTwilightBackground(Modifier.fillMaxSize())
            }

            // Ambient vignette / dark overlay for contrast
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Black.copy(alpha = 0.35f),
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.55f)
                            )
                        )
                    )
            )

            // Centered Apple-style minimalist clock
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset { IntOffset(oledShiftX, oledShiftY) },
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = timeFormat.format(Date(now)),
                    color = Color.White.copy(alpha = 0.94f),
                    fontSize = 92.sp,
                    fontWeight = FontWeight.ExtraLight,
                    letterSpacing = (-1.5).sp
                )
                Text(
                    text = dateFormat.format(Date(now)),
                    color = Color.White.copy(alpha = 0.72f),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Light,
                    letterSpacing = 0.5.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            // Lower-Left: Now Playing ambient capsule if music is active
            if (media.isActive) {
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 48.dp, bottom = 48.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color.Black.copy(alpha = 0.55f))
                        .border(1.dp, Color.White.copy(alpha = 0.20f), RoundedCornerShape(20.dp))
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (media.artwork != null) {
                        Image(
                            bitmap = media.artwork,
                            contentDescription = null,
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(Modifier.width(10.dp))
                    } else {
                        EqualizerBars(
                            isPlaying = media.isPlaying,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                    }
                    Column {
                        Text(
                            text = media.title ?: "Now Playing",
                            color = Color.White.copy(alpha = 0.95f),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (!media.artist.isNullOrBlank()) {
                            Text(
                                text = media.artist,
                                color = Color.White.copy(alpha = 0.65f),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Normal,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            // Lower-Right: Daily Wallpaper location caption
            val caption = wallpaper.dailyCaption
            if (wallpaper.daily && !caption.isNullOrBlank()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 48.dp, bottom = 48.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.Black.copy(alpha = 0.45f))
                        .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(16.dp))
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = caption,
                        color = Color.White.copy(alpha = 0.75f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Normal
                    )
                }
            }

            // Subtle hint at bottom center
            Text(
                text = "Press any key to wake",
                color = Color.White.copy(alpha = 0.28f),
                fontSize = 12.sp,
                fontWeight = FontWeight.Normal,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 20.dp)
            )
        }
    }
}
