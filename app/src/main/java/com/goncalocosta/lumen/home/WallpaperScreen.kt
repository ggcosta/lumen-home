package com.goncalocosta.lumen.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text

internal const val WP_DAILY_KEY = "wp:daily"
internal fun wallpaperKey(w: BundledWallpaper) = "wp:${w.id}"

/** Focus key of the currently active wallpaper card (where focus lands when the screen opens). */
internal fun activeWallpaperKey(s: WallpaperState) =
    if (s.daily) WP_DAILY_KEY else wallpaperKey(s.current)

private val CardWidth = 136.dp
private val CardHeight = 84.dp

/**
 * Wallpaper picker displaying bundled wallpapers and the Daily mode.
 * Choosing applies live; Back closes.
 */
@Composable
fun WallpaperScreen(
    visible: Boolean,
    state: WallpaperState,
    focus: FocusRegistry,
    onSelectBundled: (BundledWallpaper) -> Unit,
    onSelectDaily: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(200)) + slideInVertically(tween(260, easing = EmphasizedDecelerate)) { it / 8 },
        exit = fadeOut(tween(150)) + slideOutVertically(tween(180, easing = EmphasizedAccelerate)) { it / 8 },
        modifier = modifier
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                modifier = Modifier
                    .padding(horizontal = 40.dp)
                    .clip(DialogShape)
                    .background(Color.Black.copy(alpha = 0.55f))
                    .border(1.dp, Color.White.copy(alpha = 0.18f), DialogShape)
                    .padding(horizontal = 28.dp, vertical = 22.dp)
            ) {
                Text("Wallpaper", color = GlassWhite, fontSize = 22.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(14.dp))
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    for (wallpaper in BundledWallpaper.entries) {
                        val thumb = state.thumbnails[wallpaper.id]
                        WallpaperCard(
                            key = wallpaperKey(wallpaper),
                            label = wallpaper.label,
                            selected = !state.daily && state.current == wallpaper,
                            focus = focus,
                            onClick = { onSelectBundled(wallpaper) }
                        ) {
                            if (thumb != null) {
                                Image(
                                    bitmap = thumb,
                                    contentDescription = wallpaper.label,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Spacer(
                                    Modifier
                                        .fillMaxSize()
                                        .background(Color(0xFF1E2832))
                                )
                            }
                        }
                    }
                    WallpaperCard(
                        key = WP_DAILY_KEY,
                        label = "Daily",
                        selected = state.daily,
                        focus = focus,
                        onClick = onSelectDaily
                    ) {
                        val img = if (state.daily) state.image else null
                        if (img != null) {
                            Image(img, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        } else {
                            Spacer(
                                Modifier
                                    .fillMaxSize()
                                    .drawBehind {
                                        drawRect(
                                            Brush.linearGradient(
                                                listOf(Color(0xFF203A43), Color(0xFF2C5364))
                                            )
                                        )
                                    }
                            )
                        }
                        Text(
                            "NEW EACH DAY",
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(6.dp)
                                .background(Color.Black.copy(alpha = 0.55f), PillShape)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    text = if (state.daily) {
                        state.dailyCaption ?: "Daily wallpaper: downloading today's picture…"
                    } else {
                        "Select Daily for a new Bing picture every day (needs internet)."
                    },
                    color = GlassSubtleText,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.width(CardWidth * 4)
                )
            }
        }
    }
}

@Composable
private fun WallpaperCard(
    key: String,
    label: String,
    selected: Boolean,
    focus: FocusRegistry,
    onClick: () -> Unit,
    content: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.06f else 1f, tween(140), label = "wp-scale")
    Column(
        modifier = Modifier.width(CardWidth),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(CardWidth, CardHeight)
                .focusRequester(focus.of(key))
                .onFocusChanged {
                    focused = it.isFocused
                    if (it.isFocused) focus.onFocused(key)
                }
                .tvClickable(onClick = onClick)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .clip(GridTileShape)
                .border(
                    width = if (focused) 3.dp else if (selected) 2.dp else 1.dp,
                    color = Color.White.copy(alpha = if (focused) 1f else if (selected) 0.65f else 0.20f),
                    shape = GridTileShape
                ),
            content = content
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = if (selected) "● $label" else label,
            color = if (focused || selected) GlassWhite else GlassSubtleText,
            fontSize = 13.sp,
            fontWeight = if (focused) FontWeight.Medium else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
