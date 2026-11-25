package io.github.lrosa2.speedbeep

import android.app.Application

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        Graph.init(this)
    }
}
