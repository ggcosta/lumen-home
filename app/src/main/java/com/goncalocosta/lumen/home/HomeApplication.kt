package com.goncalocosta.lumen.home

import android.app.Application

class HomeApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        PlaybackKeepAwakeManager.init(this)
        SleepTimerManager.init(this)
        NetworkMonitor.init(this)
        BluetoothMonitor.init(this)
    }
}
