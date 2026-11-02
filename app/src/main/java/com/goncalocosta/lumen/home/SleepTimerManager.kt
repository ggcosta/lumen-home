package com.goncalocosta.lumen.home

import android.app.Application
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.KeyEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

object SleepTimerManager {
    private const val TAG = "SleepTimer"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var timerJob: Job? = null
    private var applicationContext: Context? = null

    private val _targetTimeMs = MutableStateFlow<Long?>(null)
    private val _minutesRemaining = MutableStateFlow<Int?>(null)
    val minutesRemaining: StateFlow<Int?> = _minutesRemaining.asStateFlow()

    fun init(application: Application) {
        applicationContext = application.applicationContext
    }

    fun setTimer(minutes: Int) {
        if (minutes <= 0) {
            cancel()
            return
        }
        val target = System.currentTimeMillis() + minutes * 60_000L
        _targetTimeMs.value = target
        _minutesRemaining.value = minutes

        timerJob?.cancel()
        timerJob = scope.launch {
            while (isActive) {
                delay(1000L)
                val currentTarget = _targetTimeMs.value ?: break
                val remainingMs = currentTarget - System.currentTimeMillis()
                if (remainingMs <= 0) {
                    _targetTimeMs.value = null
                    _minutesRemaining.value = null
                    executeSleep()
                    break
                } else {
                    val remainingMins = ((remainingMs + 59_999L) / 60_000L).toInt()
                    _minutesRemaining.value = remainingMins
                }
            }
        }
    }

    fun cancel() {
        timerJob?.cancel()
        timerJob = null
        _targetTimeMs.value = null
        _minutesRemaining.value = null
    }

    private fun executeSleep() {
        Log.i(TAG, "Sleep timer expired. Putting TV into sleep.")
        val context = applicationContext ?: return

        // 1. Pause any active media playback
        val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        if (am != null) {
            val down = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PAUSE)
            val up = KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PAUSE)
            am.dispatchMediaKeyEvent(down)
            am.dispatchMediaKeyEvent(up)
        }

        // 2. Put TV into sleep / standby via standard Android TV sleep intents
        runCatching {
            val sleepIntent = Intent("android.intent.action.ACTION_REQUEST_SHUTDOWN")
                .putExtra("android.intent.extra.KEY_CONFIRM", false)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(sleepIntent)
        }.recoverCatching {
            // Android TV Dream / Screensaver fallback
            val dreamIntent = Intent(Intent.ACTION_MAIN)
                .addCategory("android.intent.category.DESK_DOCK")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(dreamIntent)
        }
    }
}
