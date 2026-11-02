package com.goncalocosta.lumen.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import kotlinx.coroutines.launch

enum class MenuAction(val label: String, val destructive: Boolean = false) {
    Move("Move"),
    AddToDock("Add to dock"),
    RemoveFromDock("Remove from dock"),
    AppInfo("App info"),
    Uninstall("Uninstall", destructive = true),
    Cancel("Cancel")
}

private fun actionsFor(request: MenuRequest, pinned: Boolean, dockSize: Int): List<MenuAction> = buildList {
    if (request.fromDock) {
        if (dockSize > 1) add(MenuAction.Move)
        add(MenuAction.RemoveFromDock)
    } else {
        add(if (pinned) MenuAction.RemoveFromDock else MenuAction.AddToDock)
    }
    add(MenuAction.AppInfo)
    if (request.app.canUninstall) add(MenuAction.Uninstall)
    add(MenuAction.Cancel)
}

/**
 * Centred glass card with the app's colour icon and a vertical, wrap-around action list.
 * Fades/scales in over a light dim. Rendered in-tree (no extra window → no window-creation latency).
 */
@Composable
fun AppActionsDialog(
    request: MenuRequest?,
    pinned: Boolean,
    dockSize: Int,
    onAction: (MenuRequest, MenuAction) -> Unit
) {
    val progress = remember { Animatable(0f) }
    val last = remember { arrayOfNulls<MenuRequest>(1) }
    if (request != null) last[0] = request
    val shown = request ?: last[0]

    LaunchedEffect(request != null) {
        progress.animateTo(if (request != null) 1f else 0f, tween(if (request != null) 160 else 110))
    }
    val composed by remember(request) { derivedStateOf { request != null || progress.value > 0.001f } }
    if (!composed || shown == null) return

    val actions = remember(shown, pinned, dockSize) { actionsFor(shown, pinned, dockSize) }
    val requesters = remember(actions) { List(actions.size) { FocusRequester() } }
    val focusedIndex = remember { IntArray(1) }

    LaunchedEffect(request) {
        if (request != null) {
            withFrameNanos { }
            runCatching { requesters.first().requestFocus() }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .drawBehind { drawRect(Color.Black.copy(alpha = 0.42f * progress.value)) },
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .width(380.dp)
                .graphicsLayer {
                    val p = progress.value
                    val s = 0.92f + 0.08f * p
                    scaleX = s
                    scaleY = s
                    alpha = p
                }
                .clip(DialogShape)
                .drawWithCache {
                    val body = Brush.verticalGradient(
                        listOf(Color(0xFF2A323A).copy(alpha = 0.94f), GlassFillDeep.copy(alpha = 0.96f))
                    )
                    val sheen = Brush.verticalGradient(
                        listOf(Color.White.copy(alpha = 0.10f), Color.Transparent),
                        endY = size.height * 0.4f
                    )
                    onDrawBehind {
                        drawRect(body)
                        drawRect(sheen)
                    }
                }
                .border(
                    1.dp,
                    Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.32f), Color.White.copy(alpha = 0.08f))),
                    DialogShape
                )
                .padding(18.dp)
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    val i = focusedIndex[0]
                    val target = when {
                        event.key == Key.DirectionUp && i == 0 -> actions.lastIndex
                        event.key == Key.DirectionDown && i == actions.lastIndex -> 0
                        event.key == Key.DirectionLeft || event.key == Key.DirectionRight -> return@onPreviewKeyEvent true
                        else -> return@onPreviewKeyEvent false
                    }
                    runCatching { requesters[target].requestFocus() }
                    true
                }
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 6.dp, bottom = 14.dp)) {
                Image(
                    bitmap = shown.app.icon,
                    contentDescription = null,
                    modifier = Modifier
                        .size(52.dp)
                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                )
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(
                        shown.app.label,
                        color = GlassWhite,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        if (pinned) "In your dock" else "Not in your dock",
                        color = GlassSubtleText,
                        fontSize = 13.sp
                    )
                }
            }
            actions.forEachIndexed { index, action ->
                ActionRow(
                    action = action,
                    requester = requesters[index],
                    enabled = request != null,
                    onFocused = { focusedIndex[0] = index },
                    onClick = { onAction(shown, action) }
                )
                if (index != actions.lastIndex) Spacer(Modifier.height(4.dp))
            }
        }
    }
}

@Composable
private fun ActionRow(
    action: MenuAction,
    requester: FocusRequester,
    enabled: Boolean,
    onFocused: () -> Unit,
    onClick: () -> Unit
) {
    val progress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val textColor = if (action.destructive) Color(0xFFFF9C8F) else GlassWhite
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(46.dp)
            .focusRequester(requester)
            .focusProperties { canFocus = enabled }
            .onFocusChanged {
                if (it.isFocused) onFocused()
                scope.launch { progress.animateTo(if (it.isFocused) 1f else 0f, tween(120)) }
            }
            .tvClickable(enabled = enabled, onClick = onClick)
            .drawBehind {
                val p = progress.value
                if (p > 0.01f) {
                    val r = CornerRadius(14.dp.toPx(), 14.dp.toPx())
                    drawRoundRect(Color.White.copy(alpha = 0.18f * p), cornerRadius = r)
                    drawRoundRect(
                        Color.White.copy(alpha = 0.50f * p),
                        cornerRadius = r,
                        style = Stroke(1.dp.toPx())
                    )
                }
            }
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(
            text = action.label,
            color = textColor,
            fontSize = 17.sp,
            modifier = Modifier.graphicsLayer { alpha = 0.78f + 0.22f * progress.value }
        )
    }
}
