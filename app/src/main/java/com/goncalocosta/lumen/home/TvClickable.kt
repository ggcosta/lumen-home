package com.goncalocosta.lumen.home

import androidx.compose.foundation.focusable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type

internal fun isSelectKey(key: Key) =
    key == Key.Enter || key == Key.DirectionCenter || key == Key.NumPadEnter

private fun isMenuKey(key: Key) =
    key == Key.Menu || key == Key.Settings || key == Key.Bookmark

private const val LONG_PRESS_REPEATS = 10

/**
 * Lightweight D-pad click/long-click + focusable, replacing TV Material `Card`
 * (which carries its own surface, scale, glow and indication animations per tile).
 *
 * A click fires on KeyUp only if the matching KeyDown was received by *this* node.
 * So when a long press opens a menu and focus moves, the trailing KeyUp/repeat events
 * land on the menu item, which ignores them. No global "suppress" flags are needed.
 */
@Composable
fun Modifier.tvClickable(
    enabled: Boolean = true,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit
): Modifier {
    val press = remember { BooleanArray(2) } // [0] = down seen, [1] = long fired
    val click by rememberUpdatedState(onClick)
    val longClick by rememberUpdatedState(onLongClick)
    return this
        .onKeyEvent { event ->
            if (!enabled) return@onKeyEvent false
            val native = event.nativeKeyEvent
            when {
                isSelectKey(event.key) -> {
                    when (event.type) {
                        KeyEventType.KeyDown -> {
                            if (native.repeatCount == 0) {
                                press[0] = true
                                press[1] = false
                            } else if (press[0] && !press[1] && longClick != null &&
                                (native.isLongPress || native.repeatCount >= LONG_PRESS_REPEATS)
                            ) {
                                press[1] = true
                                longClick?.invoke()
                            }
                        }
                        KeyEventType.KeyUp -> {
                            if (press[0] && !press[1]) click()
                            press[0] = false
                            press[1] = false
                        }
                    }
                    true
                }
                isMenuKey(event.key) && longClick != null -> {
                    if (event.type == KeyEventType.KeyDown && native.repeatCount == 0) longClick?.invoke()
                    true
                }
                else -> false
            }
        }
        .focusable(enabled)
}
