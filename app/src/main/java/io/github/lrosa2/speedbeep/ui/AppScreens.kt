package io.github.lrosa2.speedbeep.ui

import android.app.Activity
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.lrosa2.speedbeep.Graph
import io.github.lrosa2.speedbeep.engine.Checklist
import io.github.lrosa2.speedbeep.engine.Settings
import io.github.lrosa2.speedbeep.watch.Beeper
import io.github.lrosa2.speedbeep.watch.WatchBus
import io.github.lrosa2.speedbeep.watch.WatchService
import io.github.lrosa2.speedbeep.watch.WatchStarter
import kotlinx.coroutines.launch

private enum class Screen { MAIN, SETTINGS, ABOUT }

@Composable
fun AppScreens(beeper: Beeper) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by Graph.settings.settings.collectAsStateWithLifecycle(initialValue = null)
    val status by WatchBus.status.collectAsStateWithLifecycle()
    val roads by Graph.roads.collectAsStateWithLifecycle()
    var screen by rememberSaveable { mutableStateOf(Screen.MAIN) }
    // Bumped on every resume and permission answer, so the checklist is read again.
    var refresh by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { refresh++ }
    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { refresh++ }

    val s = settings ?: return
    val setup = remember(refresh, s) { Checklist.missing(SetupProbe.state(context, s)) }
    val change: ((Settings) -> Settings) -> Unit = { transform -> scope.launch { Graph.settings.update(transform) } }

    KeepScreenOn(context as Activity, s.keepScreenOn)
    BackHandler(enabled = screen != Screen.MAIN) { screen = Screen.MAIN }

    when (screen) {
        Screen.MAIN -> MainScreen(
            status = status,
            settings = s,
            roads = roads,
            setup = setup,
            onStartStop = {
                if (status == null) WatchStarter.start(context, "app", fromBackground = false)
                else WatchService.stop(context, "app", byUser = true)
            },
            onPause = { WatchService.send(context, WatchService.ACTION_PAUSE) },
            onResume = { WatchService.send(context, WatchService.ACTION_RESUME) },
            onQuiet = { WatchService.send(context, WatchService.ACTION_QUIET) },
            onMode = { mode -> change { it.copy(mode = mode) } },
            onThreshold = { delta -> change { it.stepThreshold(delta) } },
            onFix = { item -> SetupProbe.fix(item, context, { permissions.launch(it) }) { screen = Screen.SETTINGS } },
            onSettings = { screen = Screen.SETTINGS },
            onAbout = { screen = Screen.ABOUT },
        )
        Screen.SETTINGS -> SettingsScreen(s, beeper, change, onBack = { screen = Screen.MAIN }, car = {})
        Screen.ABOUT -> AboutScreen(roads, onBack = { screen = Screen.MAIN })
    }
}

/** "Keep screen on while app is open" (§7.3). */
@Composable
private fun KeepScreenOn(activity: Activity, on: Boolean) {
    DisposableEffect(activity, on) {
        val flag = WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        if (on) activity.window.addFlags(flag) else activity.window.clearFlags(flag)
        onDispose { activity.window.clearFlags(flag) }
    }
}
