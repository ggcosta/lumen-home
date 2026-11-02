package com.goncalocosta.lumen.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import kotlinx.coroutines.launch

private val VpnDialogShape = RoundedCornerShape(22.dp)
private val VpnItemShape = RoundedCornerShape(12.dp)

@Composable
fun VpnSelectionDialog(
    visible: Boolean,
    vpnApps: List<HomeApp>,
    selectedPackage: String?,
    onSelect: (HomeApp) -> Unit,
    onDismiss: () -> Unit
) {
    val progress = remember { Animatable(0f) }

    LaunchedEffect(visible) {
        progress.animateTo(if (visible) 1f else 0f, tween(if (visible) 160 else 110))
    }
    val composed by remember(visible) { derivedStateOf { visible || progress.value > 0.001f } }
    if (!composed || vpnApps.isEmpty()) return

    val requesters = remember(vpnApps) { List(vpnApps.size) { FocusRequester() } }
    val focusedIndex = remember { mutableIntStateOf(0) }

    LaunchedEffect(visible) {
        if (visible) {
            val initial = vpnApps.indexOfFirst { it.packageName == selectedPackage }.coerceAtLeast(0)
            focusedIndex.intValue = initial
            withFrameNanos { }
            runCatching { requesters.getOrNull(initial)?.requestFocus() }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .drawBehind { drawRect(Color.Black.copy(alpha = 0.50f * progress.value)) },
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .width(360.dp)
                .graphicsLayer {
                    val p = progress.value
                    val s = 0.92f + 0.08f * p
                    scaleX = s
                    scaleY = s
                    alpha = p
                }
                .clip(VpnDialogShape)
                .drawWithCache {
                    val body = Brush.verticalGradient(
                        listOf(Color(0xFF222830).copy(alpha = 0.96f), Color(0xFF161A20).copy(alpha = 0.98f))
                    )
                    val sheen = Brush.verticalGradient(
                        listOf(Color.White.copy(alpha = 0.12f), Color.Transparent),
                        endY = size.height * 0.4f
                    )
                    onDrawBehind {
                        drawRect(body)
                        drawRect(sheen)
                    }
                }
                .border(
                    1.dp,
                    Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.35f), Color.White.copy(alpha = 0.08f))),
                    VpnDialogShape
                )
                .padding(20.dp)
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    val count = vpnApps.size
                    if (count == 0) return@onPreviewKeyEvent false
                    when (event.key) {
                        Key.DirectionDown -> {
                            val next = (focusedIndex.intValue + 1) % count
                            focusedIndex.intValue = next
                            requesters[next].requestFocus()
                            true
                        }
                        Key.DirectionUp -> {
                            val prev = (focusedIndex.intValue - 1 + count) % count
                            focusedIndex.intValue = prev
                            requesters[prev].requestFocus()
                            true
                        }
                        Key.Back, Key.Escape -> {
                            onDismiss()
                            true
                        }
                        else -> false
                    }
                }
        ) {
            Text(
                text = "Choose VPN App",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Select the app linked to the VPN button",
                color = Color.White.copy(alpha = 0.65f),
                fontSize = 13.sp
            )
            Spacer(Modifier.height(16.dp))

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                vpnApps.forEachIndexed { index, app ->
                    val isSelected = app.packageName == selectedPackage
                    VpnAppItem(
                        app = app,
                        isSelected = isSelected,
                        focusRequester = requesters[index],
                        onFocused = { focusedIndex.intValue = index },
                        onClick = { onSelect(app) }
                    )
                }
            }
        }
    }
}

@Composable
private fun VpnAppItem(
    app: HomeApp,
    isSelected: Boolean,
    focusRequester: FocusRequester,
    onFocused: () -> Unit,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val focusProgress by animateFloatAsState(
        targetValue = if (isFocused) 1f else 0f,
        animationSpec = tween(130),
        label = "vpn-item-focus"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .focusRequester(focusRequester)
            .onFocusChanged {
                isFocused = it.isFocused
                if (it.isFocused) onFocused()
            }
            .tvClickable(onClick = onClick)
            .clip(VpnItemShape)
            .background(
                Brush.horizontalGradient(
                    listOf(
                        Color.White.copy(alpha = 0.22f * focusProgress),
                        Color.White.copy(alpha = 0.08f * focusProgress)
                    )
                )
            )
            .border(
                width = 1.dp,
                color = if (isFocused) Color.White.copy(alpha = 0.55f) else Color.White.copy(alpha = 0.08f),
                shape = VpnItemShape
            )
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            bitmap = app.icon,
            contentDescription = app.label,
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(8.dp)),
            contentScale = ContentScale.Fit
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = app.label,
            color = if (isFocused) Color.White else Color.White.copy(alpha = 0.82f),
            fontSize = 15.sp,
            fontWeight = if (isFocused) FontWeight.Medium else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (isSelected) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF22C55E).copy(alpha = 0.25f))
                    .border(1.dp, Color(0xFF22C55E).copy(alpha = 0.50f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "Active",
                    color = Color(0xFF4ADE80),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}
