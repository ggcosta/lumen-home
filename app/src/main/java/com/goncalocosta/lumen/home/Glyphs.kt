package com.goncalocosta.lumen.home

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate

/**
 * Draws widescreen-proportioned monochrome frosted-white glyphs inside the 64x36dp rectangular canvas.
 */
internal fun DrawScope.drawMonochromeWhiteGlyph(
    glyphType: GlassGlyphType
) {
    val w = size.width
    val h = size.height
    val cx = w / 2f
    val cy = h / 2f
    val white = Color.White
    val softWhite = Color.White.copy(alpha = 0.72f)

    when (glyphType) {
        GlassGlyphType.Lumen -> {
            // Lumen "lamp" mark: a rounded L holding a dot, traced from Lumen TV's 120-unit icon grid
            val unit = h * 0.74f / 77f
            val ox = cx - 62f * unit
            val oy = cy - 59f * unit
            fun at(x: Float, y: Float) = Offset(ox + x * unit, oy + y * unit)

            val lPath = Path().apply {
                moveTo(at(36f, 28f).x, at(36f, 28f).y)
                lineTo(at(36f, 74f).x, at(36f, 74f).y)
                arcTo(
                    rect = Rect(at(36f, 58f), at(68f, 90f)),
                    startAngleDegrees = 180f,
                    sweepAngleDegrees = -90f,
                    forceMoveTo = false
                )
                lineTo(at(88f, 90f).x, at(88f, 90f).y)
            }
            drawPath(
                path = lPath,
                color = white,
                style = Stroke(width = 15f * unit, cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
            drawCircle(color = white, radius = 11f * unit, center = at(66f, 56f))
        }

        GlassGlyphType.Meo -> {
            // Wide MEO 3-bar emblem (| | \)
            val barW = h * 0.14f
            val barH = h * 0.70f
            val topY = (h - barH) / 2f

            drawRoundRect(
                color = white,
                topLeft = Offset(w * 0.27f, topY),
                size = Size(barW, barH),
                cornerRadius = CornerRadius(barW / 2f, barW / 2f)
            )
            drawRoundRect(
                color = white,
                topLeft = Offset(w * 0.44f, topY),
                size = Size(barW, barH),
                cornerRadius = CornerRadius(barW / 2f, barW / 2f)
            )
            drawLine(
                color = white,
                start = Offset(w * 0.61f, topY + barW / 2f),
                end = Offset(w * 0.71f, topY + barH - barW / 2f),
                strokeWidth = barW,
                cap = StrokeCap.Round
            )
        }

        GlassGlyphType.YouTube -> {
            // Widescreen YouTube play screen badge
            val rectW = w * 0.66f
            val rectH = h * 0.76f
            val left = (w - rectW) / 2f
            val top = (h - rectH) / 2f
            drawRoundRect(
                color = white,
                topLeft = Offset(left, top),
                size = Size(rectW, rectH),
                cornerRadius = CornerRadius(h * 0.20f, h * 0.20f)
            )
            val play = Path().apply {
                moveTo(w * 0.44f, h * 0.33f)
                lineTo(w * 0.59f, cy)
                lineTo(w * 0.44f, h * 0.67f)
                close()
            }
            drawPath(path = play, color = Color(0xFF262626))
        }

        GlassGlyphType.Netflix -> {
            val barW = h * 0.16f
            val topY = h * 0.12f
            val botY = h * 0.88f
            val leftX = w * 0.35f
            val rightX = w * 0.56f

            drawRect(color = softWhite, topLeft = Offset(leftX, topY), size = Size(barW, botY - topY))
            drawRect(color = softWhite, topLeft = Offset(rightX, topY), size = Size(barW, botY - topY))
            val diag = Path().apply {
                moveTo(leftX, topY)
                lineTo(leftX + barW, topY)
                lineTo(rightX + barW, botY)
                lineTo(rightX, botY)
                close()
            }
            drawPath(path = diag, color = white)
        }

        GlassGlyphType.Stremio -> {
            // Wide horizontal diamond/prism with play icon
            val diamond = Path().apply {
                moveTo(cx, h * 0.08f)
                lineTo(w * 0.76f, cy)
                lineTo(cx, h * 0.92f)
                lineTo(w * 0.24f, cy)
                close()
            }
            drawPath(
                path = diamond,
                color = white,
                style = Stroke(width = h * 0.085f, join = StrokeJoin.Round)
            )
            val play = Path().apply {
                moveTo(w * 0.45f, h * 0.34f)
                lineTo(w * 0.59f, cy)
                lineTo(w * 0.45f, h * 0.66f)
                close()
            }
            drawPath(path = play, color = white)
        }

        GlassGlyphType.PlayStore -> {
            val outer = Path().apply {
                moveTo(w * 0.32f, h * 0.12f)
                lineTo(w * 0.72f, cy)
                lineTo(w * 0.32f, h * 0.88f)
                close()
            }
            drawPath(
                path = outer,
                color = white,
                style = Stroke(width = h * 0.085f, join = StrokeJoin.Round)
            )
            drawLine(
                color = softWhite,
                start = Offset(w * 0.33f, h * 0.15f),
                end = Offset(w * 0.56f, h * 0.65f),
                strokeWidth = h * 0.065f
            )
            drawLine(
                color = softWhite,
                start = Offset(w * 0.33f, h * 0.85f),
                end = Offset(w * 0.56f, h * 0.35f),
                strokeWidth = h * 0.065f
            )
        }

        GlassGlyphType.Downloader -> {
            // Wide tray + download arrow
            drawLine(
                color = white,
                start = Offset(cx, h * 0.14f),
                end = Offset(cx, h * 0.64f),
                strokeWidth = h * 0.10f,
                cap = StrokeCap.Round
            )
            val head = Path().apply {
                moveTo(w * 0.37f, h * 0.45f)
                lineTo(cx, h * 0.66f)
                lineTo(w * 0.63f, h * 0.45f)
            }
            drawPath(
                path = head,
                color = white,
                style = Stroke(width = h * 0.10f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
            drawRoundRect(
                color = softWhite,
                topLeft = Offset(w * 0.25f, h * 0.78f),
                size = Size(w * 0.50f, h * 0.10f),
                cornerRadius = CornerRadius(h * 0.05f, h * 0.05f)
            )
        }

        GlassGlyphType.DevTools -> {
            // Widescreen terminal card `[ >_ ]`
            val frameW = w * 0.72f
            val frameH = h * 0.80f
            val left = (w - frameW) / 2f
            val top = (h - frameH) / 2f
            drawRoundRect(
                color = softWhite,
                topLeft = Offset(left, top),
                size = Size(frameW, frameH),
                cornerRadius = CornerRadius(h * 0.16f, h * 0.16f),
                style = Stroke(width = h * 0.075f)
            )
            val chevron = Path().apply {
                moveTo(w * 0.30f, h * 0.34f)
                lineTo(w * 0.44f, cy)
                lineTo(w * 0.30f, h * 0.66f)
            }
            drawPath(
                path = chevron,
                color = white,
                style = Stroke(width = h * 0.085f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
            drawLine(
                color = white,
                start = Offset(w * 0.50f, h * 0.64f),
                end = Offset(w * 0.68f, h * 0.64f),
                strokeWidth = h * 0.085f,
                cap = StrokeCap.Round
            )
        }

        GlassGlyphType.PrimeVideo -> {
            val smile = Path().apply {
                moveTo(w * 0.20f, h * 0.46f)
                quadraticTo(cx, h * 0.76f, w * 0.80f, h * 0.44f)
            }
            drawPath(
                path = smile,
                color = white,
                style = Stroke(width = h * 0.09f, cap = StrokeCap.Round)
            )
            val tip = Path().apply {
                moveTo(w * 0.69f, h * 0.38f)
                lineTo(w * 0.82f, h * 0.43f)
                lineTo(w * 0.75f, h * 0.60f)
            }
            drawPath(
                path = tip,
                color = white,
                style = Stroke(width = h * 0.085f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
        }

        GlassGlyphType.Spotify -> {
            for (i in 0..2) {
                val yOff = h * (0.28f + i * 0.21f)
                val span = w * (0.26f - i * 0.035f)
                val wave = Path().apply {
                    moveTo(cx - span, yOff)
                    quadraticTo(cx, yOff - h * 0.10f, cx + span, yOff)
                }
                drawPath(
                    path = wave,
                    color = white,
                    style = Stroke(width = h * 0.095f, cap = StrokeCap.Round)
                )
            }
        }

        GlassGlyphType.Vlc -> {
            val cone = Path().apply {
                moveTo(cx, h * 0.14f)
                lineTo(w * 0.68f, h * 0.82f)
                lineTo(w * 0.32f, h * 0.82f)
                close()
            }
            drawPath(
                path = cone,
                color = white,
                style = Stroke(width = h * 0.085f, join = StrokeJoin.Round)
            )
            drawLine(
                color = white,
                start = Offset(w * 0.25f, h * 0.84f),
                end = Offset(w * 0.75f, h * 0.84f),
                strokeWidth = h * 0.095f,
                cap = StrokeCap.Round
            )
        }

        GlassGlyphType.DisneyPlus, GlassGlyphType.Max, GlassGlyphType.RtpPlay -> {
            val rectW = w * 0.68f
            val rectH = h * 0.76f
            drawRoundRect(
                color = white,
                topLeft = Offset((w - rectW) / 2f, (h - rectH) / 2f),
                size = Size(rectW, rectH),
                cornerRadius = CornerRadius(h * 0.20f, h * 0.20f),
                style = Stroke(width = h * 0.08f)
            )
            val play = Path().apply {
                moveTo(w * 0.44f, h * 0.34f)
                lineTo(w * 0.59f, cy)
                lineTo(w * 0.44f, h * 0.66f)
                close()
            }
            drawPath(path = play, color = white)
        }

        GlassGlyphType.MoreApps -> {
            // Widescreen 3x2 rectangular app grid glyph for "All apps"
            val cols = 3
            val rows = 2
            val cellW = w * 0.17f
            val cellH = h * 0.28f
            val gapX = w * 0.065f
            val gapY = h * 0.14f
            val totalW = cols * cellW + (cols - 1) * gapX
            val totalH = rows * cellH + (rows - 1) * gapY
            val startX = (w - totalW) / 2f
            val startY = (h - totalH) / 2f
            for (r in 0 until rows) {
                for (c in 0 until cols) {
                    val x = startX + c * (cellW + gapX)
                    val y = startY + r * (cellH + gapY)
                    drawRoundRect(
                        color = if ((r + c) % 2 == 1) softWhite else white,
                        topLeft = Offset(x, y),
                        size = Size(cellW, cellH),
                        cornerRadius = CornerRadius(cellH * 0.32f, cellH * 0.32f)
                    )
                }
            }
        }

        GlassGlyphType.DynamicSilhouette -> Unit
    }
}

/** Settings gear: 8 rounded teeth around a thick ring with a hollow centre. */
internal fun DrawScope.drawGearGlyph(color: Color) {
    val s = minOf(size.width, size.height)
    val c = Offset(size.width / 2f, size.height / 2f)
    val toothW = s * 0.16f
    val toothH = s * 0.20f
    for (i in 0 until 8) {
        rotate(degrees = i * 45f, pivot = c) {
            drawRoundRect(
                color = color,
                topLeft = Offset(c.x - toothW / 2f, c.y - s * 0.48f),
                size = Size(toothW, toothH),
                cornerRadius = CornerRadius(toothW * 0.3f, toothW * 0.3f)
            )
        }
    }
    drawCircle(color = color, radius = s * 0.25f, center = c, style = Stroke(width = s * 0.14f))
}

/** Wallpaper / landscape picture glyph: frame with sun/moon and twin mountain peaks. */
internal fun DrawScope.drawWallpaperGlyph(color: Color) {
    val w = size.width
    val h = size.height
    val padX = w * 0.10f
    val padY = h * 0.14f
    val stroke = minOf(w, h) * 0.09f
    val corner = minOf(w, h) * 0.14f

    // Outer frame
    drawRoundRect(
        color = color,
        topLeft = Offset(padX, padY),
        size = Size(w - padX * 2, h - padY * 2),
        cornerRadius = CornerRadius(corner, corner),
        style = Stroke(width = stroke)
    )

    // Sun / moon in top right
    drawCircle(
        color = color,
        radius = minOf(w, h) * 0.10f,
        center = Offset(w * 0.68f, h * 0.38f)
    )

    // Mountain peaks
    val mountains = Path().apply {
        moveTo(padX + stroke, h - padY - stroke)
        lineTo(w * 0.38f, h * 0.52f)
        lineTo(w * 0.54f, h * 0.66f)
        lineTo(w * 0.68f, h * 0.46f)
        lineTo(w - padX - stroke, h - padY - stroke)
        close()
    }
    drawPath(path = mountains, color = color)
}

/** VPN security shield glyph with a centered keyhole. */
internal fun DrawScope.drawVpnGlyph(color: Color) {
    val s = minOf(size.width, size.height)
    val cx = size.width / 2f
    val w = s * 0.72f
    val h = s * 0.84f
    val left = (size.width - w) / 2f
    val top = (size.height - h) / 2f
    val right = left + w
    val bottom = top + h

    // Outer shield contour
    val shield = Path().apply {
        moveTo(cx, top)
        cubicTo(cx + w * 0.28f, top, right, top + h * 0.08f, right, top + h * 0.38f)
        cubicTo(right, top + h * 0.68f, cx, top + h * 0.90f, cx, bottom)
        cubicTo(cx, top + h * 0.90f, left, top + h * 0.68f, left, top + h * 0.38f)
        cubicTo(left, top + h * 0.08f, cx - w * 0.28f, top, cx, top)
        close()
    }
    drawPath(
        path = shield,
        color = color,
        style = Stroke(width = s * 0.09f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    )

    // Center keyhole
    val keyhole = Path().apply {
        addOval(Rect(cx - s * 0.08f, top + h * 0.36f, cx + s * 0.08f, top + h * 0.50f))
        moveTo(cx - s * 0.04f, top + h * 0.46f)
        lineTo(cx - s * 0.06f, top + h * 0.64f)
        lineTo(cx + s * 0.06f, top + h * 0.64f)
        lineTo(cx + s * 0.04f, top + h * 0.46f)
        close()
    }
    drawPath(path = keyhole, color = color)
}

/** Wi-Fi waves glyph. */
internal fun DrawScope.drawWifiGlyph(color: Color) {
    val s = minOf(size.width, size.height)
    val cx = size.width / 2f
    val base = size.height * 0.82f
    val stroke = s * 0.10f

    // Center bottom dot
    drawCircle(color = color, radius = s * 0.08f, center = Offset(cx, base))

    // Inner wave
    val r1 = s * 0.32f
    drawArc(
        color = color,
        startAngle = 220f,
        sweepAngle = 100f,
        useCenter = false,
        topLeft = Offset(cx - r1, base - r1),
        size = Size(r1 * 2, r1 * 2),
        style = Stroke(width = stroke, cap = StrokeCap.Round)
    )

    // Outer wave
    val r2 = s * 0.58f
    drawArc(
        color = color,
        startAngle = 225f,
        sweepAngle = 90f,
        useCenter = false,
        topLeft = Offset(cx - r2, base - r2),
        size = Size(r2 * 2, r2 * 2),
        style = Stroke(width = stroke, cap = StrokeCap.Round)
    )
}

/** Ethernet / RJ45 network plug glyph. */
internal fun DrawScope.drawEthernetGlyph(color: Color) {
    val s = minOf(size.width, size.height)
    val w = s * 0.76f
    val h = s * 0.68f
    val left = (size.width - w) / 2f
    val top = (size.height - h) / 2f
    val stroke = s * 0.09f
    val corner = s * 0.12f

    // Connector housing
    drawRoundRect(
        color = color,
        topLeft = Offset(left, top),
        size = Size(w, h),
        cornerRadius = CornerRadius(corner, corner),
        style = Stroke(width = stroke)
    )

    // Inner bottom notch / clip
    val notchW = w * 0.36f
    val notchH = h * 0.28f
    drawRoundRect(
        color = color,
        topLeft = Offset(left + (w - notchW) / 2f, top + h - notchH),
        size = Size(notchW, notchH + stroke / 2f),
        cornerRadius = CornerRadius(corner * 0.5f, corner * 0.5f),
        style = Stroke(width = stroke)
    )

    // Top contact pins
    val pinW = w * 0.12f
    val pinY = top + h * 0.22f
    val pinLen = h * 0.22f
    drawLine(color, Offset(left + w * 0.28f, pinY), Offset(left + w * 0.28f, pinY + pinLen), stroke)
    drawLine(color, Offset(left + w * 0.50f, pinY), Offset(left + w * 0.50f, pinY + pinLen), stroke)
    drawLine(color, Offset(left + w * 0.72f, pinY), Offset(left + w * 0.72f, pinY + pinLen), stroke)
}

/** Bluetooth rune glyph. */
internal fun DrawScope.drawBluetoothGlyph(color: Color) {
    val s = minOf(size.width, size.height)
    val cx = size.width / 2f
    val cy = size.height / 2f
    val h = s * 0.82f
    val top = cy - h / 2f
    val bottom = cy + h / 2f
    val w = s * 0.52f
    val stroke = s * 0.09f

    val path = Path().apply {
        // Vertical spine
        moveTo(cx, top)
        lineTo(cx, bottom)
        // Upper loop
        lineTo(cx + w * 0.6f, cy + h * 0.25f)
        lineTo(cx - w * 0.5f, cy - h * 0.25f)
        // Lower loop
        moveTo(cx - w * 0.5f, cy + h * 0.25f)
        lineTo(cx + w * 0.6f, cy - h * 0.25f)
        lineTo(cx, top)
    }
    drawPath(
        path = path,
        color = color,
        style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round)
    )
}

/** Sleep timer / crescent moon glyph. */
internal fun DrawScope.drawMoonGlyph(color: Color) {
    val s = minOf(size.width, size.height)
    val cx = size.width / 2f
    val cy = size.height / 2f
    val r = s * 0.40f

    val moon = Path().apply {
        moveTo(cx + r * 0.2f, cy - r)
        cubicTo(cx - r * 0.8f, cy - r, cx - r, cy + r, cx + r * 0.2f, cy + r)
        cubicTo(cx - r * 0.2f, cy + r * 0.6f, cx - r * 0.2f, cy - r * 0.6f, cx + r * 0.2f, cy - r)
        close()
    }
    drawPath(path = moon, color = color)
}

/** TV Ambient Screensaver glyph: Apple TV widescreen display with ambient magical sparkles (SF Symbol sparkles.tv style). */
internal fun DrawScope.drawScreensaverGlyph(color: Color) {
    val s = minOf(size.width, size.height)
    val cx = size.width / 2f
    val cy = size.height / 2f
    val w = s * 0.86f
    val h = s * 0.54f
    val stroke = s * 0.082f
    val left = (size.width - w) / 2f
    val top = cy - h / 2f - s * 0.045f
    val corner = s * 0.09f

    // Sleek modern widescreen TV display bezel
    drawRoundRect(
        color = color,
        topLeft = Offset(left, top),
        size = Size(w, h),
        cornerRadius = CornerRadius(corner, corner),
        style = Stroke(width = stroke)
    )

    // Modern TV angled corner feet (Apple TV / modern OLED profile)
    val legH = s * 0.09f
    drawLine(
        color = color,
        start = Offset(left + w * 0.20f, top + h),
        end = Offset(left + w * 0.13f, top + h + legH),
        strokeWidth = stroke,
        cap = StrokeCap.Round
    )
    drawLine(
        color = color,
        start = Offset(left + w * 0.80f, top + h),
        end = Offset(left + w * 0.87f, top + h + legH),
        strokeWidth = stroke,
        cap = StrokeCap.Round
    )

    // Primary ambient 4-point sparkle star in the screen
    val starCx = cx - s * 0.035f
    val starCy = top + h / 2f
    val r1 = s * 0.165f
    val innerR1 = s * 0.045f

    val star = Path().apply {
        moveTo(starCx, starCy - r1)
        cubicTo(starCx, starCy - innerR1, starCx + innerR1, starCy, starCx + r1, starCy)
        cubicTo(starCx + innerR1, starCy, starCx, starCy + innerR1, starCx, starCy + r1)
        cubicTo(starCx, starCy + innerR1, starCx - innerR1, starCy, starCx - r1, starCy)
        cubicTo(starCx - innerR1, starCy, starCx, starCy - innerR1, starCx, starCy - r1)
        close()
    }
    drawPath(path = star, color = color)

    // Secondary subtle ambient twinkle in upper-right screen area
    val star2Cx = starCx + s * 0.20f
    val star2Cy = starCy - s * 0.095f
    val r2 = s * 0.075f
    val innerR2 = s * 0.02f

    val star2 = Path().apply {
        moveTo(star2Cx, star2Cy - r2)
        cubicTo(star2Cx, star2Cy - innerR2, star2Cx + innerR2, star2Cy, star2Cx + r2, star2Cy)
        cubicTo(star2Cx + innerR2, star2Cy, star2Cx, star2Cy + innerR2, star2Cx, star2Cy + r2)
        cubicTo(star2Cx, star2Cy + innerR2, star2Cx - innerR2, star2Cy, star2Cx - r2, star2Cy)
        cubicTo(star2Cx - innerR2, star2Cy, star2Cx, star2Cy - innerR2, star2Cx, star2Cy - r2)
        close()
    }
    drawPath(path = star2, color = color)
}

/** Clock / timer glyph for timeout & countdown selectors. */
internal fun DrawScope.drawClockTimerGlyph(color: Color) {
    val s = minOf(size.width, size.height)
    val cx = size.width / 2f
    val cy = size.height / 2f
    val r = s * 0.40f
    val stroke = s * 0.09f

    // Clock outer dial
    drawCircle(
        color = color,
        radius = r,
        center = Offset(cx, cy),
        style = Stroke(width = stroke)
    )

    // Clock hour hand (pointing up)
    drawLine(
        color = color,
        start = Offset(cx, cy),
        end = Offset(cx, cy - r * 0.55f),
        strokeWidth = stroke,
        cap = StrokeCap.Round
    )

    // Clock minute hand (pointing right to 3 o'clock)
    drawLine(
        color = color,
        start = Offset(cx, cy),
        end = Offset(cx + r * 0.45f, cy),
        strokeWidth = stroke,
        cap = StrokeCap.Round
    )

    // Center pivot dot
    drawCircle(
        color = color,
        radius = stroke * 0.65f,
        center = Offset(cx, cy)
    )
}

/** Ambient screensaver / 4-point sparkle glyph. */
internal fun DrawScope.drawSparkleGlyph(color: Color) {
    val s = minOf(size.width, size.height)
    val cx = size.width / 2f
    val cy = size.height / 2f
    val r = s * 0.42f
    val innerR = s * 0.12f

    val star = Path().apply {
        moveTo(cx, cy - r)
        cubicTo(cx, cy - innerR, cx + innerR, cy, cx + r, cy)
        cubicTo(cx + innerR, cy, cx, cy + innerR, cx, cy + r)
        cubicTo(cx, cy + innerR, cx - innerR, cy, cx - r, cy)
        cubicTo(cx - innerR, cy, cx, cy - innerR, cx, cy - r)
        close()
    }
    drawPath(path = star, color = color)
}

/** Media Play triangle glyph. */
internal fun DrawScope.drawPlayGlyph(color: Color) {
    val s = minOf(size.width, size.height)
    val cx = size.width / 2f
    val cy = size.height / 2f
    val w = s * 0.65f
    val h = s * 0.72f

    val triangle = Path().apply {
        moveTo(cx - w * 0.38f, cy - h / 2f)
        lineTo(cx + w * 0.48f, cy)
        lineTo(cx - w * 0.38f, cy + h / 2f)
        close()
    }
    drawPath(path = triangle, color = color)
}

/** Media Pause dual bars glyph. */
internal fun DrawScope.drawPauseGlyph(color: Color) {
    val s = minOf(size.width, size.height)
    val cx = size.width / 2f
    val cy = size.height / 2f
    val barW = s * 0.18f
    val barH = s * 0.68f
    val gap = s * 0.16f
    val top = cy - barH / 2f

    drawRoundRect(
        color = color,
        topLeft = Offset(cx - gap / 2f - barW, top),
        size = Size(barW, barH),
        cornerRadius = CornerRadius(barW / 2f, barW / 2f)
    )
    drawRoundRect(
        color = color,
        topLeft = Offset(cx + gap / 2f, top),
        size = Size(barW, barH),
        cornerRadius = CornerRadius(barW / 2f, barW / 2f)
    )
}

/** Media Skip Next glyph. */
internal fun DrawScope.drawSkipNextGlyph(color: Color) {
    val s = minOf(size.width, size.height)
    val cx = size.width / 2f
    val cy = size.height / 2f
    val w = s * 0.65f
    val h = s * 0.65f

    val triangle = Path().apply {
        moveTo(cx - w * 0.45f, cy - h / 2f)
        lineTo(cx + w * 0.20f, cy)
        lineTo(cx - w * 0.45f, cy + h / 2f)
        close()
    }
    drawPath(path = triangle, color = color)

    val barW = s * 0.12f
    drawRoundRect(
        color = color,
        topLeft = Offset(cx + w * 0.25f, cy - h / 2f),
        size = Size(barW, h),
        cornerRadius = CornerRadius(barW / 2f, barW / 2f)
    )
}
