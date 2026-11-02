package com.goncalocosta.lumen.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import kotlinx.coroutines.launch
import kotlin.math.min

private val GridTileSpring = spring<Float>(dampingRatio = 1f, stiffness = 900f)
private const val GRID_COLUMNS = 5

/**
 * Frosted glass sheet that slides up over the home screen with a 5-column grid of all apps.
 * Composed only while visible or animating; the slide is a pure graphicsLayer translation.
 */
@Composable
fun AllAppsPanel(
    progress: Animatable<Float, AnimationVector1D>,
    visible: Boolean,
    apps: List<HomeApp>,
    pinned: Set<String>,
    gridState: LazyGridState,
    focus: FocusRegistry,
    canFocus: Boolean,
    onLaunch: (HomeApp) -> Unit,
    onOpenMenu: (HomeApp, Int) -> Unit,
    onAppFocused: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var everOpened by remember { mutableStateOf(false) }
    if (visible || progress.value > 0.001f) {
        everOpened = true
    }
    if (!everOpened) return

    Column(
        modifier = modifier
            .fillMaxWidth()
            .fillMaxHeight(0.88f)
            .graphicsLayer {
                val p = progress.value
                translationY = (1f - p) * size.height
            }
            .clip(PanelShape)
            .drawWithCache {
                val body = Brush.verticalGradient(
                    colors = listOf(
                        GlassFill.copy(alpha = 0.84f),
                        GlassFillDeep.copy(alpha = 0.93f)
                    )
                )
                val sheen = Brush.verticalGradient(
                    colors = listOf(Color.White.copy(alpha = 0.09f), Color.Transparent),
                    startY = 0f,
                    endY = 140.dp.toPx()
                )
                val topLine = Brush.horizontalGradient(
                    colors = listOf(Color.Transparent, Color.White.copy(alpha = 0.45f), Color.Transparent)
                )
                onDrawBehind {
                    drawRect(body)
                    drawRect(sheen)
                    drawRect(
                        brush = topLine,
                        topLeft = Offset(size.width * 0.06f, 0f),
                        size = Size(size.width * 0.88f, 1.dp.toPx())
                    )
                }
            }
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    0f to Color.White.copy(alpha = 0.26f),
                    0.25f to Color.White.copy(alpha = 0.06f),
                    1f to Color.Transparent
                ),
                shape = PanelShape
            )
            .padding(horizontal = 36.dp)
    ) {
        Box(
            Modifier
                .align(Alignment.CenterHorizontally)
                .padding(top = 12.dp)
                .size(width = 44.dp, height = 4.dp)
                .clip(PillShape)
                .background(Color.White.copy(alpha = 0.30f))
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 12.dp, top = 14.dp, bottom = 6.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            Text("All apps", color = GlassWhite, fontSize = 30.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.width(14.dp))
            Text(
                "${apps.size}",
                color = GlassSubtleText,
                fontSize = 18.sp,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            Spacer(Modifier.weight(1f))
            Text(
                "Hold OK for options   ·   Back to close",
                color = GlassSubtleText,
                fontSize = 14.sp,
                modifier = Modifier.padding(bottom = 5.dp)
            )
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(GRID_COLUMNS),
            state = gridState,
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 16.dp, bottom = 48.dp),
            horizontalArrangement = Arrangement.spacedBy(22.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            itemsIndexed(
                apps,
                key = { _, app -> gridKey(app.packageName) },
                contentType = { _, app -> if (app.banner != null) 1 else 0 }
            ) { index, app ->
                val key = gridKey(app.packageName)
                GridTile(
                    app = app,
                    pinned = app.packageName in pinned,
                    focusKey = key,
                    focus = focus,
                    canFocus = canFocus,
                    onFocused = { onAppFocused(app.packageName) },
                    onClick = { onLaunch(app) },
                    onLongClick = { onOpenMenu(app, index) }
                )
            }
        }
    }
}

@Composable
private fun GridTile(
    app: HomeApp,
    pinned: Boolean,
    focusKey: String,
    focus: FocusRegistry,
    canFocus: Boolean,
    onFocused: () -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val progress = remember { Animatable(0f) }
    val focusedFlag = remember { BooleanArray(1) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .focusRequester(focus.of(focusKey))
            .focusProperties { this.canFocus = canFocus }
            .onFocusChanged {
                if (it.isFocused == focusedFlag[0]) return@onFocusChanged
                focusedFlag[0] = it.isFocused
                scope.launch { progress.animateTo(if (it.isFocused) 1f else 0f, GridTileSpring) }
                if (it.isFocused) {
                    focus.onFocused(focusKey)
                    onFocused()
                }
            }
            .tvClickable(onLongClick = onLongClick, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .graphicsLayer {
                    val s = 1f + 0.08f * progress.value
                    scaleX = s
                    scaleY = s
                }
                .drawBehind {
                    val p = progress.value
                    if (p > 0.01f) {
                        val corner = 14.dp.toPx()
                        val spread = 5.dp.toPx()
                        drawRoundRect(
                            color = Color.White.copy(alpha = 0.10f * p),
                            topLeft = Offset(-spread, -spread),
                            size = Size(size.width + spread * 2, size.height + spread * 2),
                            cornerRadius = CornerRadius(corner + spread, corner + spread)
                        )
                    }
                }
                .clip(GridTileShape)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.White.copy(alpha = 0.13f), Color.White.copy(alpha = 0.05f))
                    )
                )
                .border(
                    width = 1.dp,
                    color = Color.White.copy(alpha = 0.14f + 0.76f * progress.value),
                    shape = GridTileShape
                ),
            contentAlignment = Alignment.Center
        ) {
            val banner = app.banner
            if (banner != null) {
                Image(
                    bitmap = banner,
                    contentDescription = app.label,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Image(
                    bitmap = app.icon,
                    contentDescription = app.label,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(52.dp)
                )
            }
            if (pinned) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(7.dp)
                        .size(9.dp)
                        .clip(PillShape)
                        .background(Color.White)
                        .border(1.5.dp, Color.Black.copy(alpha = 0.35f), PillShape)
                )
            }
        }
        Spacer(Modifier.height(9.dp))
        Text(
            text = app.label,
            color = Color.White,
            fontSize = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .padding(horizontal = 4.dp)
                .graphicsLayer {
                    val p = progress.value
                    alpha = 0.82f + 0.18f * p
                    translationY = p * 2.dp.toPx()
                }
        )
    }
}
