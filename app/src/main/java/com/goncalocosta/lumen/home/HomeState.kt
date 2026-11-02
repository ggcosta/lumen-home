package com.goncalocosta.lumen.home

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester

internal const val MORE_KEY = "more-apps"

internal fun dockKey(pkg: String) = "dock:$pkg"
internal fun gridKey(pkg: String) = "grid:$pkg"
internal val DockMoreKey = dockKey(MORE_KEY)

enum class HomeMode { Browse, Move }

/** Context-menu request: which app, and whether it was opened from the dock or the grid. */
@Stable
data class MenuRequest(val app: HomeApp, val fromDock: Boolean, val index: Int)

/**
 * UI state that changes rarely (mode / panel / menu). Per-D-pad-press state (`focusedDockIndex`,
 * `dockHasFocus`) lives here too but is only *read* by tiny leaf composables or the draw phase,
 * so moving focus never recomposes the screen.
 */
@Stable
class HomeUiState {
    var mode by mutableStateOf(HomeMode.Browse)
    var panelOpen by mutableStateOf(false)
    var wallpaperOpen by mutableStateOf(false)
    var controlCenterOpen by mutableStateOf(false)
    var colorKeyDialogOpen by mutableStateOf(false)
    var ambientActive by mutableStateOf(false)
    var menu by mutableStateOf<MenuRequest?>(null)
    var moveIndex by mutableIntStateOf(0)
    var focusedDockIndex by mutableIntStateOf(0)
    var dockHasFocus by mutableStateOf(false)
    /** Focus key to move to as soon as its node is composed and focusable. */
    var pendingFocus by mutableStateOf<String?>(null)
    /** Monotonic counter so requesting the same key twice still re-triggers. */
    var pendingFocusToken by mutableIntStateOf(0)

    val interactive: Boolean get() = menu == null

    fun requestFocus(key: String) {
        pendingFocus = key
        pendingFocusToken++
    }
}

/** Lazily created FocusRequesters keyed by string, plus tracking of the currently focused key. */
@Stable
class FocusRegistry {
    private val requesters = HashMap<String, FocusRequester>()
    var currentKey: String? = null
        private set

    fun of(key: String): FocusRequester = requesters.getOrPut(key) { FocusRequester() }

    fun onFocused(key: String) {
        currentKey = key
    }

    fun tryFocus(key: String) {
        runCatching { requesters[key]?.requestFocus() }
    }
}
