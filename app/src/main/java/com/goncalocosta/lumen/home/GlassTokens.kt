package com.goncalocosta.lumen.home

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

val GlassWhite = Color(0xFFF8FAFC)
val GlassSubtleText = Color(0xCCDBE4EE)
val SkyTop = Color(0xFF0A1622)
val GlassFill = Color(0xFF1D2228)
val GlassFillDeep = Color(0xFF141A20)

val DockBarShape = RoundedCornerShape(22.dp)
val FocusedSlotShape = RoundedCornerShape(14.dp)
val PillShape = RoundedCornerShape(percent = 50)
val PanelShape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
val GridTileShape = RoundedCornerShape(14.dp)
val DialogShape = RoundedCornerShape(26.dp)

// Sized so 5 apps + "All apps" fit the TV canvas comfortably with a sleek, compact profile.
val DockSlotWidth = 116.dp
val DockSlotHeight = 78.dp
val DockSlotSpacing = 5.dp
val DockRowPadding = 3.dp
val ScreenHPadding = 64.dp
val ScreenVPadding = 40.dp

/** Material "emphasized decelerate": fast start, long gentle settle. */
val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
val EmphasizedAccelerate = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)
