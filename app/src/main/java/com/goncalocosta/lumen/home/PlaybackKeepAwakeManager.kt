package com.goncalocosta.lumen.home

import android.app.Application
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.AudioPlaybackConfiguration
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import androidx.annotation.RequiresApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Monitors active audio/media playback on the system (e.g. Spotify, YouTube Music, streaming radio).
 * When media is actively playing, holds a bright screen wake lock and notifies MainActivity to keep
 * the screen awake with FLAG_KEEP_SCREEN_ON, preventing the TV's idle sleep timer from turning off
 * the display and shutting down the TV over HDMI CEC.
 *
 * When playback stops or pauses, a 10-second debounce is applied before releasing the wake lock to
 * handle track transitions and buffering smoothly.
 */
object PlaybackKeepAwakeManager {
    private const val TAG = "PlaybackKeepAwake"
    private const val RELEASE_DEBOUNCE_MS = 10_000L

    private var audioManager: AudioManager? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    private val _isPlaybackActive = MutableStateFlow(false)
    val isPlaybackActive: StateFlow<Boolean> = _isPlaybackActive.asStateFlow()

    private var isRegistered = false

    private val releaseRunnable = Runnable {
        applyPlaybackState(false)
    }

    fun init(application: Application) {
        if (isRegistered) return
        val am = application.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        val pm = application.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return

        audioManager = am

        runCatching {
            @Suppress("DEPRECATION")
            pm.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ON_AFTER_RELEASE,
                "com.goncalocosta.lumen.home:PlaybackKeepAwake"
            ).apply {
                setReferenceCounted(false)
                wakeLock = this
            }
        }.onFailure {
            Log.w(TAG, "Failed to create wake lock", it)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            registerPlaybackCallbackOreo(am)
        }

        refresh()
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun registerPlaybackCallbackOreo(am: AudioManager) {
        val callback = object : AudioManager.AudioPlaybackCallback() {
            override fun onPlaybackConfigChanged(configs: List<AudioPlaybackConfiguration>) {
                evaluatePlayback(configs)
            }
        }
        runCatching {
            am.registerAudioPlaybackCallback(callback, mainHandler)
            isRegistered = true
        }.onFailure {
            Log.w(TAG, "Failed to register audio playback callback", it)
        }
    }

    fun refresh() {
        val am = audioManager ?: return
        val configs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            runCatching { am.activePlaybackConfigurations }.getOrNull()
        } else null
        evaluatePlayback(configs)
    }

    private fun evaluatePlayback(configs: List<AudioPlaybackConfiguration>?) {
        val am = audioManager ?: return
        val isMusicActive = runCatching { am.isMusicActive }.getOrDefault(false)

        val hasMediaConfig = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && configs != null) {
            configs.any { config ->
                runCatching {
                    isMediaUsage(config.audioAttributes?.usage) && isConfigActive(config)
                }.getOrDefault(false)
            }
        } else false

        val active = isMusicActive || hasMediaConfig

        if (active) {
            mainHandler.removeCallbacks(releaseRunnable)
            applyPlaybackState(true)
        } else {
            if (_isPlaybackActive.value) {
                // Debounce release so track transitions and buffering don't rapidly toggle the wake lock.
                mainHandler.removeCallbacks(releaseRunnable)
                mainHandler.postDelayed(releaseRunnable, RELEASE_DEBOUNCE_MS)
            } else {
                applyPlaybackState(false)
            }
        }
    }

    private fun isConfigActive(config: AudioPlaybackConfiguration): Boolean {
        val activeByReflection = runCatching {
            val method = config.javaClass.getMethod("isActive")
            method.invoke(config) as? Boolean
        }.getOrNull()
        return activeByReflection ?: true
    }

    private fun isMediaUsage(usage: Int?): Boolean {
        return when (usage) {
            AudioAttributes.USAGE_MEDIA,
            AudioAttributes.USAGE_GAME,
            AudioAttributes.USAGE_UNKNOWN -> true
            else -> false
        }
    }

    private fun applyPlaybackState(active: Boolean) {
        val previous = _isPlaybackActive.value
        _isPlaybackActive.value = active

        val wl = wakeLock
        if (wl != null) {
            runCatching {
                if (active) {
                    if (!wl.isHeld) {
                        wl.acquire()
                        Log.d(TAG, "Acquired SCREEN_BRIGHT_WAKE_LOCK (active playback)")
                    }
                } else {
                    if (wl.isHeld) {
                        wl.release()
                        Log.d(TAG, "Released SCREEN_BRIGHT_WAKE_LOCK (playback idle)")
                    }
                }
            }.onFailure {
                Log.w(TAG, "Failed to update wake lock state", it)
            }
        }

        if (previous != active) {
            Log.d(TAG, "Playback active state changed to: $active")
        }
    }
}
