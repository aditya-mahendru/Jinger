package com.jinger.app

import android.app.Application
import com.jinger.app.data.AppContainer

class JingerApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
