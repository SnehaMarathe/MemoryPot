package com.memorypot

import android.app.Application
import com.memorypot.di.AppContainer

class MemoryPotApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
