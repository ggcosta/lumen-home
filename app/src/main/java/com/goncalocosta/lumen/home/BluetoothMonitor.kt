package com.goncalocosta.lumen.home

import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class BluetoothStatus(
    val isEnabled: Boolean = false,
    val label: String = "Bluetooth Off"
)

object BluetoothMonitor {
    private val _status = MutableStateFlow(BluetoothStatus())
    val status: StateFlow<BluetoothStatus> = _status.asStateFlow()

    fun init(application: Application) {
        val adapter = runCatching { BluetoothAdapter.getDefaultAdapter() }.getOrNull()

        fun update() {
            val enabled = adapter?.isEnabled == true
            _status.value = BluetoothStatus(
                isEnabled = enabled,
                label = if (enabled) "Audio & Remotes" else "Bluetooth Off"
            )
        }

        update()

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == BluetoothAdapter.ACTION_STATE_CHANGED) {
                    update()
                }
            }
        }

        val filter = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
        ContextCompat.registerReceiver(application, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
    }
}
