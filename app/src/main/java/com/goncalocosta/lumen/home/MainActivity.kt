package com.goncalocosta.lumen.home

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.KeyEvent
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val vm: HomeViewModel by viewModels()
    private var homePresses by mutableIntStateOf(0)

    @OptIn(ExperimentalTvMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                PlaybackKeepAwakeManager.isPlaybackActive.collect { isPlaying ->
                    if (isPlaying) {
                        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    } else {
                        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    }
                }
            }
        }

        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Color.White,
                    onPrimary = Color.Black,
                    background = SkyTop,
                    onBackground = GlassWhite,
                    surface = GlassFill,
                    onSurface = GlassWhite
                )
            ) {
                HomeScreen(
                    vm = vm,
                    homePresses = homePresses,
                    onLaunch = ::launchApp,
                    onUninstall = { app -> uninstallPackage(this, app.packageName) },
                    onAppInfo = ::openAppInfo,
                    onOpenSettings = ::openSettings,
                    onOpenVpn = ::openVpnApp
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.hasCategory(Intent.CATEGORY_HOME)) {
            homePresses++
            vm.refreshDaily()
        }
    }

    override fun onStart() {
        super.onStart()
        vm.setForeground(true)
    }

    override fun onStop() {
        super.onStop()
        vm.setForeground(false)
    }

    override fun onResume() {
        super.onResume()
        PlaybackKeepAwakeManager.refresh()
        MediaSessionTracker.refresh(this)
        vm.refreshIfStale()
        vm.refreshDaily()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            if (vm.isAmbientActive.value) {
                vm.dismissAmbient()
                return true
            }
            vm.onUserInteraction()

            when (event.keyCode) {
                KeyEvent.KEYCODE_PROG_RED -> {
                    launchColorKey(ColorKey.Red)
                    return true
                }
                KeyEvent.KEYCODE_PROG_GREEN -> {
                    launchColorKey(ColorKey.Green)
                    return true
                }
                KeyEvent.KEYCODE_PROG_YELLOW -> {
                    launchColorKey(ColorKey.Yellow)
                    return true
                }
                KeyEvent.KEYCODE_PROG_BLUE -> {
                    launchColorKey(ColorKey.Blue)
                    return true
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    private fun launchColorKey(color: ColorKey) {
        val pkg = vm.getPackageForColorKey(color)
        if (pkg != null) {
            val intent = packageManager.getLeanbackLaunchIntentForPackage(pkg)
                ?: packageManager.getLaunchIntentForPackage(pkg)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                runCatching { startActivity(intent) }
            }
        }
    }

    private fun launchApp(app: HomeApp) {
        runCatching { startActivity(app.launchIntent()) }
            .onFailure {
                Toast.makeText(this, app.label, Toast.LENGTH_SHORT).show()
            }
    }

    private fun openAppInfo(app: HomeApp) {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", app.packageName, null)
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { startActivity(intent) }
            .onFailure { openSettings() }
    }

    private fun openSettings() {
        val intent = Intent(Settings.ACTION_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { startActivity(intent) }
            .recoverCatching {
                startActivity(
                    Intent("android.settings.SETTINGS")
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
    }

    private fun openVpnApp() {
        val selectedPkg = vm.selectedVpnPackage.value
        if (selectedPkg != null) {
            val launchIntent = packageManager.getLeanbackLaunchIntentForPackage(selectedPkg)
                ?: packageManager.getLaunchIntentForPackage(selectedPkg)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                val launched = runCatching { startActivity(launchIntent) }.isSuccess
                if (launched) return
            }
        }
        val vpnSettingsIntent = Intent("android.net.vpn.SETTINGS")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { startActivity(vpnSettingsIntent) }
            .recoverCatching {
                startActivity(
                    Intent(Settings.ACTION_VPN_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
            .onFailure {
                openSettings()
            }
    }
}
