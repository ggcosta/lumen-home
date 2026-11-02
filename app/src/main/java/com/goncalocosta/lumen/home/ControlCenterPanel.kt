package com.goncalocosta.lumen.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
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

private val MacControlCenterShape = RoundedCornerShape(26.dp)
private val MacCardShape = RoundedCornerShape(16.dp)

internal const val CC_WIFI_KEY = "cc:wifi"
internal const val CC_BLUETOOTH_KEY = "cc:bluetooth"
internal const val CC_MEDIA_KEY = "cc:media"
internal const val CC_SLEEP_KEY = "cc:sleep"
internal const val CC_SCREENSAVER_KEY = "cc:screensaver"
internal const val CC_WALLPAPERS_KEY = "cc:wallpapers"
internal const val CC_SETTINGS_KEY = "cc:settings"
internal const val CC_SCREENSAVER_TIMER_KEY = "cc:screensaver_timer"

@Composable
fun ControlCenterPanel(
    visible: Boolean,
    focus: FocusRegistry,
    canFocus: Boolean,
    networkStatus: NetworkStatus,
    bluetoothStatus: BluetoothStatus,
    mediaState: MediaTrackInfo,
    sleepMinutesRemaining: Int?,
    screensaverTimeoutMinutes: Int,
    onCycleSleepTimer: () -> Unit,
    onCycleScreensaverTimeout: () -> Unit,
    onStartScreensaver: () -> Unit,
    onOpenWallpapers: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenNetworkSettings: () -> Unit,
    onOpenBluetoothSettings: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(180)) + scaleIn(tween(220, easing = EmphasizedDecelerate), initialScale = 0.94f),
        exit = fadeOut(tween(140)) + scaleOut(tween(180, easing = EmphasizedAccelerate), targetScale = 0.94f)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Darkened scrim backdrop - clicking dismisses
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f))
                    .tvClickable(onClick = onDismiss)
            )

            // macOS / iPadOS Liquid Glass Floating Panel in Top-Right
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 16.dp, end = 28.dp)
                    .width(336.dp)
                    .clip(MacControlCenterShape)
                    .drawWithCache {
                        val w = size.width
                        val h = size.height
                        val horizonBleed = Brush.radialGradient(
                            colors = listOf(
                                Color(0xFF38BDF8).copy(alpha = 0.16f),
                                Color(0xFF1E293B).copy(alpha = 0.28f),
                                Color.Transparent
                            ),
                            center = Offset(w * 0.50f, h * 0.15f),
                            radius = w * 0.85f
                        )
                        val sheen = Brush.verticalGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.15f),
                                Color(0xFF161C24).copy(alpha = 0.75f),
                                Color(0xFF0C0F14).copy(alpha = 0.92f)
                            )
                        )
                        val topLine = Brush.horizontalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.White.copy(alpha = 0.50f),
                                Color.Transparent
                            )
                        )
                        onDrawBehind {
                            drawRect(color = GlassFillDeep.copy(alpha = 0.78f))
                            drawRect(brush = horizonBleed)
                            drawRect(brush = sheen)
                            drawLine(
                                brush = topLine,
                                start = Offset(w * 0.10f, 1.dp.toPx()),
                                end = Offset(w * 0.90f, 1.dp.toPx()),
                                strokeWidth = 1.2.dp.toPx()
                            )
                        }
                    }
                    .border(
                        width = 1.2.dp,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.38f),
                                Color.White.copy(alpha = 0.08f),
                                Color.White.copy(alpha = 0.20f)
                            )
                        ),
                        shape = MacControlCenterShape
                    )
                    .padding(14.dp)
                    .tvClickable(onClick = {}) // swallow clicks on panel
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // ---- ROW 1: Connectivity (Wi-Fi + Bluetooth) | Now Playing ----
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Left Column: Wi-Fi on top, Bluetooth on bottom (width 149.dp, height 126.dp total)
                        Column(
                            modifier = Modifier.width(149.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Wi-Fi Card
                            LiquidGlassTile(
                                focusKey = CC_WIFI_KEY,
                                focus = focus,
                                canFocus = canFocus,
                                height = 59.dp,
                                onClick = onOpenNetworkSettings,
                                onLeftNav = onDismiss
                            ) { isFocused ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(34.dp)
                                            .clip(CircleShape)
                                            .background(
                                                if (networkStatus.isConnected) {
                                                    Brush.verticalGradient(
                                                        listOf(Color(0xFF007AFF), Color(0xFF0056B3))
                                                    )
                                                } else {
                                                    Brush.verticalGradient(
                                                        listOf(Color.White.copy(alpha = 0.16f), Color.White.copy(alpha = 0.08f))
                                                    )
                                                }
                                            )
                                            .border(1.dp, Color.White.copy(alpha = 0.25f), CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Canvas(Modifier.size(18.dp)) {
                                            if (networkStatus.isEthernet) drawEthernetGlyph(Color.White)
                                            else drawWifiGlyph(Color.White)
                                        }
                                    }
                                    Column(verticalArrangement = Arrangement.Center) {
                                        Text(
                                            text = if (networkStatus.isEthernet) "Ethernet" else "Wi-Fi",
                                            color = if (isFocused) GlassWhite else Color.White.copy(alpha = 0.95f),
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = if (networkStatus.isConnected) "Connected" else "Off",
                                            color = if (isFocused) GlassSubtleText else Color.White.copy(alpha = 0.55f),
                                            fontSize = 11.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }

                            // Bluetooth Card
                            LiquidGlassTile(
                                focusKey = CC_BLUETOOTH_KEY,
                                focus = focus,
                                canFocus = canFocus,
                                height = 59.dp,
                                onClick = onOpenBluetoothSettings,
                                onLeftNav = onDismiss
                            ) { isFocused ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(34.dp)
                                            .clip(CircleShape)
                                            .background(
                                                if (bluetoothStatus.isEnabled) {
                                                    Brush.verticalGradient(
                                                        listOf(Color(0xFF007AFF), Color(0xFF0056B3))
                                                    )
                                                } else {
                                                    Brush.verticalGradient(
                                                        listOf(Color.White.copy(alpha = 0.16f), Color.White.copy(alpha = 0.08f))
                                                    )
                                                }
                                            )
                                            .border(1.dp, Color.White.copy(alpha = 0.25f), CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Canvas(Modifier.size(18.dp)) {
                                            drawBluetoothGlyph(Color.White)
                                        }
                                    }
                                    Column(verticalArrangement = Arrangement.Center) {
                                        Text(
                                            text = "Bluetooth",
                                            color = if (isFocused) GlassWhite else Color.White.copy(alpha = 0.95f),
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = if (bluetoothStatus.isEnabled) "On" else "Off",
                                            color = if (isFocused) GlassSubtleText else Color.White.copy(alpha = 0.55f),
                                            fontSize = 11.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }

                        // Right Column: Now Playing Card (width 149.dp, height 126.dp)
                        LiquidGlassTile(
                            focusKey = CC_MEDIA_KEY,
                            focus = focus,
                            canFocus = canFocus,
                            modifier = Modifier.width(149.dp),
                            height = 126.dp,
                            onClick = { MediaSessionTracker.openCurrentApp(context) },
                            onLeftNav = onDismiss
                        ) { isFocused ->
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(10.dp),
                                verticalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (mediaState.artwork != null) {
                                        Image(
                                            bitmap = mediaState.artwork,
                                            contentDescription = null,
                                            modifier = Modifier
                                                .size(38.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(8.dp)),
                                            contentScale = ContentScale.Crop
                                        )
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .size(38.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(
                                                    Brush.verticalGradient(
                                                        listOf(Color(0xFFE11D48), Color(0xFFBE123C))
                                                    )
                                                )
                                                .border(1.dp, Color.White.copy(alpha = 0.30f), RoundedCornerShape(8.dp)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Canvas(Modifier.size(18.dp)) {
                                                drawPlayGlyph(Color.White)
                                            }
                                        }
                                    }

                                    if (mediaState.isPlaying) {
                                        EqualizerBars(
                                            isPlaying = true,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }

                                Column {
                                    Text(
                                        text = mediaState.title ?: "Now Playing",
                                        color = if (isFocused) GlassWhite else Color.White.copy(alpha = 0.95f),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = mediaState.artist ?: "No track active",
                                        color = if (isFocused) GlassSubtleText else Color.White.copy(alpha = 0.55f),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Normal,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                // Media Controls: Prev, Play/Pause, Next
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceEvenly,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(24.dp)
                                            .tvClickable { MediaSessionTracker.skipPrevious(context) },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Canvas(
                                            Modifier
                                                .size(12.dp)
                                                .graphicsLayer { rotationZ = 180f }
                                        ) {
                                            drawSkipNextGlyph(Color.White.copy(alpha = 0.85f))
                                        }
                                    }

                                    Box(
                                        modifier = Modifier
                                            .size(26.dp)
                                            .tvClickable { MediaSessionTracker.togglePlayPause(context) },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Canvas(Modifier.size(14.dp)) {
                                            if (mediaState.isPlaying) drawPauseGlyph(Color.White)
                                            else drawPlayGlyph(Color.White)
                                        }
                                    }

                                    Box(
                                        modifier = Modifier
                                            .size(24.dp)
                                            .tvClickable { MediaSessionTracker.skipNext(context) },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Canvas(Modifier.size(12.dp)) {
                                            drawSkipNextGlyph(Color.White.copy(alpha = 0.85f))
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // ---- ROW 2: Sleep Timer (Focus) | Ambient Screensaver & Wallpapers ----
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Focus / Sleep Timer Card (matching macOS "Focus" pill)
                        LiquidGlassTile(
                            focusKey = CC_SLEEP_KEY,
                            focus = focus,
                            canFocus = canFocus,
                            modifier = Modifier.width(149.dp),
                            height = 56.dp,
                            onClick = onCycleSleepTimer,
                            onLeftNav = onDismiss
                        ) { isFocused ->
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (sleepMinutesRemaining != null) {
                                                Brush.verticalGradient(
                                                    listOf(Color(0xFF8B5CF6), Color(0xFF6D28D9))
                                                )
                                            } else {
                                                Brush.verticalGradient(
                                                    listOf(Color.White.copy(alpha = 0.16f), Color.White.copy(alpha = 0.08f))
                                                )
                                            }
                                        )
                                        .border(1.dp, Color.White.copy(alpha = 0.25f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Canvas(Modifier.size(16.dp)) {
                                        drawMoonGlyph(Color.White)
                                    }
                                }

                                Column(verticalArrangement = Arrangement.Center) {
                                    Text(
                                        text = "Sleep Timer",
                                        color = if (isFocused) GlassWhite else Color.White.copy(alpha = 0.95f),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = if (sleepMinutesRemaining != null) "${sleepMinutesRemaining}m" else "Off",
                                        color = if (isFocused) GlassSubtleText else Color.White.copy(alpha = 0.55f),
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }

                        // Right 2 icon buttons: Screensaver and Wallpapers
                        Row(
                            modifier = Modifier.width(149.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Screensaver Button - launches Apple TV Ambient Screensaver
                            LiquidGlassTile(
                                focusKey = CC_SCREENSAVER_KEY,
                                focus = focus,
                                canFocus = canFocus,
                                modifier = Modifier.weight(1f),
                                height = 56.dp,
                                onClick = onStartScreensaver,
                                onLeftNav = onDismiss
                            ) { isFocused ->
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Canvas(Modifier.size(26.dp)) {
                                        drawScreensaverGlyph(if (isFocused) Color.White else Color.White.copy(alpha = 0.90f))
                                    }
                                }
                            }

                            // Wallpapers Button
                            LiquidGlassTile(
                                focusKey = CC_WALLPAPERS_KEY,
                                focus = focus,
                                canFocus = canFocus,
                                modifier = Modifier.weight(1f),
                                height = 56.dp,
                                onClick = onOpenWallpapers,
                                onLeftNav = onDismiss
                            ) { isFocused ->
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Canvas(Modifier.size(22.dp)) {
                                        drawWallpaperGlyph(if (isFocused) Color.White else Color.White.copy(alpha = 0.88f))
                                    }
                                }
                            }
                        }
                    }

                    // ---- ROW 3: System Settings | Screensaver Auto-Timer ----
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // System Settings Card
                        LiquidGlassTile(
                            focusKey = CC_SETTINGS_KEY,
                            focus = focus,
                            canFocus = canFocus,
                            modifier = Modifier.weight(1f),
                            height = 50.dp,
                            onClick = onOpenSettings,
                            onLeftNav = onDismiss
                        ) { isFocused ->
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(30.dp)
                                        .clip(CircleShape)
                                        .background(Color.White.copy(alpha = 0.12f))
                                        .border(1.dp, Color.White.copy(alpha = 0.20f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Canvas(Modifier.size(16.dp)) {
                                        drawGearGlyph(if (isFocused) Color.White else GlassSubtleText)
                                    }
                                }

                                Column(verticalArrangement = Arrangement.Center) {
                                    Text(
                                        text = "Settings",
                                        color = if (isFocused) GlassWhite else Color.White.copy(alpha = 0.95f),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "Android TV",
                                        color = if (isFocused) GlassSubtleText else Color.White.copy(alpha = 0.55f),
                                        fontSize = 10.sp
                                    )
                                }
                            }
                        }

                        // Screensaver Auto-Timer Card
                        LiquidGlassTile(
                            focusKey = CC_SCREENSAVER_TIMER_KEY,
                            focus = focus,
                            canFocus = canFocus,
                            modifier = Modifier.weight(1f),
                            height = 50.dp,
                            onClick = onCycleScreensaverTimeout,
                            onLeftNav = onDismiss
                        ) { isFocused ->
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(30.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (screensaverTimeoutMinutes > 0) Color(0xFF38BDF8).copy(alpha = 0.25f)
                                            else Color.White.copy(alpha = 0.12f)
                                        )
                                        .border(1.dp, Color.White.copy(alpha = 0.20f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Canvas(Modifier.size(16.dp)) {
                                        drawClockTimerGlyph(if (screensaverTimeoutMinutes > 0) Color(0xFF38BDF8) else Color.White)
                                    }
                                }

                                Column(verticalArrangement = Arrangement.Center) {
                                    Text(
                                        text = "Screensaver",
                                        color = if (isFocused) GlassWhite else Color.White.copy(alpha = 0.95f),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = if (screensaverTimeoutMinutes > 0) "Auto: ${screensaverTimeoutMinutes}m" else "Auto: Off",
                                        color = if (isFocused) GlassSubtleText else Color.White.copy(alpha = 0.55f),
                                        fontSize = 10.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Reusable Liquid Glass card tile matching the specular highlights, refraction, and frosted fill of the rest of the UI.
 */
@Composable
private fun LiquidGlassTile(
    focusKey: String,
    focus: FocusRegistry,
    canFocus: Boolean,
    modifier: Modifier = Modifier,
    height: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit,
    onLeftNav: () -> Unit,
    content: @Composable (isFocused: Boolean) -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val progress by animateFloatAsState(if (isFocused) 1f else 0f, tween(120), label = "tile-focus")

    Box(
        modifier = modifier
            .height(height)
            .focusRequester(focus.of(focusKey))
            .focusProperties { this.canFocus = canFocus }
            .onFocusChanged {
                isFocused = it.isFocused
                if (it.isFocused) focus.onFocused(focusKey)
            }
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionLeft) {
                    onLeftNav()
                    true
                } else false
            }
            .tvClickable(onClick = onClick)
            .graphicsLayer {
                val s = 1f + 0.035f * progress
                scaleX = s
                scaleY = s
            }
            .clip(MacCardShape)
            .drawBehind {
                val p = progress
                if (p > 0.01f) {
                    // Ambient specular refraction halo around focused tile
                    drawRoundRect(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.18f * p),
                                Color(0xFF38BDF8).copy(alpha = 0.10f * p),
                                Color.Transparent
                            ),
                            center = Offset(size.width / 2f, size.height / 2f),
                            radius = size.width * 0.75f
                        ),
                        cornerRadius = CornerRadius(16.dp.toPx(), 16.dp.toPx())
                    )
                }
            }
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.12f + 0.20f * progress),
                        Color.White.copy(alpha = 0.04f + 0.10f * progress),
                        Color(0xFF0F141C).copy(alpha = 0.35f)
                    )
                )
            )
            .drawWithCache {
                val w = size.width
                val p = progress
                val topHighlight = Brush.horizontalGradient(
                    colors = listOf(
                        Color.Transparent,
                        Color.White.copy(alpha = 0.25f + 0.50f * p),
                        Color.Transparent
                    )
                )
                onDrawWithContent {
                    drawContent()
                    // Crisp specular apex reflection line across the top rim
                    drawLine(
                        brush = topHighlight,
                        start = Offset(w * 0.15f, 1.dp.toPx()),
                        end = Offset(w * 0.85f, 1.dp.toPx()),
                        strokeWidth = 1.dp.toPx()
                    )
                }
            }
            .border(
                width = (1f + 0.3f * progress).dp,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.30f + 0.50f * progress),
                        Color.White.copy(alpha = 0.08f + 0.20f * progress),
                        Color.White.copy(alpha = 0.16f + 0.32f * progress)
                    )
                ),
                shape = MacCardShape
            )
    ) {
        content(isFocused)
    }
}
