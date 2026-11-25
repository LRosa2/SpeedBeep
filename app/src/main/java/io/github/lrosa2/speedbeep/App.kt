package io.github.lrosa2.speedbeep

import android.app.Application
import io.github.lrosa2.speedbeep.car.CarController
import io.github.lrosa2.speedbeep.watch.WatchNotifications

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        Graph.init(this)
        CarController.init(this)
        WatchNotifications.createChannels(this)
    }
}
