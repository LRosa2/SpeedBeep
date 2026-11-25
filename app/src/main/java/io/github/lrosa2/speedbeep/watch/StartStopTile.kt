package io.github.lrosa2.speedbeep.watch

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import io.github.lrosa2.speedbeep.Graph
import io.github.lrosa2.speedbeep.ui.MainActivity

/** Quick-settings tile that starts or stops watching (spec §5.2). */
class StartStopTile : TileService() {
    companion object {
        fun refresh(context: Context) {
            requestListeningState(context, ComponentName(context, StartStopTile::class.java))
        }
    }

    override fun onStartListening() {
        qsTile?.apply {
            state = if (WatchBus.running) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            updateTile()
        }
    }

    override fun onClick() {
        if (WatchBus.running) {
            Graph.events.log("tile: stop")
            WatchService.stop(this, "tile", byUser = true)
            return
        }
        Graph.events.log("tile: start")
        if (WatchStarter.start(this, "tile", fromBackground = true)) return
        // Android refused the start from here: open the app, which starts watching (§5.2).
        val open = Intent(this, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_START, true)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivityAndCollapse(
            PendingIntent.getActivity(this, 0, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT),
        )
    }
}
