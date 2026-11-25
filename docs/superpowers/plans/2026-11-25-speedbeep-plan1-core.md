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
- Tests use JUnit 6 through `junit-bom` 6.1.3 and `junit-jupiter`. Run Gradle from Git
  Bash as `/e/AndroidProj/SpeedBeep/gradlew -p /e/AndroidProj/SpeedBeep <task>`. This plan
  writes that as `GW <task>`.
- `parallelpbf` is GPLv3. It is allowed **only** in `:roadbuilder`, a private PC tool that
  is never distributed, and must **never** become a dependency of `:core` or the app.
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
   Tested in Task 7.
3. **Corrupt or truncated road file**, from a partial copy or a wrong version. It must raise
   `RoadFileFormatException`, never an index error, so the app can fall back to simple
   mode. Tested in Task 3.
4. **Position outside the grid or at its south-west edge** (abroad). It must give
   `NOT_COVERED` without an exception. Tested in Task 4.
5. **A pause ends while GPS is lost** (parked in a garage). The result must be NO_GPS, not
   ARMED with a stale speed. Tested in Task 7.

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
      RoadClass.kt          road class enum (TOWN/FAST)
      RoadClassifier.kt     OSM tags → RoadClass, maxspeed parsing (§6.2)
      Polyline.kt           one road piece in micro-degrees
      Geo.kt                distances and bearings in metres/degrees
      RoadFileFormat.kt     RoadFileFormatException, constants, Grid, Varint (§6.3)
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

### Task 2: Project scaffold and road classifier

Sets up the repository and the `:core` module. Its first deliverable is the classifier that
turns OSM tags into FAST/TOWN (§6.2).

**Files:**
- Copy: `gradlew`, `gradlew.bat`, `gradle.properties`, `gradle/wrapper/*` from `E:\AndroidProj\CarCare`
- Create: `settings.gradle.kts`, `build.gradle.kts`, `gradle/libs.versions.toml`, `.gitignore`, `README.md`
- Create: `core/build.gradle.kts`
- Create: `core/src/main/kotlin/io/github/lrosa2/speedbeep/core/road/RoadClass.kt`
- Create: `core/src/main/kotlin/io/github/lrosa2/speedbeep/core/road/RoadClassifier.kt`
- Create: `core/src/main/kotlin/io/github/lrosa2/speedbeep/core/road/RoadFileFormat.kt` (only the exception for now; Task 3 completes it)
- Test: `core/src/test/kotlin/io/github/lrosa2/speedbeep/core/road/RoadClassifierTest.kt`

**Interfaces:**
- Produces:
  - `enum class RoadClass(val code: Int) { TOWN(0), FAST(1) }` with
    `RoadClass.fromCode(code: Int): RoadClass`, which throws `RoadFileFormatException`
  - `class RoadFileFormatException(message: String) : RuntimeException`
  - `object RoadClassifier` with:
    - `DRIVABLE: Set<String>`
    - `parseMaxspeed(raw: String?): Int?`
    - `effectiveLimit(tags: Map<String, String>): Int?`
    - `isDrivable(tags: Map<String, String>): Boolean`
    - `classify(tags: Map<String, String>): RoadClass?` (null = leave the way out)

- [ ] **Step 1: Copy the wrapper and write the build files**

```bash
mkdir -p /e/AndroidProj/SpeedBeep/gradle/wrapper
cp /e/AndroidProj/CarCare/gradlew /e/AndroidProj/CarCare/gradlew.bat /e/AndroidProj/CarCare/gradle.properties /e/AndroidProj/SpeedBeep/
cp /e/AndroidProj/CarCare/gradle/wrapper/* /e/AndroidProj/SpeedBeep/gradle/wrapper/
```

`settings.gradle.kts`:

```kotlin
pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
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

rootProject.name = "SpeedBeep"

include(":core")
```

`build.gradle.kts`:

```kotlin
// Plugins are declared once here and applied per module. Plan 2 adds the Android plugins.
plugins {
    alias(libs.plugins.kotlin.jvm) apply false
}
```

`gradle/libs.versions.toml`:

```toml
[versions]
# Toolchain matches the sibling projects (CarCare, Singletrail, HomeBasket).
kotlin = "2.3.21"
junit = "6.1.3"
# OSM .osm.pbf reader, used only by :roadbuilder (GPLv3, never shipped in the app).
parallelpbf = "0.3.1"
# parallelpbf logs through slf4j 1.7; without a binding its parse errors are lost.
slf4j = "1.7.36"

[libraries]
junit-bom = { group = "org.junit", name = "junit-bom", version.ref = "junit" }
junit-jupiter = { group = "org.junit.jupiter", name = "junit-jupiter" }
junit-platform-launcher = { group = "org.junit.platform", name = "junit-platform-launcher" }
parallelpbf = { group = "com.wolt.osm", name = "parallelpbf", version.ref = "parallelpbf" }
slf4j-simple = { group = "org.slf4j", name = "slf4j-simple", version.ref = "slf4j" }

[plugins]
kotlin-jvm = { id = "org.jetbrains.kotlin.jvm", version.ref = "kotlin" }
```

`.gitignore`:

```gitignore
# Gradle / build output
.gradle/
.kotlin/
build/
/captures

# Local machine config
/local.properties

# Android Studio / IntelliJ
*.iml
/.idea/
.DS_Store

# Signing material: never commit
keystore.properties
*.jks
*.keystore

# OSM extracts belong in C:\work\tmp\speedbeep-osm, never in the repo
*.osm.pbf
```

`README.md`:

```markdown
# SpeedBeep

Map-less Android app that beeps when you drive over a set speed (default 53 km/h),
except on motorways and roads posted above 50. Design: `docs/superpowers/specs/2026-11-25-speedbeep-design.md`.

Modules:
- `:core`: road file format, road matcher, beep rules (plain Kotlin, unit-tested)
- `:roadbuilder`: PC tool that builds `app/src/main/assets/roads-ch.bin` from the Geofabrik Switzerland extract

Road data © OpenStreetMap contributors, ODbL.
```

`core/build.gradle.kts`:

```kotlin
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
}

// Java 17 bytecode so the Android app (Plan 2) can consume this module.
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

dependencies {
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
}
```

- [ ] **Step 2: Write the failing classifier test**

`core/src/test/kotlin/io/github/lrosa2/speedbeep/core/road/RoadClassifierTest.kt`:

```kotlin
package io.github.lrosa2.speedbeep.core.road

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource

class RoadClassifierTest {

    private fun tags(vararg pairs: Pair<String, String>) = mapOf(*pairs)

    @ParameterizedTest(name = "maxspeed \"{0}\" is {1} km/h")
    @CsvSource(
        "50, 50",
        "'80 km/h', 80",
        "CH:urban, 50",
        "ch:rural, 80",
        "CH:trunk, 100",
        "CH:motorway, 120",
        "CH:zone30, 30",
        "CH:30, 30",
        "CH:zone20, 20",
        "walk, 20",
        "'30 mph', 48",
        "'60;80', 60",
    )
    fun `parses usable limits`(raw: String, expected: Int) {
        assertEquals(expected, RoadClassifier.parseMaxspeed(raw))
    }

    @ParameterizedTest(name = "maxspeed \"{0}\" has no usable limit")
    @ValueSource(strings = ["none", "signals", "variable", "fast", "", "  "])
    fun `values without a usable limit give null`(raw: String) {
        assertNull(RoadClassifier.parseMaxspeed(raw))
    }

    @Test
    fun `missing value gives null`() {
        assertNull(RoadClassifier.parseMaxspeed(null))
    }

    @Test
    fun `motorway and trunk without a limit are fast`() {
        for (highway in listOf("motorway", "motorway_link", "trunk", "trunk_link")) {
            assertEquals(RoadClass.FAST, RoadClassifier.classify(tags("highway" to highway)), highway)
        }
    }

    @Test
    fun `an explicit limit wins over the road type`() {
        assertEquals(RoadClass.TOWN, RoadClassifier.classify(tags("highway" to "trunk", "maxspeed" to "50")))
        assertEquals(RoadClass.FAST, RoadClassifier.classify(tags("highway" to "residential", "maxspeed" to "60")))
        assertEquals(RoadClass.FAST, RoadClassifier.classify(tags("highway" to "primary", "maxspeed" to "80")))
    }

    @Test
    fun `other roads without any speed information are town (known gap)`() {
        for (highway in listOf("primary", "secondary", "tertiary", "unclassified", "residential", "living_street", "road")) {
            assertEquals(RoadClass.TOWN, RoadClassifier.classify(tags("highway" to highway)), highway)
        }
    }

    @Test
    fun `fallback tags give the limit when maxspeed is missing`() {
        assertEquals(RoadClass.FAST, RoadClassifier.classify(tags("highway" to "secondary", "source:maxspeed" to "CH:rural")))
        assertEquals(RoadClass.FAST, RoadClassifier.classify(tags("highway" to "secondary", "maxspeed:type" to "CH:rural")))
        assertEquals(RoadClass.TOWN, RoadClassifier.classify(tags("highway" to "residential", "zone:maxspeed" to "CH:30")))
    }

    @Test
    fun `maxspeed wins over the type tag`() {
        assertEquals(50, RoadClassifier.effectiveLimit(tags("maxspeed" to "50", "maxspeed:type" to "CH:rural")))
    }

    @Test
    fun `the lower of the forward and backward limits wins`() {
        assertEquals(50, RoadClassifier.effectiveLimit(tags("maxspeed:forward" to "80", "maxspeed:backward" to "50")))
        assertEquals(80, RoadClassifier.effectiveLimit(tags("maxspeed:forward" to "80")))
        assertEquals(
            RoadClass.TOWN,
            RoadClassifier.classify(tags("highway" to "primary", "maxspeed:forward" to "80", "maxspeed:backward" to "50")),
        )
    }

    @Test
    fun `a variable motorway limit counts as no limit, so the motorway stays fast`() {
        assertEquals(RoadClass.FAST, RoadClassifier.classify(tags("highway" to "motorway", "maxspeed" to "signals")))
    }

    @Test
    fun `non-drivable ways are left out`() {
        for (highway in listOf("service", "track", "path", "footway", "cycleway", "pedestrian", "construction")) {
            assertNull(RoadClassifier.classify(tags("highway" to highway)), highway)
        }
        assertNull(RoadClassifier.classify(tags("name" to "no highway tag")))
    }

    @Test
    fun `areas and closed roads are left out unless cars are allowed`() {
        assertNull(RoadClassifier.classify(tags("highway" to "residential", "area" to "yes")))
        assertNull(RoadClassifier.classify(tags("highway" to "residential", "access" to "no")))
        assertNull(RoadClassifier.classify(tags("highway" to "residential", "motor_vehicle" to "no")))
        assertEquals(
            RoadClass.TOWN,
            RoadClassifier.classify(tags("highway" to "residential", "access" to "no", "motorcar" to "yes")),
        )
    }
}
```

- [ ] **Step 3: Run it to verify it fails**

Run: `GW :core:test`
Expected: compilation FAILS with `Unresolved reference 'RoadClassifier'` and `'RoadClass'`.

- [ ] **Step 4: Write the classifier**

`core/src/main/kotlin/io/github/lrosa2/speedbeep/core/road/RoadFileFormat.kt` (first part; Task 3 replaces this file with the full version):

```kotlin
package io.github.lrosa2.speedbeep.core.road

/** The road file is unreadable: wrong magic or version, truncated, or inconsistent. */
class RoadFileFormatException(message: String) : RuntimeException(message)
```

`core/src/main/kotlin/io/github/lrosa2/speedbeep/core/road/RoadClass.kt`:

```kotlin
package io.github.lrosa2.speedbeep.core.road

/** Class of a road as stored in the road file (spec §6.2). */
enum class RoadClass(val code: Int) {
    /** Posted 50 or less, or no speed information: the threshold applies. */
    TOWN(0),

    /** Motorway, Autostrasse, or posted above 50: silent once confirmed. */
    FAST(1);

    companion object {
        fun fromCode(code: Int): RoadClass =
            entries.firstOrNull { it.code == code } ?: throw RoadFileFormatException("unknown road class code $code")
    }
}
```

`core/src/main/kotlin/io/github/lrosa2/speedbeep/core/road/RoadClassifier.kt`:

```kotlin
package io.github.lrosa2.speedbeep.core.road

/** Turns OSM way tags into a [RoadClass] (spec §6.2). Used by the road-file builder. */
object RoadClassifier {

    val DRIVABLE: Set<String> = setOf(
        "motorway", "trunk", "primary", "secondary", "tertiary", "unclassified",
        "residential", "living_street", "road",
        "motorway_link", "trunk_link", "primary_link", "secondary_link", "tertiary_link",
    )

    private val FAST_WITHOUT_LIMIT = setOf("motorway", "motorway_link", "trunk", "trunk_link")

    /** Swiss implicit values (lower-case). "none", "signals" and "variable" are deliberately absent: no usable limit. */
    private val NAMED_LIMITS: Map<String, Int> = mapOf(
        "ch:urban" to 50,
        "ch:rural" to 80,
        "ch:trunk" to 100,
        "ch:motorway" to 120,
        "ch:zone30" to 30,
        "ch:30" to 30,
        "ch:zone20" to 20,
        "walk" to 20,
    )

    private val KMH = Regex("""^(\d{1,3})\s*(km/h|kmh|kph)?$""")
    private val MPH = Regex("""^(\d{1,3})\s*mph$""")

    /** One maxspeed-like value in km/h, or null for "no usable limit". Several values separated by ';' give the lowest. */
    fun parseMaxspeed(raw: String?): Int? {
        if (raw.isNullOrBlank()) return null
        return raw.split(';')
            .map { it.trim().lowercase() }
            .filter { it.isNotEmpty() }
            .mapNotNull(::parseSingle)
            .minOrNull()
    }

    private fun parseSingle(value: String): Int? {
        KMH.matchEntire(value)?.let { return it.groupValues[1].toInt() }
        MPH.matchEntire(value)?.let { return Math.round(it.groupValues[1].toInt() * 1.609344).toInt() }
        return NAMED_LIMITS[value]
    }

    /** Effective limit in km/h, or null when the tags give none. The order follows spec §6.2. */
    fun effectiveLimit(tags: Map<String, String>): Int? {
        parseMaxspeed(tags["maxspeed"])?.let { return it }
        listOfNotNull(parseMaxspeed(tags["maxspeed:forward"]), parseMaxspeed(tags["maxspeed:backward"]))
            .minOrNull()
            ?.let { return it }
        for (key in listOf("maxspeed:type", "zone:maxspeed", "source:maxspeed")) {
            parseMaxspeed(tags[key])?.let { return it }
        }
        return null
    }

    fun isDrivable(tags: Map<String, String>): Boolean {
        if (tags["highway"] !in DRIVABLE) return false
        if (tags["area"] == "yes") return false
        val closed = tags["access"] == "no" || tags["motor_vehicle"] == "no"
        return !closed || tags["motorcar"] == "yes"
    }

    /** The class of a drivable way, or null when the way does not belong in the road file. */
    fun classify(tags: Map<String, String>): RoadClass? {
        if (!isDrivable(tags)) return null
        val limit = effectiveLimit(tags)
        return when {
            limit != null && limit > 50 -> RoadClass.FAST
            limit == null && tags["highway"] in FAST_WITHOUT_LIMIT -> RoadClass.FAST
            else -> RoadClass.TOWN
        }
    }
}
```

- [ ] **Step 5: Run the tests to verify they pass**

Run: `GW :core:test`
Expected: `BUILD SUCCESSFUL`, with all `RoadClassifierTest` tests passing.

- [ ] **Step 6: Commit**

```bash
git -C E:/AndroidProj/SpeedBeep add -A
git -C E:/AndroidProj/SpeedBeep commit -m "Scaffold :core and add the OSM road classifier

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 3: Road file format (writer and reader)

Implements §6.3: the grid, varint encoding, writing polylines into cells, and reading them
back with full validation.

**Files:**
- Create: `core/src/main/kotlin/io/github/lrosa2/speedbeep/core/road/Polyline.kt`
- Create: `core/src/main/kotlin/io/github/lrosa2/speedbeep/core/road/Geo.kt`
- Modify: `core/src/main/kotlin/io/github/lrosa2/speedbeep/core/road/RoadFileFormat.kt` (replace with the full version)
- Create: `core/src/main/kotlin/io/github/lrosa2/speedbeep/core/road/RoadFileWriter.kt`
- Create: `core/src/main/kotlin/io/github/lrosa2/speedbeep/core/road/RoadFile.kt`
- Test: `core/src/test/kotlin/io/github/lrosa2/speedbeep/core/road/GeoTest.kt`
- Test: `core/src/test/kotlin/io/github/lrosa2/speedbeep/core/road/VarintTest.kt`
- Test: `core/src/test/kotlin/io/github/lrosa2/speedbeep/core/road/RoadFileTest.kt`

**Interfaces:**
- Consumes: `RoadClass`, `RoadFileFormatException` (Task 2).
- Produces:
  - `class Polyline(roadClass: RoadClass, latE6: IntArray, lonE6: IntArray)`
    - `size: Int`
    - `slice(from: Int, toExclusive: Int): Polyline`
    - value equality
  - `object Geo`:
    - `M_PER_DEG = 111_320.0`
    - `distanceM(aLat, aLon, bLat, bLon): Double`
    - `distanceToSegmentM(pLat, pLon, aLat, aLon, bLat, bLon): Double`
    - `bearingDeg(aLat, aLon, bLat, bLon): Double` (0 = north, clockwise, in [0, 360))
    - `lineAngleDiff(aDeg, bDeg): Double` (in [0, 90])
  - `object RoadFileFormat`: `MAGIC`, `VERSION`, `HEADER_SIZE = 48`,
    `INDEX_ENTRY_SIZE = 12`, `DEFAULT_CELL_SIZE_E6 = 10_000`
  - `class Grid(originLatE6, originLonE6, cellSizeE6, width, height)`:
    - `row(latE6)`, `col(lonE6)`
    - `contains(row, col)`
    - `cellId(row, col)`
    - `cellOriginLatE6(id)`, `cellOriginLonE6(id)`
  - `internal object Varint`: `zigzag`, `unzigzag`, `write(ByteArrayOutputStream, Int)`, `read(ByteBuffer): Int`
  - `RoadFileWriter.write(roads: List<Polyline>, dataTimestampSec: Long, buildTimestampSec: Long, out: OutputStream, cellSizeE6: Int = 10_000)`
  - `RoadFile.parse(bytes: ByteBuffer): RoadFile`, which throws `RoadFileFormatException`.
    A `RoadFile` exposes:
    - `dataTimestampSec`, `buildTimestampSec`
    - `grid`
    - `cellCount`
    - `hasCell(id)`
    - `cellsAround(lat: Double, lon: Double, radiusM: Double): IntArray` (empty = not covered)
    - `polylines(cellId: Int): List<Polyline>`

- [ ] **Step 1: Write the failing tests**

`core/src/test/kotlin/io/github/lrosa2/speedbeep/core/road/GeoTest.kt`:

```kotlin
package io.github.lrosa2.speedbeep.core.road

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class GeoTest {
    private val lat = 47.37
    private val lon = 8.54

    @Test
    fun `a thousandth of a degree of latitude is about 111 m`() {
        assertEquals(111.32, Geo.distanceM(lat, lon, lat + 0.001, lon), 0.01)
    }

    @Test
    fun `distance to a segment is perpendicular inside it and to the nearest end outside it`() {
        val east = 0.002 // a segment running east from (lat, lon)
        val tenMetresNorth = 10.0 / Geo.M_PER_DEG
        assertEquals(10.0, Geo.distanceToSegmentM(lat + tenMetresNorth, lon + east / 2, lat, lon, lat, lon + east), 0.01)
        val beyondEnd = Geo.distanceM(lat, lon + east * 2, lat, lon + east)
        assertEquals(beyondEnd, Geo.distanceToSegmentM(lat, lon + east * 2, lat, lon, lat, lon + east), 0.01)
    }

    @Test
    fun `a zero-length segment is a point`() {
        assertEquals(
            Geo.distanceM(lat + 0.0001, lon, lat, lon),
            Geo.distanceToSegmentM(lat + 0.0001, lon, lat, lon, lat, lon),
            0.001,
        )
    }

    @Test
    fun `bearings run clockwise from north`() {
        assertEquals(0.0, Geo.bearingDeg(lat, lon, lat + 0.01, lon), 0.01)
        assertEquals(90.0, Geo.bearingDeg(lat, lon, lat, lon + 0.01), 0.01)
        assertEquals(180.0, Geo.bearingDeg(lat, lon, lat - 0.01, lon), 0.01)
        assertEquals(270.0, Geo.bearingDeg(lat, lon, lat, lon - 0.01), 0.01)
    }

    @Test
    fun `line angles ignore direction`() {
        assertEquals(20.0, Geo.lineAngleDiff(10.0, 350.0), 1e-9)
        assertEquals(0.0, Geo.lineAngleDiff(0.0, 180.0), 1e-9)
        assertEquals(90.0, Geo.lineAngleDiff(90.0, 0.0), 1e-9)
        assertEquals(20.0, Geo.lineAngleDiff(170.0, 10.0), 1e-9)
    }
}
```

`core/src/test/kotlin/io/github/lrosa2/speedbeep/core/road/VarintTest.kt`:

```kotlin
package io.github.lrosa2.speedbeep.core.road

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer

class VarintTest {

    @Test
    fun `signed values survive zigzag varint round trips`() {
        val values = intArrayOf(0, 1, -1, 63, -64, 64, 127, 128, 300, -300, Int.MAX_VALUE, Int.MIN_VALUE)
        val out = ByteArrayOutputStream()
        values.forEach { Varint.write(out, Varint.zigzag(it)) }
        val buf = ByteBuffer.wrap(out.toByteArray())
        values.forEach { assertEquals(it, Varint.unzigzag(Varint.read(buf))) }
        assertEquals(0, buf.remaining())
    }

    @Test
    fun `a truncated varint is a format error`() {
        val out = ByteArrayOutputStream()
        Varint.write(out, 300) // two bytes
        val firstByteOnly = ByteBuffer.wrap(out.toByteArray(), 0, 1)
        assertThrows<RoadFileFormatException> { Varint.read(firstByteOnly) }
    }

    @Test
    fun `an over-long varint is a format error`() {
        val sixContinuationBytes = ByteBuffer.wrap(ByteArray(6) { 0x80.toByte() })
        assertThrows<RoadFileFormatException> { Varint.read(sixContinuationBytes) }
    }
}
```

`core/src/test/kotlin/io/github/lrosa2/speedbeep/core/road/RoadFileTest.kt`:

```kotlin
package io.github.lrosa2.speedbeep.core.road

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer

class RoadFileTest {

    // Town road running east along 47.375 N, crossing the cell boundary at 8.540 E.
    private val town = Polyline(
        RoadClass.TOWN,
        intArrayOf(47_375_000, 47_375_000, 47_375_000, 47_375_000),
        intArrayOf(8_535_000, 8_538_000, 8_541_000, 8_545_000),
    )

    // Motorway running north along 8.5505 E, crossing the boundaries at 47.37 and 47.38 N.
    private val motorway = Polyline(
        RoadClass.FAST,
        intArrayOf(47_360_000, 47_372_000, 47_381_000),
        intArrayOf(8_550_500, 8_550_500, 8_550_500),
    )

    private fun bytesOf(vararg roads: Polyline): ByteArray {
        val out = ByteArrayOutputStream()
        RoadFileWriter.write(roads.toList(), dataTimestampSec = 1_764_000_000, buildTimestampSec = 1_764_100_000, out = out)
        return out.toByteArray()
    }

    private fun parse(bytes: ByteArray) = RoadFile.parse(ByteBuffer.wrap(bytes))

    @Test
    fun `header round-trips`() {
        val file = parse(bytesOf(town, motorway))
        assertEquals(1_764_000_000, file.dataTimestampSec)
        assertEquals(1_764_100_000, file.buildTimestampSec)
        assertEquals(47_360_000, file.grid.originLatE6)
        assertEquals(8_530_000, file.grid.originLonE6)
        assertEquals(3, file.grid.width)
        assertEquals(3, file.grid.height)
        assertEquals(5, file.cellCount)
    }

    @Test
    fun `roads are cut into one piece per cell with every touching segment`() {
        val file = parse(bytesOf(town, motorway))
        // Cell ids are row * 3 + col. Town road: row 1, cols 0 and 1. Motorway: col 2, rows 0..2.
        assertEquals(listOf(town.slice(0, 3)), file.polylines(3))
        assertEquals(listOf(town.slice(1, 4)), file.polylines(4))
        assertEquals(listOf(motorway.slice(0, 2)), file.polylines(2))
        assertEquals(listOf(motorway.slice(0, 3)), file.polylines(5))
        assertEquals(listOf(motorway.slice(1, 3)), file.polylines(8))
        assertEquals(emptyList<Polyline>(), file.polylines(0))
    }

    @Test
    fun `cellsAround finds only existing cells in the search box`() {
        val file = parse(bytesOf(town, motorway))
        assertArrayEquals(intArrayOf(3), file.cellsAround(47.375, 8.5395, 30.0))
        assertArrayEquals(intArrayOf(3, 4), file.cellsAround(47.375, 8.5399, 30.0))
    }

    @Test
    fun `positions outside or at the south-west edge of the grid are not covered`() {
        val file = parse(bytesOf(town, motorway))
        assertArrayEquals(intArrayOf(), file.cellsAround(46.0, 7.0, 30.0))
        assertArrayEquals(intArrayOf(), file.cellsAround(47.3599, 8.5299, 30.0))
        assertArrayEquals(intArrayOf(), file.cellsAround(89.99, 179.9, 30.0))
    }

    @Test
    fun `writing nothing is refused`() {
        assertThrows<IllegalArgumentException> { RoadFileWriter.write(emptyList(), 0, 0, ByteArrayOutputStream()) }
    }

    @Test
    fun `a wrong magic is refused`() {
        val bytes = bytesOf(town).also { it[0] = 0 }
        assertThrows<RoadFileFormatException> { parse(bytes) }
    }

    @Test
    fun `another version is refused`() {
        val bytes = bytesOf(town).also { it[4] = 2 }
        assertThrows<RoadFileFormatException> { parse(bytes) }
    }

    @Test
    fun `truncated files are refused, never an index error`() {
        val bytes = bytesOf(town, motorway)
        for (length in listOf(0, 10, RoadFileFormat.HEADER_SIZE - 1, RoadFileFormat.HEADER_SIZE + 5, bytes.size - 1)) {
            assertThrows<RoadFileFormatException>("length $length") { parse(bytes.copyOf(length)) }
        }
    }

    @Test
    fun `a corrupt cell payload is a format error when the cell is read`() {
        val bytes = bytesOf(town, motorway)
        val firstPayload = RoadFileFormat.HEADER_SIZE + 5 * RoadFileFormat.INDEX_ENTRY_SIZE
        bytes[firstPayload] = 0x7F // claims 127 polylines in a cell of a few bytes
        val file = parse(bytes)
        assertThrows<RoadFileFormatException> { file.polylines(2) }
    }
}
```

- [ ] **Step 2: Run them to verify they fail**

Run: `GW :core:test`
Expected: compilation FAILS with unresolved `Polyline`, `Geo`, `Varint`, `RoadFileWriter`,
`RoadFile` and `RoadFileFormat`.

- [ ] **Step 3: Write `Polyline` and `Geo`**

`core/src/main/kotlin/io/github/lrosa2/speedbeep/core/road/Polyline.kt`:

```kotlin
package io.github.lrosa2.speedbeep.core.road

/** One road, or one piece of a road inside a grid cell. Coordinates in micro-degrees (1e-6°). */
class Polyline(val roadClass: RoadClass, val latE6: IntArray, val lonE6: IntArray) {
    init {
        require(latE6.size == lonE6.size) { "latitude and longitude counts differ" }
        require(latE6.size >= 2) { "a polyline needs at least 2 points" }
    }

    val size: Int get() = latE6.size

    /** Points [from, toExclusive) as a new polyline of the same class. */
    fun slice(from: Int, toExclusive: Int) =
        Polyline(roadClass, latE6.copyOfRange(from, toExclusive), lonE6.copyOfRange(from, toExclusive))

    override fun equals(other: Any?) =
        other is Polyline && other.roadClass == roadClass &&
            other.latE6.contentEquals(latE6) && other.lonE6.contentEquals(lonE6)

    override fun hashCode() = 31 * (31 * roadClass.hashCode() + latE6.contentHashCode()) + lonE6.contentHashCode()

    override fun toString() = "Polyline($roadClass, ${(0 until size).joinToString { "${latE6[it]}/${lonE6[it]}" }})"
}
```

`core/src/main/kotlin/io/github/lrosa2/speedbeep/core/road/Geo.kt`:

```kotlin
package io.github.lrosa2.speedbeep.core.road

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min

/**
 * Small-distance geometry in metres and degrees. A local flat projection is exact enough
 * for the tens of metres the matcher works with.
 */
object Geo {
    /** Metres per degree of latitude (and of longitude at the equator). */
    const val M_PER_DEG = 111_320.0

    fun distanceM(aLat: Double, aLon: Double, bLat: Double, bLon: Double): Double {
        val metresPerDegLon = cos(Math.toRadians((aLat + bLat) / 2)) * M_PER_DEG
        return hypot((bLon - aLon) * metresPerDegLon, (bLat - aLat) * M_PER_DEG)
    }

    /** Distance from P to segment AB in metres, in a flat projection centred on P. */
    fun distanceToSegmentM(
        pLat: Double, pLon: Double,
        aLat: Double, aLon: Double,
        bLat: Double, bLon: Double,
    ): Double {
        val metresPerDegLon = cos(Math.toRadians(pLat)) * M_PER_DEG
        val ax = (aLon - pLon) * metresPerDegLon
        val ay = (aLat - pLat) * M_PER_DEG
        val bx = (bLon - pLon) * metresPerDegLon
        val by = (bLat - pLat) * M_PER_DEG
        val dx = bx - ax
        val dy = by - ay
        val lengthSquared = dx * dx + dy * dy
        val t = if (lengthSquared == 0.0) 0.0 else ((-ax) * dx + (-ay) * dy) / lengthSquared
        val clamped = t.coerceIn(0.0, 1.0)
        return hypot(ax + clamped * dx, ay + clamped * dy)
    }

    /** Direction from A to B: 0 = north, 90 = east, in [0, 360). */
    fun bearingDeg(aLat: Double, aLon: Double, bLat: Double, bLon: Double): Double {
        val dx = (bLon - aLon) * cos(Math.toRadians((aLat + bLat) / 2))
        val dy = bLat - aLat
        return (Math.toDegrees(atan2(dx, dy)) + 360.0) % 360.0
    }

    /** Smallest angle between two undirected lines, in [0, 90]. */
    fun lineAngleDiff(aDeg: Double, bDeg: Double): Double {
        val d = abs(aDeg - bDeg) % 180.0
        return min(d, 180.0 - d)
    }
}
```

- [ ] **Step 4: Complete `RoadFileFormat.kt`**

Replace the whole file with:

```kotlin
package io.github.lrosa2.speedbeep.core.road

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer

/** The road file is unreadable: wrong magic or version, truncated, or inconsistent. */
class RoadFileFormatException(message: String) : RuntimeException(message)

/**
 * Road file version 1 (spec §6.3), little-endian:
 *
 * ```
 * header (48 bytes): magic i32 "SBRD" | version u16 | reserved u16 | dataTimestampSec i64 |
 *                    buildTimestampSec i64 | originLatE6 i32 | originLonE6 i32 | cellSizeE6 i32 |
 *                    gridWidth i32 | gridHeight i32 | cellCount i32
 * index: cellCount × (cellId i32 | offset i32 from file start | length i32), sorted by cellId
 * cell payload: varint polylineCount, then per polyline:
 *               varint classCode | varint pointCount | pointCount × (zigzag-varint dLatE6, zigzag-varint dLonE6)
 *               deltas start from the cell's south-west corner, then from the previous point
 * ```
 */
object RoadFileFormat {
    /** "SBRD" in file byte order, read as a little-endian Int. */
    const val MAGIC = 0x44524253
    const val VERSION = 1
    const val HEADER_SIZE = 48
    const val INDEX_ENTRY_SIZE = 12
    const val DEFAULT_CELL_SIZE_E6 = 10_000
}

/** Cell grid: row 0 at the south edge, column 0 at the west edge, ids row-major. */
class Grid(
    val originLatE6: Int,
    val originLonE6: Int,
    val cellSizeE6: Int,
    val width: Int,
    val height: Int,
) {
    fun row(latE6: Int): Int = Math.floorDiv(latE6 - originLatE6, cellSizeE6)
    fun col(lonE6: Int): Int = Math.floorDiv(lonE6 - originLonE6, cellSizeE6)
    fun contains(row: Int, col: Int): Boolean = row in 0 until height && col in 0 until width
    fun cellId(row: Int, col: Int): Int = row * width + col
    fun cellOriginLatE6(cellId: Int): Int = originLatE6 + (cellId / width) * cellSizeE6
    fun cellOriginLonE6(cellId: Int): Int = originLonE6 + (cellId % width) * cellSizeE6
}

/** Unsigned LEB128 varints plus zigzag for signed deltas. */
internal object Varint {
    fun zigzag(v: Int): Int = (v shl 1) xor (v shr 31)
    fun unzigzag(v: Int): Int = (v ushr 1) xor -(v and 1)

    fun write(out: ByteArrayOutputStream, value: Int) {
        var v = value
        while ((v and 0x7F.inv()) != 0) {
            out.write((v and 0x7F) or 0x80)
            v = v ushr 7
        }
        out.write(v)
    }

    fun read(buf: ByteBuffer): Int {
        var result = 0
        var shift = 0
        while (true) {
            if (shift > 28) throw RoadFileFormatException("varint longer than 5 bytes")
            if (!buf.hasRemaining()) throw RoadFileFormatException("truncated varint")
            val b = buf.get().toInt() and 0xFF
            result = result or ((b and 0x7F) shl shift)
            if ((b and 0x80) == 0) return result
            shift += 7
        }
    }
}
```

- [ ] **Step 5: Write the writer**

`core/src/main/kotlin/io/github/lrosa2/speedbeep/core/road/RoadFileWriter.kt`:

```kotlin
package io.github.lrosa2.speedbeep.core.road

import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Writes road file version 1 (layout in [RoadFileFormat]). */
object RoadFileWriter {

    fun write(
        roads: List<Polyline>,
        dataTimestampSec: Long,
        buildTimestampSec: Long,
        out: OutputStream,
        cellSizeE6: Int = RoadFileFormat.DEFAULT_CELL_SIZE_E6,
    ) {
        require(roads.isNotEmpty()) { "no roads to write" }
        val grid = gridFor(roads, cellSizeE6)
        val pieces = splitIntoCells(roads, grid)
        val cellIds = pieces.keys.sorted()
        val payloads = cellIds.map { encodeCell(grid, it, pieces.getValue(it)) }

        val indexEnd = RoadFileFormat.HEADER_SIZE + cellIds.size * RoadFileFormat.INDEX_ENTRY_SIZE
        val head = ByteBuffer.allocate(indexEnd).order(ByteOrder.LITTLE_ENDIAN)
        head.putInt(RoadFileFormat.MAGIC)
        head.putShort(RoadFileFormat.VERSION.toShort())
        head.putShort(0)
        head.putLong(dataTimestampSec)
        head.putLong(buildTimestampSec)
        head.putInt(grid.originLatE6)
        head.putInt(grid.originLonE6)
        head.putInt(grid.cellSizeE6)
        head.putInt(grid.width)
        head.putInt(grid.height)
        head.putInt(cellIds.size)
        var offset = indexEnd.toLong()
        for ((i, id) in cellIds.withIndex()) {
            check(offset + payloads[i].size <= Int.MAX_VALUE) { "road file would exceed 2 GB" }
            head.putInt(id)
            head.putInt(offset.toInt())
            head.putInt(payloads[i].size)
            offset += payloads[i].size
        }
        out.write(head.array())
        payloads.forEach(out::write)
    }

    private fun gridFor(roads: List<Polyline>, cellSizeE6: Int): Grid {
        var minLat = Int.MAX_VALUE
        var minLon = Int.MAX_VALUE
        var maxLat = Int.MIN_VALUE
        var maxLon = Int.MIN_VALUE
        for (road in roads) {
            for (i in 0 until road.size) {
                minLat = minOf(minLat, road.latE6[i])
                maxLat = maxOf(maxLat, road.latE6[i])
                minLon = minOf(minLon, road.lonE6[i])
                maxLon = maxOf(maxLon, road.lonE6[i])
            }
        }
        val originLat = Math.floorDiv(minLat, cellSizeE6) * cellSizeE6
        val originLon = Math.floorDiv(minLon, cellSizeE6) * cellSizeE6
        return Grid(
            originLatE6 = originLat,
            originLonE6 = originLon,
            cellSizeE6 = cellSizeE6,
            width = Math.floorDiv(maxLon - originLon, cellSizeE6) + 1,
            height = Math.floorDiv(maxLat - originLat, cellSizeE6) + 1,
        )
    }

    /**
     * Cuts every road into runs of consecutive segments per cell. A segment goes to every
     * cell its bounding box touches, so a cell may hold a segment that only passes near it.
     */
    private fun splitIntoCells(roads: List<Polyline>, grid: Grid): Map<Int, List<Polyline>> {
        val cells = HashMap<Int, MutableList<Polyline>>()
        for (road in roads) {
            val segmentsByCell = LinkedHashMap<Int, MutableList<Int>>()
            for (s in 0 until road.size - 1) {
                val rows = grid.row(minOf(road.latE6[s], road.latE6[s + 1]))..grid.row(maxOf(road.latE6[s], road.latE6[s + 1]))
                val cols = grid.col(minOf(road.lonE6[s], road.lonE6[s + 1]))..grid.col(maxOf(road.lonE6[s], road.lonE6[s + 1]))
                for (row in rows) {
                    for (col in cols) {
                        segmentsByCell.getOrPut(grid.cellId(row, col)) { mutableListOf() }.add(s)
                    }
                }
            }
            for ((cell, segments) in segmentsByCell) {
                var runStart = segments[0]
                for (k in 1..segments.size) {
                    val runEnds = k == segments.size || segments[k] != segments[k - 1] + 1
                    if (runEnds) {
                        // Segments runStart..segments[k-1] are points runStart..segments[k-1]+1.
                        cells.getOrPut(cell) { mutableListOf() }.add(road.slice(runStart, segments[k - 1] + 2))
                        if (k < segments.size) runStart = segments[k]
                    }
                }
            }
        }
        return cells
    }

    private fun encodeCell(grid: Grid, cellId: Int, polylines: List<Polyline>): ByteArray {
        val out = ByteArrayOutputStream()
        Varint.write(out, polylines.size)
        val originLat = grid.cellOriginLatE6(cellId)
        val originLon = grid.cellOriginLonE6(cellId)
        for (p in polylines) {
            Varint.write(out, p.roadClass.code)
            Varint.write(out, p.size)
            var prevLat = originLat
            var prevLon = originLon
            for (i in 0 until p.size) {
                Varint.write(out, Varint.zigzag(p.latE6[i] - prevLat))
                Varint.write(out, Varint.zigzag(p.lonE6[i] - prevLon))
                prevLat = p.latE6[i]
                prevLon = p.lonE6[i]
            }
        }
        return out.toByteArray()
    }
}
```

- [ ] **Step 6: Write the reader**

`core/src/main/kotlin/io/github/lrosa2/speedbeep/core/road/RoadFile.kt`:

```kotlin
package io.github.lrosa2.speedbeep.core.road

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor

/**
 * Read-only view of a road file. [parse] checks the header and index. A cell's payload is
 * checked when it is first decoded. The 32 most recently used cells stay decoded.
 * Not thread-safe: use one instance from one thread.
 */
class RoadFile private constructor(
    private val buf: ByteBuffer,
    val dataTimestampSec: Long,
    val buildTimestampSec: Long,
    val grid: Grid,
    private val cellIds: IntArray,
    private val offsets: IntArray,
    private val lengths: IntArray,
) {
    val cellCount: Int get() = cellIds.size

    private val cache = object : LinkedHashMap<Int, List<Polyline>>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, List<Polyline>>?) = size > 32
    }

    fun hasCell(cellId: Int): Boolean = cellIds.binarySearch(cellId) >= 0

    /** Existing cells overlapping the box of ±[radiusM] around the point. Empty means not covered. */
    fun cellsAround(lat: Double, lon: Double, radiusM: Double): IntArray {
        val dLatE6 = radiusM / Geo.M_PER_DEG * 1e6
        val dLonE6 = radiusM / (Geo.M_PER_DEG * cos(Math.toRadians(lat))) * 1e6
        val latE6 = lat * 1e6
        val lonE6 = lon * 1e6
        // Clamp to the grid first, so far-away positions cost nothing and never overflow.
        val rowFrom = maxOf(grid.row(floor(latE6 - dLatE6).toInt()), 0)
        val rowTo = minOf(grid.row(ceil(latE6 + dLatE6).toInt()), grid.height - 1)
        val colFrom = maxOf(grid.col(floor(lonE6 - dLonE6).toInt()), 0)
        val colTo = minOf(grid.col(ceil(lonE6 + dLonE6).toInt()), grid.width - 1)
        val found = ArrayList<Int>(4)
        for (row in rowFrom..rowTo) {
            for (col in colFrom..colTo) {
                val id = grid.cellId(row, col)
                if (hasCell(id)) found += id
            }
        }
        return found.toIntArray()
    }

    fun polylines(cellId: Int): List<Polyline> {
        cache[cellId]?.let { return it }
        val index = cellIds.binarySearch(cellId)
        if (index < 0) return emptyList()
        val decoded = decodeCell(cellId, offsets[index], lengths[index])
        cache[cellId] = decoded
        return decoded
    }

    private fun decodeCell(cellId: Int, offset: Int, length: Int): List<Polyline> {
        val b = buf.duplicate().order(ByteOrder.LITTLE_ENDIAN)
        b.limit(offset + length)
        b.position(offset)
        val count = Varint.read(b)
        if (count < 0 || count > length) throw RoadFileFormatException("cell $cellId: bad polyline count $count")
        val originLat = grid.cellOriginLatE6(cellId)
        val originLon = grid.cellOriginLonE6(cellId)
        val result = ArrayList<Polyline>(count)
        repeat(count) {
            val roadClass = RoadClass.fromCode(Varint.read(b))
            val points = Varint.read(b)
            if (points < 2 || points.toLong() * 2 > b.remaining()) {
                throw RoadFileFormatException("cell $cellId: bad point count $points")
            }
            val lat = IntArray(points)
            val lon = IntArray(points)
            var prevLat = originLat
            var prevLon = originLon
            for (i in 0 until points) {
                prevLat += Varint.unzigzag(Varint.read(b))
                prevLon += Varint.unzigzag(Varint.read(b))
                lat[i] = prevLat
                lon[i] = prevLon
            }
            result += Polyline(roadClass, lat, lon)
        }
        if (b.hasRemaining()) throw RoadFileFormatException("cell $cellId: ${b.remaining()} trailing bytes")
        return result
    }

    companion object {
        fun parse(bytes: ByteBuffer): RoadFile {
            val b = bytes.duplicate().order(ByteOrder.LITTLE_ENDIAN)
            val size = b.limit()
            if (size < RoadFileFormat.HEADER_SIZE) throw RoadFileFormatException("file too short: $size bytes")
            if (b.getInt(0) != RoadFileFormat.MAGIC) throw RoadFileFormatException("not a road file (bad magic)")
            val version = b.getShort(4).toInt()
            if (version != RoadFileFormat.VERSION) throw RoadFileFormatException("unsupported road file version $version")
            val grid = Grid(b.getInt(24), b.getInt(28), b.getInt(32), b.getInt(36), b.getInt(40))
            if (grid.cellSizeE6 <= 0 || grid.width <= 0 || grid.height <= 0) throw RoadFileFormatException("bad grid")
            val cellLimit = grid.width.toLong() * grid.height
            if (cellLimit > Int.MAX_VALUE) throw RoadFileFormatException("grid too large")
            val count = b.getInt(44)
            val indexEnd = RoadFileFormat.HEADER_SIZE.toLong() + count.toLong() * RoadFileFormat.INDEX_ENTRY_SIZE
            if (count < 0 || indexEnd > size) throw RoadFileFormatException("index truncated")
            val ids = IntArray(count)
            val offsets = IntArray(count)
            val lengths = IntArray(count)
            for (i in 0 until count) {
                val at = RoadFileFormat.HEADER_SIZE + i * RoadFileFormat.INDEX_ENTRY_SIZE
                val id = b.getInt(at)
                val offset = b.getInt(at + 4)
                val length = b.getInt(at + 8)
                if (id < 0 || id >= cellLimit) throw RoadFileFormatException("cell id $id outside the grid")
                if (i > 0 && id <= ids[i - 1]) throw RoadFileFormatException("index not sorted at entry $i")
                if (offset < indexEnd || length <= 0 || offset.toLong() + length > size) {
                    throw RoadFileFormatException("cell $id data lies outside the file")
                }
                ids[i] = id
                offsets[i] = offset
                lengths[i] = length
            }
            return RoadFile(b, b.getLong(8), b.getLong(16), grid, ids, offsets, lengths)
        }
    }
}
```

- [ ] **Step 7: Run the tests to verify they pass**

Run: `GW :core:test`
Expected: `BUILD SUCCESSFUL`. `GeoTest`, `VarintTest`, `RoadFileTest` and
`RoadClassifierTest` all pass.

- [ ] **Step 8: Commit**

```bash
git -C E:/AndroidProj/SpeedBeep add -A
git -C E:/AndroidProj/SpeedBeep commit -m "Add road file format: grid, varints, writer and validating reader

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 4: Road matcher

Implements §4.2 and §6.4. It finds the nearest road segment within 20 m that runs the same
way, and answers FAST, TOWN, UNKNOWN or NOT_COVERED.

**Files:**
- Create: `core/src/main/kotlin/io/github/lrosa2/speedbeep/core/road/RoadMatcher.kt`
- Test: `core/src/test/kotlin/io/github/lrosa2/speedbeep/core/road/RoadMatcherTest.kt`

**Interfaces:**
- Consumes: `RoadFile`, `Polyline`, `Geo`, `RoadFileWriter` (Task 3; the writer only in tests).
- Produces:
  - `enum class RoadMatch { FAST, TOWN, UNKNOWN, NOT_COVERED, NEUTRAL }`
  - `fun interface RoadLookup { fun classify(lat: Double, lon: Double, bearingDeg: Double?, speedKmh: Double): RoadMatch }`
  - `object NoRoadData : RoadLookup` (always NOT_COVERED)
  - `class RoadMatcher(file: RoadFile) : RoadLookup`, with constants `MAX_DISTANCE_M = 20.0`,
    `MAX_ANGLE_DEG = 30.0`, `HEADING_MIN_KMH = 15.0`, `SEARCH_RADIUS_M = 30.0`

- [ ] **Step 1: Write the failing test**

`core/src/test/kotlin/io/github/lrosa2/speedbeep/core/road/RoadMatcherTest.kt`:

```kotlin
package io.github.lrosa2.speedbeep.core.road

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import kotlin.math.cos

class RoadMatcherTest {
    // 47.37 N / 8.54 E is a cell corner, so these layouts also exercise cell edges.
    private val lat0 = 47.37
    private val lon0 = 8.54
    private val metresPerDegLon = Geo.M_PER_DEG * cos(Math.toRadians(lat0))

    private fun latOf(northM: Double) = lat0 + northM / Geo.M_PER_DEG
    private fun lonOf(eastM: Double) = lon0 + eastM / metresPerDegLon

    /** A road through (north, east) points given in metres from (lat0, lon0). */
    private fun road(roadClass: RoadClass, vararg points: Pair<Double, Double>) = Polyline(
        roadClass,
        IntArray(points.size) { Math.round(latOf(points[it].first) * 1e6).toInt() },
        IntArray(points.size) { Math.round(lonOf(points[it].second) * 1e6).toInt() },
    )

    private fun matcherOf(vararg roads: Polyline): RoadMatcher {
        val out = ByteArrayOutputStream()
        RoadFileWriter.write(roads.toList(), 0, 0, out)
        return RoadMatcher(RoadFile.parse(ByteBuffer.wrap(out.toByteArray())))
    }

    private fun RoadMatcher.at(northM: Double, eastM: Double, bearingDeg: Double?, speedKmh: Double = 60.0) =
        classify(latOf(northM), lonOf(eastM), bearingDeg, speedKmh)

    private val townEastWest = road(RoadClass.TOWN, 0.0 to -500.0, 0.0 to 500.0)
    private val motorwayEastWest = road(RoadClass.FAST, 0.0 to -500.0, 0.0 to 500.0)
    private val motorwayNorthSouth = road(RoadClass.FAST, -500.0 to 0.0, 500.0 to 0.0)

    @Test
    fun `on a town road`() {
        assertEquals(RoadMatch.TOWN, matcherOf(townEastWest).at(0.0, 0.0, 90.0))
    }

    @Test
    fun `on a motorway`() {
        assertEquals(RoadMatch.FAST, matcherOf(motorwayEastWest).at(0.0, 0.0, 90.0))
    }

    @Test
    fun `beside a parallel motorway 25 m away, the nearer road wins`() {
        val matcher = matcherOf(townEastWest, road(RoadClass.FAST, 25.0 to -500.0, 25.0 to 500.0))
        assertEquals(RoadMatch.TOWN, matcher.at(0.0, 0.0, 90.0))
        assertEquals(RoadMatch.TOWN, matcher.at(11.0, 0.0, 90.0))
        assertEquals(RoadMatch.FAST, matcher.at(14.0, 0.0, 90.0))
        assertEquals(RoadMatch.FAST, matcher.at(25.0, 0.0, 90.0))
    }

    @Test
    fun `under a motorway bridge, the heading decides`() {
        val matcher = matcherOf(townEastWest, motorwayNorthSouth)
        assertEquals(RoadMatch.TOWN, matcher.at(0.0, 0.0, 90.0))
        assertEquals(RoadMatch.TOWN, matcher.at(0.0, 0.0, 270.0))
        assertEquals(RoadMatch.FAST, matcher.at(0.0, 0.0, 0.0))
        assertEquals(RoadMatch.FAST, matcher.at(0.0, 0.0, 180.0))
    }

    @Test
    fun `below 15 km per h or without a bearing the heading is not checked`() {
        val matcher = matcherOf(townEastWest, motorwayNorthSouth)
        // On the motorway 5 m north of the crossing, heading east (across it).
        assertEquals(RoadMatch.TOWN, matcher.at(5.0, 0.0, 90.0, speedKmh = 60.0))
        assertEquals(RoadMatch.FAST, matcher.at(5.0, 0.0, 90.0, speedKmh = 10.0))
        assertEquals(RoadMatch.FAST, matcher.at(5.0, 0.0, null))
    }

    @Test
    fun `on an exit ramp and then on the town road it leads to`() {
        val matcher = matcherOf(
            road(RoadClass.FAST, -1000.0 to 0.0, 1000.0 to 0.0),
            road(RoadClass.FAST, 0.0 to 0.0, 300.0 to 60.0),
            road(RoadClass.TOWN, 300.0 to 60.0, 300.0 to 600.0),
        )
        assertEquals(RoadMatch.FAST, matcher.at(150.0, 30.0, 11.0))
        assertEquals(RoadMatch.TOWN, matcher.at(300.0, 300.0, 90.0))
    }

    @Test
    fun `a road crossing a cell edge matches on both sides`() {
        val matcher = matcherOf(road(RoadClass.FAST, 0.0 to -300.0, 0.0 to 300.0))
        assertEquals(RoadMatch.FAST, matcher.at(0.0, -5.0, 90.0))
        assertEquals(RoadMatch.FAST, matcher.at(0.0, 5.0, 90.0))
    }

    @Test
    fun `at equal distance the town road wins (when in doubt, beep)`() {
        assertEquals(RoadMatch.TOWN, matcherOf(townEastWest, motorwayEastWest).at(0.0, 0.0, 90.0))
        assertEquals(RoadMatch.TOWN, matcherOf(motorwayEastWest, townEastWest).at(0.0, 0.0, 90.0))
    }

    @Test
    fun `no road within 20 m in a covered cell is UNKNOWN`() {
        assertEquals(RoadMatch.UNKNOWN, matcherOf(townEastWest).at(50.0, 0.0, 90.0))
    }

    @Test
    fun `outside the grid, or south-west of its edge, is NOT_COVERED`() {
        val matcher = matcherOf(townEastWest)
        assertEquals(RoadMatch.NOT_COVERED, matcher.at(-40.0, 0.0, 90.0))
        assertEquals(RoadMatch.NOT_COVERED, matcher.classify(46.0, 7.0, 90.0, 60.0))
        assertEquals(RoadMatch.NOT_COVERED, matcher.classify(-33.9, 151.2, null, 0.0))
    }

    @Test
    fun `without a road file everything is NOT_COVERED`() {
        assertEquals(RoadMatch.NOT_COVERED, NoRoadData.classify(lat0, lon0, 90.0, 60.0))
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `GW :core:test --tests "*RoadMatcherTest"`
Expected: compilation FAILS with unresolved `RoadMatcher`, `RoadMatch` and `NoRoadData`.

- [ ] **Step 3: Write the matcher**

`core/src/main/kotlin/io/github/lrosa2/speedbeep/core/road/RoadMatcher.kt`:

```kotlin
package io.github.lrosa2.speedbeep.core.road

/** What the road data says about one GPS reading (spec §4.2). */
enum class RoadMatch {
    FAST,
    TOWN,

    /** Inside the covered area, but no road within 20 m running the same way. */
    UNKNOWN,

    /** Outside the road file, or no road file at all. */
    NOT_COVERED,

    /** Position too inaccurate to match. Set by FixEvaluator, never returned by a RoadLookup. */
    NEUTRAL,
}

fun interface RoadLookup {
    /** [bearingDeg] is null when unknown. The heading is only compared at [speedKmh] ≥ 15. */
    fun classify(lat: Double, lon: Double, bearingDeg: Double?, speedKmh: Double): RoadMatch
}

/** Stand-in when the road file is missing or unreadable: everything is NOT_COVERED, so the rules act as in simple mode. */
object NoRoadData : RoadLookup {
    override fun classify(lat: Double, lon: Double, bearingDeg: Double?, speedKmh: Double) = RoadMatch.NOT_COVERED
}

/** Matches a position to the nearest road segment (spec §6.4). Not thread-safe, like [RoadFile]. */
class RoadMatcher(private val file: RoadFile) : RoadLookup {

    companion object {
        const val MAX_DISTANCE_M = 20.0
        const val MAX_ANGLE_DEG = 30.0
        const val HEADING_MIN_KMH = 15.0
        const val SEARCH_RADIUS_M = 30.0
    }

    override fun classify(lat: Double, lon: Double, bearingDeg: Double?, speedKmh: Double): RoadMatch {
        val cells = file.cellsAround(lat, lon, SEARCH_RADIUS_M)
        if (cells.isEmpty()) return RoadMatch.NOT_COVERED
        val heading = if (bearingDeg != null && speedKmh >= HEADING_MIN_KMH) bearingDeg else null
        var best: RoadClass? = null
        var bestDistance = MAX_DISTANCE_M
        for (cell in cells) {
            for (p in file.polylines(cell)) {
                for (i in 0 until p.size - 1) {
                    val aLat = p.latE6[i] / 1e6
                    val aLon = p.lonE6[i] / 1e6
                    val bLat = p.latE6[i + 1] / 1e6
                    val bLon = p.lonE6[i + 1] / 1e6
                    if (aLat == bLat && aLon == bLon) continue // no direction; its neighbours cover the point
                    val d = Geo.distanceToSegmentM(lat, lon, aLat, aLon, bLat, bLon)
                    if (d > bestDistance) continue
                    if (heading != null && Geo.lineAngleDiff(Geo.bearingDeg(aLat, aLon, bLat, bLon), heading) > MAX_ANGLE_DEG) continue
                    // On a tie the town road wins: when in doubt, beep (spec D10).
                    if (best == null || d < bestDistance || p.roadClass == RoadClass.TOWN) {
                        best = p.roadClass
                        bestDistance = d
                    }
                }
            }
        }
        return when (best) {
            RoadClass.FAST -> RoadMatch.FAST
            RoadClass.TOWN -> RoadMatch.TOWN
            null -> RoadMatch.UNKNOWN
        }
    }
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `GW :core:test`
Expected: `BUILD SUCCESSFUL`, all `RoadMatcherTest` tests pass, and the earlier tests still pass.

- [ ] **Step 5: Commit**

```bash
git -C E:/AndroidProj/SpeedBeep add -A
git -C E:/AndroidProj/SpeedBeep commit -m "Add road matcher: nearest same-direction road within 20 m

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 5: Road-file builder, the Swiss road file, and the coverage checkpoint

Builds `roads-ch.bin` from the Geofabrik extract (§6.1) and prints the report. That report
gives the **known-gap figure**: how much main road has no speed information. That figure is
the biggest remaining design assumption besides Bluetooth, so this task ends with a
checkpoint for the owner.

**Files:**
- Modify: `settings.gradle.kts` (add `include(":roadbuilder")`)
- Create: `roadbuilder/build.gradle.kts`
- Create: `roadbuilder/src/main/kotlin/io/github/lrosa2/speedbeep/roadbuilder/RoadCollector.kt`
- Create: `roadbuilder/src/main/kotlin/io/github/lrosa2/speedbeep/roadbuilder/RoadStats.kt`
- Create: `roadbuilder/src/main/kotlin/io/github/lrosa2/speedbeep/roadbuilder/Probe.kt`
- Create: `roadbuilder/src/main/kotlin/io/github/lrosa2/speedbeep/roadbuilder/OsmPbfReader.kt`
- Create: `roadbuilder/src/main/kotlin/io/github/lrosa2/speedbeep/roadbuilder/Main.kt`
- Test: `roadbuilder/src/test/kotlin/io/github/lrosa2/speedbeep/roadbuilder/RoadCollectorTest.kt`
- Test: `roadbuilder/src/test/kotlin/io/github/lrosa2/speedbeep/roadbuilder/RoadStatsTest.kt`
- Test: `roadbuilder/src/test/kotlin/io/github/lrosa2/speedbeep/roadbuilder/ProbeTest.kt`
- Generate and commit: `app/src/main/assets/roads-ch.bin`

**Interfaces:**
- Consumes: `RoadClassifier`, `Polyline`, `RoadClass`, `Geo`, `RoadFileWriter`, `RoadFile`,
  `RoadMatcher` (Tasks 2–4).
- Produces:
  - CLI `build <pbf> <out.bin>` and `probe <bin> <lat> <lon> [bearingDeg]`
  - `roads-ch.bin`, which Plan 2 loads

- [ ] **Step 1: Add the module**

In `settings.gradle.kts`, change the include line to:

```kotlin
include(":core", ":roadbuilder")
```

`roadbuilder/build.gradle.kts`:

```kotlin
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    application
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

application {
    mainClass.set("io.github.lrosa2.speedbeep.roadbuilder.MainKt")
    applicationDefaultJvmArgs = listOf("-Xmx4g")
}

dependencies {
    implementation(project(":core"))
    implementation(libs.parallelpbf) // GPLv3: only in this PC tool, never in :core or the app
    runtimeOnly(libs.slf4j.simple)
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
}

// Relative paths on the command line resolve against the repository root.
tasks.named<JavaExec>("run") {
    workingDir = rootProject.projectDir
}
```

- [ ] **Step 2: Write the failing tests**

`roadbuilder/src/test/kotlin/io/github/lrosa2/speedbeep/roadbuilder/RoadCollectorTest.kt`:

```kotlin
package io.github.lrosa2.speedbeep.roadbuilder

import io.github.lrosa2.speedbeep.core.road.Polyline
import io.github.lrosa2.speedbeep.core.road.RoadClass
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class RoadCollectorTest {

    @Test
    fun `collects drivable ways with their node coordinates, ordered by way id`() {
        val collector = RoadCollector()
        collector.acceptWay(20, mapOf("highway" to "motorway"), listOf(3L, 4L))
        collector.acceptWay(10, mapOf("highway" to "residential"), listOf(1L, 2L, 3L))
        collector.acceptWay(30, mapOf("highway" to "footway"), listOf(1L, 4L))
        collector.finishWays()
        collector.acceptNode(1, 47.0, 8.0)
        collector.acceptNode(2, 47.001, 8.0)
        collector.acceptNode(3, 47.002, 8.0)
        collector.acceptNode(4, 47.003, 8.0000004)
        collector.acceptNode(99, 1.0, 1.0) // not needed by any road
        val result = collector.result()
        assertEquals(
            listOf(
                Polyline(RoadClass.TOWN, intArrayOf(47_000_000, 47_001_000, 47_002_000), intArrayOf(8_000_000, 8_000_000, 8_000_000)),
                Polyline(RoadClass.FAST, intArrayOf(47_002_000, 47_003_000), intArrayOf(8_000_000, 8_000_000)),
            ),
            result.roads,
        )
        assertEquals(2, result.stats.ways)
        assertEquals(0, result.stats.droppedWays)
    }

    @Test
    fun `a way with a node missing from the extract is dropped and counted`() {
        val collector = RoadCollector()
        collector.acceptWay(1, mapOf("highway" to "primary"), listOf(1L, 2L))
        collector.finishWays()
        collector.acceptNode(1, 47.0, 8.0)
        val result = collector.result()
        assertEquals(emptyList<Polyline>(), result.roads)
        assertEquals(1, result.stats.droppedWays)
    }

    @Test
    fun `ways with fewer than two nodes are ignored`() {
        val collector = RoadCollector()
        collector.acceptWay(1, mapOf("highway" to "primary"), listOf(1L))
        collector.finishWays()
        collector.acceptNode(1, 47.0, 8.0)
        assertEquals(0, collector.result().stats.ways)
    }

    @Test
    fun `main roads without speed information count toward the known gap`() {
        val collector = RoadCollector()
        collector.acceptWay(1, mapOf("highway" to "primary"), listOf(1L, 2L))
        collector.acceptWay(2, mapOf("highway" to "secondary", "maxspeed" to "80"), listOf(3L, 4L))
        collector.acceptWay(3, mapOf("highway" to "residential"), listOf(5L, 6L))
        collector.finishWays()
        // Each way is 0.009° of latitude long, about 1.002 km.
        listOf(1L to 47.0, 2L to 47.009, 3L to 46.0, 4L to 46.009, 5L to 45.0, 6L to 45.009)
            .forEach { (id, lat) -> collector.acceptNode(id, lat, 8.0) }
        val stats = collector.result().stats
        assertEquals(2.004, stats.mainRoadKm, 0.01)
        assertEquals(1.002, stats.mainRoadNoLimitKm, 0.01)
        assertEquals(1.002, stats.fastKm, 0.01)
        assertEquals(2.004, stats.townKm, 0.01)
    }
}
```

`roadbuilder/src/test/kotlin/io/github/lrosa2/speedbeep/roadbuilder/RoadStatsTest.kt`:

```kotlin
package io.github.lrosa2.speedbeep.roadbuilder

import io.github.lrosa2.speedbeep.core.road.RoadClass
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class RoadStatsTest {

    @Test
    fun `report shows totals, the known gap and the file`() {
        val stats = RoadStats()
        stats.add(RoadClass.FAST, "motorway", hasLimit = true, km = 1500.0)
        stats.add(RoadClass.TOWN, "primary", hasLimit = false, km = 300.0)
        stats.add(RoadClass.FAST, "secondary", hasLimit = true, km = 700.0)
        stats.droppedWays = 2
        val expected = """
            Roads written: 3 (dropped for missing nodes: 2)
              FAST: 2,200 km
              TOWN: 300 km
            Known gap: 300 of 1,000 km of primary/secondary/tertiary/unclassified road (30.0 %) has no speed information.
              Upper bound: it includes roads inside towns, where 50 is right anyway.
            File: 21.5 MB, OSM data from 2026-11-24
        """.trimIndent() + "\n"
        assertEquals(expected, stats.format(fileBytes = 21_500_000, dataTimestampSec = 1_795_478_400))
    }
}
```

(`1_795_478_400` is 2026-11-24T00:00:00Z.)

`roadbuilder/src/test/kotlin/io/github/lrosa2/speedbeep/roadbuilder/ProbeTest.kt`:

```kotlin
package io.github.lrosa2.speedbeep.roadbuilder

import io.github.lrosa2.speedbeep.core.road.Polyline
import io.github.lrosa2.speedbeep.core.road.RoadClass
import io.github.lrosa2.speedbeep.core.road.RoadFile
import io.github.lrosa2.speedbeep.core.road.RoadFileWriter
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer

class ProbeTest {

    @Test
    fun `lists the nearest roads once each, nearest first`() {
        // Town road along 47.3750 N; motorway along 47.3755 N (about 56 m further north).
        val town = Polyline(RoadClass.TOWN, intArrayOf(47_375_000, 47_375_000), intArrayOf(8_535_000, 8_545_000))
        val motorway = Polyline(RoadClass.FAST, intArrayOf(47_375_500, 47_375_500), intArrayOf(8_535_000, 8_545_000))
        val out = ByteArrayOutputStream()
        RoadFileWriter.write(listOf(town, motorway), 0, 0, out)
        val file = RoadFile.parse(ByteBuffer.wrap(out.toByteArray()))

        val nearby = Probe.nearest(file, 47.3751, 8.5395, radiusM = 300.0, limit = 5)

        assertEquals(listOf(RoadClass.TOWN, RoadClass.FAST), nearby.map { it.roadClass })
        assertEquals(11.1, nearby[0].distanceM, 0.2)
        assertEquals(44.5, nearby[1].distanceM, 0.2)
        assertEquals(90.0, nearby[0].bearingDeg, 0.5)
    }
}
```

- [ ] **Step 3: Run them to verify they fail**

Run: `GW :roadbuilder:test`
Expected: compilation FAILS with unresolved `RoadCollector`, `RoadStats` and `Probe`.

- [ ] **Step 4: Write the collector, the stats and the probe**

`roadbuilder/src/main/kotlin/io/github/lrosa2/speedbeep/roadbuilder/RoadCollector.kt`:

```kotlin
package io.github.lrosa2.speedbeep.roadbuilder

import io.github.lrosa2.speedbeep.core.road.Geo
import io.github.lrosa2.speedbeep.core.road.Polyline
import io.github.lrosa2.speedbeep.core.road.RoadClass
import io.github.lrosa2.speedbeep.core.road.RoadClassifier

/**
 * Two-pass collection of drivable ways:
 * - pass 1 [acceptWay]
 * - then [finishWays]
 * - pass 2 [acceptNode]
 * - then [result]
 *
 * The parser calls the accept functions from several worker threads.
 */
class RoadCollector {
    private class PendingWay(
        val id: Long,
        val roadClass: RoadClass,
        val highway: String,
        val hasLimit: Boolean,
        val nodeIds: LongArray,
    )

    private val ways = ArrayList<PendingWay>()
    private var neededIds = LongArray(0)
    private var latE6 = IntArray(0)
    private var lonE6 = IntArray(0)
    private var found = BooleanArray(0)

    @Synchronized
    fun acceptWay(id: Long, tags: Map<String, String>, nodeIds: List<Long>) {
        if (nodeIds.size < 2) return
        val roadClass = RoadClassifier.classify(tags) ?: return
        ways += PendingWay(id, roadClass, tags.getValue("highway"), RoadClassifier.effectiveLimit(tags) != null, nodeIds.toLongArray())
    }

    /** Sorts the ways (so the file is reproducible) and builds the sorted table of needed node ids. */
    fun finishWays() {
        ways.sortBy { it.id }
        val all = LongArray(ways.sumOf { it.nodeIds.size })
        var i = 0
        for (way in ways) for (nodeId in way.nodeIds) all[i++] = nodeId
        all.sort()
        neededIds = distinctSorted(all)
        latE6 = IntArray(neededIds.size)
        lonE6 = IntArray(neededIds.size)
        found = BooleanArray(neededIds.size)
    }

    /** Each needed node id owns one array slot, so concurrent calls never touch the same slot. */
    fun acceptNode(id: Long, lat: Double, lon: Double) {
        val slot = neededIds.binarySearch(id)
        if (slot < 0) return
        latE6[slot] = Math.round(lat * 1e6).toInt()
        lonE6[slot] = Math.round(lon * 1e6).toInt()
        found[slot] = true
    }

    fun result(): CollectResult {
        val roads = ArrayList<Polyline>(ways.size)
        val stats = RoadStats()
        for (way in ways) {
            val slots = IntArray(way.nodeIds.size) { neededIds.binarySearch(way.nodeIds[it]) }
            if (slots.any { it < 0 || !found[it] }) {
                stats.droppedWays++
                continue
            }
            val road = Polyline(way.roadClass, IntArray(slots.size) { latE6[slots[it]] }, IntArray(slots.size) { lonE6[slots[it]] })
            roads += road
            stats.add(way.roadClass, way.highway, way.hasLimit, lengthKm(road))
        }
        return CollectResult(roads, stats)
    }

    private fun lengthKm(p: Polyline): Double {
        var metres = 0.0
        for (i in 0 until p.size - 1) {
            metres += Geo.distanceM(p.latE6[i] / 1e6, p.lonE6[i] / 1e6, p.latE6[i + 1] / 1e6, p.lonE6[i + 1] / 1e6)
        }
        return metres / 1000
    }

    private fun distinctSorted(sorted: LongArray): LongArray {
        if (sorted.isEmpty()) return sorted
        var n = 1
        for (i in 1 until sorted.size) {
            if (sorted[i] != sorted[n - 1]) sorted[n++] = sorted[i]
        }
        return sorted.copyOf(n)
    }
}

data class CollectResult(val roads: List<Polyline>, val stats: RoadStats)
```

`roadbuilder/src/main/kotlin/io/github/lrosa2/speedbeep/roadbuilder/RoadStats.kt`:

```kotlin
package io.github.lrosa2.speedbeep.roadbuilder

import io.github.lrosa2.speedbeep.core.road.RoadClass
import java.time.Instant
import java.util.Locale

/** Totals for the build report (spec §6.1). */
class RoadStats {
    var ways = 0
        private set
    var droppedWays = 0
    var fastKm = 0.0
        private set
    var townKm = 0.0
        private set
    var mainRoadKm = 0.0
        private set
    var mainRoadNoLimitKm = 0.0
        private set

    fun add(roadClass: RoadClass, highway: String, hasLimit: Boolean, km: Double) {
        ways++
        if (roadClass == RoadClass.FAST) fastKm += km else townKm += km
        if (highway in GAP_TYPES) {
            mainRoadKm += km
            if (!hasLimit) mainRoadNoLimitKm += km
        }
    }

    fun format(fileBytes: Long, dataTimestampSec: Long): String {
        val gapPercent = if (mainRoadKm == 0.0) 0.0 else mainRoadNoLimitKm / mainRoadKm * 100
        val dataDate = Instant.ofEpochSecond(dataTimestampSec).toString().take(10)
        return buildString {
            appendLine("Roads written: %,d (dropped for missing nodes: %,d)".format(Locale.ROOT, ways, droppedWays))
            appendLine("  FAST: %,.0f km".format(Locale.ROOT, fastKm))
            appendLine("  TOWN: %,.0f km".format(Locale.ROOT, townKm))
            appendLine(
                "Known gap: %,.0f of %,.0f km of primary/secondary/tertiary/unclassified road (%.1f %%) has no speed information."
                    .format(Locale.ROOT, mainRoadNoLimitKm, mainRoadKm, gapPercent),
            )
            appendLine("  Upper bound: it includes roads inside towns, where 50 is right anyway.")
            appendLine("File: %.1f MB, OSM data from %s".format(Locale.ROOT, fileBytes / 1_000_000.0, dataDate))
        }
    }

    companion object {
        val GAP_TYPES = setOf("primary", "secondary", "tertiary", "unclassified")
    }
}
```

`roadbuilder/src/main/kotlin/io/github/lrosa2/speedbeep/roadbuilder/Probe.kt`:

```kotlin
package io.github.lrosa2.speedbeep.roadbuilder

import io.github.lrosa2.speedbeep.core.road.Geo
import io.github.lrosa2.speedbeep.core.road.RoadClass
import io.github.lrosa2.speedbeep.core.road.RoadFile

data class NearbyRoad(val roadClass: RoadClass, val distanceM: Double, val bearingDeg: Double)

/** Debug helper for spot checks: the nearest road pieces around a point. */
object Probe {
    fun nearest(file: RoadFile, lat: Double, lon: Double, radiusM: Double, limit: Int): List<NearbyRoad> {
        val found = ArrayList<NearbyRoad>()
        for (cell in file.cellsAround(lat, lon, radiusM)) {
            for (p in file.polylines(cell)) {
                var best: NearbyRoad? = null
                for (i in 0 until p.size - 1) {
                    val aLat = p.latE6[i] / 1e6
                    val aLon = p.lonE6[i] / 1e6
                    val bLat = p.latE6[i + 1] / 1e6
                    val bLon = p.lonE6[i + 1] / 1e6
                    val d = Geo.distanceToSegmentM(lat, lon, aLat, aLon, bLat, bLon)
                    if (best == null || d < best.distanceM) best = NearbyRoad(p.roadClass, d, Geo.bearingDeg(aLat, aLon, bLat, bLon))
                }
                if (best != null && best.distanceM <= radiusM) found += best
            }
        }
        // A road split over several cells shows up once per cell; keep one of each.
        return found
            .sortedBy { it.distanceM }
            .distinctBy { Triple(it.roadClass, Math.round(it.distanceM * 10), Math.round(it.bearingDeg)) }
            .take(limit)
    }
}
```

- [ ] **Step 5: Run the tests to verify they pass**

Run: `GW :roadbuilder:test`
Expected: `BUILD SUCCESSFUL`, all three test classes pass.

If `ProbeTest` distances are off by more than the tolerance, recompute them. The town road
is 0.0001° south of the point (11.13 m). The motorway is 0.0004° north (44.53 m). Fix the
test numbers only if the code is right.

- [ ] **Step 6: Write the PBF adapter and the CLI**

`roadbuilder/src/main/kotlin/io/github/lrosa2/speedbeep/roadbuilder/OsmPbfReader.kt`:

```kotlin
package io.github.lrosa2.speedbeep.roadbuilder

import com.wolt.osm.parallelpbf.ParallelBinaryParser
import java.io.File

/** Thin adapter over parallelpbf. Callbacks run on the parser's worker threads. */
object OsmPbfReader {
    // Each parser thread can hold a few hundred MB of blob data; 4 threads fit in -Xmx4g.
    private val threads = minOf(4, Runtime.getRuntime().availableProcessors())

    fun readWays(pbf: File, onWay: (id: Long, tags: Map<String, String>, nodeIds: List<Long>) -> Unit) =
        parse(pbf) { parser -> parser.onWay { way -> onWay(way.id, way.tags, way.nodes) } }

    fun readNodes(pbf: File, onNode: (id: Long, lat: Double, lon: Double) -> Unit) =
        parse(pbf) { parser -> parser.onNode { node -> onNode(node.id, node.lat, node.lon) } }

    private fun parse(pbf: File, configure: (ParallelBinaryParser) -> Unit) {
        var completed = false
        pbf.inputStream().buffered(1 shl 20).use { input ->
            val parser = ParallelBinaryParser(input, threads)
            configure(parser)
            parser.onComplete { completed = true }
            parser.parse()
        }
        // On failure parallelpbf logs the error and returns without calling onComplete.
        check(completed) { "reading $pbf failed (see the log above)" }
    }
}
```

`roadbuilder/src/main/kotlin/io/github/lrosa2/speedbeep/roadbuilder/Main.kt`:

```kotlin
package io.github.lrosa2.speedbeep.roadbuilder

import io.github.lrosa2.speedbeep.core.road.RoadFile
import io.github.lrosa2.speedbeep.core.road.RoadFileWriter
import io.github.lrosa2.speedbeep.core.road.RoadMatcher
import java.io.File
import java.nio.ByteBuffer
import java.util.Locale
import kotlin.system.exitProcess

private val USAGE = """
    Usage:
      build <switzerland-latest.osm.pbf> <roads-ch.bin>
      probe <roads-ch.bin> <lat> <lon> [bearingDeg]
""".trimIndent()

fun main(args: Array<String>) {
    when {
        args.size == 3 && args[0] == "build" -> build(File(args[1]), File(args[2]))
        args.size in 4..5 && args[0] == "probe" ->
            probe(File(args[1]), args[2].toDouble(), args[3].toDouble(), args.getOrNull(4)?.toDouble())
        else -> {
            System.err.println(USAGE)
            exitProcess(2)
        }
    }
}

private fun build(pbf: File, out: File) {
    require(pbf.isFile) { "no such file: $pbf" }
    val collector = RoadCollector()
    println("Pass 1/2: ways")
    OsmPbfReader.readWays(pbf, collector::acceptWay)
    collector.finishWays()
    println("Pass 2/2: nodes")
    OsmPbfReader.readNodes(pbf, collector::acceptNode)
    val (roads, stats) = collector.result()
    // curl -R keeps Geofabrik's Last-Modified, so the file date is the OSM data date.
    val dataTimestampSec = pbf.lastModified() / 1000
    out.absoluteFile.parentFile.mkdirs()
    out.outputStream().buffered().use { RoadFileWriter.write(roads, dataTimestampSec, System.currentTimeMillis() / 1000, it) }
    print(stats.format(out.length(), dataTimestampSec))
}

private fun probe(bin: File, lat: Double, lon: Double, bearingDeg: Double?) {
    val file = RoadFile.parse(ByteBuffer.wrap(bin.readBytes()))
    // With a bearing, probe as a car at 50 km/h, so the heading check applies.
    val match = RoadMatcher(file).classify(lat, lon, bearingDeg, if (bearingDeg != null) 50.0 else 0.0)
    println("match: $match")
    for (road in Probe.nearest(file, lat, lon, radiusM = 300.0, limit = 5)) {
        println("  %s at %.1f m, running %.0f°".format(Locale.ROOT, road.roadClass, road.distanceM, road.bearingDeg))
    }
}
```

Run: `GW :roadbuilder:build`
Expected: `BUILD SUCCESSFUL`. Running `GW :roadbuilder:run` with no arguments prints the
usage and exits with code 2.

- [ ] **Step 7: Commit the tool**

```bash
git -C E:/AndroidProj/SpeedBeep add -A
git -C E:/AndroidProj/SpeedBeep commit -m "Add roadbuilder: OSM extract to road file, with report and probe

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

- [ ] **Step 8: Ask the owner before downloading the extract**

Ask in chat and **wait for a yes**:

> I need to download `switzerland-latest.osm.pbf` (about 552 MB) from download.geofabrik.de
> into `C:\work\tmp\speedbeep-osm\`. OK?

Then:

```bash
mkdir -p /c/work/tmp/speedbeep-osm
curl -L -R -o /c/work/tmp/speedbeep-osm/switzerland-latest.osm.pbf https://download.geofabrik.de/europe/switzerland-latest.osm.pbf
curl -L -o /c/work/tmp/speedbeep-osm/switzerland-latest.osm.pbf.md5 https://download.geofabrik.de/europe/switzerland-latest.osm.pbf.md5
(cd /c/work/tmp/speedbeep-osm && md5sum -c switzerland-latest.osm.pbf.md5)
```
Expected: `switzerland-latest.osm.pbf: OK`. Run the long download in the background.

- [ ] **Step 9: Build the Swiss road file**

Run (in the background, since it takes several minutes):
`GW :roadbuilder:run --args="build C:/work/tmp/speedbeep-osm/switzerland-latest.osm.pbf app/src/main/assets/roads-ch.bin"`

Expected:
- The report prints.
- `app/src/main/assets/roads-ch.bin` exists and is **under 40 MB**.
- `dropped for missing nodes` is 0 or close to it.

If the build runs out of memory, raise `-Xmx` in `roadbuilder/build.gradle.kts`. Do not
change the algorithm.

**Stop if the file is 40 MB or more.** Report the size to the owner, because the spec's size
target failed.

- [ ] **Step 10: Spot-check with the probe**

For each check, find a point on https://www.openstreetmap.org: right-click the road and
choose "Show address", which shows the coordinates. Then run
`GW :roadbuilder:run --args="probe app/src/main/assets/roads-ch.bin <lat> <lon> <bearing>"`,
using the road's direction as the bearing.

| Place | Expected `match` |
|---|---|
| A1 motorway, e.g. near Kemptthal (around 47.45 N, 8.70 E) | FAST |
| A city street in Zürich, e.g. Seefeldstrasse (around 47.356 N, 8.553 E) | TOWN |
| A rural 80 road between two villages, e.g. Hauptstrasse between Bülach and Embrach | FAST |
| A Tempo-30 residential street | TOWN |
| Stuttgart, 48.78 N, 9.18 E | NOT_COVERED |

Write the five results (coordinates, bearing, match, nearest road) into the commit message of
Step 11. If any check disagrees, investigate it with the `superpowers:systematic-debugging`
skill before committing. A wrong FAST in town is the worst outcome (D10).

- [ ] **Step 11: Commit the road file**

```bash
git -C E:/AndroidProj/SpeedBeep add app/src/main/assets/roads-ch.bin
git -C E:/AndroidProj/SpeedBeep commit -m "Add Swiss road file (OSM data <date from report>)

<report lines>
Spot checks: <five results>

Road data © OpenStreetMap contributors, ODbL.

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

Replace the `<…>` parts with the real report and spot-check lines before running.

- [ ] **Step 12: Checkpoint: report the known gap to the owner**

Send the owner the report, and say what it means for them:

> The road file is built: **<size> MB**. **<gap km> of <main km> km** of main roads
> (**<pct> %**) have no speed limit in OpenStreetMap. That is an upper bound, because it
> includes roads inside towns where 50 is right anyway. On the rural part of those roads,
> SpeedBeep will beep above 53 when it shouldn't; "Quiet this stretch" covers it.

- **If the gap is above 10 %**, also say that the design could be changed to treat
  unlimited main roads outside built-up areas as FAST. That would mean adding place and
  landuse boundaries to the builder. Ask whether they want it.
- **Tasks 6–7 do not depend on this answer**, so continue with them.
- **Plan 2 is not written** until the owner has answered.

---

### Task 6: Fix evaluator

Implements §4.1. It turns a raw GPS fix into a `Reading`, judging speed and position
separately. A poor position never discards a good speed.

**Files:**
- Create: `core/src/main/kotlin/io/github/lrosa2/speedbeep/core/rules/Fix.kt`
- Create: `core/src/main/kotlin/io/github/lrosa2/speedbeep/core/rules/FixEvaluator.kt`
- Test: `core/src/test/kotlin/io/github/lrosa2/speedbeep/core/rules/FixEvaluatorTest.kt`

**Interfaces:**
- Consumes: `RoadLookup`, `RoadMatch` (Task 4).
- Produces:
  - `data class Fix(elapsedMs: Long, lat: Double, lon: Double, speedMps: Float?, speedAccuracyMps: Float?, horizontalAccuracyM: Float?, bearingDeg: Float?)`
  - `data class Reading(timeMs: Long, speedKmh: Double?, match: RoadMatch)`
  - `FixEvaluator.evaluate(fix: Fix, nowMs: Long, simpleMode: Boolean, roads: RoadLookup): Reading`
  - Constants `MAX_AGE_MS = 3_000`, `MAX_FUTURE_MS = 1_000`, `MAX_SPEED_ACCURACY_MPS = 2.0f`,
    `MAX_POSITION_ACCURACY_M = 25.0f`

Plan 2's GPS source fills `Fix` from `android.location.Location`:
- `elapsedMs = elapsedRealtimeNanos / 1_000_000`
- a nullable field is `null` when the matching `has…()` is false

- [ ] **Step 1: Write the failing test**

`core/src/test/kotlin/io/github/lrosa2/speedbeep/core/rules/FixEvaluatorTest.kt`:

```kotlin
package io.github.lrosa2.speedbeep.core.rules

import io.github.lrosa2.speedbeep.core.road.RoadLookup
import io.github.lrosa2.speedbeep.core.road.RoadMatch
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class FixEvaluatorTest {
    private val now = 100_000L
    private var lookups = 0
    private var lastBearing: Double? = null
    private var lastSpeed: Double? = null
    private val roads = RoadLookup { _, _, bearing, speed ->
        lookups++
        lastBearing = bearing
        lastSpeed = speed
        RoadMatch.TOWN
    }

    private fun fix(
        ageMs: Long = 500,
        speedMps: Float? = 15f,
        speedAccuracy: Float? = 0.5f,
        positionAccuracy: Float? = 5f,
        bearing: Float? = 90f,
    ) = Fix(now - ageMs, 47.37, 8.54, speedMps, speedAccuracy, positionAccuracy, bearing)

    private fun evaluate(fix: Fix, simpleMode: Boolean = false) = FixEvaluator.evaluate(fix, now, simpleMode, roads)

    @Test
    fun `a good fix gives km per h, the road match and the reading time`() {
        val r = evaluate(fix())
        assertEquals(54.0, r.speedKmh!!, 1e-9)
        assertEquals(RoadMatch.TOWN, r.match)
        assertEquals(now, r.timeMs)
        assertEquals(90.0, lastBearing!!, 1e-9)
        assertEquals(54.0, lastSpeed!!, 1e-9)
    }

    @Test
    fun `a fix older than 3 s is ignored for speed and road`() {
        val r = evaluate(fix(ageMs = 3_001))
        assertNull(r.speedKmh)
        assertEquals(RoadMatch.NEUTRAL, r.match)
        assertEquals(0, lookups)
    }

    @Test
    fun `a fix exactly 3 s old is still used`() {
        assertEquals(54.0, evaluate(fix(ageMs = 3_000)).speedKmh!!, 1e-9)
    }

    @Test
    fun `a fix stamped slightly ahead of the clock is used, far ahead is not`() {
        assertEquals(54.0, evaluate(fix(ageMs = -500)).speedKmh!!, 1e-9)
        assertNull(evaluate(fix(ageMs = -1_500)).speedKmh)
    }

    @Test
    fun `poor speed accuracy drops only the speed`() {
        val r = evaluate(fix(speedAccuracy = 2.5f))
        assertNull(r.speedKmh)
        assertEquals(RoadMatch.TOWN, r.match)
    }

    @Test
    fun `an unreported speed accuracy is accepted`() {
        assertEquals(54.0, evaluate(fix(speedAccuracy = null)).speedKmh!!, 1e-9)
    }

    @Test
    fun `poor position accuracy keeps the speed but makes the match NEUTRAL`() {
        val r = evaluate(fix(positionAccuracy = 30f))
        assertEquals(54.0, r.speedKmh!!, 1e-9)
        assertEquals(RoadMatch.NEUTRAL, r.match)
        assertEquals(0, lookups)
    }

    @Test
    fun `an unreported position accuracy is NEUTRAL`() {
        assertEquals(RoadMatch.NEUTRAL, evaluate(fix(positionAccuracy = null)).match)
    }

    @Test
    fun `no speed gives no speed`() {
        assertNull(evaluate(fix(speedMps = null)).speedKmh)
    }

    @Test
    fun `simple mode never looks at roads`() {
        val r = evaluate(fix(), simpleMode = true)
        assertEquals(RoadMatch.UNKNOWN, r.match)
        assertEquals(54.0, r.speedKmh!!, 1e-9)
        assertEquals(0, lookups)
    }

    @Test
    fun `without a bearing the lookup gets none`() {
        evaluate(fix(bearing = null))
        assertNull(lastBearing)
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `GW :core:test --tests "*FixEvaluatorTest"`
Expected: compilation FAILS with unresolved `Fix` and `FixEvaluator`.

- [ ] **Step 3: Write `Fix`, `Reading` and `FixEvaluator`**

`core/src/main/kotlin/io/github/lrosa2/speedbeep/core/rules/Fix.kt`:

```kotlin
package io.github.lrosa2.speedbeep.core.rules

import io.github.lrosa2.speedbeep.core.road.RoadMatch

/** One GPS fix as plain values. [elapsedMs] is the monotonic time it was taken; null fields were not reported. */
data class Fix(
    val elapsedMs: Long,
    val lat: Double,
    val lon: Double,
    val speedMps: Float?,
    val speedAccuracyMps: Float?,
    val horizontalAccuracyM: Float?,
    val bearingDeg: Float?,
)

/** What the rules see for one fix (spec §4.1–4.2). [speedKmh] is null when the speed is unusable. */
data class Reading(val timeMs: Long, val speedKmh: Double?, val match: RoadMatch)
```

`core/src/main/kotlin/io/github/lrosa2/speedbeep/core/rules/FixEvaluator.kt`:

```kotlin
package io.github.lrosa2.speedbeep.core.rules

import io.github.lrosa2.speedbeep.core.road.RoadLookup
import io.github.lrosa2.speedbeep.core.road.RoadMatch

/** Fix → Reading (spec §4.1). Speed and position are judged separately. */
object FixEvaluator {
    const val MAX_AGE_MS = 3_000L

    /** The fix clock may run slightly ahead of the reading clock. */
    const val MAX_FUTURE_MS = 1_000L
    const val MAX_SPEED_ACCURACY_MPS = 2.0f
    const val MAX_POSITION_ACCURACY_M = 25.0f

    fun evaluate(fix: Fix, nowMs: Long, simpleMode: Boolean, roads: RoadLookup): Reading {
        val age = nowMs - fix.elapsedMs
        val fresh = age in -MAX_FUTURE_MS..MAX_AGE_MS
        val speedMps = fix.speedMps
        val speedAccuracy = fix.speedAccuracyMps
        val speedKmh = if (fresh && speedMps != null && (speedAccuracy == null || speedAccuracy <= MAX_SPEED_ACCURACY_MPS)) {
            speedMps.toDouble() * 3.6
        } else {
            null
        }
        val positionAccuracy = fix.horizontalAccuracyM
        val positionUsable = fresh && positionAccuracy != null && positionAccuracy <= MAX_POSITION_ACCURACY_M
        val match = when {
            simpleMode -> RoadMatch.UNKNOWN // ignored by the rules in simple mode
            !positionUsable -> RoadMatch.NEUTRAL
            else -> roads.classify(fix.lat, fix.lon, fix.bearingDeg?.toDouble(), speedKmh ?: 0.0)
        }
        return Reading(nowMs, speedKmh, match)
    }
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `GW :core:test`
Expected: `BUILD SUCCESSFUL`, with all `FixEvaluatorTest` tests passing.

- [ ] **Step 5: Commit**

```bash
git -C E:/AndroidProj/SpeedBeep add -A
git -C E:/AndroidProj/SpeedBeep commit -m "Add fix evaluator: speed and position validity judged separately

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 7: Speed rules state machine

Implements §4.3 and §4.4 in full:
- the debounce, the re-arm margin and the reminders
- the FAST road context with hysteresis, the fast-road hold, and NEUTRAL handling
- simple mode and NOT_COVERED
- Quiet, Pause and Resume, and NO_GPS

It is one cohesive state machine, so it is one task. The tests are split across three
files by topic.

**Files:**
- Create: `core/src/main/kotlin/io/github/lrosa2/speedbeep/core/rules/SpeedRules.kt`
- Test: `core/src/test/kotlin/io/github/lrosa2/speedbeep/core/rules/TestDrive.kt` (test helper)
- Test: `core/src/test/kotlin/io/github/lrosa2/speedbeep/core/rules/SpeedRulesSpeedTest.kt`
- Test: `core/src/test/kotlin/io/github/lrosa2/speedbeep/core/rules/SpeedRulesRoadTest.kt`
- Test: `core/src/test/kotlin/io/github/lrosa2/speedbeep/core/rules/SpeedRulesActionsTest.kt`

**Interfaces:**
- Consumes: `Reading` (Task 6) and `RoadMatch` (Task 4).
- Produces, for Plan 2's service:
  - `enum class Phase { ARMED, OVER, QUIET, PAUSED, FAST_ROAD, NO_GPS }`
  - `enum class RoadContext { NORMAL, FAST }`
  - `enum class Sound { NONE, MAIN, REMINDER, STOP }`
  - `data class RulesConfig(thresholdKmh: Int = 53, reminderIntervalMs: Long = 10_000, simpleMode: Boolean = false)`
  - `data class RulesOutput(phase, sound, speedKmh: Double?, roadContext, noRoadData: Boolean, pausedUntilMs: Long?)`
  - `class SpeedRules(config: RulesConfig, startMs: Long)`:
    - `config` (read-only property)
    - `updateConfig(RulesConfig)`
    - `onReading(Reading): RulesOutput`
    - `onTick(nowMs: Long): RulesOutput`, to be called about once a second
    - `quietThisStretch(nowMs): RulesOutput`
    - `pause(nowMs, durationMs): RulesOutput`
    - `resume(nowMs): RulesOutput`
  - Constants `REARM_MARGIN_KMH = 3`, `DEBOUNCE_READINGS = 2`, `FAST_ENTER_READINGS = 5`,
    `FAST_LEAVE_READINGS = 2`, `NEUTRAL_TIMEOUT_MS = 10_000`, `NO_GPS_AFTER_MS = 5_000`

- [ ] **Step 1: Write the test helper**

`core/src/test/kotlin/io/github/lrosa2/speedbeep/core/rules/TestDrive.kt`:

```kotlin
package io.github.lrosa2.speedbeep.core.rules

import io.github.lrosa2.speedbeep.core.road.RoadMatch

/** Feeds readings into [SpeedRules], one second apart by default, starting at t = 0. */
class TestDrive(config: RulesConfig = RulesConfig()) {
    var now = 0L
        private set
    val rules = SpeedRules(config, startMs = 0L)

    fun at(speedKmh: Double?, match: RoadMatch = RoadMatch.TOWN, stepMs: Long = 1_000): RulesOutput {
        now += stepMs
        return rules.onReading(Reading(now, speedKmh, match))
    }

    /** [count] readings at the same speed and match; returns their sounds. */
    fun readings(count: Int, speedKmh: Double?, match: RoadMatch = RoadMatch.TOWN): List<Sound> =
        List(count) { at(speedKmh, match).sound }

    fun tick(stepMs: Long): RulesOutput {
        now += stepMs
        return rules.onTick(now)
    }

    fun quiet() = rules.quietThisStretch(now)
    fun pause(minutes: Int) = rules.pause(now, minutes * 60_000L)
    fun resume() = rules.resume(now)
}
```

- [ ] **Step 2: Write the failing speed tests**

`core/src/test/kotlin/io/github/lrosa2/speedbeep/core/rules/SpeedRulesSpeedTest.kt`:

```kotlin
package io.github.lrosa2.speedbeep.core.rules

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class SpeedRulesSpeedTest {

    @Test
    fun `below the threshold it never beeps`() {
        val d = TestDrive()
        for (speed in listOf(40.0, 50.0, 52.9, 52.9, 52.9)) assertEquals(Sound.NONE, d.at(speed).sound)
        assertEquals(Phase.ARMED, d.at(52.0).phase)
    }

    @Test
    fun `two readings at 53 or more give the main beep`() {
        val d = TestDrive()
        assertEquals(Sound.NONE, d.at(53.0).sound)
        val out = d.at(53.0)
        assertEquals(Sound.MAIN, out.sound)
        assertEquals(Phase.OVER, out.phase)
    }

    @Test
    fun `single spikes do not beep`() {
        val d = TestDrive()
        for (speed in listOf(60.0, 45.0, 60.0, 45.0)) assertEquals(Sound.NONE, d.at(speed).sound)
    }

    @Test
    fun `a reminder sounds every 10 s while over`() {
        val d = TestDrive()
        d.at(60.0)
        assertEquals(Sound.MAIN, d.at(60.0).sound)                  // t = 2 s
        assertEquals(List(9) { Sound.NONE }, d.readings(9, 60.0))   // t = 3..11 s
        assertEquals(Sound.REMINDER, d.at(60.0).sound)              // t = 12 s
        assertEquals(List(9) { Sound.NONE }, d.readings(9, 60.0))
        assertEquals(Sound.REMINDER, d.at(60.0).sound)              // t = 22 s
    }

    @Test
    fun `reminder interval 0 means no reminders`() {
        val d = TestDrive(RulesConfig(reminderIntervalMs = 0))
        d.at(60.0)
        assertEquals(Sound.MAIN, d.at(60.0).sound)
        assertEquals(List(30) { Sound.NONE }, d.readings(30, 60.0))
    }

    @Test
    fun `dipping between 50 and 53 stops the sound but keeps the warning`() {
        val d = TestDrive()
        d.at(60.0)
        d.at(60.0)                                                  // main beep at 2 s
        assertEquals(Sound.STOP, d.at(52.0).sound)
        assertEquals(List(10) { Sound.NONE }, d.readings(10, 51.0)) // up to 13 s
        val back = d.at(60.0)                                       // 14 s: 12 s after the main beep
        assertEquals(Sound.REMINDER, back.sound)
        assertEquals(Phase.OVER, back.phase)
    }

    @Test
    fun `hovering around 53 after a warning never repeats the main beep`() {
        val d = TestDrive()
        d.at(60.0)
        d.at(60.0)
        val sounds = (1..8).map { d.at(if (it % 2 == 0) 54.0 else 52.0).sound }
        assertFalse(Sound.MAIN in sounds, sounds.toString())
    }

    @Test
    fun `dropping to 50 re-arms, and the next crossing gives a new main beep`() {
        val d = TestDrive()
        d.at(60.0)
        d.at(60.0)
        val down = d.at(50.0)
        assertEquals(Sound.STOP, down.sound)
        assertEquals(Phase.ARMED, down.phase)
        assertEquals(Sound.NONE, d.at(60.0).sound)
        assertEquals(Sound.MAIN, d.at(60.0).sound)
    }

    @Test
    fun `5 s without a usable speed gives NO_GPS and stops a running beep`() {
        val d = TestDrive()
        d.at(60.0)
        d.at(60.0) // last usable speed at 2 s
        assertEquals(listOf(Sound.NONE, Sound.NONE, Sound.NONE, Sound.NONE, Sound.STOP), d.readings(5, null))
        val out = d.at(null)
        assertEquals(Phase.NO_GPS, out.phase)
        assertNull(out.speedKmh)
    }

    @Test
    fun `NO_GPS also comes from the clock when no fixes arrive`() {
        val d = TestDrive()
        assertEquals(Phase.ARMED, d.tick(4_999).phase)
        assertEquals(Phase.NO_GPS, d.tick(1).phase)
    }

    @Test
    fun `after NO_GPS the debounce starts over`() {
        val d = TestDrive()
        d.tick(5_000)
        val first = d.at(60.0)
        assertEquals(Sound.NONE, first.sound)
        assertEquals(Phase.ARMED, first.phase)
        assertEquals(Sound.MAIN, d.at(60.0).sound)
    }

    @Test
    fun `raising the threshold while over stops the warning without a new main beep`() {
        val d = TestDrive()
        d.at(57.0)
        d.at(57.0) // main beep at T = 53
        d.rules.updateConfig(RulesConfig(thresholdKmh = 60))
        val atNewThreshold = d.at(58.0)
        assertEquals(Sound.STOP, atNewThreshold.sound)
        assertEquals(Phase.OVER, atNewThreshold.phase)
        assertEquals(List(15) { Sound.NONE }, d.readings(15, 58.0)) // no reminders below the new T
        assertEquals(Phase.ARMED, d.at(57.0).phase)                 // re-arms at the new T − R
    }

    @Test
    fun `the output carries the speed`() {
        assertEquals(47.5, TestDrive().at(47.5).speedKmh!!, 1e-9)
    }
}
```

- [ ] **Step 3: Write the failing road tests**

`core/src/test/kotlin/io/github/lrosa2/speedbeep/core/rules/SpeedRulesRoadTest.kt`:

```kotlin
package io.github.lrosa2.speedbeep.core.rules

import io.github.lrosa2.speedbeep.core.road.RoadMatch.FAST
import io.github.lrosa2.speedbeep.core.road.RoadMatch.NEUTRAL
import io.github.lrosa2.speedbeep.core.road.RoadMatch.NOT_COVERED
import io.github.lrosa2.speedbeep.core.road.RoadMatch.TOWN
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SpeedRulesRoadTest {

    private fun allSilent(sounds: List<Sound>) = assertTrue(sounds.all { it == Sound.NONE }, sounds.toString())

    @Test
    fun `five FAST readings confirm the fast road, silently`() {
        val d = TestDrive()
        val outs = List(5) { d.at(120.0, FAST) }
        allSilent(outs.map { it.sound })
        assertEquals(Phase.ARMED, outs[3].phase)
        assertEquals(Phase.FAST_ROAD, outs[4].phase)
        assertEquals(RoadContext.FAST, outs[4].roadContext)
    }

    @Test
    fun `accelerating past an 80 sign out of town does not beep`() {
        val d = TestDrive()
        val drive = listOf(45.0 to TOWN, 50.0 to TOWN, 54.0 to TOWN, 58.0 to FAST, 63.0 to FAST, 68.0 to FAST, 72.0 to FAST, 76.0 to FAST)
        allSilent(drive.map { (speed, match) -> d.at(speed, match).sound })
        assertEquals(Phase.FAST_ROAD, d.at(80.0, FAST).phase)
    }

    @Test
    fun `speeding on the town road before the sign still beeps`() {
        val d = TestDrive()
        d.at(54.0, TOWN)
        assertEquals(Sound.MAIN, d.at(56.0, TOWN).sound)
    }

    @Test
    fun `a motorway on-ramp from a town road does not beep`() {
        val d = TestDrive()
        d.at(50.0, TOWN)
        allSilent(listOf(60.0, 70.0, 80.0, 90.0, 100.0).map { d.at(it, FAST).sound })
        assertEquals(Phase.FAST_ROAD, d.at(105.0, FAST).phase)
    }

    @Test
    fun `GPS returning on the motorway after a tunnel does not beep`() {
        val d = TestDrive()
        d.readings(5, 110.0, FAST)
        d.readings(8, null)
        assertEquals(Phase.NO_GPS, d.at(null).phase)
        allSilent(d.readings(3, 110.0, FAST))
        assertEquals(Phase.FAST_ROAD, d.at(110.0, FAST).phase)
    }

    @Test
    fun `GPS returning with weak positions after a tunnel does not beep either`() {
        val d = TestDrive()
        d.readings(5, 110.0, FAST)
        d.readings(9, null)
        allSilent(d.readings(3, 110.0, NEUTRAL))
        assertEquals(Phase.FAST_ROAD, d.at(110.0, FAST).phase)
    }

    @Test
    fun `leaving the motorway onto a town road at 70 beeps on the second town reading`() {
        val d = TestDrive()
        d.readings(5, 100.0, FAST)
        val first = d.at(70.0, TOWN)
        assertEquals(Sound.NONE, first.sound)
        assertEquals(Phase.FAST_ROAD, first.phase)
        val second = d.at(70.0, TOWN)
        assertEquals(Sound.MAIN, second.sound)
        assertEquals(Phase.OVER, second.phase)
    }

    @Test
    fun `one stray TOWN reading on the motorway does not beep`() {
        val d = TestDrive()
        d.readings(5, 120.0, FAST)
        val sounds = listOf(TOWN, FAST, TOWN, FAST, FAST).map { d.at(120.0, it).sound }
        allSilent(sounds)
    }

    @Test
    fun `a stray FAST reading does not cut off a running warning`() {
        val d = TestDrive()
        d.at(60.0, TOWN)
        assertEquals(Sound.MAIN, d.at(60.0, TOWN).sound) // t = 2 s
        val stray = d.at(60.0, FAST)
        assertEquals(Sound.NONE, stray.sound)
        assertEquals(Phase.OVER, stray.phase)
        allSilent(d.readings(8, 60.0, TOWN))              // t = 4..11 s
        assertEquals(Sound.REMINDER, d.at(60.0, TOWN).sound)
    }

    @Test
    fun `weak positions in town still beep`() {
        val d = TestDrive()
        assertEquals(listOf(Sound.NONE, Sound.MAIN), d.readings(2, 60.0, NEUTRAL))
    }

    @Test
    fun `a weak position does not break a run of FAST readings`() {
        val d = TestDrive()
        val outs = listOf(FAST, FAST, NEUTRAL, FAST, FAST, FAST).map { d.at(120.0, it) }
        allSilent(outs.map { it.sound })
        assertEquals(Phase.FAST_ROAD, outs.last().phase)
    }

    @Test
    fun `10 s of only weak positions on the motorway falls back to beeping`() {
        val d = TestDrive()
        d.readings(5, 120.0, FAST)
        allSilent(d.readings(10, 120.0, NEUTRAL))  // neutral streak 0..9 s
        assertEquals(Sound.MAIN, d.at(120.0, NEUTRAL).sound) // 10 s
    }

    @Test
    fun `simple mode ignores road data`() {
        val d = TestDrive(RulesConfig(simpleMode = true))
        assertEquals(listOf(Sound.NONE, Sound.MAIN), d.readings(2, 60.0, FAST))
    }

    @Test
    fun `no road data behaves like simple mode and says so`() {
        val d = TestDrive()
        d.at(60.0, NOT_COVERED)
        val out = d.at(60.0, NOT_COVERED)
        assertEquals(Sound.MAIN, out.sound)
        assertTrue(out.noRoadData)
        assertFalse(d.at(60.0, TOWN).noRoadData)
    }

    @Test
    fun `simple mode does not claim missing road data`() {
        assertFalse(TestDrive(RulesConfig(simpleMode = true)).at(60.0, NOT_COVERED).noRoadData)
    }

    @Test
    fun `switching to simple mode on the motorway beeps at once`() {
        val d = TestDrive()
        d.readings(5, 120.0, FAST)
        d.rules.updateConfig(RulesConfig(simpleMode = true))
        assertEquals(Sound.MAIN, d.at(120.0, FAST).sound)
    }
}
```

- [ ] **Step 4: Write the failing action tests**

`core/src/test/kotlin/io/github/lrosa2/speedbeep/core/rules/SpeedRulesActionsTest.kt`:

```kotlin
package io.github.lrosa2.speedbeep.core.rules

import io.github.lrosa2.speedbeep.core.road.RoadMatch
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SpeedRulesActionsTest {

    @Test
    fun `quiet this stretch silences until back at 50`() {
        val d = TestDrive()
        d.at(60.0)
        d.at(60.0)
        val quiet = d.quiet()
        assertEquals(Sound.STOP, quiet.sound)
        assertEquals(Phase.QUIET, quiet.phase)
        assertTrue(d.readings(15, 60.0).all { it == Sound.NONE })
        assertEquals(Phase.QUIET, d.at(52.0).phase)
        assertEquals(Phase.ARMED, d.at(50.0).phase)
        d.at(60.0)
        assertEquals(Sound.MAIN, d.at(60.0).sound)
    }

    @Test
    fun `quiet does nothing unless over`() {
        val d = TestDrive()
        d.at(40.0)
        val out = d.quiet()
        assertEquals(Sound.NONE, out.sound)
        assertEquals(Phase.ARMED, out.phase)
    }

    @Test
    fun `a pause silences for its length, then re-arms`() {
        val d = TestDrive()
        d.at(40.0)
        val paused = d.pause(10)
        assertEquals(Phase.PAUSED, paused.phase)
        assertEquals(d.now + 600_000, paused.pausedUntilMs)
        val during = List(59) { d.at(60.0, stepMs = 10_000).sound }           // up to 590 s
        assertTrue(during.all { it == Sound.NONE })
        assertEquals(Phase.PAUSED, d.at(60.0, stepMs = 9_000).phase)        // 599 s
        val ended = d.at(60.0, stepMs = 1_000)                               // 600 s
        assertEquals(Phase.ARMED, ended.phase)
        assertEquals(Sound.NONE, ended.sound)
        assertNull(ended.pausedUntilMs)
        assertEquals(Sound.MAIN, d.at(60.0).sound)
    }

    @Test
    fun `pausing while over stops the beep`() {
        val d = TestDrive()
        d.at(60.0)
        d.at(60.0)
        val out = d.pause(10)
        assertEquals(Sound.STOP, out.sound)
        assertEquals(Phase.PAUSED, out.phase)
    }

    @Test
    fun `resume ends the pause at once and the debounce starts over`() {
        val d = TestDrive()
        d.at(60.0)
        d.pause(10)
        d.at(60.0)
        assertEquals(Phase.ARMED, d.resume().phase)
        assertEquals(Sound.NONE, d.at(60.0).sound)
        assertEquals(Sound.MAIN, d.at(60.0).sound)
    }

    @Test
    fun `a pause ending on the motorway goes straight to the fast road`() {
        val d = TestDrive()
        d.readings(5, 120.0, RoadMatch.FAST)
        d.pause(10)
        d.readings(3, 120.0, RoadMatch.FAST)
        assertEquals(Phase.FAST_ROAD, d.resume().phase)
    }

    @Test
    fun `a pause ending with no GPS gives NO_GPS, not a stale speed`() {
        val d = TestDrive()
        d.at(30.0)
        d.pause(1)                                    // t = 1 s, until 61 s
        assertEquals(Phase.PAUSED, d.tick(30_000).phase) // no NO_GPS during a pause
        val after = d.tick(30_000)                    // t = 61 s
        assertEquals(Phase.NO_GPS, after.phase)
        assertNull(after.speedKmh)
        assertNull(after.pausedUntilMs)
    }

    @Test
    fun `pause during NO_GPS, then resume, is still NO_GPS`() {
        val d = TestDrive()
        d.tick(5_000)
        assertEquals(Phase.PAUSED, d.pause(5).phase)
        assertEquals(Phase.NO_GPS, d.resume().phase)
    }
}
```

- [ ] **Step 5: Run them to verify they fail**

Run: `GW :core:test`
Expected: compilation FAILS with unresolved `SpeedRules`, `RulesConfig`, `RulesOutput`,
`Phase`, `Sound` and `RoadContext`.

- [ ] **Step 6: Write the state machine**

`core/src/main/kotlin/io/github/lrosa2/speedbeep/core/rules/SpeedRules.kt`:

```kotlin
package io.github.lrosa2.speedbeep.core.rules

import io.github.lrosa2.speedbeep.core.road.RoadMatch

enum class Phase { ARMED, OVER, QUIET, PAUSED, FAST_ROAD, NO_GPS }

enum class RoadContext { NORMAL, FAST }

/** What the beeper should do now. STOP cuts any sound still playing. */
enum class Sound { NONE, MAIN, REMINDER, STOP }

data class RulesConfig(
    val thresholdKmh: Int = 53,
    /** 0 = no reminders. */
    val reminderIntervalMs: Long = 10_000,
    val simpleMode: Boolean = false,
)

data class RulesOutput(
    val phase: Phase,
    val sound: Sound,
    /** Last usable speed; null while NO_GPS. */
    val speedKmh: Double?,
    val roadContext: RoadContext,
    /** Smart mode, but the last reading was outside the road data. */
    val noRoadData: Boolean,
    /** End of the running pause, or null. */
    val pausedUntilMs: Long?,
)

/**
 * The beep state machine of spec §4.3–4.4. Feed it every reading ([onReading]) and a clock
 * tick about once a second ([onTick]), and act on [RulesOutput.sound]. Times are monotonic
 * milliseconds. Not thread-safe.
 */
class SpeedRules(config: RulesConfig, startMs: Long) {

    companion object {
        const val REARM_MARGIN_KMH = 3
        const val DEBOUNCE_READINGS = 2
        const val FAST_ENTER_READINGS = 5
        const val FAST_LEAVE_READINGS = 2
        const val NEUTRAL_TIMEOUT_MS = 10_000L
        const val NO_GPS_AFTER_MS = 5_000L
    }

    var config: RulesConfig = config
        private set

    private var phase = Phase.ARMED
    private var context = RoadContext.NORMAL
    private var fastRun = 0
    private var normalRun = 0
    private var neutralSinceMs: Long? = null
    private var noRoadData = false
    private var overRun = 0
    private var wasAbove = false
    private var lastBeepMs = 0L
    private var lastUsableMs = startMs
    private var lastSpeed: Double? = null
    private var pausedUntilMs: Long? = null

    /** Takes effect from the next reading. */
    fun updateConfig(newConfig: RulesConfig) {
        config = newConfig
    }

    fun onReading(reading: Reading): RulesOutput {
        val speed = reading.speedKmh ?: return onTick(reading.timeMs)
        val now = reading.timeMs
        lastUsableMs = now
        lastSpeed = speed
        updateRoadContext(reading)

        if (phase == Phase.PAUSED) {
            if (now < pausedUntilMs!!) return output(Sound.NONE)
            endPause()
        }
        if (phase == Phase.NO_GPS) phase = Phase.ARMED

        val threshold = config.thresholdKmh
        val above = speed >= threshold
        val aboveBefore = wasAbove
        wasAbove = above
        // Counted on the fast road too, so leaving it while over beeps without a new debounce.
        overRun = if (above) overRun + 1 else 0

        if (context == RoadContext.FAST) {
            val sound = if (phase == Phase.OVER) Sound.STOP else Sound.NONE
            phase = Phase.FAST_ROAD
            return output(sound)
        }
        if (phase == Phase.FAST_ROAD) phase = Phase.ARMED

        val sound = when (phase) {
            Phase.ARMED ->
                if (overRun >= DEBOUNCE_READINGS && !holdForFastRoad(reading)) {
                    phase = Phase.OVER
                    lastBeepMs = now
                    Sound.MAIN
                } else {
                    Sound.NONE
                }
            Phase.OVER -> when {
                speed <= threshold - REARM_MARGIN_KMH -> {
                    phase = Phase.ARMED
                    Sound.STOP
                }
                !above -> if (aboveBefore) Sound.STOP else Sound.NONE
                config.reminderIntervalMs > 0 && now - lastBeepMs >= config.reminderIntervalMs -> {
                    lastBeepMs = now
                    Sound.REMINDER
                }
                else -> Sound.NONE
            }
            Phase.QUIET -> {
                if (speed <= threshold - REARM_MARGIN_KMH) phase = Phase.ARMED
                Sound.NONE
            }
            else -> Sound.NONE
        }
        return output(sound)
    }

    fun onTick(nowMs: Long): RulesOutput {
        if (phase == Phase.PAUSED) {
            if (nowMs < pausedUntilMs!!) return output(Sound.NONE)
            endPause()
        }
        if (phase != Phase.NO_GPS && nowMs - lastUsableMs >= NO_GPS_AFTER_MS) {
            val sound = if (phase == Phase.OVER) Sound.STOP else Sound.NONE
            phase = Phase.NO_GPS
            overRun = 0
            wasAbove = false
            lastSpeed = null
            return output(sound)
        }
        return output(Sound.NONE)
    }

    fun quietThisStretch(nowMs: Long): RulesOutput {
        if (phase != Phase.OVER) return onTick(nowMs)
        phase = Phase.QUIET
        return output(Sound.STOP)
    }

    fun pause(nowMs: Long, durationMs: Long): RulesOutput {
        val sound = if (phase == Phase.OVER) Sound.STOP else Sound.NONE
        phase = Phase.PAUSED
        pausedUntilMs = nowMs + durationMs
        overRun = 0
        return output(sound)
    }

    fun resume(nowMs: Long): RulesOutput {
        if (phase == Phase.PAUSED) endPause()
        return onTick(nowMs)
    }

    private fun endPause() {
        pausedUntilMs = null
        overRun = 0
        wasAbove = false
        phase = if (context == RoadContext.FAST) Phase.FAST_ROAD else Phase.ARMED
    }

    /** §4.3 fast-road hold: this reading itself points at a fast road that is not confirmed yet. */
    private fun holdForFastRoad(reading: Reading): Boolean =
        !config.simpleMode &&
            (reading.match == RoadMatch.FAST || (reading.match == RoadMatch.NEUTRAL && fastRun > 0))

    private fun updateRoadContext(reading: Reading) {
        if (config.simpleMode || reading.match == RoadMatch.NOT_COVERED) {
            noRoadData = !config.simpleMode
            context = RoadContext.NORMAL
            fastRun = 0
            normalRun = 0
            neutralSinceMs = null
            return
        }
        noRoadData = false
        when (reading.match) {
            RoadMatch.FAST -> {
                neutralSinceMs = null
                normalRun = 0
                fastRun++
                if (fastRun >= FAST_ENTER_READINGS) context = RoadContext.FAST
            }
            RoadMatch.TOWN, RoadMatch.UNKNOWN -> {
                neutralSinceMs = null
                fastRun = 0
                normalRun++
                if (normalRun >= FAST_LEAVE_READINGS) context = RoadContext.NORMAL
            }
            RoadMatch.NEUTRAL -> {
                // Neither counts toward a switch nor breaks a run; only a long weak-position streak resets.
                val since = neutralSinceMs ?: reading.timeMs.also { neutralSinceMs = it }
                if (reading.timeMs - since >= NEUTRAL_TIMEOUT_MS) {
                    context = RoadContext.NORMAL
                    fastRun = 0
                }
            }
            RoadMatch.NOT_COVERED -> Unit // handled above
        }
    }

    private fun output(sound: Sound) = RulesOutput(
        phase = phase,
        sound = sound,
        speedKmh = if (phase == Phase.NO_GPS) null else lastSpeed,
        roadContext = context,
        noRoadData = noRoadData,
        pausedUntilMs = pausedUntilMs,
    )
}
```

- [ ] **Step 7: Run the tests to verify they pass**

Run: `GW :core:test`
Expected: `BUILD SUCCESSFUL`. Every test in the three `SpeedRules*Test` classes passes, and
the earlier tests still pass.

If a scenario fails, use `superpowers:systematic-debugging`. Each test's comments give the
expected time of every reading. Check whether the test or the code disagrees with spec
§4.3, and fix whichever is wrong. If the spec itself is ambiguous, say so instead of
guessing.

- [ ] **Step 8: Commit**

```bash
git -C E:/AndroidProj/SpeedBeep add -A
git -C E:/AndroidProj/SpeedBeep commit -m "Add speed rules state machine with fast-road hold, simple mode and pauses

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 8: Close Plan 1 and prepare Plan 2

**Files:**
- Modify: `docs/superpowers/specs/2026-11-25-speedbeep-design.md`, only if Task 1 or Task 5
  produced decisions not yet recorded

- [ ] **Step 1: Run every test**

Run: `GW test`
Expected: `BUILD SUCCESSFUL`, with no skipped or failing tests in `:core` or `:roadbuilder`.

- [ ] **Step 2: Check the Plan 2 inputs**

Confirm, and list in the hand-off message, each of these:
- **Spike result** recorded as D14 in the spec (Task 1 Step 9). If the owner has not
  reported yet, say so: Plan 2's car-link task cannot be written without it.
- **Known-gap answer** from the owner (Task 5 Step 12).
- **`roads-ch.bin`** is committed and under 40 MB.
- **`:core` public API** matches the Interfaces blocks of Tasks 2–7. Check with:
  ```bash
  grep -rhoE "^(class|object|enum class|data class|fun interface) [A-Za-z]+" /e/AndroidProj/SpeedBeep/core/src/main/kotlin | sort
  ```

- [ ] **Step 3: Hand off**

Tell the owner that Plan 1 is done. Say what was verified:
- the test counts
- the road-file size and spot checks
- the spike result

Name the open items. Then write Plan 2 (the Android app) with the
`superpowers:writing-plans` skill. Plan 2 starts from:
- spec §3, §5, §7 and §9 step 4
- the spike decision (D14)
- the `:core` interfaces above
