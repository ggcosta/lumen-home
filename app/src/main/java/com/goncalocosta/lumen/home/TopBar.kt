package com.goncalocosta.lumen.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal const val VPN_BUTTON_KEY = "vpn"
internal const val SETTINGS_KEY = "settings"
internal const val SETTINGS_DROPDOWN_WP = "settings:wp"
internal const val SETTINGS_DROPDOWN_SYS = "settings:sys"

private val DropdownShape = RoundedCornerShape(16.dp)
private val DropdownItemShape = RoundedCornerShape(10.dp)

private val VpnConnectedColor = Color(0xFF22C55E)
private val VpnDisconnectedColor = Color(0xFFEF4444)

@Composable
fun TopBar(
    focus: FocusRegistry,
    canFocus: Boolean,
    controlCenterOpen: Boolean,
    vpnApp: HomeApp?,
    vpnAppsCount: Int,
    isVpnConnected: Boolean,
    mediaState: MediaTrackInfo,
    onOpenVpn: () -> Unit,
    onLongClickVpn: () -> Unit,
    onToggleControlCenter: () -> Unit,
    onNavigateDown: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        ClockAndDate()

        Row(
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            NowPlayingPill(
                media = mediaState,
                focus = focus,
                canFocus = canFocus && !controlCenterOpen,
                onNavigateDown = onNavigateDown
            )

            if (vpnApp != null) {
                VpnButton(
                    vpnApp = vpnApp,
                    focus = focus,
                    canFocus = canFocus && !controlCenterOpen,
                    isVpnConnected = isVpnConnected,
                    onClick = onOpenVpn,
                    onLongClick = if (vpnAppsCount > 1) onLongClickVpn else null,
                    onNavigateDown = onNavigateDown
                )
            }

            SettingsButton(
                focus = focus,
                canFocus = canFocus && !controlCenterOpen,
                active = controlCenterOpen,
                onClick = onToggleControlCenter,
                onNavigateDown = onNavigateDown
            )
        }
    }
}

/** Only this leaf recomposes once per minute. */
@Composable
private fun ClockAndDate() {
    val context = LocalContext.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay((60_000L - now % 60_000L).coerceAtLeast(500L))
        }
    }
    val timeFormat = remember { android.text.format.DateFormat.getTimeFormat(context) }
    val dateFormat = remember {
        val pattern = android.text.format.DateFormat.getBestDateTimePattern(Locale.getDefault(), "EEEEdMMMM")
        SimpleDateFormat(pattern, Locale.getDefault())
    }
    val date = Date(now)
    Column {
        Text(
            text = timeFormat.format(date),
            color = GlassWhite,
            fontSize = 46.sp,
            fontWeight = FontWeight.Light,
            letterSpacing = (-0.5).sp
        )
        Text(
            text = dateFormat.format(date),
            color = GlassSubtleText,
            fontSize = 17.sp,
            fontWeight = FontWeight.Normal,
            modifier = Modifier.padding(start = 2.dp)
        )
    }
}

@Composable
private fun SettingsButton(
    focus: FocusRegistry,
    canFocus: Boolean,
    active: Boolean,
    onClick: () -> Unit,
    onNavigateDown: () -> Unit = {}
) {
    val progress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    Box(
        modifier = Modifier
            .padding(top = 8.dp)
            .size(48.dp)
            .focusRequester(focus.of(SETTINGS_KEY))
            .focusProperties { this.canFocus = canFocus }
            .onFocusChanged {
                if (it.isFocused) focus.onFocused(SETTINGS_KEY)
                scope.launch { progress.animateTo(if (it.isFocused || active) 1f else 0f, tween(160)) }
            }
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionDown && !active) {
                    onNavigateDown()
                    true
                } else false
            }
            .tvClickable(onClick = onClick)
            .graphicsLayer {
                val s = 1f + 0.10f * progress.value
                scaleX = s
                scaleY = s
            }
            .drawBehind {
                val p = progress.value
                val r = size.minDimension / 2f
                drawCircle(
                    brush = Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = 0.14f + 0.20f * p),
                            Color.White.copy(alpha = 0.05f + 0.12f * p)
                        )
                    ),
                    radius = r
                )
                drawCircle(
                    color = Color.White.copy(alpha = 0.20f + 0.45f * p),
                    radius = r - 0.5.dp.toPx(),
                    style = Stroke(1.dp.toPx())
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            Modifier
                .size(22.dp)
                .graphicsLayer { alpha = 0.80f + 0.20f * progress.value }
        ) { drawGearGlyph(Color.White) }
    }
}

@Composable
private fun VpnButton(
    vpnApp: HomeApp?,
    focus: FocusRegistry,
    canFocus: Boolean,
    isVpnConnected: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
    onNavigateDown: () -> Unit = {}
) {
    val progress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val indicatorColor by animateColorAsState(
        targetValue = if (isVpnConnected) Color(0xFF34D399) else Color(0xFFEF4444),
        animationSpec = tween(250),
        label = "vpn-indicator-color"
    )

    Box(
        modifier = Modifier
            .padding(top = 8.dp)
            .size(48.dp)
            .focusRequester(focus.of(VPN_BUTTON_KEY))
            .focusProperties { this.canFocus = canFocus }
            .onFocusChanged {
                if (it.isFocused) focus.onFocused(VPN_BUTTON_KEY)
                scope.launch { progress.animateTo(if (it.isFocused) 1f else 0f, tween(160)) }
            }
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionDown) {
                    onNavigateDown()
                    true
                } else false
            }
            .tvClickable(
                onLongClick = onLongClick,
                onClick = onClick
            )
            .graphicsLayer {
                val s = 1f + 0.10f * progress.value
                scaleX = s
                scaleY = s
            }
            .drawBehind {
                val p = progress.value
                val r = size.minDimension / 2f
                drawCircle(
                    brush = Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = 0.14f + 0.20f * p),
                            Color.White.copy(alpha = 0.05f + 0.12f * p)
                        )
                    ),
                    radius = r
                )
                // Specular rim: soft emerald tint when connected
                val rimColor = if (isVpnConnected) {
                    Color(0xFF34D399).copy(alpha = 0.30f + 0.38f * p)
                } else {
                    Color.White.copy(alpha = 0.20f + 0.45f * p)
                }
                drawCircle(
                    color = rimColor,
                    radius = r - 0.5.dp.toPx(),
                    style = Stroke(1.dp.toPx())
                )
            },
        contentAlignment = Alignment.Center
    ) {
        // Dynamic app glyph (e.g. NordVPN white silhouette) or fallback shield
        if (vpnApp != null) {
            Image(
                bitmap = vpnApp.glyph,
                contentDescription = vpnApp.label,
                modifier = Modifier
                    .size(26.dp)
                    .padding(bottom = 2.dp)
                    .graphicsLayer { alpha = 0.85f + 0.15f * progress.value },
                contentScale = ContentScale.Fit
            )
        } else {
            Canvas(
                Modifier
                    .size(22.dp)
                    .padding(bottom = 2.dp)
                    .graphicsLayer { alpha = 0.85f + 0.15f * progress.value }
            ) {
                drawVpnGlyph(Color.White)
            }
        }

        // Sleek centered bottom LED status capsule
        Canvas(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 5.dp)
                .size(width = 11.dp, height = 3.5.dp)
        ) {
            val corner = CornerRadius(1.75.dp.toPx(), 1.75.dp.toPx())
            // Subtle ambient halo
            drawRoundRect(
                color = indicatorColor.copy(alpha = 0.40f),
                topLeft = Offset(-1.5.dp.toPx(), -1.dp.toPx()),
                size = Size(size.width + 3.dp.toPx(), size.height + 2.dp.toPx()),
                cornerRadius = CornerRadius(2.75.dp.toPx(), 2.75.dp.toPx())
            )
            // Crisp illuminated core
            drawRoundRect(
                color = indicatorColor,
                topLeft = Offset.Zero,
                size = size,
                cornerRadius = corner
            )
        }
    }
}
