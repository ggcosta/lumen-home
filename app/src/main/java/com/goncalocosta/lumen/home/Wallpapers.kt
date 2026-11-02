package com.goncalocosta.lumen.home

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale

/**
 * High-definition bundled wallpapers located in `assets/wallpapers/`.
 */
enum class BundledWallpaper(
    val id: String,
    val label: String,
    val assetPath: String,
    val thumbPath: String
) {
    Mist("mist", "Mist", "wallpapers/mist.jpg", "wallpapers/thumbs/mist.jpg"),
    Aurora("aurora", "Aurora", "wallpapers/aurora.jpg", "wallpapers/thumbs/aurora.jpg"),
    Peaks("peaks", "Peaks", "wallpapers/peaks.jpg", "wallpapers/thumbs/peaks.jpg"),
    Highlands("highlands", "Highlands", "wallpapers/highlands.jpg", "wallpapers/thumbs/highlands.jpg"),
    Valley("valley", "Valley", "wallpapers/valley.jpg", "wallpapers/thumbs/valley.jpg");

    companion object {
        fun fromId(id: String?): BundledWallpaper =
            entries.firstOrNull { it.id.equals(id, ignoreCase = true) || it.name.equals(id, ignoreCase = true) } ?: Mist
    }
}

@Immutable
data class WallpaperState(
    val current: BundledWallpaper = BundledWallpaper.Mist,
    val daily: Boolean = false,
    val image: ImageBitmap? = null,
    val dailyCaption: String? = null,
    val thumbnails: Map<String, ImageBitmap> = emptyMap()
)

private val WallpaperScrim = Brush.verticalGradient(
    listOf(
        Color.Black.copy(alpha = 0.40f),
        Color.Black.copy(alpha = 0.12f),
        Color.Black.copy(alpha = 0.58f)
    )
)

/** Active wallpaper, crossfading between bundled wallpapers and the daily image. */
@Composable
fun WallpaperBackground(state: WallpaperState, modifier: Modifier = Modifier) {
    Crossfade(
        targetState = state.image,
        animationSpec = tween(500),
        modifier = modifier,
        label = "wp-crossfade"
    ) { bmp ->
        Box(Modifier.fillMaxSize()) {
            if (bmp != null) {
                Image(
                    bitmap = bmp,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Spacer(Modifier.fillMaxSize().background(Color(0xFF0B1218)))
            }
            // Scrim keeps the clock and dock text readable on bright photos.
            Spacer(
                Modifier
                    .fillMaxSize()
                    .drawBehind { drawRect(WallpaperScrim) }
            )
        }
    }
}
