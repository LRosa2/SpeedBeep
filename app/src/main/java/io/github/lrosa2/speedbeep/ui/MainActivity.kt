package io.github.lrosa2.speedbeep.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.github.lrosa2.speedbeep.Graph
import io.github.lrosa2.speedbeep.watch.Beeper
import io.github.lrosa2.speedbeep.watch.WatchStarter

class MainActivity : ComponentActivity() {
    companion object {
        /** Opens the app and starts watching: the tile's fallback (§5.2), and the emulator checks. */
        const val EXTRA_START = "io.github.lrosa2.speedbeep.extra.START"
    }

    /** For "Test beep"; the service has its own. */
    private lateinit var beeper: Beeper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        beeper = Beeper(this, Graph.events)
        handleStart(intent)
        setContent { SpeedBeepTheme { AppScreens(beeper) } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleStart(intent)
    }

    override fun onDestroy() {
        beeper.stop()
        super.onDestroy()
    }

    private fun handleStart(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_START, false) == true) {
            intent.removeExtra(EXTRA_START)
            WatchStarter.start(this, "app opened to start", fromBackground = false)
        }
    }
}
