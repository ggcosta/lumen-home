package com.goncalocosta.lumen.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch

private const val MENU_FOCUS_KEY = "menu"

@Composable
fun HomeScreen(
    vm: HomeViewModel,
    homePresses: Int,
    onLaunch: (HomeApp) -> Unit,
    onUninstall: (HomeApp) -> Unit,
    onAppInfo: (HomeApp) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenVpn: () -> Unit
) {
    val mainApps by vm.mainApps.collectAsStateWithLifecycle()
    val allApps by vm.allApps.collectAsStateWithLifecycle()
    val isLoaded by vm.isLoaded.collectAsStateWithLifecycle()
    val wallpaper by vm.wallpaper.collectAsStateWithLifecycle()
    val isVpnConnected by vm.isVpnConnected.collectAsStateWithLifecycle()
    val vpnApps by vm.vpnApps.collectAsStateWithLifecycle()
    val selectedVpnApp by vm.selectedVpnApp.collectAsStateWithLifecycle()
    val networkStatus by vm.networkStatus.collectAsStateWithLifecycle()
    val bluetoothStatus by vm.bluetoothStatus.collectAsStateWithLifecycle()
    val mediaState by vm.mediaState.collectAsStateWithLifecycle()
    val sleepMinutesRemaining by vm.sleepMinutesRemaining.collectAsStateWithLifecycle()
    val screensaverTimeout by vm.screensaverTimeout.collectAsStateWithLifecycle()
    val remoteKeyMappings by vm.remoteKeyMappings.collectAsStateWithLifecycle()
    val isAmbientActive by vm.isAmbientActive.collectAsStateWithLifecycle()
    val latestMain by rememberUpdatedState(mainApps)
    val latestAll by rememberUpdatedState(allApps)

    val context = androidx.compose.ui.platform.LocalContext.current
    var vpnPickerOpen by remember { mutableStateOf(false) }

    val state = remember { HomeUiState() }
    val focus = remember { FocusRegistry() }
    val listState = rememberLazyListState()
    val gridState = rememberLazyGridState()
    val panelProgress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val pinned = remember(mainApps) { mainApps.mapTo(HashSet()) { it.packageName } }

    fun firstDockKey() = latestMain.firstOrNull()?.let { dockKey(it.packageName) } ?: DockMoreKey

    fun keyExists(key: String): Boolean = when {
        key == DockMoreKey || key == SETTINGS_KEY || key == VPN_BUTTON_KEY || key == MENU_FOCUS_KEY ||
            key == NOW_PLAYING_KEY || key.startsWith("cc:") -> true
        key.startsWith("dock:") -> latestMain.any { dockKey(it.packageName) == key }
        key.startsWith("grid:") -> latestAll.any { gridKey(it.packageName) == key }
        else -> true
    }

    // ---- Panel animation (slides sheet up/down with hardware layer translation) ----
    LaunchedEffect(state.panelOpen) {
        if (state.panelOpen) {
            panelProgress.animateTo(1f, tween(200, easing = EmphasizedDecelerate))
        } else {
            panelProgress.animateTo(0f, tween(160, easing = EmphasizedAccelerate))
        }
    }

    // ---- Deterministic focus routing: scroll the lazy list if needed, then request focus ----
    LaunchedEffect(state.pendingFocusToken) {
        val key = state.pendingFocus ?: return@LaunchedEffect
        if (key.startsWith("dock:")) {
            val idx = if (key == DockMoreKey) latestMain.size
            else latestMain.indexOfFirst { dockKey(it.packageName) == key }
            if (idx >= 0 && listState.layoutInfo.visibleItemsInfo.none { it.index == idx }) {
                listState.scrollToItem(idx)
            }
        } else if (key.startsWith("grid:")) {
            val idx = latestAll.indexOfFirst { gridKey(it.packageName) == key }
            if (idx >= 0 && gridState.layoutInfo.visibleItemsInfo.none { it.index == idx }) {
                gridState.scrollToItem(idx)
            }
        }
        repeat(12) {
            withFrameNanos { }
            focus.tryFocus(key)
            if (focus.currentKey == key) {
                state.pendingFocus = null
                return@LaunchedEffect
            }
        }
        state.pendingFocus = null
    }

    // ---- Initial focus once the dock exists (restores the last focused app) ----
    LaunchedEffect(isLoaded) {
        if (!isLoaded) return@LaunchedEffect
        val last = vm.lastDockPackage?.let(::dockKey)?.takeIf(::keyExists)
        state.requestFocus(last ?: firstDockKey())
    }

    // ---- If the focused app disappeared (uninstalled / disabled), land somewhere sensible ----
    LaunchedEffect(mainApps, allApps) {
        val current = focus.currentKey ?: return@LaunchedEffect
        if (state.menu == null && !keyExists(current)) {
            val fallback = if (state.panelOpen) {
                latestAll.firstOrNull()?.let { gridKey(it.packageName) }
            } else {
                null
            }
            state.requestFocus(fallback ?: firstDockKey())
        }
    }

    fun openPanel() {
        if (state.mode != HomeMode.Browse || latestAll.isEmpty()) return
        state.controlCenterOpen = false
        state.panelOpen = true
        val pkg = vm.lastGridPackage?.takeIf { p -> latestAll.any { it.packageName == p } }
            ?: latestAll.first().packageName
        state.requestFocus(gridKey(pkg))
    }

    fun closePanel() {
        state.panelOpen = false
        state.requestFocus(DockMoreKey)
    }

    fun openControlCenter() {
        if (state.mode != HomeMode.Browse || state.panelOpen || state.wallpaperOpen || state.menu != null) return
        state.controlCenterOpen = true
        state.requestFocus(CC_WIFI_KEY)
    }

    fun closeControlCenter() {
        if (state.controlCenterOpen) {
            state.controlCenterOpen = false
            state.requestFocus(SETTINGS_KEY)
        }
    }

    fun openColorKeys() {
        closeControlCenter()
        state.colorKeyDialogOpen = true
    }

    fun closeColorKeys() {
        state.colorKeyDialogOpen = false
        openControlCenter()
    }

    fun openWallpapers() {
        closeControlCenter()
        if (state.mode != HomeMode.Browse || state.panelOpen || state.menu != null) return
        state.wallpaperOpen = true
        state.requestFocus(activeWallpaperKey(wallpaper))
    }

    fun closeWallpapers() {
        state.wallpaperOpen = false
        state.requestFocus(SETTINGS_KEY)
    }

    fun openMenu(app: HomeApp, index: Int, fromDock: Boolean) {
        focus.onFocused(MENU_FOCUS_KEY)
        state.menu = MenuRequest(app, fromDock, index)
    }

    fun finishMove() {
        state.mode = HomeMode.Browse
        latestMain.getOrNull(state.moveIndex)?.let { state.requestFocus(dockKey(it.packageName)) }
    }

    fun onMenuAction(req: MenuRequest, action: MenuAction) {
        state.menu = null
        val app = req.app
        val pkg = app.packageName
        val origin = if (req.fromDock) dockKey(pkg) else gridKey(pkg)
        when (action) {
            MenuAction.Move -> {
                state.moveIndex = req.index
                state.mode = HomeMode.Move
                state.requestFocus(origin)
            }
            MenuAction.RemoveFromDock -> {
                if (req.fromDock) {
                    val next = latestMain.getOrNull(req.index + 1) ?: latestMain.getOrNull(req.index - 1)
                    vm.unpin(pkg)
                    state.requestFocus(next?.let { dockKey(it.packageName) } ?: DockMoreKey)
                } else {
                    vm.unpin(pkg)
                    state.requestFocus(origin)
                }
            }
            MenuAction.AddToDock -> {
                vm.pin(pkg)
                state.requestFocus(origin)
            }
            MenuAction.AppInfo -> {
                state.requestFocus(origin)
                onAppInfo(app)
            }
            MenuAction.Uninstall -> {
                state.requestFocus(origin)
                onUninstall(app)
            }
            MenuAction.Cancel -> state.requestFocus(origin)
        }
    }

    fun onMove(from: Int, to: Int) {
        val anchorIndex = listState.firstVisibleItemIndex
        val anchorOffset = listState.firstVisibleItemScrollOffset
        vm.moveMain(from, to)
        state.moveIndex = to
        scope.launch {
            listState.scrollToItem(anchorIndex, anchorOffset)
            withFrameNanos { }
            val info = listState.layoutInfo
            val item = info.visibleItemsInfo.firstOrNull { it.index == to }
            if (item == null || item.offset < 0 || item.offset + item.size > info.viewportEndOffset) {
                listState.animateScrollToItem(to)
            }
        }
    }

    // ---- Home pressed while already on the launcher: reset to the dock ----
    LaunchedEffect(homePresses) {
        if (homePresses == 0) return@LaunchedEffect
        state.menu = null
        state.mode = HomeMode.Browse
        state.panelOpen = false
        state.wallpaperOpen = false
        state.controlCenterOpen = false
        state.colorKeyDialogOpen = false
        vm.dismissAmbient()
        state.requestFocus(firstDockKey())
        listState.animateScrollToItem(0)
    }

    BackHandler {
        val menu = state.menu
        when {
            isAmbientActive -> vm.dismissAmbient()
            state.colorKeyDialogOpen -> closeColorKeys()
            state.controlCenterOpen -> closeControlCenter()
            menu != null -> onMenuAction(menu, MenuAction.Cancel)
            vpnPickerOpen -> {
                vpnPickerOpen = false
                state.requestFocus(VPN_BUTTON_KEY)
            }
            state.wallpaperOpen -> closeWallpapers()
            state.mode == HomeMode.Move -> finishMove()
            state.panelOpen -> closePanel()
            else -> Unit // Home screen: swallow Back.
        }
    }

    val dockAlpha by animateFloatAsState(
        targetValue = if (isLoaded && !isAmbientActive) 1f else 0f,
        animationSpec = tween(240),
        label = "dock-alpha"
    )
    val homeInteractive = state.menu == null && !state.panelOpen && !state.wallpaperOpen &&
        !state.controlCenterOpen && !state.colorKeyDialogOpen && !vpnPickerOpen && !isAmbientActive

    Box(Modifier.fillMaxSize()) {
        WallpaperBackground(wallpaper)

        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = if (isAmbientActive) 0f else 1f }
        ) {
            TopBar(
                focus = focus,
                canFocus = homeInteractive && state.mode == HomeMode.Browse,
                controlCenterOpen = state.controlCenterOpen,
                vpnApp = selectedVpnApp,
                vpnAppsCount = vpnApps.size,
                isVpnConnected = isVpnConnected,
                mediaState = mediaState,
                onOpenVpn = onOpenVpn,
                onLongClickVpn = {
                    if (vpnApps.size > 1) vpnPickerOpen = true
                },
                onToggleControlCenter = {
                    if (state.controlCenterOpen) closeControlCenter() else openControlCenter()
                },
                onNavigateDown = {
                    val target = vm.lastDockPackage?.let(::dockKey)?.takeIf(::keyExists) ?: firstDockKey()
                    state.requestFocus(target)
                },
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(horizontal = ScreenHPadding, vertical = ScreenVPadding)
            )

            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(start = 32.dp, end = 32.dp, bottom = 36.dp)
                    .graphicsLayer { alpha = dockAlpha },
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                DockHeader(state = state, apps = mainApps)
                Spacer(Modifier.height(8.dp))
                if (isLoaded) {
                    Dock(
                        apps = mainApps,
                        state = state,
                        focus = focus,
                        listState = listState,
                        canFocus = homeInteractive,
                        onLaunch = onLaunch,
                        onOpenMenu = { app, index -> openMenu(app, index, fromDock = true) },
                        onOpenAllApps = ::openPanel,
                        onMove = ::onMove,
                        onMoveDone = ::finishMove,
                        onAppFocused = { vm.lastDockPackage = it },
                        onNavigateUp = {
                            val upTarget = when {
                                mediaState.isActive -> NOW_PLAYING_KEY
                                selectedVpnApp != null -> VPN_BUTTON_KEY
                                else -> SETTINGS_KEY
                            }
                            state.requestFocus(upTarget)
                        }
                    )
                }
            }
        }

        // Dim the home screen behind the sheet (single rect, alpha animated in draw phase).
        Spacer(
            Modifier
                .fillMaxSize()
                .drawBehind {
                    val p = panelProgress.value
                    if (p > 0.01f) drawRect(Color.Black.copy(alpha = 0.50f * p))
                }
        )

        AllAppsPanel(
            progress = panelProgress,
            visible = state.panelOpen,
            apps = allApps,
            pinned = pinned,
            gridState = gridState,
            focus = focus,
            canFocus = state.panelOpen && state.menu == null && !isAmbientActive,
            onLaunch = onLaunch,
            onOpenMenu = { app, index -> openMenu(app, index, fromDock = false) },
            onAppFocused = { vm.lastGridPackage = it },
            modifier = Modifier.align(Alignment.BottomCenter)
        )

        WallpaperScreen(
            visible = state.wallpaperOpen,
            state = wallpaper,
            focus = focus,
            onSelectBundled = vm::selectBundled,
            onSelectDaily = { vm.selectDaily(force = true) }
        )

        AppActionsDialog(
            request = state.menu,
            pinned = state.menu?.app?.packageName in pinned,
            dockSize = mainApps.size,
            onAction = ::onMenuAction
        )

        VpnSelectionDialog(
            visible = vpnPickerOpen,
            vpnApps = vpnApps,
            selectedPackage = selectedVpnApp?.packageName,
            onSelect = { app ->
                vm.selectVpnPackage(app.packageName)
                vpnPickerOpen = false
                state.requestFocus(VPN_BUTTON_KEY)
            },
            onDismiss = {
                vpnPickerOpen = false
                state.requestFocus(VPN_BUTTON_KEY)
            }
        )

        ControlCenterPanel(
            visible = state.controlCenterOpen,
            focus = focus,
            canFocus = state.controlCenterOpen && state.menu == null && !state.colorKeyDialogOpen && !isAmbientActive,
            networkStatus = networkStatus,
            bluetoothStatus = bluetoothStatus,
            mediaState = mediaState,
            sleepMinutesRemaining = sleepMinutesRemaining,
            screensaverTimeoutMinutes = screensaverTimeout,
            onCycleSleepTimer = vm::cycleSleepTimer,
            onCycleScreensaverTimeout = vm::cycleScreensaverTimeout,
            onStartScreensaver = {
                closeControlCenter()
                vm.startAmbient()
            },
            onOpenWallpapers = {
                closeControlCenter()
                openWallpapers()
            },
            onOpenSettings = {
                closeControlCenter()
                onOpenSettings()
            },
            onOpenNetworkSettings = {
                closeControlCenter()
                runCatching {
                    context.startActivity(
                        android.content.Intent(android.provider.Settings.ACTION_WIFI_SETTINGS)
                            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }.onFailure { onOpenSettings() }
            },
            onOpenBluetoothSettings = {
                closeControlCenter()
                runCatching {
                    context.startActivity(
                        android.content.Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS)
                            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }.onFailure { onOpenSettings() }
            },
            onDismiss = ::closeControlCenter
        )

        ColorKeyDialog(
            visible = state.colorKeyDialogOpen,
            apps = allApps,
            currentMappings = remoteKeyMappings,
            onAssignKey = vm::assignColorKey,
            onDismiss = ::closeColorKeys
        )

        AmbientScreensaver(
            visible = isAmbientActive,
            wallpaper = wallpaper,
            media = mediaState,
            onWake = vm::dismissAmbient
        )
    }
}
