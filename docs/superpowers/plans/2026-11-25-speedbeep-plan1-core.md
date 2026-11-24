# SpeedBeep Plan 1: spike, core logic and road file

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Prove that Bluetooth auto-start works on the owner's phone, and build and test
everything that does not need the Android app:
- the road-file format
- the Swiss road file and its builder
- the road matcher
- the beep rules

**Architecture:**
- **`:core`** is a plain Kotlin/JVM module with no Android and no third-party runtime
  dependencies. It holds the road-file format, reader and writer, the matcher, and the
  speed state machine.
- **`:roadbuilder`** is a JVM command-line tool. It reads the Geofabrik Switzerland
  `.osm.pbf` and writes `app/src/main/assets/roads-ch.bin` using the `:core` writer.
- **The spike** is a separate, throwaway Android project in `C:\work\tmp\speedbeep-spike`.
- The Android app itself is **Plan 2**. It is written after this plan, once the spike result
  and the `:core` signatures exist.

**Tech Stack:**
- Kotlin 2.3.21, Gradle 9.6.0, JDK 21, JUnit 6 (Jupiter).
- `com.wolt.osm:parallelpbf:0.3.1` reads OpenStreetMap `.osm.pbf` files (roadbuilder only).
- Spike: AGP 9.4.1 with androidx core-ktx.

**Spec:** `docs/superpowers/specs/2026-11-25-speedbeep-design.md`. Executors read it
alongside this plan; § numbers below refer to it.

## Why two plans

The car link in the app depends on what the spike shows on the real phone. That decides
whether it uses the companion route or the notification fallback (§5.1). Writing the app
tasks now would leave placeholders. Plan 2 (the app) is written when Task 1's result is in
and Tasks 2–8 are done.

## Global Constraints

- Repository: `E:\AndroidProj\SpeedBeep`, branch `main`, local only (no push in this plan).
- Packages:
  - core: `io.github.lrosa2.speedbeep.core.road` and `io.github.lrosa2.speedbeep.core.rules`
  - builder: `io.github.lrosa2.speedbeep.roadbuilder`
  - spike: `io.github.lrosa2.speedbeep.spike`
- Toolchain, the same as the sibling projects:
  - AGP 9.4.1, Kotlin 2.3.21, Gradle 9.6.0 (wrapper copied from `E:\AndroidProj\CarCare`)
  - JDK 21 runs Gradle; JVM modules emit Java 17 bytecode
- Android levels (spike now, app in Plan 2): compileSdk 37, targetSdk 36, minSdk 34.
- `:core` has **no** Android dependency and **no** third-party runtime dependency.
- Tests use JUnit 6 through `junit-bom` 6.1.3 and `junit-jupiter`. Run with
  `gradlew.bat <module>:test` from the repo root.
- Values from the spec, copied verbatim. These are the only places these numbers appear,
  as named constants.

  | Value | Setting |
  |---|---|
  | 53 | threshold T default |
  | 3 | re-arm margin R |
  | 2 | readings of debounce |
  | 5 | readings to enter FAST |
  | 2 | readings to leave FAST |
  | 20 m | match distance |
  | 30° | heading tolerance (compared modulo 180°) |
  | 15 km/h | heading-check floor |
  | 30 m | search box |
  | 25 m | position accuracy limit |
  | 2 m/s | speed accuracy limit |
  | 3 s | maximum fix age |
  | 5 s | until NO_GPS |
  | 10 s | NEUTRAL timeout |
  | 10 s | reminder interval default |

- Road file:
  - Little-endian, magic `SBRD`, version 1, cells of 0.01° (`10_000` micro-degrees).
  - Under 40 MB, committed at `app/src/main/assets/roads-ch.bin`.
- The OSM extract `switzerland-latest.osm.pbf` lives in `C:\work\tmp\speedbeep-osm\` and is
  **never committed**.
- The spike lives in `C:\work\tmp\speedbeep-spike\` and is **never committed**.
- Every commit message ends with the line
  `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.
- Run shell commands with absolute paths. Use `git -C E:/AndroidProj/SpeedBeep …` rather
  than `cd`.

## Review Focus

Each failure mode below has a test in the task named.

1. **Stale or delayed GPS fixes.** For example, a cached fix delivered when the service
   starts, or a fix 4 s old. It must not drive the threshold or the road match. Task 6
   tests `FixEvaluator` with old and future timestamps.
2. **Threshold changed while OVER.** The owner taps "+" from 53 to 60 at 57 km/h.
   Reminders must stop, no new main beep, and re-arming happens at the new T − R.
   Tested in Task 6.
3. **Corrupt or truncated road file**, from a partial copy or a wrong version. It must raise
   `RoadFileFormatException`, never an index error, so the app can fall back to simple
   mode. Tested in Task 3.
4. **Position outside the grid or at its south-west edge** (abroad). It must give
   `NOT_COVERED` without an exception. Tested in Task 4.
5. **A pause ends while GPS is lost** (parked in a garage). The result must be NO_GPS, not
   ARMED with a stale speed. Tested in Task 8.

---

## File structure

```
E:\AndroidProj\SpeedBeep\
  settings.gradle.kts, build.gradle.kts, gradle.properties, gradlew, gradlew.bat
  gradle/libs.versions.toml, gradle/wrapper/*
  .gitignore, README.md
  core/
    build.gradle.kts
    src/main/kotlin/io/github/lrosa2/speedbeep/core/road/
      RoadClass.kt          road class enum (TOWN/FAST) + RoadFileFormatException
      RoadClassifier.kt     OSM tags → RoadClass, maxspeed parsing (§6.2)
      Polyline.kt           one road piece in micro-degrees
      Geo.kt                distances and bearings in metres/degrees
      RoadFileFormat.kt     constants, Grid, Varint (§6.3)
      RoadFileWriter.kt     List<Polyline> → bytes
      RoadFile.kt           bytes → cells → polylines (reader)
      RoadMatcher.kt        RoadMatch, RoadLookup, NoRoadData, RoadMatcher (§4.2, §6.4)
    src/main/kotlin/io/github/lrosa2/speedbeep/core/rules/
      Fix.kt                Fix, Reading
      FixEvaluator.kt       Fix → Reading (§4.1)
      SpeedRules.kt         state machine (§4.3, §4.4) + its value types
    src/test/kotlin/...     one test file per main file
  roadbuilder/
    build.gradle.kts
    src/main/kotlin/io/github/lrosa2/speedbeep/roadbuilder/
      Main.kt               CLI: build | probe
      OsmPbfReader.kt       parallelpbf adapter
      RoadCollector.kt      two-pass way/node collection
      RoadStats.kt          report numbers + formatting
      Probe.kt              nearest roads around a point
    src/test/kotlin/...
  app/src/main/assets/roads-ch.bin    (generated in Task 5, committed)
```

---

### Task 1: Bluetooth auto-start spike (throwaway, on the owner's phone)

This answers the riskiest question in §8: on the owner's Android 15/16 phone, does linking
the car through CompanionDeviceManager start a location foreground service while the app
is closed and the phone locked? It also checks the quick-settings tile route.

The spike logs every step with a timestamp to a file and shows that log on screen. A
missing "Allow all the time" grant must look different from a companion route that doesn't
work.

**This task does not block Tasks 2–8.** After handing the APK to the owner (Step 7),
continue with Task 2 and collect the result whenever the owner reports it.

**Files** (all under `C:\work\tmp\speedbeep-spike\`, never committed):
- Create: `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`
- Copy: `gradlew`, `gradlew.bat`, `gradle/wrapper/*` from `E:\AndroidProj\CarCare`
- Create: `app/build.gradle.kts`, `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/java/io/github/lrosa2/speedbeep/spike/`:
  - `SpikeLog.kt`
  - `SpikeNotifications.kt`
  - `SpikeActivity.kt`
  - `SpikeWatchService.kt`
  - `SpikeCarService.kt`
  - `SpikeTile.kt`

**Interfaces:** none. Nothing here is reused as code. Plan 2 reuses only the *findings*,
which are recorded in Step 9.

- [ ] **Step 1: Create the project skeleton**

```bash
mkdir -p /c/work/tmp/speedbeep-spike/gradle/wrapper /c/work/tmp/speedbeep-spike/app/src/main/java/io/github/lrosa2/speedbeep/spike
cp /e/AndroidProj/CarCare/gradlew /e/AndroidProj/CarCare/gradlew.bat /e/AndroidProj/CarCare/gradle.properties /c/work/tmp/speedbeep-spike/
cp /e/AndroidProj/CarCare/gradle/wrapper/* /c/work/tmp/speedbeep-spike/gradle/wrapper/
```

`C:\work\tmp\speedbeep-spike\settings.gradle.kts`:

```kotlin
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "SpeedBeepSpike"
include(":app")
```

`C:\work\tmp\speedbeep-spike\build.gradle.kts`:

```kotlin
// AGP 9 compiles Kotlin in Android modules itself; the kotlin-jvm entry only pins the
// Kotlin Gradle plugin version, as in the sibling projects.
plugins {
    id("com.android.application") version "9.4.1" apply false
    id("org.jetbrains.kotlin.jvm") version "2.3.21" apply false
}
```

`C:\work\tmp\speedbeep-spike\app\build.gradle.kts`:

```kotlin
plugins {
    id("com.android.application")
}

android {
    namespace = "io.github.lrosa2.speedbeep.spike"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.github.lrosa2.speedbeep.spike"
        minSdk = 34
        targetSdk = 36
        versionCode = 1
        versionName = "spike"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.19.1")
}
```

- [ ] **Step 2: Write the manifest**

`app/src/main/AndroidManifest.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-feature android:name="android.software.companion_device_setup" android:required="true" />

    <uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
    <uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
    <uses-permission android:name="android.permission.ACCESS_BACKGROUND_LOCATION" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_LOCATION" />
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
    <uses-permission android:name="android.permission.REQUEST_COMPANION_START_FOREGROUND_SERVICES_FROM_BACKGROUND" />
    <uses-permission android:name="android.permission.REQUEST_OBSERVE_COMPANION_DEVICE_PRESENCE" />

    <application
        android:icon="@android:drawable/ic_dialog_info"
        android:label="SpeedBeep spike">

        <activity
            android:name=".SpikeActivity"
            android:exported="true"
            android:launchMode="singleTop">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

        <service
            android:name=".SpikeWatchService"
            android:exported="false"
            android:foregroundServiceType="location" />

        <service
            android:name=".SpikeCarService"
            android:exported="true"
            android:permission="android.permission.BIND_COMPANION_DEVICE_SERVICE">
            <intent-filter>
                <action android:name="android.companion.CompanionDeviceService" />
            </intent-filter>
        </service>

        <service
            android:name=".SpikeTile"
            android:exported="true"
            android:icon="@android:drawable/ic_menu_compass"
            android:label="Spike watch"
            android:permission="android.permission.BIND_QUICK_SETTINGS_TILE">
            <intent-filter>
                <action android:name="android.service.quicksettings.action.QS_TILE" />
            </intent-filter>
        </service>
    </application>
</manifest>
```

- [ ] **Step 3: Write the log, notification and state helpers**

`SpikeLog.kt`:

```kotlin
package io.github.lrosa2.speedbeep.spike

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Everything the spike does goes to a file, so the owner can share it after a locked-phone test. */
object SpikeLog {
    private val time = SimpleDateFormat("HH:mm:ss.SSS", Locale.ROOT)

    private fun file(ctx: Context) = File(ctx.filesDir, "spike-log.txt")

    @Synchronized
    fun log(ctx: Context, message: String) {
        Log.i("SpeedBeepSpike", message)
        file(ctx).appendText("${time.format(Date())} $message\n")
    }

    fun read(ctx: Context): String = file(ctx).takeIf { it.exists() }?.readText() ?: "(log is empty)"

    fun clear(ctx: Context) {
        file(ctx).delete()
    }

    fun permissions(ctx: Context): String = listOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_BACKGROUND_LOCATION,
        Manifest.permission.POST_NOTIFICATIONS,
    ).joinToString(" ") { p ->
        val granted = ctx.checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED
        "${p.substringAfterLast('.')}=$granted"
    }
}

/** Whether the watch service is running in this process (spike only, not persisted). */
object SpikeState {
    @Volatile
    var running = false
}
```

`SpikeNotifications.kt`:

```kotlin
package io.github.lrosa2.speedbeep.spike

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context

object SpikeNotifications {
    private const val CHANNEL = "spike"
    const val ID = 1

    fun build(ctx: Context, text: String): Notification {
        val manager = ctx.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, "Spike", NotificationManager.IMPORTANCE_LOW),
        )
        return Notification.Builder(ctx, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentTitle("SpeedBeep spike")
            .setContentText(text)
            .setOngoing(true)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .build()
    }
}
```

- [ ] **Step 4: Write the watch service**

`SpikeWatchService.kt`:

```kotlin
package io.github.lrosa2.speedbeep.spike

import android.app.NotificationManager
import android.app.Service
import android.content.ComponentName
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.location.LocationRequest
import android.os.IBinder
import android.service.quicksettings.TileService
import androidx.core.app.ServiceCompat

/** Location foreground service that only shows the GPS speed. Logs every start attempt. */
class SpikeWatchService : Service(), LocationListener {
    private var fixes = 0
    private val locationManager by lazy { getSystemService(LocationManager::class.java) }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        SpikeLog.log(this, "service onStartCommand; ${SpikeLog.permissions(this)}")
        try {
            ServiceCompat.startForeground(
                this,
                SpikeNotifications.ID,
                SpikeNotifications.build(this, "Spike: waiting for GPS"),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION,
            )
            SpikeLog.log(this, "startForeground OK")
        } catch (e: Exception) {
            SpikeLog.log(this, "startForeground threw ${e.javaClass.name}: ${e.message}")
            stopSelf()
            return START_NOT_STICKY
        }
        try {
            val request = LocationRequest.Builder(1_000L)
                .setMinUpdateIntervalMillis(1_000L)
                .setQuality(LocationRequest.QUALITY_HIGH_ACCURACY)
                .build()
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, request, mainExecutor, this)
            SpikeLog.log(this, "GPS updates requested")
        } catch (e: SecurityException) {
            SpikeLog.log(this, "requestLocationUpdates threw SecurityException: ${e.message}")
        }
        SpikeState.running = true
        refreshTile()
        return START_NOT_STICKY
    }

    override fun onLocationChanged(location: Location) {
        fixes++
        val speed = if (location.hasSpeed()) "%.0f km/h".format(location.speed * 3.6f) else "no speed"
        if (fixes == 1 || fixes % 30 == 0) {
            SpikeLog.log(this, "fix #$fixes $speed accuracy=${location.accuracy} m")
        }
        getSystemService(NotificationManager::class.java)
            .notify(SpikeNotifications.ID, SpikeNotifications.build(this, "Spike: $speed (fix #$fixes)"))
    }

    override fun onDestroy() {
        locationManager.removeUpdates(this)
        SpikeState.running = false
        refreshTile()
        SpikeLog.log(this, "service destroyed after $fixes fixes")
        super.onDestroy()
    }

    private fun refreshTile() {
        TileService.requestListeningState(this, ComponentName(this, SpikeTile::class.java))
    }
}
```

- [ ] **Step 5: Write the companion service and the tile**

`SpikeCarService.kt`:

```kotlin
package io.github.lrosa2.speedbeep.spike

import android.companion.AssociationInfo
import android.companion.CompanionDeviceService
import android.companion.DevicePresenceEvent
import android.content.Intent

/** Bound by Android while the linked car is present (§5.1). Starts/stops the watch service. */
class SpikeCarService : CompanionDeviceService() {

    // API 34–35 deliver these; API 36 delivers onDevicePresenceEvent instead.
    @Deprecated("Replaced by onDevicePresenceEvent on API 36")
    override fun onDeviceAppeared(associationInfo: AssociationInfo) {
        SpikeLog.log(this, "onDeviceAppeared #${associationInfo.id}")
        startWatch()
    }

    @Deprecated("Replaced by onDevicePresenceEvent on API 36")
    override fun onDeviceDisappeared(associationInfo: AssociationInfo) {
        SpikeLog.log(this, "onDeviceDisappeared #${associationInfo.id}")
        stopService(Intent(this, SpikeWatchService::class.java))
    }

    override fun onDevicePresenceEvent(event: DevicePresenceEvent) {
        SpikeLog.log(this, "onDevicePresenceEvent event=${event.event} association=${event.associationId}")
        when (event.event) {
            DevicePresenceEvent.EVENT_BT_CONNECTED, DevicePresenceEvent.EVENT_BLE_APPEARED -> startWatch()
            DevicePresenceEvent.EVENT_BT_DISCONNECTED, DevicePresenceEvent.EVENT_BLE_DISAPPEARED ->
                stopService(Intent(this, SpikeWatchService::class.java))
        }
    }

    private fun startWatch() {
        SpikeLog.log(this, "start requested; ${SpikeLog.permissions(this)}")
        try {
            startForegroundService(Intent(this, SpikeWatchService::class.java))
            SpikeLog.log(this, "startForegroundService returned")
        } catch (e: Exception) {
            SpikeLog.log(this, "startForegroundService threw ${e.javaClass.name}: ${e.message}")
        }
    }
}
```

`SpikeTile.kt`:

```kotlin
package io.github.lrosa2.speedbeep.spike

import android.app.PendingIntent
import android.content.Intent
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/** Quick-settings tile: tries a direct service start, falls back to opening the activity. */
class SpikeTile : TileService() {

    override fun onStartListening() {
        qsTile?.apply {
            state = if (SpikeState.running) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            updateTile()
        }
    }

    override fun onClick() {
        if (SpikeState.running) {
            SpikeLog.log(this, "tile: stop")
            stopService(Intent(this, SpikeWatchService::class.java))
            return
        }
        try {
            startForegroundService(Intent(this, SpikeWatchService::class.java))
            SpikeLog.log(this, "tile: startForegroundService returned")
        } catch (e: Exception) {
            SpikeLog.log(this, "tile: start threw ${e.javaClass.name}: ${e.message}; opening activity")
            val open = Intent(this, SpikeActivity::class.java)
                .putExtra(SpikeActivity.EXTRA_START, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivityAndCollapse(
                PendingIntent.getActivity(this, 0, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT),
            )
        }
    }
}
```

- [ ] **Step 6: Write the activity**

`SpikeActivity.kt`:

```kotlin
package io.github.lrosa2.speedbeep.spike

import android.Manifest
import android.app.Activity
import android.companion.AssociationInfo
import android.companion.AssociationRequest
import android.companion.BluetoothDeviceFilter
import android.companion.CompanionDeviceManager
import android.companion.ObservingDevicePresenceRequest
import android.content.Intent
import android.content.IntentSender
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** Buttons for each setup step, the current permission/link state, and the log. */
class SpikeActivity : Activity() {
    private lateinit var status: TextView
    private lateinit var logView: TextView
    private val companions by lazy { getSystemService(CompanionDeviceManager::class.java) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 64, 32, 32)
        }
        fun button(label: String, action: () -> Unit) {
            root.addView(Button(this).apply {
                text = label
                setOnClickListener { action(); refresh() }
            })
        }
        status = TextView(this).also(root::addView)
        button("1. Location while in use") {
            requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), 1)
        }
        button("2. Location: Allow all the time") {
            requestPermissions(arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION), 2)
        }
        button("3. Notifications") { requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 3) }
        button("4. Link car") { linkCar() }
        button("Start watching (manual)") { startForegroundService(Intent(this, SpikeWatchService::class.java)) }
        button("Stop watching") { stopService(Intent(this, SpikeWatchService::class.java)) }
        button("Share log") { shareLog() }
        button("Clear log") { SpikeLog.clear(this) }
        logView = TextView(this).apply {
            textSize = 11f
            setTextIsSelectable(true)
        }
        root.addView(
            ScrollView(this).apply { addView(logView) },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f),
        )
        setContentView(root)
        handleTileStart(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleTileStart(intent)
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        refresh()
    }

    private fun handleTileStart(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_START, false) == true) {
            SpikeLog.log(this, "activity opened by tile; starting service")
            startForegroundService(Intent(this, SpikeWatchService::class.java))
        }
    }

    private fun refresh() {
        val linked = companions.myAssociations
            .joinToString { "#${it.id} ${it.displayName} ${it.deviceMacAddress}" }
            .ifEmpty { "none" }
        status.text = "Android API ${Build.VERSION.SDK_INT}\n${SpikeLog.permissions(this)}\nLinked: $linked"
        logView.text = SpikeLog.read(this).lines().takeLast(200).joinToString("\n")
    }

    private fun linkCar() {
        val request = AssociationRequest.Builder()
            .addDeviceFilter(BluetoothDeviceFilter.Builder().build())
            .setSingleDevice(false)
            .build()
        companions.associate(request, mainExecutor, object : CompanionDeviceManager.Callback() {
            override fun onAssociationPending(intentSender: IntentSender) {
                SpikeLog.log(this@SpikeActivity, "association pending; showing Android's device list")
                startIntentSenderForResult(intentSender, REQUEST_ASSOCIATE, null, 0, 0, 0)
            }

            override fun onAssociationCreated(associationInfo: AssociationInfo) {
                SpikeLog.log(
                    this@SpikeActivity,
                    "linked #${associationInfo.id} ${associationInfo.displayName} ${associationInfo.deviceMacAddress}",
                )
                observe(associationInfo)
                refresh()
            }

            override fun onFailure(error: CharSequence?) {
                SpikeLog.log(this@SpikeActivity, "link failed: $error")
                refresh()
            }
        })
    }

    private fun observe(info: AssociationInfo) {
        try {
            if (Build.VERSION.SDK_INT >= 36) {
                companions.startObservingDevicePresence(
                    ObservingDevicePresenceRequest.Builder().setAssociationId(info.id).build(),
                )
            } else {
                val address = info.deviceMacAddress?.toString()?.uppercase()
                    ?: error("association has no MAC address")
                @Suppress("DEPRECATION")
                companions.startObservingDevicePresence(address)
            }
            SpikeLog.log(this, "observing presence of #${info.id}")
        } catch (e: Exception) {
            SpikeLog.log(this, "observe failed: ${e.javaClass.name}: ${e.message}")
        }
    }

    private fun shareLog() {
        val send = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_TEXT, SpikeLog.read(this))
        startActivity(Intent.createChooser(send, "Share spike log"))
    }

    companion object {
        const val EXTRA_START = "start"
        private const val REQUEST_ASSOCIATE = 7
    }
}
```

- [ ] **Step 7: Build it and check it on the emulator**

```bash
cd /c/work/tmp/speedbeep-spike && ./gradlew.bat assembleDebug
```
Expected: `BUILD SUCCESSFUL`, and the APK at
`C:\work\tmp\speedbeep-spike\app\build\outputs\apk\debug\app-debug.apk`.

If the compiler reports a signature mismatch on an Android call, check it with
`javap -cp C:/Android/sdk/platforms/android-36/android.jar <class>` and fix the call. Don't
guess.

Smoke test on the emulator (AVD `Pixel_7_API_34`, see the device-testing memory). Start it
headless, then:

```bash
adb install -r /c/work/tmp/speedbeep-spike/app/build/outputs/apk/debug/app-debug.apk
adb shell pm grant io.github.lrosa2.speedbeep.spike android.permission.ACCESS_FINE_LOCATION
adb shell pm grant io.github.lrosa2.speedbeep.spike android.permission.POST_NOTIFICATIONS
adb shell am start -n io.github.lrosa2.speedbeep.spike/.SpikeActivity
adb emu geo fix 8.5417 47.3769
adb exec-out uiautomator dump /dev/tty | grep -o 'Start watching (manual)[^"]*' | head -1
```
Tap "Start watching (manual)" using the bounds from the dump (`adb shell input tap X Y`).
Then confirm that `adb shell dumpsys notification --noredact | grep -i "SpeedBeep spike"`
shows the notification. The car and tile routes cannot be tested on the emulator. Stop the
emulator afterwards with `adb emu kill`.

- [ ] **Step 8: Hand the APK to the owner with the test protocol**

Send `C:\work\tmp\speedbeep-spike\app\build\outputs\apk\debug\app-debug.apk` with
SendUserFile, and include this protocol in the message:

1. On the phone, open the APK and allow installing from that source when asked.
   Alternatively, with USB debugging:
   `adb install -r app-debug.apk`.
2. Open **SpeedBeep spike** and tap, in this order:
   - **1** (allow "While using the app")
   - **2** (choose **"Allow all the time"** on the page that opens)
   - **3**
3. With the car's Bluetooth on and connected, tap **4. Link car** and pick the car in
   Android's list.
   - If the car is **not in the list**, note that. It is a key result.
4. **Manual test:** tap *Start watching*. A notification "Spike: … km/h" appears. Tap
   *Stop watching*.
5. **Tile test:**
   - Add the **Spike watch** tile to quick settings.
   - Swipe the app away from recents.
   - Tap the tile: the notification should appear. Tap it again: it should go away.
6. **Car test:**
   - Swipe the app away and lock the phone.
   - Turn the car off and on again, or switch the phone's Bluetooth off and on.
   - Within about 10 s the notification should appear and show the speed while driving.
   - Turn the car off: the notification should go away.
7. Open the app, tap **Share log**, and send the text back.

Optional pre-check before going to the car: any paired Bluetooth headset in place of the
car for steps 3 and 6. **Only the car result decides.**

- [ ] **Step 9: Record the result (when the owner reports back)**

From the shared log, decide which case applies and write it into the spec as a new
decisions-log row (`D14`):

| Log shows | Meaning | Plan 2 uses |
|---|---|---|
| `onDevicePresenceEvent event=2` (or `onDeviceAppeared`) → `startForeground OK` → fixes | Companion route works | Companion route (§5.1) |
| Presence event arrives, `startForeground threw SecurityException` | Missing "Allow all the time" grant, or while-in-use restriction | Fix the grant and retest. Not a reason for the fallback. |
| No presence event at all, but the car is linked and connected | Presence does not fire for this head unit | §5.1 fallback notification |
| Car not in Android's device list | Companion association impossible for this car | §5.1 fallback notification, with the car picked from bonded devices |
| `tile: start threw …` or service `startForeground threw` after a tile tap | Tile can't start the service directly | Tile opens the app (already built into the spike's code) |

Commit the spec change:
```bash
git -C E:/AndroidProj/SpeedBeep commit -am "Spec: record Bluetooth auto-start spike result (D14)

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---
