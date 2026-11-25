package io.github.lrosa2.speedbeep

import android.app.Application
import android.content.Context
import io.github.lrosa2.speedbeep.engine.EventLog
import io.github.lrosa2.speedbeep.engine.LoadedRoads
import io.github.lrosa2.speedbeep.engine.RoadStore
import io.github.lrosa2.speedbeep.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.time.Instant
import java.time.ZoneOffset

/** The app's few singletons, created once in [App.onCreate]. */
object Graph {
    lateinit var events: EventLog
        private set

    lateinit var settings: SettingsRepository
        private set

    /** Null until the road file is loaded (a few seconds on the first start of a new version). */
    val roads = MutableStateFlow<LoadedRoads?>(null)

    private val io = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun init(app: Application) {
        settings = SettingsRepository(app)
        events = EventLog(File(app.filesDir, "events.log"))
        io.launch { roads.value = loadRoads(app) }
    }

    /** Recorded drives; shared through the FileProvider in Task 7. */
    fun drivesDir(context: Context): File = File(context.getExternalFilesDir(null) ?: context.filesDir, "drives")

    private fun loadRoads(app: Application): LoadedRoads {
        val info = app.packageManager.getPackageInfo(app.packageName, 0)
        // lastUpdateTime also changes when a debug build is reinstalled with the same version code.
        val key = "${info.longVersionCode}-${info.lastUpdateTime}"
        val loaded = RoadStore(File(app.filesDir, "roads"), key) { app.assets.open(RoadStore.FILE_NAME) }.load()
        val date = loaded.dataTimestampSec?.let { Instant.ofEpochSecond(it).atOffset(ZoneOffset.UTC).toLocalDate() }
        events.log(loaded.problem?.let { "road data: $it; simple mode everywhere" } ?: "road data loaded (OSM data of $date)")
        return loaded
    }
}
