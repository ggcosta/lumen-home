package com.goncalocosta.lumen.home

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text

private val ColorKeyDialogShape = RoundedCornerShape(22.dp)
private val ColorKeyItemShape = RoundedCornerShape(12.dp)

@Composable
fun ColorKeyDialog(
    visible: Boolean,
    apps: List<HomeApp>,
    currentMappings: Map<ColorKey, String?>,
    onAssignKey: (ColorKey, String?) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedColorForPicking by remember { mutableStateOf<ColorKey?>(null) }
    val initialFocus = remember { FocusRequester() }

    LaunchedEffect(visible, selectedColorForPicking) {
        if (visible) {
            initialFocus.requestFocus()
        }
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(180)) + scaleIn(tween(180), initialScale = 0.94f),
        exit = fadeOut(tween(140)) + scaleOut(tween(140), targetScale = 0.94f)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.70f))
                .tvClickable(onClick = {
                    if (selectedColorForPicking != null) {
                        selectedColorForPicking = null
                    } else {
                        onDismiss()
                    }
                }),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .width(440.dp)
                    .clip(ColorKeyDialogShape)
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color(0xFF1E232E).copy(alpha = 0.96f),
                                Color(0xFF141720).copy(alpha = 0.98f)
                            )
                        )
                    )
                    .border(1.dp, Color.White.copy(alpha = 0.22f), ColorKeyDialogShape)
                    .padding(24.dp)
                    .tvClickable(onClick = {}) // swallow click
            ) {
                if (selectedColorForPicking == null) {
                    // List of 4 color buttons
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "Remote Color Keys",
                            color = GlassWhite,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Map physical Red, Green, Yellow, Blue remote buttons to launch your favorite apps.",
                            color = GlassSubtleText,
                            fontSize = 13.sp,
                            lineHeight = 18.sp
                        )

                        Spacer(Modifier.height(4.dp))

                        ColorKey.entries.forEachIndexed { index, colorKey ->
                            val assignedPkg = currentMappings[colorKey]
                            val assignedApp = apps.firstOrNull { it.packageName == assignedPkg }
                            val isFirst = index == 0

                            ColorKeyRow(
                                colorKey = colorKey,
                                assignedApp = assignedApp,
                                requester = if (isFirst) initialFocus else null,
                                onClick = { selectedColorForPicking = colorKey }
                            )
                        }

                        Spacer(Modifier.height(6.dp))

                        // Close button
                        Box(
                            modifier = Modifier
                                .align(Alignment.End)
                                .clip(ColorKeyItemShape)
                                .background(Color.White.copy(alpha = 0.08f))
                                .border(1.dp, Color.White.copy(alpha = 0.15f), ColorKeyItemShape)
                                .tvClickable(onClick = onDismiss)
                                .padding(horizontal = 18.dp, vertical = 8.dp)
                        ) {
                            Text("Done", color = GlassWhite, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                } else {
                    // App picker for the selected color
                    val activeColor = selectedColorForPicking!!
                    val listState = rememberLazyListState()

                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Canvas(Modifier.size(14.dp)) {
                                drawCircle(activeColor.color)
                            }
                            Text(
                                text = "Select App for ${activeColor.label} Key",
                                color = GlassWhite,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Spacer(Modifier.height(14.dp))

                        LazyColumn(
                            state = listState,
                            modifier = Modifier.height(320.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            item {
                                // Option to clear / unassign
                                AppPickerRow(
                                    label = "None (Disable)",
                                    icon = null,
                                    requester = initialFocus,
                                    onClick = {
                                        onAssignKey(activeColor, null)
                                        selectedColorForPicking = null
                                    }
                                )
                            }

                            items(apps, key = { it.packageName }) { app ->
                                AppPickerRow(
                                    label = app.label,
                                    icon = app.glyph,
                                    requester = null,
                                    onClick = {
                                        onAssignKey(activeColor, app.packageName)
                                        selectedColorForPicking = null
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ColorKeyRow(
    colorKey: ColorKey,
    assignedApp: HomeApp?,
    requester: FocusRequester?,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .then(if (requester != null) Modifier.focusRequester(requester) else Modifier)
            .onFocusChanged { isFocused = it.isFocused }
            .tvClickable(onClick = onClick)
            .clip(ColorKeyItemShape)
            .background(
                if (isFocused) Color.White.copy(alpha = 0.22f)
                else Color.White.copy(alpha = 0.06f)
            )
            .border(
                width = 1.dp,
                color = if (isFocused) Color.White.copy(alpha = 0.55f) else Color.White.copy(alpha = 0.12f),
                shape = ColorKeyItemShape
            )
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Canvas(Modifier.size(16.dp)) {
                drawCircle(colorKey.color)
            }
            Text(
                text = "${colorKey.label} Button",
                color = if (isFocused) GlassWhite else Color.White.copy(alpha = 0.90f),
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (assignedApp != null) {
                Image(
                    bitmap = assignedApp.glyph,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    contentScale = ContentScale.Fit
                )
            }
            Text(
                text = assignedApp?.label ?: "None",
                color = if (assignedApp != null) Color(0xFF6EE7B7) else GlassSubtleText,
                fontSize = 13.sp,
                fontWeight = FontWeight.Normal
            )
        }
    }
}

@Composable
private fun AppPickerRow(
    label: String,
    icon: androidx.compose.ui.graphics.ImageBitmap?,
    requester: FocusRequester?,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .then(if (requester != null) Modifier.focusRequester(requester) else Modifier)
            .onFocusChanged { isFocused = it.isFocused }
            .tvClickable(onClick = onClick)
            .clip(ColorKeyItemShape)
            .background(
                if (isFocused) Color.White.copy(alpha = 0.22f)
                else Color.White.copy(alpha = 0.05f)
            )
            .border(
                width = 1.dp,
                color = if (isFocused) Color.White.copy(alpha = 0.55f) else Color.White.copy(alpha = 0.10f),
                shape = ColorKeyItemShape
            )
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (icon != null) {
            Image(
                bitmap = icon,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                contentScale = ContentScale.Fit
            )
        } else {
            Canvas(Modifier.size(10.dp)) {
                drawCircle(Color.White.copy(alpha = 0.4f))
            }
        }

        Text(
            text = label,
            color = if (isFocused) GlassWhite else Color.White.copy(alpha = 0.88f),
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
