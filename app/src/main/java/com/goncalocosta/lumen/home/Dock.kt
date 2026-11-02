package com.goncalocosta.lumen.home

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import kotlinx.coroutines.launch

private val HighlightSpring = spring<Float>(dampingRatio = 0.84f, stiffness = 520f)
private val TileSpring = spring<Float>(dampingRatio = 1f, stiffness = 900f)

/** Title of the focused dock app, or the move-mode hint. Only this leaf recomposes on focus moves. */
@Composable
fun DockHeader(state: HomeUiState, apps: List<HomeApp>, modifier: Modifier = Modifier) {
    Box(modifier.height(34.dp), contentAlignment = Alignment.Center) {
        if (state.mode == HomeMode.Move) {
            Box(
                Modifier
                    .clip(PillShape)
                    .background(GlassFill.copy(alpha = 0.80f))
                    .border(1.dp, Color.White.copy(alpha = 0.25f), PillShape)
                    .padding(horizontal = 18.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "◀ ▶  Reorder      OK  Done",
                    color = GlassWhite,
                    fontSize = 13.sp
                )
            }
        } else {
            val idx = state.focusedDockIndex
            val title = apps.getOrNull(idx)?.label ?: if (idx == apps.size) "All apps" else ""
            val alpha by animateFloatAsState(
                targetValue = if (state.dockHasFocus) 1f else 0f,
                animationSpec = tween(180),
                label = "dock-title-alpha"
            )
            Crossfade(
                targetState = title,
                animationSpec = tween(140),
                modifier = Modifier.graphicsLayer { this.alpha = alpha },
                label = "dock-title"
            ) { text ->
                Text(
                    text = text,
                    color = GlassWhite,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
fun Dock(
    apps: List<HomeApp>,
    state: HomeUiState,
    focus: FocusRegistry,
    listState: LazyListState,
    canFocus: Boolean,
    onLaunch: (HomeApp) -> Unit,
    onOpenMenu: (HomeApp, Int) -> Unit,
    onOpenAllApps: () -> Unit,
    onMove: (from: Int, to: Int) -> Unit,
    onMoveDone: () -> Unit,
    onAppFocused: (String) -> Unit,
    onNavigateUp: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val highlight = remember { Animatable(0f) }
    val highlightAlpha = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    fun onSlotFocused(index: Int) {
        state.focusedDockIndex = index
        scope.launch {
            // Entering the dock from elsewhere: appear in place instead of gliding across.
            if (highlightAlpha.value < 0.3f) highlight.snapTo(index.toFloat())
            else highlight.animateTo(index.toFloat(), HighlightSpring)
        }
    }

    LiquidGlassDockContainer(modifier) {
        LazyRow(
            state = listState,
            contentPadding = PaddingValues(horizontal = DockRowPadding),
            horizontalArrangement = Arrangement.spacedBy(DockSlotSpacing),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .onFocusChanged {
                    if (state.dockHasFocus != it.hasFocus) state.dockHasFocus = it.hasFocus
                    scope.launch { highlightAlpha.animateTo(if (it.hasFocus) 1f else 0f, tween(160)) }
                }
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    if (state.mode == HomeMode.Move) {
                        val from = state.moveIndex
                        val to = when (event.key) {
                            Key.DirectionLeft -> from - 1
                            Key.DirectionRight -> from + 1
                            Key.DirectionUp, Key.DirectionDown -> return@onPreviewKeyEvent true
                            else -> {
                                if (isSelectKey(event.key)) {
                                    onMoveDone()
                                    return@onPreviewKeyEvent true
                                }
                                return@onPreviewKeyEvent false
                            }
                        }
                        if (to in apps.indices) {
                            onMove(from, to)
                            state.focusedDockIndex = to
                            scope.launch { highlight.animateTo(to.toFloat(), HighlightSpring) }
                        }
                        true
                    } else if (event.key == Key.DirectionDown) {
                        true
                    } else if (event.key == Key.DirectionUp) {
                        onNavigateUp()
                        true
                    } else if (event.key == Key.DirectionLeft && (state.focusedDockIndex <= 0 || (apps.isNotEmpty() && focus.currentKey == dockKey(apps.first().packageName)) || (apps.isEmpty() && focus.currentKey == DockMoreKey))) {
                        true
                    } else if (event.key == Key.DirectionRight && (state.focusedDockIndex >= apps.size || focus.currentKey == DockMoreKey)) {
                        true
                    } else {
                        false
                    }
                }
                .drawBehind {
                    // Sliding frosted focus card. Reads animation state only here → no recomposition.
                    val a = highlightAlpha.value
                    val info = listState.layoutInfo
                    val first = info.visibleItemsInfo.firstOrNull()
                    if (a < 0.01f || first == null) return@drawBehind
                    val pitch = (DockSlotWidth + DockSlotSpacing).toPx()
                    val x = DockRowPadding.toPx() + first.offset + (highlight.value - first.index) * pitch
                    drawGlassHighlight(
                        topLeft = Offset(x, (size.height - DockSlotHeight.toPx()) / 2f),
                        size = Size(DockSlotWidth.toPx(), DockSlotHeight.toPx()),
                        alpha = a
                    )
                }
        ) {
            itemsIndexed(
                apps,
                key = { _, app -> app.packageName },
                contentType = { _, _ -> "app" }
            ) { index, app ->
                val moving = state.mode == HomeMode.Move && index == state.moveIndex
                DockTile(
                    app = app,
                    label = app.label,
                    moving = moving,
                    focusKey = dockKey(app.packageName),
                    focus = focus,
                    canFocus = canFocus,
                    isFirst = index == 0,
                    isLast = false,
                    onFocused = {
                        onSlotFocused(index)
                        onAppFocused(app.packageName)
                    },
                    onClick = {
                        if (state.mode == HomeMode.Browse) onLaunch(app) else onMoveDone()
                    },
                    onLongClick = { if (state.mode == HomeMode.Browse) onOpenMenu(app, index) }
                )
            }
            item(key = MORE_KEY, contentType = "more") {
                DockTile(
                    app = null,
                    label = "All apps",
                    moving = false,
                    focusKey = DockMoreKey,
                    focus = focus,
                    canFocus = canFocus && state.mode == HomeMode.Browse,
                    isFirst = apps.isEmpty(),
                    isLast = true,
                    onFocused = { onSlotFocused(apps.size) },
                    onClick = { if (state.mode == HomeMode.Browse) onOpenAllApps() },
                    onLongClick = null
                )
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawGlassHighlight(
    topLeft: Offset,
    size: Size,
    alpha: Float
) {
    val corner = 14.dp.toPx()
    // Soft outer glow (two cheap translucent halos instead of a blur).
    for ((spread, a) in arrayOf(7.dp.toPx() to 0.05f, 3.5.dp.toPx() to 0.08f)) {
        drawRoundRect(
            color = Color.White.copy(alpha = a * alpha),
            topLeft = Offset(topLeft.x - spread, topLeft.y - spread),
            size = Size(size.width + spread * 2, size.height + spread * 2),
            cornerRadius = CornerRadius(corner + spread, corner + spread)
        )
    }
    // Frosted milky body.
    drawRoundRect(
        brush = Brush.verticalGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.34f * alpha),
                Color(0xFFE5DDD3).copy(alpha = 0.13f * alpha)
            ),
            startY = topLeft.y,
            endY = topLeft.y + size.height
        ),
        topLeft = topLeft,
        size = size,
        cornerRadius = CornerRadius(corner, corner)
    )
    // Specular rim, brightest along the top edge.
    drawRoundRect(
        brush = Brush.verticalGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.80f * alpha),
                Color.White.copy(alpha = 0.16f * alpha),
                Color.White.copy(alpha = 0.34f * alpha)
            ),
            startY = topLeft.y,
            endY = topLeft.y + size.height
        ),
        topLeft = topLeft,
        size = size,
        cornerRadius = CornerRadius(corner, corner),
        style = Stroke(width = 1.3.dp.toPx())
    )
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun DockTile(
    app: HomeApp?,
    label: String,
    moving: Boolean,
    focusKey: String,
    focus: FocusRegistry,
    canFocus: Boolean,
    isFirst: Boolean = false,
    isLast: Boolean = false,
    onFocused: () -> Unit,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?
) {
    val progress = remember { Animatable(0f) }
    val focusedFlag = remember { BooleanArray(1) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(moving) {
        if (moving) progress.animateTo(1f, TileSpring)
        else if (!focusedFlag[0]) progress.animateTo(0f, TileSpring)
    }

    Box(
        modifier = Modifier
            .size(DockSlotWidth, DockSlotHeight)
            .focusRequester(focus.of(focusKey))
            .focusProperties {
                this.canFocus = canFocus
                if (isFirst) {
                    left = FocusRequester.Cancel
                }
                if (isLast) {
                    right = FocusRequester.Cancel
                }
            }
            .onFocusChanged {
                if (it.isFocused == focusedFlag[0]) return@onFocusChanged
                focusedFlag[0] = it.isFocused
                scope.launch { progress.animateTo(if (it.isFocused) 1f else 0f, TileSpring) }
                if (it.isFocused) {
                    focus.onFocused(focusKey)
                    onFocused()
                }
            }
            .tvClickable(onLongClick = onLongClick, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(width = 54.dp, height = 30.dp)
                    .graphicsLayer {
                        val p = progress.value
                        val s = 1f + 0.12f * p
                        scaleX = s
                        scaleY = s
                        alpha = 0.72f + 0.28f * p
                        translationY = -2.dp.toPx() * p
                    },
                contentAlignment = Alignment.Center
            ) {
                val glyphType = app?.glyphType ?: GlassGlyphType.MoreApps
                if (glyphType != GlassGlyphType.DynamicSilhouette) {
                    Canvas(Modifier.fillMaxSize()) { drawMonochromeWhiteGlyph(glyphType) }
                } else if (app != null) {
                    Image(
                        bitmap = app.glyph,
                        contentDescription = app.label,
                        modifier = Modifier.size(30.dp),
                        contentScale = ContentScale.Fit
                    )
                }
            }
            Spacer(Modifier.height(5.dp))
            Text(
                text = label,
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .padding(horizontal = 6.dp)
                    .graphicsLayer {
                        val p = progress.value
                        alpha = 0.72f + 0.28f * p
                        translationY = -p * 1.5.dp.toPx()
                    }
            )
        }
        if (moving) {
            Text("‹", color = GlassWhite, fontSize = 26.sp, modifier = Modifier.align(Alignment.CenterStart).padding(start = 8.dp))
            Text("›", color = GlassWhite, fontSize = 26.sp, modifier = Modifier.align(Alignment.CenterEnd).padding(end = 8.dp))
        }
    }
}

/**
 * The single continuous horizontal Frosted Liquid Glass Dock container.
 * Uses `drawWithCache` so gradients are cached and zero allocations occur during D-pad navigation.
 */
@Composable
fun LiquidGlassDockContainer(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .clip(DockBarShape)
            .drawWithCache {
                val w = size.width
                val h = size.height
                val horizonBleed = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFFA67850).copy(alpha = 0.32f),
                        Color(0xFF705038).copy(alpha = 0.18f),
                        Color.Transparent
                    ),
                    center = Offset(w * 0.36f, h * 0.65f),
                    radius = w * 0.55f
                )
                val sheen = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF8CA6B5).copy(alpha = 0.18f),
                        Color.White.copy(alpha = 0.03f),
                        Color(0xFF120E0A).copy(alpha = 0.38f)
                    ),
                    startY = 0f,
                    endY = h
                )
                val topLine = Brush.horizontalGradient(
                    colors = listOf(
                        Color.Transparent,
                        Color.White.copy(alpha = 0.35f),
                        Color.Transparent
                    )
                )
                onDrawBehind {
                    drawRect(color = GlassFill.copy(alpha = 0.62f))
                    drawRect(brush = horizonBleed)
                    drawRect(brush = sheen)
                    drawRect(brush = topLine, topLeft = Offset(w * 0.08f, 0f), size = Size(w * 0.84f, 1.dp.toPx()))
                }
            }
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.24f),
                        Color.White.copy(alpha = 0.06f),
                        Color.White.copy(alpha = 0.12f)
                    )
                ),
                shape = DockBarShape
            )
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        content()
    }
}
