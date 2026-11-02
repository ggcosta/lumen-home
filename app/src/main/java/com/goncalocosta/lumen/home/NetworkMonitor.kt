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
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class NetworkStatus(
    val isConnected: Boolean = false,
    val isEthernet: Boolean = false,
    val isWifi: Boolean = false,
    val label: String = "Offline"
)

object NetworkMonitor {
    private val _status = MutableStateFlow(NetworkStatus())
    val status: StateFlow<NetworkStatus> = _status.asStateFlow()

    fun init(application: Application) {
        val cm = application.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
        val wm = application.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager

        fun evaluateNetwork() {
            val activeNet = cm.activeNetwork
            val caps = if (activeNet != null) cm.getNetworkCapabilities(activeNet) else null

            if (caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
                when {
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> {
                        _status.value = NetworkStatus(
                            isConnected = true,
                            isEthernet = true,
                            isWifi = false,
                            label = "Ethernet Connected"
                        )
                    }
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> {
                        val ssid = runCatching {
                            val info = wm?.connectionInfo
                            val rawSsid = info?.ssid?.trim('"')
                            if (!rawSsid.isNullOrBlank() && rawSsid != "<unknown ssid>") rawSsid else null
                        }.getOrNull()
                        val wifiLabel = if (ssid != null) "Wi-Fi: $ssid" else "Wi-Fi Connected"
                        _status.value = NetworkStatus(
                            isConnected = true,
                            isEthernet = false,
                            isWifi = true,
                            label = wifiLabel
                        )
                    }
                    else -> {
                        _status.value = NetworkStatus(
                            isConnected = true,
                            label = "Connected"
                        )
                    }
                }
            } else {
                _status.value = NetworkStatus(isConnected = false, label = "Offline")
            }
        }

        evaluateNetwork()

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                evaluateNetwork()
            }

            override fun onLost(network: Network) {
                evaluateNetwork()
            }

            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                evaluateNetwork()
            }
        }

        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                cm.registerDefaultNetworkCallback(callback)
            } else {
                val req = NetworkRequest.Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .build()
                cm.registerNetworkCallback(req, callback)
            }
        }
    }
}
