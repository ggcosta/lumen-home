package com.goncalocosta.lumen.home

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class HomeViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = Prefs(application)
    private val loader = AppLoader(application.packageManager, application.packageName)
    private val mutex = Mutex()
    private val _mainApps = MutableStateFlow<List<HomeApp>>(emptyList())
    private val _allApps = MutableStateFlow<List<HomeApp>>(emptyList())
    private val _isLoaded = MutableStateFlow(false)
    val mainApps: StateFlow<List<HomeApp>> = _mainApps
    val allApps: StateFlow<List<HomeApp>> = _allApps
    val isLoaded: StateFlow<Boolean> = _isLoaded

    /** Last app focused in the dock / grid, restored after relaunch of the UI. */
    var lastDockPackage: String? = null
    var lastGridPackage: String? = null

    private var reloadJob: Job? = null
    private var packageSequence = -1

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            scheduleReload(debounceMs = 400)
        }
    }

    private val _wallpaper = MutableStateFlow(
        WallpaperState(
            current = prefs.wallpaperBundled(),
            daily = prefs.wallpaperDaily(),
            dailyCaption = prefs.dailyCaption()
        )
    )
    val wallpaper: StateFlow<WallpaperState> = _wallpaper
    private var dailyJob: Job? = null
    private var wallpaperLoadJob: Job? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var vpnNetworkCallback: ConnectivityManager.NetworkCallback? = null

    private val _isVpnConnected = MutableStateFlow(false)
    val isVpnConnected: StateFlow<Boolean> = _isVpnConnected

    private val _installedVpnPackages = MutableStateFlow<List<String>>(emptyList())
    val installedVpnPackages: StateFlow<List<String>> = _installedVpnPackages

    private val _selectedVpnPackage = MutableStateFlow<String?>(null)
    val selectedVpnPackage: StateFlow<String?> = _selectedVpnPackage

    private val _vpnApps = MutableStateFlow<List<HomeApp>>(emptyList())
    val vpnApps: StateFlow<List<HomeApp>> = _vpnApps

    private val _selectedVpnApp = MutableStateFlow<HomeApp?>(null)
    val selectedVpnApp: StateFlow<HomeApp?> = _selectedVpnApp

    val networkStatus: StateFlow<NetworkStatus> = NetworkMonitor.status
    val bluetoothStatus: StateFlow<BluetoothStatus> = BluetoothMonitor.status
    val mediaState: StateFlow<MediaTrackInfo> = MediaSessionTracker.mediaState
    val sleepMinutesRemaining: StateFlow<Int?> = SleepTimerManager.minutesRemaining

    private val _screensaverTimeout = MutableStateFlow(prefs.screensaverTimeoutMinutes())
    val screensaverTimeout: StateFlow<Int> = _screensaverTimeout

    private val _remoteKeyMappings = MutableStateFlow<Map<ColorKey, String?>>(emptyMap())
    val remoteKeyMappings: StateFlow<Map<ColorKey, String?>> = _remoteKeyMappings

    private val _isAmbientActive = MutableStateFlow(false)
    val isAmbientActive: StateFlow<Boolean> = _isAmbientActive

    @Volatile
    private var lastInteractionTime = System.currentTimeMillis()

    /** The idle countdown only runs while the launcher is visible, not while another app is in front. */
    private val _isForeground = MutableStateFlow(false)

    /** lastModified of the daily file currently shown, so resumes don't re-decode an unchanged image. */
    @Volatile
    private var shownDailyStamp = -1L
    private var vpnAppsJob: Job? = null

    private val dateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_DATE_CHANGED,
                Intent.ACTION_TIMEZONE_CHANGED,
                Intent.ACTION_SCREEN_ON,
                Intent.ACTION_USER_PRESENT -> {
                    refreshDaily()
                }
            }
        }
    }

    private fun loadAssetBitmap(path: String): ImageBitmap? = runCatching {
        getApplication<Application>().assets.open(path).use { stream ->
            android.graphics.BitmapFactory.decodeStream(stream)?.asImageBitmap()
        }?.also { it.prepareToDraw() }
    }.getOrNull()

    /** Decodes the cached daily image, or returns null when it is missing or already on screen. */
    private fun decodeDailyIfChanged(app: Application): ImageBitmap? {
        val f = DailyWallpaper.file(app)
        if (!f.exists()) return null
        val stamp = f.lastModified()
        val current = _wallpaper.value
        if (stamp == shownDailyStamp && current.daily && current.image != null) return null
        return DailyWallpaper.decode(app)?.also { shownDailyStamp = stamp }
    }

    fun selectBundled(wallpaper: BundledWallpaper) {
        prefs.setWallpaperBundled(wallpaper)
        prefs.setWallpaperDaily(false)
        shownDailyStamp = -1L
        wallpaperLoadJob?.cancel()
        wallpaperLoadJob = viewModelScope.launch(Dispatchers.IO) {
            val bmp = loadAssetBitmap(wallpaper.assetPath)
            _wallpaper.value = _wallpaper.value.copy(
                current = wallpaper,
                daily = false,
                image = bmp
            )
        }
    }

    fun selectDaily(force: Boolean = false) {
        prefs.setWallpaperDaily(true)
        _wallpaper.update { it.copy(daily = true) }
        refreshDaily(force = force)
    }

    /** Loads the cached image if needed and downloads today's if the date changed or forced. */
    fun refreshDaily(force: Boolean = false) {
        if (!_wallpaper.value.daily) return
        if (dailyJob?.isActive == true && !force) return
        dailyJob?.cancel()
        dailyJob = viewModelScope.launch(Dispatchers.IO) {
            val app = getApplication<Application>()
            val dailyBmp = decodeDailyIfChanged(app)
            if (dailyBmp != null) {
                _wallpaper.update { current ->
                    if (current.image == null || current.daily) {
                        current.copy(image = dailyBmp, dailyCaption = prefs.dailyCaption())
                    } else current
                }
            }
            if (DailyWallpaper.refreshIfNeeded(app, prefs, force = force)) {
                decodeDailyIfChanged(app)?.let { img ->
                    _wallpaper.update { it.copy(image = img, dailyCaption = prefs.dailyCaption()) }
                }
            } else if (_wallpaper.value.image == null) {
                // Fallback to bundled wallpaper if no daily image exists yet
                val fallbackBmp = loadAssetBitmap(_wallpaper.value.current.assetPath)
                _wallpaper.update { it.copy(image = fallbackBmp) }
            }
        }
    }

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val thumbs = BundledWallpaper.entries.mapNotNull { wp ->
                loadAssetBitmap(wp.thumbPath)?.let { wp.id to it }
            }.toMap()
            val initial = _wallpaper.value
            val activeBitmap = if (initial.daily) {
                decodeDailyIfChanged(application) ?: loadAssetBitmap(initial.current.assetPath)
            } else {
                loadAssetBitmap(initial.current.assetPath)
            }
            _wallpaper.update { current ->
                current.copy(
                    image = current.image ?: activeBitmap,
                    thumbnails = thumbs,
                    dailyCaption = current.dailyCaption ?: prefs.dailyCaption()
                )
            }
        }
        val dateFilter = IntentFilter().apply {
            addAction(Intent.ACTION_DATE_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        ContextCompat.registerReceiver(
            application,
            dateReceiver,
            dateFilter,
            ContextCompat.RECEIVER_EXPORTED
        )
        runCatching {
            val cm = application.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            if (cm != null) {
                val callback = object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) {
                        if (_wallpaper.value.daily) {
                            refreshDaily()
                        }
                    }
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    cm.registerDefaultNetworkCallback(callback)
                } else {
                    val request = NetworkRequest.Builder()
                        .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                        .build()
                    cm.registerNetworkCallback(request, callback)
                }
                networkCallback = callback
            }
        }
        registerVpnCallback()
        refreshVpnState()
        refreshVpnApps()
        refreshDaily()
        viewModelScope.launch {
            while (true) {
                delay(30 * 60 * 1000L) // every 30 minutes
                if (_wallpaper.value.daily) {
                    refreshDaily()
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        ContextCompat.registerReceiver(application, packageReceiver, filter, ContextCompat.RECEIVER_EXPORTED)
        _remoteKeyMappings.value = loadRemoteKeys()
        // Sleeps until the idle deadline instead of polling; restarts when the timeout, ambient or
        // foreground state changes.
        viewModelScope.launch {
            combine(_screensaverTimeout, _isAmbientActive, _isForeground, ::Triple).collectLatest { (timeoutMin, active, foreground) ->
                if (timeoutMin <= 0 || active || !foreground) return@collectLatest
                while (true) {
                    val remaining = timeoutMin * 60_000L - (System.currentTimeMillis() - lastInteractionTime)
                    if (remaining <= 0) {
                        _isAmbientActive.value = true
                        break
                    }
                    delay(remaining)
                }
            }
        }
        viewModelScope.launch { initialLoad() }
    }

    override fun onCleared() {
        runCatching { getApplication<Application>().unregisterReceiver(packageReceiver) }
        runCatching { getApplication<Application>().unregisterReceiver(dateReceiver) }
        val cm = getApplication<Application>().getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        runCatching {
            networkCallback?.let { cb -> cm?.unregisterNetworkCallback(cb) }
        }
        runCatching {
            vpnNetworkCallback?.let { cb -> cm?.unregisterNetworkCallback(cb) }
        }
    }

    /**
     * Called from onResume. Checks VPN status and asks PackageManager whether anything changed
     * since the last load.
     */
    fun refreshIfStale() {
        refreshVpnState()
        refreshVpnApps()
        if (!_isLoaded.value) return
        if (Build.VERSION.SDK_INT >= 26 && packageSequence >= 0) {
            val changed = runCatching {
                getApplication<Application>().packageManager.getChangedPackages(packageSequence)
            }.getOrNull()
            if (changed == null) return
        }
        scheduleReload(debounceMs = 0)
    }

    private fun registerVpnCallback() {
        runCatching {
            val cm = getApplication<Application>().getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
            val request = NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_VPN)
                .removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
                .build()
            val callback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    _isVpnConnected.value = true
                }

                override fun onLost(network: Network) {
                    refreshVpnState()
                }

                override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                    refreshVpnState()
                }
            }
            cm.registerNetworkCallback(request, callback)
            vpnNetworkCallback = callback
        }
    }

    @Suppress("DEPRECATION")
    fun refreshVpnState() {
        val cm = getApplication<Application>().getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
        var connected = false
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val active = cm.activeNetwork
                if (active != null) {
                    val caps = cm.getNetworkCapabilities(active)
                    if (caps?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true) {
                        connected = true
                    }
                }
                if (!connected) {
                    for (network in cm.allNetworks) {
                        val caps = cm.getNetworkCapabilities(network) ?: continue
                        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
                            connected = true
                            break
                        }
                    }
                }
            } else {
                @Suppress("DEPRECATION")
                connected = cm.getNetworkInfo(ConnectivityManager.TYPE_VPN)?.isConnected == true
            }
        }
        _isVpnConnected.value = connected
    }

    /** ~15 PackageManager binder calls, so it runs off the main thread (it is called from onResume). */
    fun refreshVpnApps() {
        vpnAppsJob?.cancel()
        vpnAppsJob = viewModelScope.launch(Dispatchers.IO) { scanVpnApps() }
    }

    private suspend fun scanVpnApps() {
        val app = getApplication<Application>()
        val pm = app.packageManager
        val packages = LinkedHashSet<String>()

        // 1. Query services declaring android.net.VpnService
        runCatching {
            val vpnIntent = Intent("android.net.VpnService")
            val services = pm.queryIntentServices(vpnIntent, 0)
            for (info in services) {
                info.serviceInfo?.packageName?.let { packages.add(it) }
            }
        }

        // 2. Known common Android TV VPN packages as fallback / scan
        val knownVpns = listOf(
            "com.nordvpn.android",
            "com.expressvpn.vpn",
            "net.openvpn.openvpn",
            "com.wireguard.android",
            "com.surfshark.vpnclient.android",
            "com.mullvad.vpn",
            "ch.protonvpn.android",
            "com.windscribe.vpn",
            "com.tailscale.ipn",
            "com.cloudflare.onedotonedotonedotone",
            "de.blinkt.openvpn",
            "org.strongswan.android",
            "com.pia.android"
        )
        for (pkg in knownVpns) {
            if (pkg !in packages) {
                val exists = runCatching { pm.getPackageInfo(pkg, 0) }.isSuccess
                if (exists) packages.add(pkg)
            }
        }

        val installed = packages.toList()
        withContext(Dispatchers.Main) {
            _installedVpnPackages.value = installed
            updateVpnAppsState()
        }
    }

    private fun updateVpnAppsState() {
        val installed = _installedVpnPackages.value
        val all = _allApps.value
        val apps = all.filter { it.packageName in installed }
        _vpnApps.value = apps

        val saved = prefs.vpnPackage()
        val chosenPkg = resolveSelectedVpnPackage(saved, installed)
        _selectedVpnPackage.value = chosenPkg
        val chosenApp = apps.find { it.packageName == chosenPkg }
            ?: all.find { it.packageName == chosenPkg }
        _selectedVpnApp.value = chosenApp
        if (chosenApp != null) {
            viewModelScope.launch(Dispatchers.Default) { chosenApp.glyph }
        }
    }

    fun selectVpnPackage(packageName: String) {
        prefs.setVpnPackage(packageName)
        _selectedVpnPackage.value = packageName
        val app = _allApps.value.find { it.packageName == packageName }
        _selectedVpnApp.value = app
        if (app != null) {
            viewModelScope.launch(Dispatchers.Default) { app.glyph }
        }
    }

    private fun scheduleReload(debounceMs: Long) {
        reloadJob?.cancel()
        reloadJob = viewModelScope.launch {
            if (debounceMs > 0) delay(debounceMs)
            reload()
        }
    }

    private suspend fun initialLoad() = mutex.withLock {
        val pinned = prefs.main()
        if (pinned.isNotEmpty()) {
            // Phase 1: only the dock apps → dock is visible almost immediately on cold start.
            val first = withContext(Dispatchers.Default) {
                loader.load(only = pinned.toSet(), prewarmGlyphs = pinned)
            }
            if (first.isNotEmpty()) {
                _mainApps.value = resolveMain(first, pinned)
                _isLoaded.value = true
            }
        }
        // Phase 2: full catalogue.
        loadAllLocked()
    }

    private suspend fun reload() = mutex.withLock { loadAllLocked() }

    private suspend fun loadAllLocked() {
        val loaded = withContext(Dispatchers.Default) {
            markSequence()
            loader.load(prewarmGlyphs = prefs.main())
        }
        apply(loaded)
        refreshVpnApps()
    }

    private fun markSequence() {
        if (Build.VERSION.SDK_INT >= 26) {
            val pm = getApplication<Application>().packageManager
            packageSequence = runCatching { pm.getChangedPackages(0)?.sequenceNumber ?: 0 }.getOrDefault(0)
        }
    }

    fun moveMain(fromIndex: Int, toIndex: Int) {
        val list = _mainApps.value.toMutableList()
        if (fromIndex !in list.indices || toIndex !in list.indices || fromIndex == toIndex) return
        val item = list.removeAt(fromIndex)
        list.add(toIndex, item)
        _mainApps.value = list
        prefs.saveMain(list.map { it.packageName })
    }

    fun unpin(packageName: String) {
        val list = _mainApps.value.filter { it.packageName != packageName }
        _mainApps.value = list
        prefs.saveMain(list.map { it.packageName })
    }

    fun pin(packageName: String) {
        val added = _allApps.value.find { it.packageName == packageName } ?: return
        if (_mainApps.value.any { it.packageName == packageName }) return
        _mainApps.value = _mainApps.value + added
        prefs.saveMain(_mainApps.value.map { it.packageName })
        // Make sure the dock glyph is ready before it is drawn.
        if (added.glyphType == GlassGlyphType.DynamicSilhouette) {
            viewModelScope.launch(Dispatchers.Default) { added.glyph }
        }
    }

    private fun resolveMain(loaded: List<HomeApp>, ids: List<String>): List<HomeApp> {
        val byPkg = loaded.associateBy { it.packageName }
        val seen = HashSet<String>()
        return ids.mapNotNull { pkg -> byPkg[pkg]?.takeIf { seen.add(pkg) } }
    }

    private fun apply(loaded: List<HomeApp>) {
        val byPkg = loaded.associateBy { it.packageName }
        var ids = prefs.main()
        if (ids.isEmpty() && !prefs.migrated()) {
            ids = loaded
                .filter { "developertools" !in it.packageName.lowercase() }
                .take(5)
                .map { it.packageName }
            if (ids.isEmpty() && LUMEN_TV in byPkg) ids = listOf(LUMEN_TV)
            if (ids.isNotEmpty()) prefs.saveMain(ids) else prefs.markMigrated()
        }
        val main = resolveMain(loaded, ids).toMutableList()
        if (main.isEmpty() && LUMEN_TV in byPkg) {
            main.add(byPkg.getValue(LUMEN_TV))
            prefs.saveMain(listOf(LUMEN_TV))
        }
        // StateFlow conflates equal values → unchanged reloads cause no UI work at all.
        _mainApps.value = main
        _allApps.value = loaded.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
        _isLoaded.value = true
        updateVpnAppsState()
    }

    fun onUserInteraction() {
        lastInteractionTime = System.currentTimeMillis()
        if (_isAmbientActive.value) {
            _isAmbientActive.value = false
        }
    }

    /** Called from onStart/onStop. Coming back to Home counts as an interaction. */
    fun setForeground(foreground: Boolean) {
        if (foreground) lastInteractionTime = System.currentTimeMillis()
        _isForeground.value = foreground
    }

    fun startAmbient() {
        _isAmbientActive.value = true
    }

    fun dismissAmbient() {
        _isAmbientActive.value = false
        lastInteractionTime = System.currentTimeMillis()
    }

    fun cycleScreensaverTimeout() {
        val next = when (_screensaverTimeout.value) {
            2 -> 5
            5 -> 10
            10 -> 0
            else -> 2
        }
        _screensaverTimeout.value = next
        prefs.setScreensaverTimeoutMinutes(next)
    }

    fun cycleSleepTimer() {
        val current = SleepTimerManager.minutesRemaining.value
        val next = when (current) {
            null -> 15
            15 -> 30
            30 -> 45
            45 -> 60
            60 -> 90
            else -> 0
        }
        SleepTimerManager.setTimer(next)
    }

    fun assignColorKey(color: ColorKey, packageName: String?) {
        prefs.setRemoteKey(color, packageName)
        _remoteKeyMappings.value = loadRemoteKeys()
    }

    fun getPackageForColorKey(color: ColorKey): String? = _remoteKeyMappings.value[color]

    private fun loadRemoteKeys(): Map<ColorKey, String?> =
        ColorKey.entries.associateWith { prefs.remoteKey(it) }
}

internal fun resolveSelectedVpnPackage(saved: String?, installed: List<String>): String? = when {
    saved != null && saved in installed -> saved
    "com.nordvpn.android" in installed -> "com.nordvpn.android"
    installed.isNotEmpty() -> installed.first()
    else -> saved
}
