# SpeedBeep Plan 2: the Android app

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the SpeedBeep app on top of Plan 1's `:core` and road file, so the owner
can test everything in one car trip: auto-start, beeps on both outputs, lock-screen
actions, and a recorded drive.

**Architecture:**
- **Pure engine, thin Android shell.** Every decision that can be made without Android
  lives in plain Kotlin under `io.github.lrosa2.speedbeep.engine` and is tested on the JVM:
  - the watch session (fix → `FixEvaluator` → `SpeedRules`)
  - status text and notification buttons
  - tone PCM and the reminder/main-beep overlap rule
  - settings ranges and storage format
  - road-file loading, the event log
  - drive recording and replay
  - the car-presence policy with its 30 s grace
- **The Android classes only translate:** `WatchService`, `Beeper`, `GpsSource`, the
  notifications, the tile, the car link and the Compose screens.
  They are checked on the emulator with adb.
- **Single thread.** Fixes, ticks, notification actions and settings changes all run on
  the main thread. `SpeedRules`, `RoadMatcher` and `RoadFile` are not thread-safe.
- **Car link.**
  - The pure policy (`CarPolicy`) is done now.
  - The companion route (D12) is the default transport.
  - The §5.1 notification fallback is compiled in, switchable in settings and off by
    default, so one car trip can try both routes. See D16.

**Tech Stack:**
- Kotlin 2.3.21, AGP 9.4.1 (built-in Kotlin), Gradle 9.6.0, JDK 21.
- Jetpack Compose (BOM 2026.09.00, Material 3), DataStore Preferences 1.3.0.
- JUnit 6 for the JVM tests. Emulator `Pixel_7_API_34` for the on-device checks.

**Spec:** `docs/superpowers/specs/2026-11-25-speedbeep-design.md`. § numbers below refer
to it. Plan 1 (`docs/superpowers/plans/2026-11-25-speedbeep-plan1-core.md`) built `:core`,
`:roadbuilder` and `app/src/main/assets/roads-ch.bin`.

## Why the car link is split

The spike (Plan 1 Task 1) is still waiting for the owner's car test. The owner asked for
the app to be finished as far as possible before that trip. So:
- Task 9 builds the companion route the spec chose (D12).
- Task 10 builds the §5.1 fallback behind a setting, so the same trip can test it if the
  companion route fails.
- D14 is recorded from the spike log after the trip, and decides the default.

## Global Constraints

- Repository `E:\AndroidProj\SpeedBeep`, branch **`plan2-app`**, cut from `plan1-core`
  (`ceb9a7d`). Local only: no push, no merge to `main` in this plan.
- Package `io.github.lrosa2.speedbeep`. Sub-packages:
  - `engine`: pure Kotlin, no `android.*` imports, JVM-tested
  - `watch`: service, beeper, GPS, notifications, tile
  - `car`: companion link, presence service, fallback receiver
  - `ui`: activity and Compose screens
- Kotlin sources go under `app/src/<set>/java/` (same as the sibling projects).
  Source sets used: `main`, `debug` (replay), `release`, `test`.
- Toolchain: copy versions from `E:\AndroidProj\CarCare\gradle\libs.versions.toml`:
  - AGP, Compose, DataStore, lifecycle and activity
  - **not** Room, KSP or serialization
- Android levels: compileSdk 37, targetSdk 36, minSdk 34.
- Debug builds use applicationId `io.github.lrosa2.speedbeep.debug`.
- `parallelpbf` (GPLv3) must never reach the app. Task 1 checks `releaseRuntimeClasspath`.
- Every value from the spec has one home:
  - Rules values live in `:core`.
  - App values are named constants:
    - `CarPolicy.GRACE_MS` = 30 s
    - `Tones.REMINDER_MS` = 400 ms
    - the `Settings` ranges of §7.3
  - Settings defaults are taken from `RulesConfig()` where `:core` already has them.
- Gradle from Git Bash: `/e/AndroidProj/SpeedBeep/gradlew -p /e/AndroidProj/SpeedBeep
  <task>`, written here as `GW <task>`. `GT <tasks>` is the workspace helper `gtest.sh`.
  It reruns the tests and prints one line per module, `<module>: N tests, F failures, E errors`, from
  that module's JUnit XML.
- adb and the emulator:
  - `ADB` = `/c/Android/sdk/platform-tools/adb.exe`.
  - Start the emulator headless in the background:
    `/c/Android/sdk/emulator/emulator.exe -avd Pixel_7_API_34 -no-window -no-audio -no-boot-anim -no-snapshot-save`
  - Stop it with `ADB emu kill`.
  - The only AVD is API 34, so the API 36 presence branch is not exercised there.
- Debug package `PKG` = `io.github.lrosa2.speedbeep.debug`. The event log is read with
  `ADB shell run-as PKG cat files/events.log`.
- Every commit message ends with `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.
- Never commit `keystore.properties`, `*.jks` or `*.keystore`.

## Review Focus

Each item has a test in the task named.

1. **Replay on the wrong clock.** Recorded fix times come from another boot. Unless
   rebased onto the live monotonic clock, `FixEvaluator` drops every fix as stale and the
   replay shows only NO_GPS. Task 5, `ReplayTest`.
2. **A long main beep and a short reminder interval.** The main beep can last up to 10 s
   and reminders can come every 3 s. A reminder must never cut into a main beep that is
   still playing. Task 4, `SoundPolicyTest`.
3. **Stop, then a Bluetooth drop-out.** If the car drops and returns within 30 s after the
   owner pressed Stop, watching must stay stopped. Task 9, `CarPolicyTest`.
4. **Road file copy interrupted, damaged or missing.** The app must run in simple mode
   with a reason and never crash. The next start copies again. Task 1, `RoadStoreTest`.
5. **Pause end shown in the wrong time base.** The rules use monotonic time, but
   "Paused until 14:32" is clock time. Task 3, `StatusTextTest`.

Not unit-testable and checked on the emulator instead:
- `startForeground` comes first; a refused start ends in a notification, not a crash
  (Task 6).
- Notification actions work (Task 11).

---

## File structure

```
app/
  build.gradle.kts
  src/main/AndroidManifest.xml
  src/main/res/  values/strings.xml, values/themes.xml, values/colors.xml,
                 drawable/ic_launcher_foreground.xml, drawable/ic_stat_speed.xml,
                 mipmap-anydpi/ic_launcher.xml, xml/file_paths.xml
  src/main/java/io/github/lrosa2/speedbeep/
    App.kt, Graph.kt                     process start, the few singletons
    engine/EventLog.kt                   shareable event log (no positions)
    engine/RoadStore.kt                  asset → filesDir → mmap → RoadMatcher (§6.3)
    engine/Settings.kt                   settings, ranges, storage codec (§7.3)
    engine/WatchSession.kt               fix → rules → status + sound (§4)
    engine/StatusText.kt                 notification line and buttons (§5.3)
    engine/Tones.kt                      built-in sounds as PCM (§7.2)
    engine/SoundPolicy.kt                main beep vs reminder overlap
    engine/DriveCsv.kt, Replay.kt, DriveRecorder.kt   recording and replay (§9)
    engine/Checklist.kt                  setup checklist items (§7.3)
    engine/CarPolicy.kt                  car presence, 30 s grace, stopped note (§5.1, §5.3)
    settings/SettingsRepository.kt       DataStore Preferences
    watch/WatchService.kt, WatchBus.kt, WatchStarter.kt, WatchNotifications.kt
    watch/Beeper.kt, LocationSource.kt (GpsSource), StartStopTile.kt
    car/CarLink.kt, CarController.kt, CarPresenceService.kt, AclReceiver.kt
    ui/MainActivity.kt, AppScreens.kt, MainScreen.kt, SettingsScreen.kt,
       CarSettings.kt, AboutScreen.kt, Theme.kt, Sharing.kt, SetupProbe.kt
  src/debug/java/.../watch/LocationSources.kt, ReplaySource.kt
  src/release/java/.../watch/LocationSources.kt
  src/test/java/io/github/lrosa2/speedbeep/engine/*Test.kt
tools/replay/make-replays.sh             synthetic drives for the emulator
docs/IN-CAR-TEST.md                      the owner's one-trip test protocol
```

---

### Task 1: App module, event log and road store

**Files:**
- Modify: `gradle/libs.versions.toml`, `build.gradle.kts`, `settings.gradle.kts`
- Create: `app/build.gradle.kts`, `app/src/main/AndroidManifest.xml`, the res files listed in
  File structure, `App.kt`, `Graph.kt`, `ui/MainActivity.kt` (placeholder)
- Create: `engine/EventLog.kt`, `engine/RoadStore.kt`
- Test: `app/src/test/java/io/github/lrosa2/speedbeep/engine/EventLogTest.kt`, `RoadStoreTest.kt`

**Interfaces:**
- Consumes (`:core`):
  - `RoadFile.parse(ByteBuffer)` and `RoadFile.dataTimestampSec`
  - `RoadMatcher(file)`, `NoRoadData`, `RoadLookup`, `RoadFileFormatException`
  - `RoadFileWriter.write(roads, dataTs, buildTs, out)` and `Polyline(roadClass, latE6, lonE6)`
- Produces:
  - `EventLog(file: File, maxBytes: Long = MAX_BYTES, zone: ZoneId = systemDefault, clock: () -> Long)`
    with `log(String)`, `read(): String` and `clear()`
  - `LoadedRoads(lookup: RoadLookup, dataTimestampSec: Long?, problem: String?)`
  - `RoadStore(dir: File, versionKey: String, openAsset: () -> InputStream).load(): LoadedRoads`, `RoadStore.FILE_NAME`
  - `Graph.events: EventLog`, `Graph.roads: MutableStateFlow<LoadedRoads?>`, `Graph.init(app)`

- [ ] **Step 1: Write the build files**

`gradle/libs.versions.toml`:

```toml
[versions]
# Toolchain matches the sibling projects (CarCare, Singletrail, HomeBasket).
agp = "9.4.1"
kotlin = "2.3.21"
coreKtx = "1.19.1"
lifecycle = "2.11.0"
activityCompose = "1.13.0"
composeBom = "2026.09.00"
datastore = "1.3.0"
junit = "6.1.3"
# OSM .osm.pbf reader, used only by :roadbuilder (GPLv3, never shipped in the app).
parallelpbf = "0.3.1"
# parallelpbf logs through slf4j 1.7; without a binding its parse errors are lost.
slf4j = "1.7.36"

[libraries]
androidx-core-ktx = { group = "androidx.core", name = "core-ktx", version.ref = "coreKtx" }
androidx-lifecycle-runtime-ktx = { group = "androidx.lifecycle", name = "lifecycle-runtime-ktx", version.ref = "lifecycle" }
androidx-lifecycle-runtime-compose = { group = "androidx.lifecycle", name = "lifecycle-runtime-compose", version.ref = "lifecycle" }
androidx-activity-compose = { group = "androidx.activity", name = "activity-compose", version.ref = "activityCompose" }
androidx-compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "composeBom" }
androidx-compose-ui = { group = "androidx.compose.ui", name = "ui" }
androidx-compose-material3 = { group = "androidx.compose.material3", name = "material3" }
androidx-datastore-preferences = { group = "androidx.datastore", name = "datastore-preferences", version.ref = "datastore" }
junit-bom = { group = "org.junit", name = "junit-bom", version.ref = "junit" }
junit-jupiter = { group = "org.junit.jupiter", name = "junit-jupiter" }
junit-platform-launcher = { group = "org.junit.platform", name = "junit-platform-launcher" }
parallelpbf = { group = "com.wolt.osm", name = "parallelpbf", version.ref = "parallelpbf" }
slf4j-simple = { group = "org.slf4j", name = "slf4j-simple", version.ref = "slf4j" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-jvm = { id = "org.jetbrains.kotlin.jvm", version.ref = "kotlin" }
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
```

`build.gradle.kts`:

```kotlin
// Plugins are declared once here and applied per module. AGP 9 compiles Kotlin in Android
// modules itself, so org.jetbrains.kotlin.android is not used (same as the sibling projects).
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.jvm) apply false
}
```

In `settings.gradle.kts`, replace `include(":core", ":roadbuilder")` with
`include(":core", ":roadbuilder", ":app")`.

`app/build.gradle.kts`:

```kotlin
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Release signing: the shared key of the sibling projects. keystore.properties (git-ignored)
// points at it; without that file, release builds are simply unsigned.
val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
val hasReleaseKey = keystoreProperties.getProperty("storeFile") != null

android {
    namespace = "io.github.lrosa2.speedbeep"
    // Compile against 37 because current AndroidX/Compose requires it; the app targets 36.
    compileSdk = 37

    defaultConfig {
        applicationId = "io.github.lrosa2.speedbeep"
        minSdk = 34
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    signingConfigs {
        if (hasReleaseKey) {
            create("release") {
                storeFile = file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            // Installs side by side with the release build.
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = false
            if (hasReleaseKey) signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests.all { it.useJUnitPlatform() }
    }
}

dependencies {
    implementation(project(":core"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.datastore.preferences)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}
```

`app/src/main/AndroidManifest.xml` (components are added by the task that writes them):

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
    <uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
    <uses-permission android:name="android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS" />

    <application
        android:name=".App"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:supportsRtl="true"
        android:theme="@style/Theme.SpeedBeep">

        <activity
            android:name=".ui.MainActivity"
            android:exported="true"
            android:launchMode="singleTop">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
```

`app/src/main/res/values/strings.xml`:

```xml
<resources>
    <string name="app_name">SpeedBeep</string>
    <string name="tile_label">SpeedBeep</string>
</resources>
```

`app/src/main/res/values/themes.xml`:

```xml
<resources>
    <!-- Compose draws everything; this only sets the window before the first frame. -->
    <style name="Theme.SpeedBeep" parent="android:Theme.Material.NoActionBar" />
</resources>
```

`app/src/main/res/values/colors.xml`:

```xml
<resources>
    <color name="ic_launcher_background">#C62828</color>
</resources>
```

`app/src/main/res/drawable/ic_launcher_foreground.xml`:

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <path
        android:pathData="M32,68 A22,22 0 1,1 76,68"
        android:strokeWidth="6"
        android:strokeColor="#FFFFFF"
        android:strokeLineCap="round" />
    <path
        android:pathData="M54,66 L68,46"
        android:strokeWidth="5"
        android:strokeColor="#FFFFFF"
        android:strokeLineCap="round" />
</vector>
```

`app/src/main/res/drawable/ic_stat_speed.xml`:

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:pathData="M4,17 A8,8 0 1,1 20,17"
        android:strokeWidth="2"
        android:strokeColor="#FFFFFF"
        android:strokeLineCap="round" />
    <path
        android:pathData="M12,16 L16,10"
        android:strokeWidth="2"
        android:strokeColor="#FFFFFF"
        android:strokeLineCap="round" />
</vector>
```

`app/src/main/res/mipmap-anydpi/ic_launcher.xml`:

```xml
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/ic_launcher_background" />
    <foreground android:drawable="@drawable/ic_launcher_foreground" />
</adaptive-icon>
```

`app/src/main/java/io/github/lrosa2/speedbeep/ui/MainActivity.kt` (placeholder, replaced in Task 7):

```kotlin
package io.github.lrosa2.speedbeep.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { Text("SpeedBeep") } }
    }
}
```

- [ ] **Step 2: Write the failing tests**

`app/src/test/java/io/github/lrosa2/speedbeep/engine/EventLogTest.kt`:

```kotlin
package io.github.lrosa2.speedbeep.engine

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.time.ZoneId
import java.time.ZonedDateTime

class EventLogTest {
    @TempDir
    lateinit var dir: File

    private val zurich = ZoneId.of("Europe/Zurich")
    private val at = ZonedDateTime.of(2026, 11, 25, 14, 22, 5, 0, zurich).toInstant().toEpochMilli()

    private fun log(maxBytes: Long = EventLog.MAX_BYTES) = EventLog(File(dir, "events.log"), maxBytes, zurich) { at }

    @Test
    fun `each event is one line with date and time`() {
        val log = log()
        log.log("watching started (app)")
        assertEquals("11-25 14:22:05 watching started (app)\n", log.read())
    }

    @Test
    fun `a missing log reads as empty`() {
        assertEquals("", log().read())
    }

    @Test
    fun `a full log keeps its newest whole lines`() {
        val log = log(maxBytes = 1_000)
        repeat(100) { log.log("event number $it") }
        val text = log.read()
        assertTrue(text.length <= 1_000)
        assertTrue(text.endsWith("event number 99\n"))
        assertTrue(text.startsWith("11-25 14:22:05 event number "))
    }

    @Test
    fun `clear empties the log`() {
        val log = log()
        log.log("x")
        log.clear()
        assertEquals("", log.read())
    }
}
```

`app/src/test/java/io/github/lrosa2/speedbeep/engine/RoadStoreTest.kt`:

```kotlin
package io.github.lrosa2.speedbeep.engine

import io.github.lrosa2.speedbeep.core.road.Polyline
import io.github.lrosa2.speedbeep.core.road.RoadClass
import io.github.lrosa2.speedbeep.core.road.RoadFileWriter
import io.github.lrosa2.speedbeep.core.road.RoadMatch
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream

class RoadStoreTest {
    @TempDir
    lateinit var dir: File

    /** One fast road running east from 47.0 / 8.0. */
    private val roadBytes: ByteArray = ByteArrayOutputStream().also {
        val road = Polyline(RoadClass.FAST, intArrayOf(47_000_000, 47_000_000), intArrayOf(8_000_000, 8_001_000))
        RoadFileWriter.write(listOf(road), 1_700_000_000L, 1_700_000_100L, it)
    }.toByteArray()

    private var opened = 0

    private fun store(key: String, bytes: ByteArray? = roadBytes) = RoadStore(dir, key) {
        opened++
        if (bytes == null) throw FileNotFoundException("roads-ch.bin") else ByteArrayInputStream(bytes)
    }

    @Test
    fun `the first start copies the road file and matches roads`() {
        val roads = store("1-100").load()
        assertNull(roads.problem)
        assertEquals(1_700_000_000L, roads.dataTimestampSec)
        assertEquals(RoadMatch.FAST, roads.lookup.classify(47.0, 8.0005, 90.0, 50.0))
        assertEquals(1, opened)
    }

    @Test
    fun `the same version does not copy again`() {
        store("1-100").load()
        store("1-100").load()
        assertEquals(1, opened)
    }

    @Test
    fun `a new version copies again`() {
        store("1-100").load()
        store("2-200").load()
        assertEquals(2, opened)
    }

    @Test
    fun `a damaged file gives simple mode with a reason`() {
        val roads = store("1-100", "not a road file".toByteArray()).load()
        assertTrue(roads.problem!!.startsWith("road file damaged"))
        assertEquals(RoadMatch.NOT_COVERED, roads.lookup.classify(47.0, 8.0005, 90.0, 50.0))
    }

    @Test
    fun `a missing asset gives simple mode with a reason`() {
        val roads = store("1-100", bytes = null).load()
        assertTrue(roads.problem!!.startsWith("road file unavailable"))
        assertEquals(RoadMatch.NOT_COVERED, roads.lookup.classify(47.0, 8.0005, 90.0, 50.0))
    }

    @Test
    fun `an interrupted copy is not used and the next start copies again`() {
        // The asset stream fails half-way, as when the phone runs out of space.
        val failing = RoadStore(dir, "1-100") {
            object : InputStream() {
                var n = 0
                override fun read(): Int = if (n++ < 100) 1 else throw IOException("disk full")
            }
        }
        assertTrue(failing.load().problem!!.startsWith("road file unavailable"))
        assertNull(store("1-100").load().problem)
    }
}
```

- [ ] **Step 3: Run the tests and watch them fail**

Run: `GW :app:testDebugUnitTest`
Expected: FAIL at compile time with `Unresolved reference 'EventLog'` and `'RoadStore'`.

- [ ] **Step 4: Write the event log and the road store**

`app/src/main/java/io/github/lrosa2/speedbeep/engine/EventLog.kt`:

```kotlin
package io.github.lrosa2.speedbeep.engine

import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * A plain-text log the owner can share after a drive (spec §9 step 5). It records what the
 * app decided, never positions. Thread-safe. When it grows past [maxBytes] the older half is
 * dropped, at a line boundary.
 */
class EventLog(
    private val file: File,
    private val maxBytes: Long = MAX_BYTES,
    zone: ZoneId = ZoneId.systemDefault(),
    private val clock: () -> Long = System::currentTimeMillis,
) {
    companion object {
        const val MAX_BYTES = 256L * 1024
    }

    private val time = DateTimeFormatter.ofPattern("MM-dd HH:mm:ss", Locale.ROOT).withZone(zone)

    @Synchronized
    fun log(message: String) {
        file.parentFile?.mkdirs()
        file.appendText("${time.format(Instant.ofEpochMilli(clock()))} $message\n")
        if (file.length() > maxBytes) trim()
    }

    @Synchronized
    fun read(): String = if (file.isFile) file.readText() else ""

    @Synchronized
    fun clear() {
        file.delete()
    }

    private fun trim() {
        val text = file.readText()
        val from = (text.length - (maxBytes / 2).toInt()).coerceAtLeast(0)
        val cut = text.indexOf('\n', from)
        file.writeText(if (cut < 0) "" else text.substring(cut + 1))
    }
}
```

`app/src/main/java/io/github/lrosa2/speedbeep/engine/RoadStore.kt`:

```kotlin
package io.github.lrosa2.speedbeep.engine

import io.github.lrosa2.speedbeep.core.road.NoRoadData
import io.github.lrosa2.speedbeep.core.road.RoadFile
import io.github.lrosa2.speedbeep.core.road.RoadFileFormatException
import io.github.lrosa2.speedbeep.core.road.RoadLookup
import io.github.lrosa2.speedbeep.core.road.RoadMatcher
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.nio.channels.FileChannel
import java.nio.file.StandardOpenOption

/** The app's road data. [problem] is set when the app must run in simple mode everywhere (spec §6.3). */
class LoadedRoads(val lookup: RoadLookup, val dataTimestampSec: Long?, val problem: String?)

/**
 * Copies the bundled road file into [dir] once per [versionKey], then memory-maps it
 * read-only (spec §6.3). Each version gets its own file name, so a mapped file is never
 * overwritten. Never throws: any failure gives [NoRoadData] and a [LoadedRoads.problem].
 * The first load copies 16.8 MB, so call it off the main thread.
 */
class RoadStore(private val dir: File, versionKey: String, private val openAsset: () -> InputStream) {
    companion object {
        /** Asset name in the APK. */
        const val FILE_NAME = "roads-ch.bin"
        private const val PREFIX = "roads-ch-"
    }

    private val target = File(dir, PREFIX + versionKey.filter { it.isLetterOrDigit() || it == '-' } + ".bin")

    fun load(): LoadedRoads = try {
        if (!target.isFile) copy()
        val bytes = FileChannel.open(target.toPath(), StandardOpenOption.READ).use {
            it.map(FileChannel.MapMode.READ_ONLY, 0, it.size())
        }
        val file = RoadFile.parse(bytes)
        LoadedRoads(RoadMatcher(file), file.dataTimestampSec, null)
    } catch (e: RoadFileFormatException) {
        target.delete() // copy again on the next start; best effort while still mapped
        LoadedRoads(NoRoadData, null, "road file damaged (${e.message})")
    } catch (e: IOException) {
        LoadedRoads(NoRoadData, null, "road file unavailable (${e.message})")
    }

    private fun copy() {
        dir.mkdirs()
        // Copy to a temporary name first, so an interrupted copy is never taken for the real file.
        val tmp = File(dir, "$PREFIX.tmp")
        openAsset().use { input -> tmp.outputStream().use { input.copyTo(it, 1 shl 16) } }
        if (!tmp.renameTo(target)) throw IOException("cannot rename ${tmp.name}")
        // Older versions' copies. One still mapped elsewhere may refuse, which is harmless.
        dir.listFiles { f -> f.name.startsWith(PREFIX) && f.name.endsWith(".bin") && f != target }
            ?.forEach { it.delete() }
    }
}
```

- [ ] **Step 5: Run the tests and watch them pass**

Run: `GW :app:testDebugUnitTest`
Expected: `BUILD SUCCESSFUL`. `EventLogTest` 4/4 and `RoadStoreTest` 6/6 pass.

- [ ] **Step 6: Load the road file at process start**

`app/src/main/java/io/github/lrosa2/speedbeep/App.kt`:

```kotlin
package io.github.lrosa2.speedbeep

import android.app.Application

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        Graph.init(this)
    }
}
```

`app/src/main/java/io/github/lrosa2/speedbeep/Graph.kt`:

```kotlin
package io.github.lrosa2.speedbeep

import android.app.Application
import io.github.lrosa2.speedbeep.engine.EventLog
import io.github.lrosa2.speedbeep.engine.LoadedRoads
import io.github.lrosa2.speedbeep.engine.RoadStore
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

    /** Null until the road file is loaded (a few seconds on the first start of a new version). */
    val roads = MutableStateFlow<LoadedRoads?>(null)

    private val io = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun init(app: Application) {
        events = EventLog(File(app.filesDir, "events.log"))
        io.launch { roads.value = loadRoads(app) }
    }

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
```

- [ ] **Step 7: Build the APK and check the license boundary**

Run: `GW :app:assembleDebug`
Expected: `BUILD SUCCESSFUL`, and `app/build/outputs/apk/debug/app-debug.apk` exists.

Run: `GW :app:dependencies --configuration releaseRuntimeClasspath | grep -c parallelpbf`
Expected: `0`.

- [ ] **Step 8: Commit**

```bash
git -C E:/AndroidProj/SpeedBeep add gradle/libs.versions.toml build.gradle.kts settings.gradle.kts app
git -C E:/AndroidProj/SpeedBeep commit -m "App module: event log, road store, process start

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 2: Settings

**Files:**
- Create: `engine/Settings.kt`, `settings/SettingsRepository.kt`
- Modify: `Graph.kt` (adds `settings`)
- Test: `engine/SettingsCodecTest.kt`

**Interfaces:**
- Consumes: `RulesConfig(thresholdKmh, reminderIntervalMs, simpleMode)` from `:core`.
- Produces:
  - enums `Mode { SMART, SIMPLE }`, `Output { PHONE_SPEAKER, CAR_SPEAKERS }`,
    `SoundKind { BEEP, DOUBLE_BEEP, CHIME, PHONE_ALARM }`, `CarStart { AUTOMATIC, NOTIFICATION }`
  - `data class Settings(thresholdKmh, mode, mainBeepMs, reminderIntervalS, sound, alarmUri, alarmTitle, volumePercent, output, pauseMin, keepScreenOn, recordDrives, carStart, carAddress, carName)`
    with `clamped()`, `simpleMode`, `toRulesConfig()`, `stepThreshold(delta)`, `stepReminder(delta)`
  - `SettingsCodec.encode(Settings): Map<String, Any>` and `decode(Map<String, Any?>): Settings`
  - `SettingsRepository(context)` with `settings: Flow<Settings>` and
    `suspend update(transform: (Settings) -> Settings)`
  - `Graph.settings: SettingsRepository`

- [ ] **Step 1: Write the failing test**

`app/src/test/java/io/github/lrosa2/speedbeep/engine/SettingsCodecTest.kt`:

```kotlin
package io.github.lrosa2.speedbeep.engine

import io.github.lrosa2.speedbeep.core.rules.RulesConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SettingsCodecTest {
    @Test
    fun `no saved settings gives the spec defaults`() {
        val s = SettingsCodec.decode(emptyMap())
        assertEquals(53, s.thresholdKmh)
        assertEquals(Mode.SMART, s.mode)
        assertEquals(2_000, s.mainBeepMs)
        assertEquals(10, s.reminderIntervalS)
        assertEquals(SoundKind.BEEP, s.sound)
        assertEquals(80, s.volumePercent)
        assertEquals(Output.PHONE_SPEAKER, s.output)
        assertEquals(10, s.pauseMin)
        assertTrue(s.keepScreenOn)
        assertFalse(s.recordDrives)
        assertEquals(CarStart.AUTOMATIC, s.carStart)
        assertNull(s.carAddress)
    }

    @Test
    fun `settings survive a save and load`() {
        val s = Settings(
            thresholdKmh = 60, mode = Mode.SIMPLE, mainBeepMs = 3_500, reminderIntervalS = 0,
            sound = SoundKind.PHONE_ALARM, alarmUri = "content://media/1", alarmTitle = "Argon",
            volumePercent = 55, output = Output.CAR_SPEAKERS, pauseMin = 25, keepScreenOn = false,
            recordDrives = true, carStart = CarStart.NOTIFICATION, carAddress = "AA:BB:CC:DD:EE:FF", carName = "Yaris",
        )
        assertEquals(s, SettingsCodec.decode(SettingsCodec.encode(s)))
    }

    @Test
    fun `values outside the ranges are pulled in`() {
        val s = SettingsCodec.decode(
            mapOf("thresholdKmh" to 5, "mainBeepMs" to 60_000, "reminderIntervalS" to 1, "volumePercent" to 140, "pauseMin" to 0),
        )
        assertEquals(20, s.thresholdKmh)
        assertEquals(10_000, s.mainBeepMs)
        assertEquals(3, s.reminderIntervalS)
        assertEquals(100, s.volumePercent)
        assertEquals(1, s.pauseMin)
    }

    @Test
    fun `reminders can be turned off`() {
        assertEquals(0, SettingsCodec.decode(mapOf("reminderIntervalS" to 0)).reminderIntervalS)
    }

    @Test
    fun `an unknown saved name falls back to the default`() {
        assertEquals(Mode.SMART, SettingsCodec.decode(mapOf("mode" to "TURBO")).mode)
    }

    @Test
    fun `a phone alarm sound without its address falls back to the beep`() {
        assertEquals(SoundKind.BEEP, SettingsCodec.decode(mapOf("sound" to "PHONE_ALARM")).sound)
    }

    @Test
    fun `the rules get threshold, reminder interval and mode`() {
        assertEquals(
            RulesConfig(thresholdKmh = 60, reminderIntervalMs = 15_000, simpleMode = true),
            Settings(thresholdKmh = 60, reminderIntervalS = 15, mode = Mode.SIMPLE).toRulesConfig(),
        )
    }

    @Test
    fun `the threshold stepper stays in range`() {
        assertEquals(54, Settings().stepThreshold(+1).thresholdKmh)
        assertEquals(150, Settings(thresholdKmh = 150).stepThreshold(+1).thresholdKmh)
        assertEquals(20, Settings(thresholdKmh = 20).stepThreshold(-1).thresholdKmh)
    }

    @Test
    fun `the reminder stepper goes from off straight to 3 s and back`() {
        assertEquals(3, Settings(reminderIntervalS = 0).stepReminder(+1).reminderIntervalS)
        assertEquals(0, Settings(reminderIntervalS = 3).stepReminder(-1).reminderIntervalS)
        assertEquals(11, Settings(reminderIntervalS = 10).stepReminder(+1).reminderIntervalS)
        assertEquals(60, Settings(reminderIntervalS = 60).stepReminder(+1).reminderIntervalS)
    }
}
```

- [ ] **Step 2: Run it and watch it fail**

Run: `GW :app:testDebugUnitTest`
Expected: FAIL at compile time with `Unresolved reference 'SettingsCodec'`.

- [ ] **Step 3: Write the settings**

`app/src/main/java/io/github/lrosa2/speedbeep/engine/Settings.kt`:

```kotlin
package io.github.lrosa2.speedbeep.engine

import io.github.lrosa2.speedbeep.core.rules.RulesConfig

enum class Mode { SMART, SIMPLE }

enum class Output { PHONE_SPEAKER, CAR_SPEAKERS }

enum class SoundKind { BEEP, DOUBLE_BEEP, CHIME, PHONE_ALARM }

/** How the linked car starts watching: the companion route (D12) or the §5.1 notification fallback (D16). */
enum class CarStart { AUTOMATIC, NOTIFICATION }

private val RULE_DEFAULTS = RulesConfig()

/** The owner's settings (spec §7.3). Always use [clamped] values. */
data class Settings(
    val thresholdKmh: Int = RULE_DEFAULTS.thresholdKmh,
    val mode: Mode = Mode.SMART,
    val mainBeepMs: Int = 2_000,
    /** 0 = no reminders. */
    val reminderIntervalS: Int = (RULE_DEFAULTS.reminderIntervalMs / 1_000).toInt(),
    val sound: SoundKind = SoundKind.BEEP,
    /** Only used with [SoundKind.PHONE_ALARM]. */
    val alarmUri: String? = null,
    val alarmTitle: String? = null,
    val volumePercent: Int = 80,
    val output: Output = Output.PHONE_SPEAKER,
    val pauseMin: Int = 10,
    val keepScreenOn: Boolean = true,
    val recordDrives: Boolean = false,
    val carStart: CarStart = CarStart.AUTOMATIC,
    /** Bluetooth address of the linked car, upper case; null when no car is linked. */
    val carAddress: String? = null,
    val carName: String? = null,
) {
    companion object {
        val THRESHOLD_KMH = 20..150
        val MAIN_BEEP_MS = 500..10_000
        val REMINDER_S = 3..60
        val VOLUME_PERCENT = 0..100
        val PAUSE_MIN = 1..60
    }

    val simpleMode: Boolean get() = mode == Mode.SIMPLE

    fun clamped(): Settings = copy(
        thresholdKmh = thresholdKmh.coerceIn(THRESHOLD_KMH),
        mainBeepMs = mainBeepMs.coerceIn(MAIN_BEEP_MS),
        reminderIntervalS = if (reminderIntervalS <= 0) 0 else reminderIntervalS.coerceIn(REMINDER_S),
        volumePercent = volumePercent.coerceIn(VOLUME_PERCENT),
        pauseMin = pauseMin.coerceIn(PAUSE_MIN),
        sound = if (sound == SoundKind.PHONE_ALARM && alarmUri == null) SoundKind.BEEP else sound,
    )

    fun toRulesConfig() = RulesConfig(
        thresholdKmh = thresholdKmh,
        reminderIntervalMs = reminderIntervalS * 1_000L,
        simpleMode = simpleMode,
    )

    fun stepThreshold(delta: Int) = copy(thresholdKmh = (thresholdKmh + delta).coerceIn(THRESHOLD_KMH))

    /** Off (0) and the 3–60 s range, with nothing in between. */
    fun stepReminder(delta: Int): Settings {
        val next = when {
            reminderIntervalS == 0 -> if (delta > 0) REMINDER_S.first else 0
            reminderIntervalS + delta < REMINDER_S.first -> 0
            else -> (reminderIntervalS + delta).coerceAtMost(REMINDER_S.last)
        }
        return copy(reminderIntervalS = next)
    }
}

/** Settings ⇄ plain key/value pairs (Int, Boolean or String), the shape DataStore stores. */
object SettingsCodec {
    fun encode(s: Settings): Map<String, Any> = buildMap {
        put("thresholdKmh", s.thresholdKmh)
        put("mode", s.mode.name)
        put("mainBeepMs", s.mainBeepMs)
        put("reminderIntervalS", s.reminderIntervalS)
        put("sound", s.sound.name)
        s.alarmUri?.let { put("alarmUri", it) }
        s.alarmTitle?.let { put("alarmTitle", it) }
        put("volumePercent", s.volumePercent)
        put("output", s.output.name)
        put("pauseMin", s.pauseMin)
        put("keepScreenOn", s.keepScreenOn)
        put("recordDrives", s.recordDrives)
        put("carStart", s.carStart.name)
        s.carAddress?.let { put("carAddress", it) }
        s.carName?.let { put("carName", it) }
    }

    fun decode(m: Map<String, Any?>): Settings {
        val d = Settings()
        fun int(key: String, default: Int) = m[key] as? Int ?: default
        fun bool(key: String, default: Boolean) = m[key] as? Boolean ?: default
        fun str(key: String) = m[key] as? String
        fun <E : Enum<E>> enum(key: String, default: E, values: Array<E>) = values.firstOrNull { it.name == m[key] } ?: default
        return Settings(
            thresholdKmh = int("thresholdKmh", d.thresholdKmh),
            mode = enum("mode", d.mode, Mode.entries.toTypedArray()),
            mainBeepMs = int("mainBeepMs", d.mainBeepMs),
            reminderIntervalS = int("reminderIntervalS", d.reminderIntervalS),
            sound = enum("sound", d.sound, SoundKind.entries.toTypedArray()),
            alarmUri = str("alarmUri"),
            alarmTitle = str("alarmTitle"),
            volumePercent = int("volumePercent", d.volumePercent),
            output = enum("output", d.output, Output.entries.toTypedArray()),
            pauseMin = int("pauseMin", d.pauseMin),
            keepScreenOn = bool("keepScreenOn", d.keepScreenOn),
            recordDrives = bool("recordDrives", d.recordDrives),
            carStart = enum("carStart", d.carStart, CarStart.entries.toTypedArray()),
            carAddress = str("carAddress"),
            carName = str("carName"),
        ).clamped()
    }
}
```

- [ ] **Step 4: Run it and watch it pass**

Run: `GW :app:testDebugUnitTest`
Expected: `BUILD SUCCESSFUL`. `SettingsCodecTest` passes 9/9.

- [ ] **Step 5: Store the settings in DataStore**

`app/src/main/java/io/github/lrosa2/speedbeep/settings/SettingsRepository.kt`:

```kotlin
package io.github.lrosa2.speedbeep.settings

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.github.lrosa2.speedbeep.engine.Settings
import io.github.lrosa2.speedbeep.engine.SettingsCodec
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.dataStore by preferencesDataStore(name = "settings")

/** DataStore Preferences behind [SettingsCodec] (spec §3: settings live in DataStore). */
class SettingsRepository(private val context: Context) {

    val settings: Flow<Settings> = context.dataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { decode(it) }
        .distinctUntilChanged()

    suspend fun update(transform: (Settings) -> Settings) {
        context.dataStore.edit { prefs ->
            val next = transform(decode(prefs)).clamped()
            prefs.clear()
            for ((key, value) in SettingsCodec.encode(next)) {
                when (value) {
                    is Int -> prefs[intPreferencesKey(key)] = value
                    is Boolean -> prefs[booleanPreferencesKey(key)] = value
                    is String -> prefs[stringPreferencesKey(key)] = value
                    else -> error("unsupported settings value for $key")
                }
            }
        }
    }

    private fun decode(prefs: Preferences) = SettingsCodec.decode(prefs.asMap().mapKeys { it.key.name })
}
```

In `Graph.kt`:
- add the imports `io.github.lrosa2.speedbeep.settings.SettingsRepository`;
- add the property below `events`:
  ```kotlin
  lateinit var settings: SettingsRepository
      private set
  ```
- add, as the first line of `init`:
  ```kotlin
  settings = SettingsRepository(app)
  ```

- [ ] **Step 6: Build and commit**

Run: `GT :app:testDebugUnitTest` then `GW :app:assembleDebug`
Expected: the line `app: 19 tests, 0 failures, 0 errors`, then `BUILD SUCCESSFUL`.

```bash
git -C E:/AndroidProj/SpeedBeep add app
git -C E:/AndroidProj/SpeedBeep commit -m "App settings with spec ranges, stored in DataStore

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 3: Watch session and status text

**Files:**
- Create: `engine/WatchSession.kt`, `engine/StatusText.kt`
- Test: `engine/WatchSessionTest.kt`, `engine/StatusTextTest.kt`

**Interfaces:**
- Consumes:
  - `Settings` (Task 2)
  - `FixEvaluator.evaluate(fix, nowMs, simpleMode, roads)`, `Fix`
  - `SpeedRules`, `RulesOutput`, `Phase`, `RoadContext`, `Sound`
  - `RoadLookup` and `NoRoadData` (`:core`)
- Produces:
  - `data class WatchStatus(phase, speedKmh: Double?, roadContext, noRoadData, pausedUntilMs: Long?, thresholdKmh, simpleMode, pauseMin)`
  - `data class WatchUpdate(status: WatchStatus, sound: Sound)`
  - `WatchSession(settings, startMs, roads = NoRoadData)` with:
    - `onFix(fix, nowMs)`, `onTick(nowMs)`, `quiet(nowMs)`, `pause(nowMs)` and
      `resume(nowMs)`, each returning `WatchUpdate`
    - `updateSettings(Settings)`, `setRoads(RoadLookup)` and `status`
  - `enum class NoteButton { QUIET, PAUSE, RESUME, STOP }`
  - `StatusText.line(status, monoNowMs, wallNowMs, zone): String`, `StatusText.buttons(phase): List<NoteButton>`

- [ ] **Step 1: Write the failing tests**

`app/src/test/java/io/github/lrosa2/speedbeep/engine/WatchSessionTest.kt`:

```kotlin
package io.github.lrosa2.speedbeep.engine

import io.github.lrosa2.speedbeep.core.road.RoadLookup
import io.github.lrosa2.speedbeep.core.road.RoadMatch
import io.github.lrosa2.speedbeep.core.rules.Fix
import io.github.lrosa2.speedbeep.core.rules.Phase
import io.github.lrosa2.speedbeep.core.rules.Sound
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WatchSessionTest {
    private val town = RoadLookup { _, _, _, _ -> RoadMatch.TOWN }
    private val fast = RoadLookup { _, _, _, _ -> RoadMatch.FAST }

    private fun fix(t: Long, kmh: Double) = Fix(t, 47.0, 8.0, (kmh / 3.6).toFloat(), 0.5f, 5f, 90f)

    /** One fresh fix per second from [from], at the given speeds. */
    private fun drive(session: WatchSession, from: Long, kmh: List<Double>): List<WatchUpdate> =
        kmh.mapIndexed { i, v ->
            val t = from + i * 1_000L
            session.onFix(fix(t, v), t)
        }

    @Test
    fun `going over in town gives a main beep on the second reading above`() {
        val s = WatchSession(Settings(), 0, town)
        val sounds = drive(s, 0, listOf(45.0, 60.0, 60.0, 60.0)).map { it.sound }
        assertEquals(listOf(Sound.NONE, Sound.NONE, Sound.MAIN, Sound.NONE), sounds)
        assertEquals(Phase.OVER, s.status.phase)
    }

    @Test
    fun `a fast road stays silent`() {
        val s = WatchSession(Settings(), 0, fast)
        val updates = drive(s, 0, List(6) { 110.0 })
        assertTrue(updates.none { it.sound == Sound.MAIN })
        assertEquals(Phase.FAST_ROAD, s.status.phase)
    }

    @Test
    fun `simple mode beeps on a fast road`() {
        val s = WatchSession(Settings(mode = Mode.SIMPLE), 0, fast)
        assertEquals(Sound.MAIN, drive(s, 0, listOf(110.0, 110.0)).last().sound)
    }

    @Test
    fun `road data that arrives later is used`() {
        val s = WatchSession(Settings(), 0) // NoRoadData until the file is loaded
        drive(s, 0, listOf(40.0))
        assertTrue(s.status.noRoadData)
        s.setRoads(town)
        drive(s, 1_000, listOf(40.0))
        assertFalse(s.status.noRoadData)
    }

    @Test
    fun `a pause lasts the set number of minutes`() {
        val s = WatchSession(Settings(pauseMin = 5), 0, town)
        assertEquals(2_000L + 5 * 60_000, s.pause(2_000).status.pausedUntilMs)
    }

    @Test
    fun `a new threshold shows at once and applies from the next reading`() {
        val s = WatchSession(Settings(), 0, town)
        drive(s, 0, listOf(55.0))
        s.updateSettings(Settings(thresholdKmh = 60))
        assertEquals(60, s.status.thresholdKmh)
        assertEquals(Sound.NONE, drive(s, 1_000, listOf(55.0, 55.0)).last().sound)
    }

    @Test
    fun `no fix for 5 s means No GPS`() {
        val s = WatchSession(Settings(), 0, town)
        assertEquals(Phase.NO_GPS, s.onTick(5_000).status.phase)
    }

    @Test
    fun `quiet this stretch stops the beep`() {
        val s = WatchSession(Settings(), 0, town)
        drive(s, 0, listOf(60.0, 60.0))
        val update = s.quiet(2_000)
        assertEquals(Sound.STOP, update.sound)
        assertEquals(Phase.QUIET, update.status.phase)
    }
}
```

`app/src/test/java/io/github/lrosa2/speedbeep/engine/StatusTextTest.kt`:

```kotlin
package io.github.lrosa2.speedbeep.engine

import io.github.lrosa2.speedbeep.core.rules.Phase
import io.github.lrosa2.speedbeep.core.rules.RoadContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class StatusTextTest {
    private val zurich = ZoneId.of("Europe/Zurich")

    private fun status(
        phase: Phase,
        kmh: Double? = 52.6,
        noRoadData: Boolean = false,
        simple: Boolean = false,
        pausedUntil: Long? = null,
    ) = WatchStatus(phase, kmh, RoadContext.NORMAL, noRoadData, pausedUntil, 53, simple, 10)

    private fun line(s: WatchStatus) = StatusText.line(s, 0, 0, zurich)

    @Test
    fun `town road`() = assertEquals("52 km/h · town", line(status(Phase.ARMED)))

    @Test
    fun `over shows the threshold`() = assertEquals("61 km/h · OVER 53", line(status(Phase.OVER, 61.0)))

    @Test
    fun `fast road`() = assertEquals("118 km/h · fast road, silent", line(status(Phase.FAST_ROAD, 118.9)))

    @Test
    fun `quiet stretch`() = assertEquals("61 km/h · quiet this stretch", line(status(Phase.QUIET, 61.0)))

    @Test
    fun `simple mode`() = assertEquals("52 km/h · simple mode", line(status(Phase.ARMED, simple = true)))

    @Test
    fun `no road data`() = assertEquals("52 km/h · simple mode · no road data", line(status(Phase.ARMED, noRoadData = true)))

    @Test
    fun `no gps`() = assertEquals("No GPS", line(status(Phase.NO_GPS, kmh = null)))

    @Test
    fun `before the first fix`() = assertEquals("Waiting for GPS", line(status(Phase.ARMED, kmh = null)))

    @Test
    fun `the pause end is shown as a clock time`() {
        val wallNow = ZonedDateTime.of(2026, 11, 25, 14, 22, 0, 0, zurich).toInstant().toEpochMilli()
        val monoNow = 70_000L
        val s = status(Phase.PAUSED, pausedUntil = monoNow + 10 * 60_000)
        assertEquals("Paused until 14:32", StatusText.line(s, monoNow, wallNow, zurich))
    }

    @Test
    fun `notification buttons follow the state`() {
        assertEquals(listOf(NoteButton.QUIET, NoteButton.PAUSE, NoteButton.STOP), StatusText.buttons(Phase.OVER))
        assertEquals(listOf(NoteButton.RESUME, NoteButton.STOP), StatusText.buttons(Phase.PAUSED))
        for (phase in listOf(Phase.ARMED, Phase.FAST_ROAD, Phase.QUIET, Phase.NO_GPS)) {
            assertEquals(listOf(NoteButton.PAUSE, NoteButton.STOP), StatusText.buttons(phase))
        }
    }
}
```

- [ ] **Step 2: Run them and watch them fail**

Run: `GW :app:testDebugUnitTest`
Expected: FAIL at compile time with `Unresolved reference 'WatchSession'` and `'StatusText'`.

- [ ] **Step 3: Write the session and the status text**

`app/src/main/java/io/github/lrosa2/speedbeep/engine/WatchSession.kt`:

```kotlin
package io.github.lrosa2.speedbeep.engine

import io.github.lrosa2.speedbeep.core.road.NoRoadData
import io.github.lrosa2.speedbeep.core.road.RoadLookup
import io.github.lrosa2.speedbeep.core.rules.Fix
import io.github.lrosa2.speedbeep.core.rules.FixEvaluator
import io.github.lrosa2.speedbeep.core.rules.Phase
import io.github.lrosa2.speedbeep.core.rules.RoadContext
import io.github.lrosa2.speedbeep.core.rules.RulesOutput
import io.github.lrosa2.speedbeep.core.rules.Sound
import io.github.lrosa2.speedbeep.core.rules.SpeedRules

/** What the notification, the main screen and the tile show. */
data class WatchStatus(
    val phase: Phase,
    val speedKmh: Double?,
    val roadContext: RoadContext,
    val noRoadData: Boolean,
    val pausedUntilMs: Long?,
    val thresholdKmh: Int,
    val simpleMode: Boolean,
    val pauseMin: Int,
)

data class WatchUpdate(val status: WatchStatus, val sound: Sound)

/**
 * One watching session: fix → [FixEvaluator] → [SpeedRules] (spec §4). Times are monotonic
 * milliseconds. Single-threaded, like SpeedRules and RoadMatcher.
 */
class WatchSession(settings: Settings, startMs: Long, private var roads: RoadLookup = NoRoadData) {
    private var settings = settings
    private val rules = SpeedRules(settings.toRulesConfig(), startMs)

    var status = WatchStatus(
        Phase.ARMED, null, RoadContext.NORMAL, false, null,
        settings.thresholdKmh, settings.simpleMode, settings.pauseMin,
    )
        private set

    fun onFix(fix: Fix, nowMs: Long) = apply(rules.onReading(FixEvaluator.evaluate(fix, nowMs, settings.simpleMode, roads)))

    fun onTick(nowMs: Long) = apply(rules.onTick(nowMs))

    fun quiet(nowMs: Long) = apply(rules.quietThisStretch(nowMs))

    fun pause(nowMs: Long) = apply(rules.pause(nowMs, settings.pauseMin * 60_000L))

    fun resume(nowMs: Long) = apply(rules.resume(nowMs))

    /** Threshold, mode and reminder interval apply from the next reading (SpeedRules.updateConfig). */
    fun updateSettings(newSettings: Settings) {
        settings = newSettings
        rules.updateConfig(newSettings.toRulesConfig())
        status = status.copy(
            thresholdKmh = newSettings.thresholdKmh,
            simpleMode = newSettings.simpleMode,
            pauseMin = newSettings.pauseMin,
        )
    }

    fun setRoads(lookup: RoadLookup) {
        roads = lookup
    }

    private fun apply(out: RulesOutput): WatchUpdate {
        status = WatchStatus(
            out.phase, out.speedKmh, out.roadContext, out.noRoadData, out.pausedUntilMs,
            settings.thresholdKmh, settings.simpleMode, settings.pauseMin,
        )
        return WatchUpdate(status, out.sound)
    }
}
```

`app/src/main/java/io/github/lrosa2/speedbeep/engine/StatusText.kt`:

```kotlin
package io.github.lrosa2.speedbeep.engine

import io.github.lrosa2.speedbeep.core.rules.Phase
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

enum class NoteButton { QUIET, PAUSE, RESUME, STOP }

/** The status line and the notification buttons of spec §5.3. */
object StatusText {
    private val CLOCK = DateTimeFormatter.ofPattern("HH:mm")

    /** [monoNowMs] and [wallNowMs] are the same moment on both clocks; they turn the pause end into a clock time. */
    fun line(s: WatchStatus, monoNowMs: Long, wallNowMs: Long, zone: ZoneId): String {
        // Truncated, so 52.9 never shows as "53 km/h · town" while still under a threshold of 53.
        val speed = s.speedKmh?.let { "${it.toInt()} km/h" }
        return when {
            s.phase == Phase.PAUSED -> "Paused until " + clock(s.pausedUntilMs ?: monoNowMs, monoNowMs, wallNowMs, zone)
            s.phase == Phase.NO_GPS -> "No GPS"
            speed == null -> "Waiting for GPS"
            s.phase == Phase.OVER -> "$speed · OVER ${s.thresholdKmh}"
            s.phase == Phase.FAST_ROAD -> "$speed · fast road, silent"
            s.phase == Phase.QUIET -> "$speed · quiet this stretch"
            s.simpleMode -> "$speed · simple mode"
            s.noRoadData -> "$speed · simple mode · no road data"
            else -> "$speed · town"
        }
    }

    fun buttons(phase: Phase): List<NoteButton> = when (phase) {
        Phase.OVER -> listOf(NoteButton.QUIET, NoteButton.PAUSE, NoteButton.STOP)
        Phase.PAUSED -> listOf(NoteButton.RESUME, NoteButton.STOP)
        else -> listOf(NoteButton.PAUSE, NoteButton.STOP)
    }

    private fun clock(monoMs: Long, monoNowMs: Long, wallNowMs: Long, zone: ZoneId): String =
        Instant.ofEpochMilli(wallNowMs + (monoMs - monoNowMs)).atZone(zone).format(CLOCK)
}
```

- [ ] **Step 4: Run them and watch them pass**

Run: `GT :app:testDebugUnitTest`
Expected: `app: 37 tests, 0 failures, 0 errors` (19 + 8 + 10).

- [ ] **Step 5: Commit**

```bash
git -C E:/AndroidProj/SpeedBeep add app
git -C E:/AndroidProj/SpeedBeep commit -m "Watch session and status text

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 4: Sounds and the beeper

**Files:**
- Create: `engine/Tones.kt`, `engine/SoundPolicy.kt`, `watch/Beeper.kt`
- Test: `engine/TonesTest.kt`, `engine/SoundPolicyTest.kt`

**Interfaces:**
- Consumes: `Settings`, `SoundKind` and `Output` (Task 2); `Sound` (`:core`); `EventLog` (Task 1).
- Produces:
  - `Tones.SAMPLE_RATE`, `Tones.REMINDER_MS`, `Tones.samples(ms)`,
    `Tones.main(kind, lengthMs): ShortArray`, `Tones.reminder(): ShortArray`
  - `enum class SoundCommand { NONE, PLAY_MAIN, PLAY_REMINDER, STOP }`
  - `SoundPolicy().decide(sound, nowMs, mainLengthMs): SoundCommand`
  - `Beeper(context, events)` with `playMain(settings)`, `playReminder(settings)` and `stop()`

**Rulings in this task:**
- **The reminder is always one built-in 1 kHz tone of 0.4 s**, at the chosen output and
  volume. §7.2 says "one short tone", and many phone alarm sounds start with silence.
- **A reminder that falls inside a main beep still playing is dropped.** The rules' next
  reminder comes one interval later. A main beep can last 10 s and reminders can come
  every 3 s, and the spec is silent on the overlap.

- [ ] **Step 1: Write the failing tests**

`app/src/test/java/io/github/lrosa2/speedbeep/engine/TonesTest.kt`:

```kotlin
package io.github.lrosa2.speedbeep.engine

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.abs

class TonesTest {
    private fun at(ms: Int) = Tones.samples(ms)

    private fun silent(pcm: ShortArray, fromMs: Int, toMs: Int) = (at(fromMs) until at(toMs)).all { pcm[it].toInt() == 0 }

    private fun loud(pcm: ShortArray, fromMs: Int, toMs: Int) = (at(fromMs) until at(toMs)).maxOf { abs(pcm[it].toInt()) } > 20_000

    /** Sign changes; a pure tone of f Hz has 2·f per second. */
    private fun crossings(pcm: ShortArray, fromMs: Int, toMs: Int) =
        (at(fromMs) + 1 until at(toMs)).count { (pcm[it - 1] < 0) != (pcm[it] < 0) }.toDouble()

    @Test
    fun `the main beep has exactly the set length`() {
        assertEquals(at(2_000), Tones.main(SoundKind.BEEP, 2_000).size)
        assertEquals(at(500), Tones.main(SoundKind.CHIME, 500).size)
    }

    @Test
    fun `the beep is a repeated 1 kHz tone with gaps`() {
        val pcm = Tones.main(SoundKind.BEEP, 2_000)
        assertTrue(loud(pcm, 20, 230))
        assertTrue(silent(pcm, 255, 345))
        assertTrue(loud(pcm, 370, 580))
        assertEquals(400.0, crossings(pcm, 20, 220), 6.0)
    }

    @Test
    fun `the double beep has two tones before its pause`() {
        val pcm = Tones.main(SoundKind.DOUBLE_BEEP, 600)
        assertTrue(loud(pcm, 10, 110))
        assertTrue(silent(pcm, 125, 195))
        assertTrue(loud(pcm, 210, 310))
        assertTrue(silent(pcm, 325, 595))
    }

    @Test
    fun `the chime goes from low to high`() {
        val pcm = Tones.main(SoundKind.CHIME, 800)
        assertEquals(2 * 660 * 0.2, crossings(pcm, 50, 250), 6.0)
        assertEquals(2 * 990 * 0.2, crossings(pcm, 350, 550), 6.0)
    }

    @Test
    fun `the reminder is one short tone`() {
        val pcm = Tones.reminder()
        assertEquals(at(Tones.REMINDER_MS), pcm.size)
        assertTrue(loud(pcm, 20, 380))
    }

    @Test
    fun `tones start and end softly`() {
        val pcm = Tones.main(SoundKind.BEEP, 1_234)
        assertEquals(0, pcm.first().toInt())
        assertTrue(abs(pcm.last().toInt()) < 2_000)
    }
}
```

`app/src/test/java/io/github/lrosa2/speedbeep/engine/SoundPolicyTest.kt`:

```kotlin
package io.github.lrosa2.speedbeep.engine

import io.github.lrosa2.speedbeep.core.rules.Sound
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SoundPolicyTest {
    @Test
    fun `a reminder never cuts into a main beep still playing`() {
        val policy = SoundPolicy()
        assertEquals(SoundCommand.PLAY_MAIN, policy.decide(Sound.MAIN, 0, 10_000))
        assertEquals(SoundCommand.NONE, policy.decide(Sound.REMINDER, 3_000, 10_000))
        assertEquals(SoundCommand.PLAY_REMINDER, policy.decide(Sound.REMINDER, 12_000, 10_000))
    }

    @Test
    fun `stop ends the main beep at once`() {
        val policy = SoundPolicy()
        policy.decide(Sound.MAIN, 0, 10_000)
        assertEquals(SoundCommand.STOP, policy.decide(Sound.STOP, 1_000, 10_000))
        assertEquals(SoundCommand.PLAY_REMINDER, policy.decide(Sound.REMINDER, 2_000, 10_000))
    }

    @Test
    fun `nothing to do stays nothing`() {
        assertEquals(SoundCommand.NONE, SoundPolicy().decide(Sound.NONE, 0, 2_000))
    }
}
```

- [ ] **Step 2: Run them and watch them fail**

Run: `GW :app:testDebugUnitTest`
Expected: FAIL at compile time with `Unresolved reference 'Tones'` and `'SoundPolicy'`.

- [ ] **Step 3: Write the tones and the policy**

`app/src/main/java/io/github/lrosa2/speedbeep/engine/Tones.kt`:

```kotlin
package io.github.lrosa2.speedbeep.engine

import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

/** The built-in sounds as 16-bit mono PCM (spec §7.2). No asset files. */
object Tones {
    const val SAMPLE_RATE = 22_050
    const val REMINDER_MS = 400
    private const val AMPLITUDE = 0.8 * Short.MAX_VALUE
    private const val FADE_MS = 5

    /** [hz] = 0 is silence. */
    private class Segment(val hz: Int, val ms: Int)

    private fun pattern(kind: SoundKind): List<Segment> = when (kind) {
        // PHONE_ALARM falls back to the beep when the alarm sound cannot play.
        SoundKind.BEEP, SoundKind.PHONE_ALARM -> listOf(Segment(1_000, 250), Segment(0, 100))
        SoundKind.DOUBLE_BEEP -> listOf(Segment(1_000, 120), Segment(0, 80), Segment(1_000, 120), Segment(0, 280))
        SoundKind.CHIME -> listOf(Segment(660, 300), Segment(990, 300), Segment(0, 200))
    }

    fun samples(ms: Int): Int = (ms.toLong() * SAMPLE_RATE / 1_000).toInt()

    /** The main beep: [kind]'s pattern repeated, then cut to exactly [lengthMs]. */
    fun main(kind: SoundKind, lengthMs: Int): ShortArray = render(pattern(kind), lengthMs)

    /** One short 1 kHz tone, whatever sound is chosen. */
    fun reminder(): ShortArray = render(listOf(Segment(1_000, REMINDER_MS)), REMINDER_MS)

    private fun render(pattern: List<Segment>, lengthMs: Int): ShortArray {
        val out = ShortArray(samples(lengthMs))
        var at = 0
        while (at < out.size) {
            for (segment in pattern) {
                val n = samples(segment.ms)
                if (segment.hz > 0) tone(out, at, n, segment.hz)
                at += n
                if (at >= out.size) break
            }
        }
        fadeOutEnd(out)
        return out
    }

    /** A sine tone with short fades at both ends, so the edges do not click. */
    private fun tone(out: ShortArray, start: Int, n: Int, hz: Int) {
        val fade = samples(FADE_MS)
        for (i in 0 until n) {
            val index = start + i
            if (index >= out.size) return
            val edge = minOf(i, n - 1 - i)
            val envelope = if (edge < fade) edge.toDouble() / fade else 1.0
            out[index] = (AMPLITUDE * envelope * sin(2 * PI * hz * i / SAMPLE_RATE)).roundToInt().toShort()
        }
    }

    /** A tone cut by the set length also ends softly. */
    private fun fadeOutEnd(out: ShortArray) {
        val fade = minOf(samples(FADE_MS), out.size)
        for (k in 0 until fade) {
            val index = out.size - 1 - k
            out[index] = (out[index] * (k.toDouble() / fade)).roundToInt().toShort()
        }
    }
}
```

`app/src/main/java/io/github/lrosa2/speedbeep/engine/SoundPolicy.kt`:

```kotlin
package io.github.lrosa2.speedbeep.engine

import io.github.lrosa2.speedbeep.core.rules.Sound

enum class SoundCommand { NONE, PLAY_MAIN, PLAY_REMINDER, STOP }

/**
 * Turns the rules' [Sound] into what the beeper does. A reminder never cuts into a main beep
 * that is still playing: main beeps can last 10 s while reminders can come every 3 s.
 */
class SoundPolicy {
    private var mainEndsMs = Long.MIN_VALUE

    fun decide(sound: Sound, nowMs: Long, mainLengthMs: Int): SoundCommand = when (sound) {
        Sound.NONE -> SoundCommand.NONE
        Sound.MAIN -> {
            mainEndsMs = nowMs + mainLengthMs
            SoundCommand.PLAY_MAIN
        }
        Sound.REMINDER -> if (nowMs < mainEndsMs) SoundCommand.NONE else SoundCommand.PLAY_REMINDER
        Sound.STOP -> {
            mainEndsMs = Long.MIN_VALUE
            SoundCommand.STOP
        }
    }
}
```

- [ ] **Step 4: Run them and watch them pass**

Run: `GT :app:testDebugUnitTest`
Expected: `app: 46 tests, 0 failures, 0 errors` (37 + 6 + 3).

- [ ] **Step 5: Write the beeper**

`app/src/main/java/io/github/lrosa2/speedbeep/watch/Beeper.kt`:

```kotlin
package io.github.lrosa2.speedbeep.watch

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.MediaPlayer
import android.net.Uri
import android.os.Handler
import android.os.Looper
import io.github.lrosa2.speedbeep.engine.EventLog
import io.github.lrosa2.speedbeep.engine.Output
import io.github.lrosa2.speedbeep.engine.Settings
import io.github.lrosa2.speedbeep.engine.SoundKind
import io.github.lrosa2.speedbeep.engine.Tones

/**
 * Plays the main beep and the reminder with the chosen sound, output and volume (spec §7.1,
 * §7.2). Phone speaker = alarm usage; car speakers = navigation usage with transient,
 * ducking audio focus, like a Maps prompt. Main thread only.
 */
class Beeper(private val context: Context, private val events: EventLog) {
    private val audio = context.getSystemService(AudioManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private var track: AudioTrack? = null
    private var player: MediaPlayer? = null
    private var focus: AudioFocusRequest? = null
    private val finish = Runnable { stop() }

    fun playMain(s: Settings) {
        stop()
        val attributes = attributes(s.output)
        requestFocus(s.output, attributes)
        val alarmPlaying = s.sound == SoundKind.PHONE_ALARM && s.alarmUri != null && playAlarm(s, attributes)
        if (!alarmPlaying) playPcm(Tones.main(s.sound, s.mainBeepMs), s, attributes)
        handler.postDelayed(finish, s.mainBeepMs + END_MARGIN_MS)
    }

    fun playReminder(s: Settings) {
        stop()
        val attributes = attributes(s.output)
        requestFocus(s.output, attributes)
        playPcm(Tones.reminder(), s, attributes)
        handler.postDelayed(finish, Tones.REMINDER_MS + END_MARGIN_MS)
    }

    /** Cuts any sound at once and gives the audio focus back. */
    fun stop() {
        handler.removeCallbacks(finish)
        track?.let {
            runCatching { it.stop() }
            it.release()
        }
        track = null
        player?.let {
            runCatching { it.stop() }
            it.release()
        }
        player = null
        focus?.let { audio.abandonAudioFocusRequest(it) }
        focus = null
    }

    private fun attributes(output: Output): AudioAttributes = AudioAttributes.Builder()
        .setUsage(
            if (output == Output.CAR_SPEAKERS) AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE
            else AudioAttributes.USAGE_ALARM,
        )
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    private fun requestFocus(output: Output, attributes: AudioAttributes) {
        if (output != Output.CAR_SPEAKERS) return
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(attributes)
            .build()
        if (audio.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) focus = request
    }

    private fun playPcm(pcm: ShortArray, s: Settings, attributes: AudioAttributes) {
        val format = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(Tones.SAMPLE_RATE)
            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
            .build()
        val t = AudioTrack.Builder()
            .setAudioAttributes(attributes)
            .setAudioFormat(format)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(pcm.size * 2)
            .build()
        t.write(pcm, 0, pcm.size)
        t.setVolume(s.volumePercent / 100f)
        t.play()
        track = t
    }

    /** Loops the chosen phone alarm sound until [finish] cuts it. False if it cannot play. */
    private fun playAlarm(s: Settings, attributes: AudioAttributes): Boolean = try {
        val volume = s.volumePercent / 100f
        player = MediaPlayer().apply {
            setAudioAttributes(attributes)
            setDataSource(context, Uri.parse(s.alarmUri))
            isLooping = true
            prepare()
            setVolume(volume, volume)
            start()
        }
        true
    } catch (e: Exception) {
        events.log("alarm sound failed (${e.javaClass.simpleName}); using the built-in beep")
        player?.release()
        player = null
        false
    }

    private companion object {
        const val END_MARGIN_MS = 150L
    }
}
```

- [ ] **Step 6: Build and commit**

Run: `GW :app:assembleDebug`
Expected: `BUILD SUCCESSFUL`.

```bash
git -C E:/AndroidProj/SpeedBeep add app
git -C E:/AndroidProj/SpeedBeep commit -m "Built-in tones, reminder/main-beep policy, beeper

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 5: GPS, drive recording and replay

**Files:**
- Create: `engine/DriveCsv.kt`, `engine/Replay.kt`, `engine/DriveRecorder.kt`
- Create: `watch/LocationSource.kt` (interface, `GpsSource`, `Location.toFix()`)
- Create: `app/src/debug/java/io/github/lrosa2/speedbeep/watch/LocationSources.kt` and
  `ReplaySource.kt`
- Create: `app/src/release/java/io/github/lrosa2/speedbeep/watch/LocationSources.kt`
- Test: `engine/DriveCsvTest.kt`, `engine/ReplayTest.kt`, `engine/DriveRecorderTest.kt`

**Interfaces:**
- Consumes: `Fix`, `FixEvaluator`, `NoRoadData` (`:core`); `EventLog` (Task 1).
- Produces:
  - `DriveCsv.HEADER`, `DriveCsv.COLUMNS`, `DriveCsv.line(fix, startMs): String`,
    `DriveCsv.parse(text): List<Fix>`
  - `Replay.rebase(fixes, startMs): List<Fix>`
  - `DriveRecorder(dir, zone)` with `record(fix, wallNowMs)`, `close()` and `file`, plus
    `DriveRecorder.latest(dir): File?`
  - `interface LocationSource { val name: String; fun start(onFix: (Fix) -> Unit); fun stop() }`
  - `GpsSource(context)`, `LocationSources.create(context, events): LocationSource`

**Recording format.** It keeps raw `Fix` fields, not `Reading`s, so a recorded drive can
re-tune the matcher's 20 m and 30° (§9 step 5). The same file is a replay file.

- [ ] **Step 1: Write the failing tests**

`app/src/test/java/io/github/lrosa2/speedbeep/engine/DriveCsvTest.kt`:

```kotlin
package io.github.lrosa2.speedbeep.engine

import io.github.lrosa2.speedbeep.core.rules.Fix
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class DriveCsvTest {
    private val full = Fix(1_000, 47.3563251, 8.5558482, 16.7f, 0.5f, 4.0f, 147.0f)
    private val bare = Fix(2_000, 47.0, 8.0, null, null, null, null)

    @Test
    fun `fixes survive writing and reading`() {
        val text = listOf(DriveCsv.HEADER, DriveCsv.COLUMNS, DriveCsv.line(full, 1_000), DriveCsv.line(bare, 1_000))
            .joinToString("\n")
        assertEquals(listOf(full.copy(elapsedMs = 0), bare.copy(elapsedMs = 1_000)), DriveCsv.parse(text))
    }

    @Test
    fun `missing values are empty fields`() {
        assertEquals("1000,47.0000000,8.0000000,,,,", DriveCsv.line(bare, 1_000))
    }

    @Test
    fun `a broken line is reported`() {
        val e = assertThrows<IllegalArgumentException> { DriveCsv.parse("0,47.0,8.0") }
        assertTrue(e.message!!.contains("7 fields"))
    }
}
```

`app/src/test/java/io/github/lrosa2/speedbeep/engine/ReplayTest.kt`:

```kotlin
package io.github.lrosa2.speedbeep.engine

import io.github.lrosa2.speedbeep.core.road.NoRoadData
import io.github.lrosa2.speedbeep.core.rules.Fix
import io.github.lrosa2.speedbeep.core.rules.FixEvaluator
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ReplayTest {
    /** Recorded on another boot: its monotonic times mean nothing on today's clock. */
    private val recorded = listOf(5_000_000L, 5_001_000L, 5_003_000L).map { Fix(it, 47.0, 8.0, 16.7f, null, 5f, 90f) }

    @Test
    fun `replayed fixes move onto the live clock and keep their spacing`() {
        assertEquals(listOf(200_000L, 201_000L, 203_000L), Replay.rebase(recorded, 200_000).map { it.elapsedMs })
    }

    @Test
    fun `only rebased fixes count as fresh`() {
        val live = 200_000L
        assertNull(FixEvaluator.evaluate(recorded[0], live, true, NoRoadData).speedKmh)
        assertNotNull(FixEvaluator.evaluate(Replay.rebase(recorded, live)[0], live, true, NoRoadData).speedKmh)
    }

    @Test
    fun `an empty recording replays nothing`() {
        assertEquals(emptyList<Fix>(), Replay.rebase(emptyList(), 0))
    }
}
```

`app/src/test/java/io/github/lrosa2/speedbeep/engine/DriveRecorderTest.kt`:

```kotlin
package io.github.lrosa2.speedbeep.engine

import io.github.lrosa2.speedbeep.core.rules.Fix
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.time.ZoneId
import java.time.ZonedDateTime

class DriveRecorderTest {
    @TempDir
    lateinit var dir: File

    @Test
    fun `a drive is written as a replayable file named after its start`() {
        val zurich = ZoneId.of("Europe/Zurich")
        val wall = ZonedDateTime.of(2026, 11, 25, 14, 22, 5, 0, zurich).toInstant().toEpochMilli()
        val fixes = listOf(8.0, 8.0001, 8.0002).mapIndexed { i, lon -> Fix(9_000L + i * 1_000, 47.0, lon, 16.7f, 0.5f, 4f, 90f) }
        val recorder = DriveRecorder(dir, zurich)
        fixes.forEach { recorder.record(it, wall) }
        recorder.close()
        val file = recorder.file!!
        assertEquals("drive-20261125-142205.csv", file.name)
        assertEquals(fixes.map { it.copy(elapsedMs = it.elapsedMs - 9_000) }, DriveCsv.parse(file.readText()))
    }

    @Test
    fun `the latest drive is found by its name`() {
        File(dir, "drive-20261124-080000.csv").writeText("")
        File(dir, "drive-20261125-080000.csv").writeText("")
        File(dir, "notes.txt").writeText("")
        assertEquals("drive-20261125-080000.csv", DriveRecorder.latest(dir)!!.name)
    }
}
```

- [ ] **Step 2: Run them and watch them fail**

Run: `GW :app:testDebugUnitTest`
Expected: FAIL at compile time with `Unresolved reference 'DriveCsv'`, `'Replay'` and
`'DriveRecorder'`.

- [ ] **Step 3: Write the format, the replay clock and the recorder**

`app/src/main/java/io/github/lrosa2/speedbeep/engine/DriveCsv.kt`:

```kotlin
package io.github.lrosa2.speedbeep.engine

import io.github.lrosa2.speedbeep.core.rules.Fix
import java.util.Locale

/** One raw fix per line; times relative to the first fix. Empty field = not reported. */
object DriveCsv {
    const val HEADER = "# SpeedBeep drive v1"
    const val COLUMNS = "t_ms,lat,lon,speed_mps,speed_acc_mps,h_acc_m,bearing_deg"

    fun line(fix: Fix, startMs: Long): String = listOf(
        (fix.elapsedMs - startMs).toString(),
        "%.7f".format(Locale.ROOT, fix.lat),
        "%.7f".format(Locale.ROOT, fix.lon),
        fix.speedMps.field(),
        fix.speedAccuracyMps.field(),
        fix.horizontalAccuracyM.field(),
        fix.bearingDeg.field(),
    ).joinToString(",")

    fun parse(text: String): List<Fix> = text.lineSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() && !it.startsWith("#") && !it.startsWith("t_ms") }
        .mapIndexed { i, l -> parseLine(l, i + 1) }
        .toList()

    private fun parseLine(line: String, record: Int): Fix {
        val f = line.split(',')
        require(f.size == 7) { "record $record: expected 7 fields, got ${f.size}" }
        return Fix(f[0].toLong(), f[1].toDouble(), f[2].toDouble(), f[3].optional(), f[4].optional(), f[5].optional(), f[6].optional())
    }

    private fun Float?.field() = this?.toString() ?: ""

    private fun String.optional() = trim().takeIf { it.isNotEmpty() }?.toFloat()
}
```

`app/src/main/java/io/github/lrosa2/speedbeep/engine/Replay.kt`:

```kotlin
package io.github.lrosa2.speedbeep.engine

import io.github.lrosa2.speedbeep.core.rules.Fix

object Replay {
    /**
     * Moves recorded fixes onto the live monotonic clock, first fix at [startMs], spacing kept.
     * Without this, FixEvaluator's 3 s age check drops every replayed fix.
     */
    fun rebase(fixes: List<Fix>, startMs: Long): List<Fix> {
        val first = fixes.firstOrNull()?.elapsedMs ?: return emptyList()
        return fixes.map { it.copy(elapsedMs = startMs + (it.elapsedMs - first)) }
    }
}
```

`app/src/main/java/io/github/lrosa2/speedbeep/engine/DriveRecorder.kt`:

```kotlin
package io.github.lrosa2.speedbeep.engine

import io.github.lrosa2.speedbeep.core.rules.Fix
import java.io.BufferedWriter
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Writes every raw fix of one drive to `drive-<start time>.csv` in [dir] ("Record drives", spec §7.3). */
class DriveRecorder(private val dir: File, private val zone: ZoneId = ZoneId.systemDefault()) {
    companion object {
        private val NAME_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")

        fun latest(dir: File): File? =
            dir.listFiles { f -> f.name.startsWith("drive-") && f.name.endsWith(".csv") }?.maxByOrNull { it.name }
    }

    private var writer: BufferedWriter? = null
    private var startMs = 0L
    private var lines = 0

    var file: File? = null
        private set

    fun record(fix: Fix, wallNowMs: Long) {
        val w = writer ?: open(fix, wallNowMs)
        w.write(DriveCsv.line(fix, startMs))
        w.newLine()
        if (++lines % 10 == 0) w.flush()
    }

    fun close() {
        writer?.close()
        writer = null
    }

    private fun open(first: Fix, wallNowMs: Long): BufferedWriter {
        dir.mkdirs()
        val f = File(dir, "drive-" + NAME_TIME.format(Instant.ofEpochMilli(wallNowMs).atZone(zone)) + ".csv")
        file = f
        startMs = first.elapsedMs
        return f.bufferedWriter().also {
            it.write(DriveCsv.HEADER)
            it.newLine()
            it.write(DriveCsv.COLUMNS)
            it.newLine()
            writer = it
        }
    }
}
```

- [ ] **Step 4: Run them and watch them pass**

Run: `GT :app:testDebugUnitTest`
Expected: `app: 54 tests, 0 failures, 0 errors` (46 + 3 + 3 + 2).

- [ ] **Step 5: Write the location sources**

`app/src/main/java/io/github/lrosa2/speedbeep/watch/LocationSource.kt`:

```kotlin
package io.github.lrosa2.speedbeep.watch

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.location.LocationRequest
import io.github.lrosa2.speedbeep.core.rules.Fix

/** Supplies fixes on the main thread (spec §3). */
interface LocationSource {
    val name: String
    fun start(onFix: (Fix) -> Unit)
    fun stop()
}

fun Location.toFix() = Fix(
    elapsedMs = elapsedRealtimeNanos / 1_000_000,
    lat = latitude,
    lon = longitude,
    speedMps = if (hasSpeed()) speed else null,
    speedAccuracyMps = if (hasSpeedAccuracy()) speedAccuracyMetersPerSecond else null,
    horizontalAccuracyM = if (hasAccuracy()) accuracy else null,
    bearingDeg = if (hasBearing()) bearing else null,
)

/** LocationManager GPS at 1 Hz, no Play Services. */
class GpsSource(context: Context) : LocationSource {
    private val manager = context.getSystemService(LocationManager::class.java)
    private val executor = context.mainExecutor
    private var listener: LocationListener? = null

    override val name = "GPS"

    // WatchStarter checks the permission; WatchService catches the SecurityException anyway.
    @SuppressLint("MissingPermission")
    override fun start(onFix: (Fix) -> Unit) {
        val l = LocationListener { onFix(it.toFix()) }
        listener = l
        val request = LocationRequest.Builder(1_000L)
            .setMinUpdateIntervalMillis(1_000L)
            .setQuality(LocationRequest.QUALITY_HIGH_ACCURACY)
            .build()
        manager.requestLocationUpdates(LocationManager.GPS_PROVIDER, request, executor, l)
    }

    override fun stop() {
        listener?.let { manager.removeUpdates(it) }
        listener = null
    }
}
```

`app/src/release/java/io/github/lrosa2/speedbeep/watch/LocationSources.kt`:

```kotlin
package io.github.lrosa2.speedbeep.watch

import android.content.Context
import io.github.lrosa2.speedbeep.engine.EventLog

object LocationSources {
    @Suppress("UNUSED_PARAMETER")
    fun create(context: Context, events: EventLog): LocationSource = GpsSource(context)
}
```

`app/src/debug/java/io/github/lrosa2/speedbeep/watch/LocationSources.kt`:

```kotlin
package io.github.lrosa2.speedbeep.watch

import android.content.Context
import io.github.lrosa2.speedbeep.engine.DriveCsv
import io.github.lrosa2.speedbeep.engine.EventLog
import java.io.File

/**
 * Debug builds replay `replay.csv` from the app's external files folder when it exists
 * (spec §3, ReplaySource). Push one with
 * `adb push town-over.csv /sdcard/Android/data/io.github.lrosa2.speedbeep.debug/files/replay.csv`.
 */
object LocationSources {
    fun create(context: Context, events: EventLog): LocationSource {
        val file = File(context.getExternalFilesDir(null), "replay.csv")
        if (!file.isFile) return GpsSource(context)
        return try {
            ReplaySource(DriveCsv.parse(file.readText())).also { events.log("replaying ${file.name}: ${it.size} fixes") }
        } catch (e: IllegalArgumentException) {
            events.log("replay.csv unreadable (${e.message}); using GPS")
            GpsSource(context)
        }
    }
}
```

`app/src/debug/java/io/github/lrosa2/speedbeep/watch/ReplaySource.kt`:

```kotlin
package io.github.lrosa2.speedbeep.watch

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import io.github.lrosa2.speedbeep.core.rules.Fix
import io.github.lrosa2.speedbeep.engine.Replay

/** Plays a recorded drive once, on the live clock, then falls silent (the rules then see No GPS). */
class ReplaySource(private val recorded: List<Fix>) : LocationSource {
    private val handler = Handler(Looper.getMainLooper())

    override val name = "replay"
    val size: Int get() = recorded.size

    override fun start(onFix: (Fix) -> Unit) {
        val start = SystemClock.elapsedRealtime() + 1_000
        for (fix in Replay.rebase(recorded, start)) {
            handler.postDelayed({ onFix(fix) }, fix.elapsedMs - SystemClock.elapsedRealtime())
        }
    }

    override fun stop() = handler.removeCallbacksAndMessages(null)
}
```

- [ ] **Step 6: Build both variants and commit**

Run: `GW :app:assembleDebug :app:assembleRelease`
Expected: `BUILD SUCCESSFUL`. The release APK is unsigned unless `keystore.properties` exists.

```bash
git -C E:/AndroidProj/SpeedBeep add app
git -C E:/AndroidProj/SpeedBeep commit -m "GPS source, drive recording, replay for debug builds

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 6: The watch service

**Files:**
- Create: `watch/WatchBus.kt`, `watch/WatchStarter.kt`, `watch/WatchNotifications.kt`,
  `watch/WatchService.kt`, `tools/replay/make-replays.sh`
- Modify: `App.kt` (channels), `Graph.kt` (`drivesDir`), `ui/MainActivity.kt` (start extra),
  `AndroidManifest.xml` (service)

**Interfaces:**
- Consumes:
  - `WatchSession`, `WatchStatus` and `WatchUpdate` (Task 3)
  - `StatusText` and `NoteButton` (Task 3)
  - `SoundPolicy`, `SoundCommand` and `Beeper` (Task 4)
  - `LocationSources`, `DriveRecorder` (Task 5)
  - `Graph.settings`, `Graph.roads` and `Graph.events`
- Produces:
  - `WatchBus.status: StateFlow<WatchStatus?>` (null = not watching), `WatchBus.running`
  - `WatchStarter.start(context, reason, fromBackground): Boolean`,
    `WatchStarter.missingPermission(context, fromBackground): String?`
  - `WatchService.ACTION_START/STOP/QUIET/PAUSE/RESUME`, `WatchService.EXTRA_REASON`
  - `WatchService.startIntent(context, reason)`, `actionIntent(context, action, byUser = true)`
  - `WatchService.send(context, action)`, `WatchService.stop(context, reason, byUser)`
  - `WatchNotifications.createChannels`, `watching`, `showProblem`, `showStopped`,
    `hideStopped`, `showCarConnected` and `hideCarConnected`
  - `Graph.drivesDir(context): File`
  - `MainActivity.EXTRA_START`
  - Event log lines the emulator checks rely on:
    - `watching started (<reason>) with <source>`
    - `state <old> → <new> at <n> km/h, road <context>[ (no road data)]`
    - `main beep at <n> km/h`, `reminder at <n> km/h`, `beep stopped`
    - `watching stopped (<reason>)`

**Order inside `begin`, from the spec and §8:**
1. `startForeground` comes first, because Android allows only a few seconds after
   `startForegroundService`.
2. Then the settings are read.
3. Then the GPS starts.

The road data may still be loading: the session starts with `NoRoadData` and switches when
`Graph.roads` fills.

- [ ] **Step 1: Write the bus, the starter and the notifications**

`app/src/main/java/io/github/lrosa2/speedbeep/watch/WatchBus.kt`:

```kotlin
package io.github.lrosa2.speedbeep.watch

import io.github.lrosa2.speedbeep.engine.WatchStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** What the running service shows; null while not watching. Written by WatchService on the main thread. */
object WatchBus {
    private val state = MutableStateFlow<WatchStatus?>(null)
    val status: StateFlow<WatchStatus?> = state.asStateFlow()
    val running: Boolean get() = state.value != null

    fun publish(status: WatchStatus?) {
        state.value = status
    }
}
```

`app/src/main/java/io/github/lrosa2/speedbeep/watch/WatchStarter.kt`:

```kotlin
package io.github.lrosa2.speedbeep.watch

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import io.github.lrosa2.speedbeep.Graph

/** The one way to start watching. Explains a refusal in a notification instead of failing silently. */
object WatchStarter {
    /** [fromBackground]: started by the car or the tile, so location must be allowed all the time (§5.1, §8). */
    fun start(context: Context, reason: String, fromBackground: Boolean): Boolean {
        val missing = missingPermission(context, fromBackground)
        if (missing != null) {
            Graph.events.log("not started ($reason): $missing")
            WatchNotifications.showProblem(context, "SpeedBeep could not start: $missing. Open SpeedBeep to fix it.")
            return false
        }
        return try {
            context.startForegroundService(WatchService.startIntent(context, reason))
            true
        } catch (e: Exception) { // ForegroundServiceStartNotAllowedException and friends
            Graph.events.log("start refused ($reason): ${e.javaClass.simpleName}: ${e.message}")
            false
        }
    }

    fun missingPermission(context: Context, fromBackground: Boolean): String? = when {
        !granted(context, Manifest.permission.ACCESS_FINE_LOCATION) -> "location is not allowed"
        fromBackground && !granted(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION) ->
            "location is not allowed all the time"
        else -> null
    }

    private fun granted(context: Context, permission: String) =
        context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
}
```

`app/src/main/java/io/github/lrosa2/speedbeep/watch/WatchNotifications.kt`:

```kotlin
package io.github.lrosa2.speedbeep.watch

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import io.github.lrosa2.speedbeep.R
import io.github.lrosa2.speedbeep.engine.NoteButton
import io.github.lrosa2.speedbeep.engine.StatusText
import io.github.lrosa2.speedbeep.engine.WatchStatus
import io.github.lrosa2.speedbeep.ui.MainActivity

/** All notifications (spec §5.3). Actions work from the lock screen: none requires unlocking. */
object WatchNotifications {
    const val WATCH_ID = 1
    private const val PROBLEM_ID = 2
    private const val STOPPED_ID = 3
    private const val CAR_ID = 4

    private const val WATCH = "watch"
    private const val PROBLEMS = "problems"
    private const val STOPPED = "stopped"
    private const val CAR = "car"

    private const val FLAGS = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT

    fun createChannels(context: Context) {
        manager(context).createNotificationChannels(
            listOf(
                NotificationChannel(WATCH, "Watching", NotificationManager.IMPORTANCE_LOW).apply {
                    setShowBadge(false)
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                },
                NotificationChannel(PROBLEMS, "Problems", NotificationManager.IMPORTANCE_DEFAULT),
                NotificationChannel(STOPPED, "Stopped while the car is connected", NotificationManager.IMPORTANCE_MIN).apply {
                    setShowBadge(false)
                },
                NotificationChannel(CAR, "Car connected", NotificationManager.IMPORTANCE_HIGH).apply {
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                },
            ),
        )
    }

    /** The ongoing notification of the running service; [status] null while starting. */
    fun watching(context: Context, status: WatchStatus?, line: String): Notification {
        val builder = Notification.Builder(context, WATCH)
            .setSmallIcon(R.drawable.ic_stat_speed)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(line)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setCategory(Notification.CATEGORY_NAVIGATION)
            .setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(openApp(context))
        if (status != null) {
            for (button in StatusText.buttons(status.phase)) builder.addAction(action(context, button, status.pauseMin))
        }
        return builder.build()
    }

    fun showProblem(context: Context, text: String) {
        val n = Notification.Builder(context, PROBLEMS)
            .setSmallIcon(R.drawable.ic_stat_speed)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setContentIntent(openApp(context))
            .setAutoCancel(true)
            .build()
        manager(context).notify(PROBLEM_ID, n)
    }

    /** "SpeedBeep stopped · [Start]" while the car is still there (§5.3). */
    fun showStopped(context: Context) {
        val n = Notification.Builder(context, STOPPED)
            .setSmallIcon(R.drawable.ic_stat_speed)
            .setContentTitle("SpeedBeep stopped")
            .setContentText("Tap Start to watch again")
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .addAction(Notification.Action.Builder(icon(context), "Start", startService(context, 30, "stopped note")).build())
            .build()
        manager(context).notify(STOPPED_ID, n)
    }

    fun hideStopped(context: Context) = manager(context).cancel(STOPPED_ID)

    /** §5.1 fallback: "Car connected · Start". A notification tap may start the service from the background. */
    fun showCarConnected(context: Context) {
        val start = startService(context, 31, "car connected (notification)")
        val n = Notification.Builder(context, CAR)
            .setSmallIcon(R.drawable.ic_stat_speed)
            .setContentTitle("Car connected")
            .setContentText("Start SpeedBeep?")
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setCategory(Notification.CATEGORY_NAVIGATION)
            .setContentIntent(start)
            .setAutoCancel(true)
            .addAction(Notification.Action.Builder(icon(context), "Start", start).build())
            .build()
        manager(context).notify(CAR_ID, n)
    }

    fun hideCarConnected(context: Context) = manager(context).cancel(CAR_ID)

    private fun action(context: Context, button: NoteButton, pauseMin: Int): Notification.Action {
        val (label, action) = when (button) {
            NoteButton.QUIET -> "Quiet this stretch" to WatchService.ACTION_QUIET
            NoteButton.PAUSE -> "Pause $pauseMin min" to WatchService.ACTION_PAUSE
            NoteButton.RESUME -> "Resume" to WatchService.ACTION_RESUME
            NoteButton.STOP -> "Stop" to WatchService.ACTION_STOP
        }
        val intent = WatchService.actionIntent(context, action).putExtra(WatchService.EXTRA_REASON, "notification")
        val pending = PendingIntent.getService(context, 10 + button.ordinal, intent, FLAGS)
        return Notification.Action.Builder(icon(context), label, pending).build()
    }

    private fun startService(context: Context, requestCode: Int, reason: String): PendingIntent =
        PendingIntent.getForegroundService(context, requestCode, WatchService.startIntent(context, reason), FLAGS)

    private fun openApp(context: Context): PendingIntent = PendingIntent.getActivity(
        context, 0, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), FLAGS,
    )

    private fun icon(context: Context) = Icon.createWithResource(context, R.drawable.ic_stat_speed)

    private fun manager(context: Context) = context.getSystemService(NotificationManager::class.java)
}
```

- [ ] **Step 2: Write the service**

`app/src/main/java/io/github/lrosa2/speedbeep/watch/WatchService.kt`:

```kotlin
package io.github.lrosa2.speedbeep.watch

import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import androidx.core.app.ServiceCompat
import io.github.lrosa2.speedbeep.Graph
import io.github.lrosa2.speedbeep.core.road.NoRoadData
import io.github.lrosa2.speedbeep.core.rules.Fix
import io.github.lrosa2.speedbeep.core.rules.Phase
import io.github.lrosa2.speedbeep.engine.DriveRecorder
import io.github.lrosa2.speedbeep.engine.NoteButton
import io.github.lrosa2.speedbeep.engine.Settings
import io.github.lrosa2.speedbeep.engine.SoundCommand
import io.github.lrosa2.speedbeep.engine.SoundPolicy
import io.github.lrosa2.speedbeep.engine.StatusText
import io.github.lrosa2.speedbeep.engine.WatchSession
import io.github.lrosa2.speedbeep.engine.WatchStatus
import io.github.lrosa2.speedbeep.engine.WatchUpdate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.ZoneId

/**
 * Foreground service of type location (spec §3): location source → session → beeper, plus
 * the notification and the optional drive recording. Everything runs on the main thread,
 * because SpeedRules, RoadMatcher and the beeper are single-threaded.
 */
class WatchService : Service() {
    companion object {
        const val ACTION_START = "io.github.lrosa2.speedbeep.action.START"
        const val ACTION_STOP = "io.github.lrosa2.speedbeep.action.STOP"
        const val ACTION_QUIET = "io.github.lrosa2.speedbeep.action.QUIET"
        const val ACTION_PAUSE = "io.github.lrosa2.speedbeep.action.PAUSE"
        const val ACTION_RESUME = "io.github.lrosa2.speedbeep.action.RESUME"
        const val EXTRA_REASON = "reason"
        private const val EXTRA_BY_USER = "byUser"
        private const val TICK_MS = 1_000L

        fun startIntent(context: Context, reason: String): Intent =
            Intent(context, WatchService::class.java).setAction(ACTION_START).putExtra(EXTRA_REASON, reason)

        fun actionIntent(context: Context, action: String, byUser: Boolean = true): Intent =
            Intent(context, WatchService::class.java).setAction(action).putExtra(EXTRA_BY_USER, byUser)

        /** Quiet, Pause or Resume while watching; ignored otherwise. */
        fun send(context: Context, action: String) {
            if (WatchBus.running) context.startService(actionIntent(context, action))
        }

        /** Stops watching if it runs. [byUser]: the owner pressed Stop (§5.3), as opposed to the car leaving. */
        fun stop(context: Context, reason: String, byUser: Boolean) {
            if (WatchBus.running) {
                context.startService(actionIntent(context, ACTION_STOP, byUser).putExtra(EXTRA_REASON, reason))
            }
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val handler = Handler(Looper.getMainLooper())
    private val events get() = Graph.events
    private val soundPolicy = SoundPolicy()
    private lateinit var beeper: Beeper

    private var started = false
    private var settings = Settings()
    private var session: WatchSession? = null
    private var source: LocationSource? = null
    private var recorder: DriveRecorder? = null
    private var lastPhase: Phase? = null
    private var lastLine: String? = null
    private var lastButtons: List<NoteButton>? = null

    private val tick = object : Runnable {
        override fun run() {
            session?.let { handle(it.onTick(now())) }
            handler.postDelayed(this, TICK_MS)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        beeper = Beeper(this, events)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START
        if (action == ACTION_START) {
            if (!started) begin(intent?.getStringExtra(EXTRA_REASON) ?: "restarted by Android")
            return START_NOT_STICKY
        }
        if (!started) {
            stopSelf(startId) // a late button press after watching ended
            return START_NOT_STICKY
        }
        when (action) {
            ACTION_STOP -> end(intent?.getStringExtra(EXTRA_REASON) ?: "stop", intent?.getBooleanExtra(EXTRA_BY_USER, false) == true)
            ACTION_QUIET -> session?.let { handle(it.quiet(now())) }
            ACTION_PAUSE -> session?.let { handle(it.pause(now())) }
            ACTION_RESUME -> session?.let { handle(it.resume(now())) }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        if (started) end("service destroyed", byUser = false)
        scope.cancel()
        super.onDestroy()
    }

    private fun begin(reason: String) {
        // Foreground first: Android allows only a few seconds after startForegroundService.
        try {
            ServiceCompat.startForeground(
                this,
                WatchNotifications.WATCH_ID,
                WatchNotifications.watching(this, null, "Starting"),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION,
            )
        } catch (e: Exception) { // SecurityException without location permission, or a refused background start
            events.log("could not watch ($reason): ${e.javaClass.simpleName}: ${e.message}")
            WatchNotifications.showProblem(this, "SpeedBeep could not start watching. Open SpeedBeep and finish its setup.")
            stopSelf()
            return
        }
        started = true
        scope.launch {
            settings = Graph.settings.settings.first()
            startSession(reason)
            Graph.settings.settings.collect { applySettings(it) }
        }
    }

    private fun startSession(reason: String) {
        if (!started) return
        val s = WatchSession(settings, now(), Graph.roads.value?.lookup ?: NoRoadData)
        session = s
        scope.launch { Graph.roads.filterNotNull().collect { s.setRoads(it.lookup) } }
        val src = LocationSources.create(this, events)
        source = src
        try {
            src.start(::onFix)
        } catch (e: SecurityException) {
            events.log("GPS refused: ${e.message}")
            WatchNotifications.showProblem(this, "SpeedBeep has no location permission. Open SpeedBeep and finish its setup.")
            end("no location permission", byUser = false)
            return
        }
        events.log("watching started ($reason) with ${src.name}")
        handle(s.onTick(now()))
        handler.postDelayed(tick, TICK_MS)
    }

    private fun applySettings(new: Settings) {
        settings = new
        if (!new.recordDrives) {
            recorder?.close()
            recorder = null
        }
        session?.let {
            it.updateSettings(new)
            publish(it.status)
        }
    }

    private fun onFix(fix: Fix) {
        val s = session ?: return
        if (settings.recordDrives) {
            val r = recorder ?: DriveRecorder(Graph.drivesDir(this)).also { recorder = it }
            r.record(fix, System.currentTimeMillis())
        }
        handle(s.onFix(fix, now()))
    }

    private fun handle(update: WatchUpdate) {
        val status = update.status
        when (soundPolicy.decide(update.sound, now(), settings.mainBeepMs)) {
            SoundCommand.PLAY_MAIN -> {
                beeper.playMain(settings)
                events.log("main beep at ${speed(status)}")
            }
            SoundCommand.PLAY_REMINDER -> {
                beeper.playReminder(settings)
                events.log("reminder at ${speed(status)}")
            }
            SoundCommand.STOP -> {
                beeper.stop()
                events.log("beep stopped")
            }
            SoundCommand.NONE -> Unit
        }
        if (status.phase != lastPhase) {
            val noData = if (status.noRoadData) " (no road data)" else ""
            events.log("state ${lastPhase ?: "start"} → ${status.phase} at ${speed(status)}, road ${status.roadContext}$noData")
            lastPhase = status.phase
        }
        publish(status)
    }

    private fun publish(status: WatchStatus) {
        WatchBus.publish(status)
        val line = StatusText.line(status, now(), System.currentTimeMillis(), ZoneId.systemDefault())
        val buttons = StatusText.buttons(status.phase)
        if (line != lastLine || buttons != lastButtons) {
            getSystemService(NotificationManager::class.java)
                .notify(WatchNotifications.WATCH_ID, WatchNotifications.watching(this, status, line))
            lastLine = line
            lastButtons = buttons
        }
    }

    private fun end(reason: String, byUser: Boolean) {
        events.log("watching stopped ($reason${if (byUser) ", by the owner" else ""})")
        started = false
        handler.removeCallbacks(tick)
        scope.coroutineContext.cancelChildren()
        source?.stop()
        source = null
        beeper.stop()
        recorder?.close()
        recorder = null
        session = null
        lastPhase = null
        lastLine = null
        lastButtons = null
        WatchBus.publish(null)
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun speed(status: WatchStatus) = status.speedKmh?.let { "${it.toInt()} km/h" } ?: "–"

    private fun now() = SystemClock.elapsedRealtime()
}
```

- [ ] **Step 3: Wire up the app**

Replace `app/src/main/java/io/github/lrosa2/speedbeep/App.kt`:

```kotlin
package io.github.lrosa2.speedbeep

import android.app.Application
import io.github.lrosa2.speedbeep.watch.WatchNotifications

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        Graph.init(this)
        WatchNotifications.createChannels(this)
    }
}
```

In `Graph.kt`, add the import `android.content.Context` and this function after `init`:

```kotlin
    /** Recorded drives; shared through the FileProvider in Task 7. */
    fun drivesDir(context: Context): File = File(context.getExternalFilesDir(null) ?: context.filesDir, "drives")
```

Replace `app/src/main/java/io/github/lrosa2/speedbeep/ui/MainActivity.kt` (still a
placeholder screen; Task 7 replaces it):

```kotlin
package io.github.lrosa2.speedbeep.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import io.github.lrosa2.speedbeep.watch.WatchStarter

class MainActivity : ComponentActivity() {
    companion object {
        /** Opens the app and starts watching: the tile's fallback (§5.2), and the emulator checks. */
        const val EXTRA_START = "io.github.lrosa2.speedbeep.extra.START"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleStart(intent)
        setContent { MaterialTheme { Text("SpeedBeep") } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleStart(intent)
    }

    private fun handleStart(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_START, false) == true) {
            intent.removeExtra(EXTRA_START)
            WatchStarter.start(this, "app opened to start", fromBackground = false)
        }
    }
}
```

In `AndroidManifest.xml`, insert before `</application>`:

```xml
        <service
            android:name=".watch.WatchService"
            android:exported="false"
            android:foregroundServiceType="location" />
```

`tools/replay/make-replays.sh`:

```bash
#!/usr/bin/env bash
# make-replays.sh OUTDIR : synthetic drives at Plan 1's spot-check points, for debug replay.
# Each row: one fix per second, speed accuracy 0.5 m/s, position accuracy 5 m.
set -e
out=${1:-.}
mkdir -p "$out"

drive() { # file lat lon bearing speed-per-second...
  local f=$1 lat=$2 lon=$3 bearing=$4
  shift 4
  {
    echo "# SpeedBeep drive v1"
    echo "t_ms,lat,lon,speed_mps,speed_acc_mps,h_acc_m,bearing_deg"
    local t=0
    for kmh in "$@"; do
      awk -v t="$t" -v lat="$lat" -v lon="$lon" -v kmh="$kmh" -v b="$bearing" \
        'BEGIN { printf "%d,%.7f,%.7f,%.2f,0.5,5.0,%.1f\n", t * 1000, lat, lon, kmh / 3.6, b }'
      t=$((t + 1))
    done
  } > "$out/$f"
  echo "wrote $out/$f"
}

times() { for _ in $(seq "$1"); do printf '%s ' "$2"; done; }

# Seefeldstrasse, Zürich (posted 50 → TOWN): 5 s at 45, 15 s at 60, 10 s at 45.
drive town-over.csv 47.356325 8.555848 147 $(times 5 45) $(times 15 60) $(times 10 45)
# A1 near Winterthur (FAST): 20 s at 110.
drive motorway.csv 47.45 8.70 35 $(times 20 110)
# Stuttgart, outside the road file (NOT COVERED): 10 s at 60.
drive abroad.csv 48.78 9.18 0 $(times 10 60)
# Seefeldstrasse again, staying over for 2 minutes: for the notification-button checks.
drive long-over.csv 47.356325 8.555848 147 $(times 5 45) $(times 120 60)
```

- [ ] **Step 4: Build and run the unit tests**

Run: `GT :app:testDebugUnitTest` then `GW :app:assembleDebug`
Expected: `app: 54 tests, 0 failures, 0 errors`, then `BUILD SUCCESSFUL`.

- [ ] **Step 5: Replay the three drives on the emulator**

Setup:
1. Start the emulator in the background.
2. Wait for `ADB shell getprop sys.boot_completed` to print `1`.
3. Then run:

```bash
ADB install -r /e/AndroidProj/SpeedBeep/app/build/outputs/apk/debug/app-debug.apk
for p in ACCESS_FINE_LOCATION ACCESS_COARSE_LOCATION ACCESS_BACKGROUND_LOCATION POST_NOTIFICATIONS; do
  ADB shell pm grant io.github.lrosa2.speedbeep.debug android.permission.$p
done
bash /e/AndroidProj/SpeedBeep/tools/replay/make-replays.sh <workspace>/replays
ADB shell mkdir -p /sdcard/Android/data/io.github.lrosa2.speedbeep.debug/files
```

For each drive (`town-over`, `motorway`, `abroad`):

```bash
ADB shell am force-stop io.github.lrosa2.speedbeep.debug
ADB push <workspace>/replays/<drive>.csv /sdcard/Android/data/io.github.lrosa2.speedbeep.debug/files/replay.csv
ADB shell run-as io.github.lrosa2.speedbeep.debug rm -f files/events.log
ADB shell am start -n io.github.lrosa2.speedbeep.debug/io.github.lrosa2.speedbeep.ui.MainActivity --ez io.github.lrosa2.speedbeep.extra.START true
```

Poll `ADB shell run-as io.github.lrosa2.speedbeep.debug cat files/events.log` until it shows
`→ NO_GPS` (the replay has ended), or for at most 60 s.

Expected:
- **Every drive** logs `road data loaded (OSM data of 2026-11-25)`, anywhere in the log,
  because it loads in parallel.
- **`town-over`, in this order (other lines may come between):**
  1. `replaying replay.csv: 30 fixes`
  2. `watching started (app opened to start) with replay`
  3. `state ARMED → OVER at 60 km/h, road NORMAL`
  4. `main beep at 60 km/h`
  5. `reminder at 60 km/h`, exactly once, about 10 s after the main beep
  6. `beep stopped`
  7. `state OVER → ARMED at 45 km/h, road NORMAL`
  8. `state ARMED → NO_GPS at –, road NORMAL`
- **`motorway`:**
  - `state ARMED → FAST_ROAD at 110 km/h, road FAST`
  - no `main beep` line
- **`abroad`:**
  - `state ARMED → OVER at 60 km/h, road NORMAL (no road data)`
  - `main beep at 60 km/h`

While `town-over` is OVER, `ADB shell dumpsys notification --noredact | grep android.text`
shows `61 km/h · OVER 53` or `60 km/h · OVER 53`.

- [ ] **Step 6: Check that a refused start explains itself and does not crash**

```bash
ADB shell am force-stop io.github.lrosa2.speedbeep.debug
ADB shell pm revoke io.github.lrosa2.speedbeep.debug android.permission.ACCESS_FINE_LOCATION
ADB shell run-as io.github.lrosa2.speedbeep.debug rm -f files/events.log
ADB shell am start -n io.github.lrosa2.speedbeep.debug/io.github.lrosa2.speedbeep.ui.MainActivity --ez io.github.lrosa2.speedbeep.extra.START true
```

Expected:
- The log shows `not started (app opened to start): location is not allowed`.
- `dumpsys notification --noredact | grep android.text` shows
  `SpeedBeep could not start: location is not allowed. Open SpeedBeep to fix it.`
- `ADB shell pidof io.github.lrosa2.speedbeep.debug` still prints a pid.

Then grant the permission again with `pm grant`.

- [ ] **Step 7: Commit**

```bash
git -C E:/AndroidProj/SpeedBeep add app tools
git -C E:/AndroidProj/SpeedBeep commit -m "Watch service: notification, sounds, recording, replay checks

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 7: Screens, setup checklist and sharing

**Files:**
- Create: `engine/Checklist.kt`
- Create in `ui/`: `SetupProbe.kt`, `Theme.kt`, `Widgets.kt`, `Sharing.kt`,
  `AppScreens.kt`, `MainScreen.kt`, `SettingsScreen.kt`, `AboutScreen.kt`
- Replace: `ui/MainActivity.kt`
- Create: `res/xml/file_paths.xml`
- Modify: `AndroidManifest.xml` (FileProvider)
- Test: `engine/ChecklistTest.kt`

**Interfaces:**
- Consumes:
  - `Settings`, `Mode`, `Output`, `SoundKind`, `CarStart` (Task 2)
  - `WatchBus`, `WatchStarter`, `WatchService.send/stop` (Task 6)
  - `Beeper` (Task 4), `DriveRecorder.latest` (Task 5)
  - `Graph.drivesDir`, `Graph.events`, `Graph.settings`, `Graph.roads`
  - `StatusText` (Task 3)
- Produces:
  - `enum class SetupItem(title, fix)` with `NOTIFICATIONS, LOCATION, BACKGROUND_LOCATION,
    CAR, NEARBY_DEVICES, BATTERY, ALARM_VOLUME`
  - `data class SetupState(...)` and `Checklist.missing(state): List<SetupItem>`
  - Composables in `Widgets.kt`, used by Task 9/10: `BigButton`, `Stepper`, `Choice`,
    `Toggle`, `Section`, `Notice`
  - `SettingsScreen(s, beeper, change, onBack, car: @Composable () -> Unit)`.
    Task 9 fills the `car` slot.

**Rulings in this task:**
- **Navigation is a `rememberSaveable` screen enum** (Main / Settings / About), with no
  navigation library. Three screens do not need one.
- **The checklist also covers two silent failures** (advisor):
  - a zero alarm volume while the output is the phone speaker;
  - a missing "Nearby devices" permission while the notification fallback is chosen.

- [ ] **Step 1: Write the failing test**

`app/src/test/java/io/github/lrosa2/speedbeep/engine/ChecklistTest.kt`:

```kotlin
package io.github.lrosa2.speedbeep.engine

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ChecklistTest {
    private val ok = SetupState(
        notifications = true, location = true, backgroundLocation = true, carLinked = true,
        bluetoothNeeded = false, bluetoothAllowed = false, batteryUnrestricted = true,
        alarmVolumeOn = true, output = Output.PHONE_SPEAKER,
    )

    @Test
    fun `nothing to do when everything is set up`() {
        assertEquals(emptyList<SetupItem>(), Checklist.missing(ok))
    }

    @Test
    fun `location all the time is asked only after location`() {
        assertEquals(listOf(SetupItem.LOCATION), Checklist.missing(ok.copy(location = false, backgroundLocation = false)))
        assertEquals(listOf(SetupItem.BACKGROUND_LOCATION), Checklist.missing(ok.copy(backgroundLocation = false)))
    }

    @Test
    fun `a silent alarm volume matters only on the phone speaker`() {
        assertEquals(listOf(SetupItem.ALARM_VOLUME), Checklist.missing(ok.copy(alarmVolumeOn = false)))
        assertEquals(emptyList<SetupItem>(), Checklist.missing(ok.copy(alarmVolumeOn = false, output = Output.CAR_SPEAKERS)))
    }

    @Test
    fun `nearby devices matters only for the notification fallback`() {
        assertEquals(listOf(SetupItem.NEARBY_DEVICES), Checklist.missing(ok.copy(bluetoothNeeded = true)))
        assertEquals(emptyList<SetupItem>(), Checklist.missing(ok.copy(bluetoothNeeded = true, bluetoothAllowed = true)))
    }

    @Test
    fun `items come in setup order`() {
        val nothing = SetupState(false, false, false, false, true, false, false, false, Output.PHONE_SPEAKER)
        assertEquals(
            listOf(SetupItem.NOTIFICATIONS, SetupItem.LOCATION, SetupItem.CAR, SetupItem.NEARBY_DEVICES, SetupItem.BATTERY, SetupItem.ALARM_VOLUME),
            Checklist.missing(nothing),
        )
    }
}
```

- [ ] **Step 2: Run it and watch it fail**

Run: `GW :app:testDebugUnitTest`
Expected: FAIL at compile time with `Unresolved reference 'SetupState'`.

- [ ] **Step 3: Write the checklist**

`app/src/main/java/io/github/lrosa2/speedbeep/engine/Checklist.kt`:

```kotlin
package io.github.lrosa2.speedbeep.engine

/** One line of the setup checklist on the main screen (spec §7.3), with its fix button's label. */
enum class SetupItem(val title: String, val fix: String) {
    NOTIFICATIONS("Allow notifications, so the controls show on the lock screen", "Allow"),
    LOCATION("Allow location", "Allow"),
    BACKGROUND_LOCATION("Allow location all the time, so the car can start SpeedBeep", "Allow"),
    CAR("Link your car", "Link"),
    NEARBY_DEVICES("Allow nearby devices, so SpeedBeep sees the car connect", "Allow"),
    BATTERY("Turn off battery optimisation (recommended)", "Turn off"),
    ALARM_VOLUME("The alarm volume is off: beeps on the phone speaker are silent", "Volume"),
}

data class SetupState(
    val notifications: Boolean,
    val location: Boolean,
    val backgroundLocation: Boolean,
    val carLinked: Boolean,
    /** The §5.1 notification fallback is chosen, which needs BLUETOOTH_CONNECT. */
    val bluetoothNeeded: Boolean,
    val bluetoothAllowed: Boolean,
    val batteryUnrestricted: Boolean,
    val alarmVolumeOn: Boolean,
    val output: Output,
)

object Checklist {
    fun missing(s: SetupState): List<SetupItem> = buildList {
        if (!s.notifications) add(SetupItem.NOTIFICATIONS)
        if (!s.location) add(SetupItem.LOCATION) else if (!s.backgroundLocation) add(SetupItem.BACKGROUND_LOCATION)
        if (!s.carLinked) add(SetupItem.CAR)
        if (s.bluetoothNeeded && !s.bluetoothAllowed) add(SetupItem.NEARBY_DEVICES)
        if (!s.batteryUnrestricted) add(SetupItem.BATTERY)
        if (s.output == Output.PHONE_SPEAKER && !s.alarmVolumeOn) add(SetupItem.ALARM_VOLUME)
    }
}
```

- [ ] **Step 4: Run it and watch it pass**

Run: `GT :app:testDebugUnitTest`
Expected: `app: 59 tests, 0 failures, 0 errors` (54 + 5).

- [ ] **Step 5: Write the screens**

`app/src/main/java/io/github/lrosa2/speedbeep/ui/SetupProbe.kt`:

```kotlin
package io.github.lrosa2.speedbeep.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.Uri
import android.os.PowerManager
import io.github.lrosa2.speedbeep.engine.CarStart
import io.github.lrosa2.speedbeep.engine.Settings
import io.github.lrosa2.speedbeep.engine.SetupItem
import io.github.lrosa2.speedbeep.engine.SetupState

/** Reads the phone's state for the checklist, and runs each item's fix. */
object SetupProbe {
    fun state(context: Context, s: Settings) = SetupState(
        notifications = granted(context, Manifest.permission.POST_NOTIFICATIONS),
        location = granted(context, Manifest.permission.ACCESS_FINE_LOCATION),
        backgroundLocation = granted(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION),
        carLinked = s.carAddress != null,
        bluetoothNeeded = s.carStart == CarStart.NOTIFICATION,
        bluetoothAllowed = granted(context, Manifest.permission.BLUETOOTH_CONNECT),
        batteryUnrestricted = context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName),
        alarmVolumeOn = context.getSystemService(AudioManager::class.java).getStreamVolume(AudioManager.STREAM_ALARM) > 0,
        output = s.output,
    )

    fun fix(item: SetupItem, context: Context, request: (Array<String>) -> Unit, openSettings: () -> Unit) {
        when (item) {
            SetupItem.NOTIFICATIONS -> request(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
            SetupItem.LOCATION -> request(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
            // Android shows its settings page here, where the owner picks "Allow all the time".
            SetupItem.BACKGROUND_LOCATION -> request(arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION))
            SetupItem.CAR -> openSettings()
            SetupItem.NEARBY_DEVICES -> request(arrayOf(Manifest.permission.BLUETOOTH_CONNECT))
            SetupItem.BATTERY -> context.startActivity(
                Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}")),
            )
            SetupItem.ALARM_VOLUME -> context.getSystemService(AudioManager::class.java)
                .adjustStreamVolume(AudioManager.STREAM_ALARM, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
        }
    }

    fun granted(context: Context, permission: String) =
        context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
}
```

`app/src/main/java/io/github/lrosa2/speedbeep/ui/Theme.kt`:

```kotlin
package io.github.lrosa2.speedbeep.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext

@Composable
fun SpeedBeepTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val colors = if (isSystemInDarkTheme()) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    MaterialTheme(colorScheme = colors) {
        Surface(modifier = Modifier.fillMaxSize(), content = content)
    }
}
```

`app/src/main/java/io/github/lrosa2/speedbeep/ui/Widgets.kt`:

```kotlin
package io.github.lrosa2.speedbeep.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** A full-width button big enough to hit at arm's length in a phone holder (§7.4). */
@Composable
fun BigButton(label: String, onClick: () -> Unit) {
    Button(onClick = onClick, modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp)) {
        Text(label, style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
fun Stepper(label: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        OutlinedButton(onClick = onMinus, modifier = Modifier.size(64.dp), contentPadding = PaddingValues(0.dp)) {
            Text("−", style = MaterialTheme.typography.headlineMedium)
        }
        Text(label, style = MaterialTheme.typography.titleLarge)
        OutlinedButton(onClick = onPlus, modifier = Modifier.size(64.dp), contentPadding = PaddingValues(0.dp)) {
            Text("+", style = MaterialTheme.typography.headlineMedium)
        }
    }
}

/** A row of chips; [options] pairs each label with whether it is selected. */
@Composable
fun Choice(title: String, options: List<Pair<String, Boolean>>, onSelect: (Int) -> Unit) {
    Text(title, style = MaterialTheme.typography.labelLarge)
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEachIndexed { i, (label, selected) ->
            FilterChip(selected = selected, onClick = { onSelect(i) }, label = { Text(label) })
        }
    }
}

@Composable
fun Toggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
fun Section(title: String) {
    Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
}

@Composable
fun Notice(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(text, color = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.padding(12.dp))
    }
}
```

`app/src/main/java/io/github/lrosa2/speedbeep/ui/Sharing.kt`:

```kotlin
package io.github.lrosa2.speedbeep.ui

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

/** The owner sends logs and recordings back with Android's share sheet (§9 step 5). */
object Sharing {
    fun text(context: Context, subject: String, text: String) {
        val send = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_SUBJECT, subject)
            .putExtra(Intent.EXTRA_TEXT, text)
        context.startActivity(Intent.createChooser(send, subject))
    }

    fun file(context: Context, subject: String, file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val send = Intent(Intent.ACTION_SEND)
            .setType("text/csv")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_SUBJECT, subject)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(send, subject))
    }
}
```

`app/src/main/java/io/github/lrosa2/speedbeep/ui/MainActivity.kt`:

```kotlin
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
```

`app/src/main/java/io/github/lrosa2/speedbeep/ui/AppScreens.kt`:

```kotlin
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
```

`app/src/main/java/io/github/lrosa2/speedbeep/ui/MainScreen.kt`:

```kotlin
package io.github.lrosa2.speedbeep.ui

import android.os.SystemClock
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.lrosa2.speedbeep.core.rules.Phase
import io.github.lrosa2.speedbeep.engine.LoadedRoads
import io.github.lrosa2.speedbeep.engine.Mode
import io.github.lrosa2.speedbeep.engine.Settings
import io.github.lrosa2.speedbeep.engine.SetupItem
import io.github.lrosa2.speedbeep.engine.StatusText
import io.github.lrosa2.speedbeep.engine.WatchStatus
import java.time.ZoneId

/** Spec §7.4: large speed, status line, big buttons, mode switch, threshold stepper. */
@Composable
fun MainScreen(
    status: WatchStatus?,
    settings: Settings,
    roads: LoadedRoads?,
    setup: List<SetupItem>,
    onStartStop: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onQuiet: () -> Unit,
    onMode: (Mode) -> Unit,
    onThreshold: (Int) -> Unit,
    onFix: (SetupItem) -> Unit,
    onSettings: () -> Unit,
    onAbout: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onSettings) { Text("Settings") }
            TextButton(onClick = onAbout) { Text("About") }
        }
        roads?.problem?.let { Notice("No road data: simple mode everywhere. ($it)") }
        for (item in setup) SetupRow(item) { onFix(item) }
        Text(
            text = status?.speedKmh?.toInt()?.toString() ?: "–",
            fontSize = 120.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        Text(
            text = status?.let { StatusText.line(it, SystemClock.elapsedRealtime(), System.currentTimeMillis(), ZoneId.systemDefault()) }
                ?: "Not watching",
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        BigButton(if (status == null) "Start" else "Stop", onStartStop)
        if (status != null) {
            if (status.phase == Phase.PAUSED) BigButton("Resume", onResume) else BigButton("Pause ${settings.pauseMin} min", onPause)
            if (status.phase == Phase.OVER) BigButton("Quiet this stretch", onQuiet)
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
        ) {
            FilterChip(selected = settings.mode == Mode.SMART, onClick = { onMode(Mode.SMART) }, label = { Text("Smart") })
            FilterChip(selected = settings.mode == Mode.SIMPLE, onClick = { onMode(Mode.SIMPLE) }, label = { Text("Simple") })
        }
        Stepper("Threshold ${settings.thresholdKmh} km/h", onMinus = { onThreshold(-1) }, onPlus = { onThreshold(+1) })
    }
}

@Composable
private fun SetupRow(item: SetupItem, onFix: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(item.title, modifier = Modifier.weight(1f))
            Button(onClick = onFix) { Text(item.fix) }
        }
    }
}
```

`app/src/main/java/io/github/lrosa2/speedbeep/ui/SettingsScreen.kt`:

```kotlin
package io.github.lrosa2.speedbeep.ui

import android.content.Intent
import android.media.AudioManager
import android.media.RingtoneManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.lrosa2.speedbeep.Graph
import io.github.lrosa2.speedbeep.engine.DriveRecorder
import io.github.lrosa2.speedbeep.engine.Mode
import io.github.lrosa2.speedbeep.engine.Output
import io.github.lrosa2.speedbeep.engine.Settings
import io.github.lrosa2.speedbeep.engine.SoundKind
import io.github.lrosa2.speedbeep.watch.Beeper
import java.util.Locale

/** Spec §7.3. [car] is the linked-car section (Task 9). */
@Composable
fun SettingsScreen(
    s: Settings,
    beeper: Beeper,
    change: ((Settings) -> Settings) -> Unit,
    onBack: () -> Unit,
    car: @Composable () -> Unit,
) {
    val context = LocalContext.current
    var testNote by remember { mutableStateOf<String?>(null) }
    val alarmPicker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val uri = result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
        if (uri != null) {
            val title = RingtoneManager.getRingtone(context, uri)?.getTitle(context)
            change { it.copy(sound = SoundKind.PHONE_ALARM, alarmUri = uri.toString(), alarmTitle = title) }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("‹ Back") }
            Text("Settings", style = MaterialTheme.typography.titleLarge)
        }

        Section("Beep")
        Stepper("Threshold ${s.thresholdKmh} km/h", { change { it.stepThreshold(-1) } }, { change { it.stepThreshold(+1) } })
        Choice("Mode", listOf("Smart" to (s.mode == Mode.SMART), "Simple" to (s.mode == Mode.SIMPLE))) { i ->
            change { it.copy(mode = Mode.entries[i]) }
        }
        Stepper(
            "Main beep %.1f s".format(Locale.ROOT, s.mainBeepMs / 1_000.0),
            { change { it.copy(mainBeepMs = it.mainBeepMs - 500) } },
            { change { it.copy(mainBeepMs = it.mainBeepMs + 500) } },
        )
        Stepper(
            if (s.reminderIntervalS == 0) "Reminder off" else "Reminder every ${s.reminderIntervalS} s",
            { change { it.stepReminder(-1) } },
            { change { it.stepReminder(+1) } },
        )
        Stepper("Pause ${s.pauseMin} min", { change { it.copy(pauseMin = it.pauseMin - 1) } }, { change { it.copy(pauseMin = it.pauseMin + 1) } })

        Section("Sound")
        val sounds = listOf("Beep", "Double beep", "Chime", s.alarmTitle ?: "Phone alarm sound…")
        Choice("Sound", sounds.mapIndexed { i, label -> label to (s.sound.ordinal == i) }) { i ->
            if (i == SoundKind.PHONE_ALARM.ordinal) alarmPicker.launch(alarmPickerIntent(s)) else change { it.copy(sound = SoundKind.entries[i]) }
        }
        Stepper(
            "Volume ${s.volumePercent} %",
            { change { it.copy(volumePercent = it.volumePercent - 10) } },
            { change { it.copy(volumePercent = it.volumePercent + 10) } },
        )
        Choice("Output", listOf("Phone speaker" to (s.output == Output.PHONE_SPEAKER), "Car speakers" to (s.output == Output.CAR_SPEAKERS))) { i ->
            change { it.copy(output = Output.entries[i]) }
        }
        BigButton("Test beep") {
            beeper.playMain(s)
            Graph.events.log("test beep (${s.sound}, ${s.output}, ${s.volumePercent} %)")
            val alarmSilent = context.getSystemService(AudioManager::class.java).getStreamVolume(AudioManager.STREAM_ALARM) == 0
            testNote = if (s.output == Output.PHONE_SPEAKER && alarmSilent) "The alarm volume is off, so the phone speaker stays silent." else null
        }
        testNote?.let { Notice(it) }

        car()

        Section("Screen and records")
        Toggle("Keep screen on while the app is open", s.keepScreenOn) { on -> change { it.copy(keepScreenOn = on) } }
        Toggle("Record drives (for tuning)", s.recordDrives) { on -> change { it.copy(recordDrives = on) } }
        val latest = remember(s.recordDrives) { DriveRecorder.latest(Graph.drivesDir(context)) }
        if (latest != null) {
            OutlinedButton(onClick = { Sharing.file(context, "SpeedBeep ${latest.name}", latest) }, modifier = Modifier.fillMaxWidth()) {
                Text("Share last drive (${latest.name})")
            }
        }
        OutlinedButton(
            onClick = { Sharing.text(context, "SpeedBeep event log", Graph.events.read().takeLast(200_000)) },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Share event log") }
    }
}

private fun alarmPickerIntent(s: Settings) = Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
    .putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
    .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
    .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
    .putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, s.alarmUri?.let { Uri.parse(it) })
```

`app/src/main/java/io/github/lrosa2/speedbeep/ui/AboutScreen.kt`:

```kotlin
package io.github.lrosa2.speedbeep.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.lrosa2.speedbeep.BuildConfig
import io.github.lrosa2.speedbeep.engine.LoadedRoads
import java.time.Instant
import java.time.ZoneOffset

/** Spec §7.5. */
@Composable
fun AboutScreen(roads: LoadedRoads?, onBack: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("‹ Back") }
            Text("About", style = MaterialTheme.typography.titleLarge)
        }
        Text("SpeedBeep ${BuildConfig.VERSION_NAME}")
        Text("Road data © OpenStreetMap contributors, ODbL")
        val ts = roads?.dataTimestampSec
        Text(
            when {
                roads == null -> "Road file: loading"
                roads.problem != null -> "Road file: ${roads.problem}"
                ts != null -> "Road file: OSM data of " + Instant.ofEpochSecond(ts).atOffset(ZoneOffset.UTC).toLocalDate()
                else -> "Road file: no date"
            },
        )
    }
}
```

`app/src/main/res/xml/file_paths.xml`:

```xml
<paths>
    <external-files-path name="drives" path="drives/" />
</paths>
```

In `AndroidManifest.xml`, insert before `</application>`:

```xml
        <provider
            android:name="androidx.core.content.FileProvider"
            android:authorities="${applicationId}.files"
            android:exported="false"
            android:grantUriPermissions="true">
            <meta-data
                android:name="android.support.FILE_PROVIDER_PATHS"
                android:resource="@xml/file_paths" />
        </provider>
```

- [ ] **Step 6: Build**

Run: `GT :app:testDebugUnitTest` then `GW :app:assembleDebug`
Expected: `app: 59 tests, 0 failures, 0 errors`, then `BUILD SUCCESSFUL`.

- [ ] **Step 7: Walk the screens on the emulator**

Install the APK and grant the four permissions, as in Task 6 Step 5. Remove `replay.csv`
from the files folder. Then:

```bash
ADB shell am force-stop io.github.lrosa2.speedbeep.debug
ADB shell am start -n io.github.lrosa2.speedbeep.debug/io.github.lrosa2.speedbeep.ui.MainActivity
ADB exec-out uiautomator dump /dev/tty
```

Expected texts in the dump:
- `Not watching`, `Start`, `Smart`, `Simple`, `Threshold 53 km/h`
- the checklist lines `Link your car` and `Turn off battery optimisation (recommended)`

Then:
1. **Threshold +:** tap the `+` next to the threshold, at the centre of its bounds with
   `ADB shell input tap X Y`. Dump again. Expected: `Threshold 54 km/h`. Tap `−` to go back
   to 53.
2. **Start:** tap `Start`. Expected: `Stop`, `Pause 10 min`, and one of the status lines
   (`Waiting for GPS` or `No GPS`). Tap `Stop`. Expected: `Not watching`.
3. **Settings:** tap `Settings`. Expected: `Test beep`, `Share event log`,
   `Main beep 2.0 s` and `Reminder every 10 s`.
4. **Test beep:** tap `Test beep`. Expected: the event log shows
   `test beep (BEEP, PHONE_SPEAKER, 80 %)`.
5. **About:** press back (`ADB shell input keyevent 4`), then tap `About`. Expected:
   `Road data © OpenStreetMap contributors, ODbL` and
   `Road file: OSM data of 2026-11-25`.

- [ ] **Step 8: Commit**

```bash
git -C E:/AndroidProj/SpeedBeep add app
git -C E:/AndroidProj/SpeedBeep commit -m "Main, settings and about screens with setup checklist and sharing

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 8: Quick-settings tile

**Files:**
- Create: `watch/StartStopTile.kt`
- Modify: `watch/WatchService.kt` (refresh the tile), `AndroidManifest.xml`

**Interfaces:**
- Consumes: `WatchBus`, `WatchStarter`, `WatchService.stop` (Task 6); `MainActivity.EXTRA_START` (Task 7).
- Produces: `StartStopTile.refresh(context)`.

- [ ] **Step 1: Write the tile**

`app/src/main/java/io/github/lrosa2/speedbeep/watch/StartStopTile.kt`:

```kotlin
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
```

In `WatchService.kt`:
- in `startSession`, after the line `events.log("watching started ($reason) with ${src.name}")`,
  add `StartStopTile.refresh(this)`;
- in `end`, after `WatchBus.publish(null)`, add `StartStopTile.refresh(this)`.

In `AndroidManifest.xml`, insert before `</application>`:

```xml
        <service
            android:name=".watch.StartStopTile"
            android:exported="true"
            android:icon="@drawable/ic_stat_speed"
            android:label="@string/tile_label"
            android:permission="android.permission.BIND_QUICK_SETTINGS_TILE">
            <intent-filter>
                <action android:name="android.service.quicksettings.action.QS_TILE" />
            </intent-filter>
        </service>
```

- [ ] **Step 2: Build and click the tile on the emulator**

Run: `GW :app:assembleDebug`, then install as in Task 6, then:

```bash
T=io.github.lrosa2.speedbeep.debug/io.github.lrosa2.speedbeep.watch.StartStopTile
ADB shell am force-stop io.github.lrosa2.speedbeep.debug
ADB shell run-as io.github.lrosa2.speedbeep.debug rm -f files/events.log
ADB shell cmd statusbar add-tile $T
ADB shell cmd statusbar click-tile $T
```

Expected:
- The log shows `tile: start`, then `watching started (tile) with GPS`.
- `ADB shell dumpsys activity services io.github.lrosa2.speedbeep.debug` lists
  `WatchService` with `isForeground=true`.

Run `ADB shell cmd statusbar click-tile $T` again. Expected: `tile: stop`, then
`watching stopped (tile, by the owner)`.

- [ ] **Step 3: Commit**

```bash
git -C E:/AndroidProj/SpeedBeep add app
git -C E:/AndroidProj/SpeedBeep commit -m "Quick-settings tile

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 9: Car link, the companion route

**Files:**
- Create: `engine/CarPolicy.kt`, `car/CarController.kt`, `car/CarLink.kt`,
  `car/CarPresenceService.kt`, `ui/CarSettings.kt`
- Modify:
  - `App.kt` (init `CarController`)
  - `watch/WatchService.kt` (report starts and the owner's Stop)
  - `ui/AppScreens.kt` (car slot)
  - `AndroidManifest.xml`
- Test: `engine/CarPolicyTest.kt`

**Interfaces:**
- Consumes: `WatchStarter`, `WatchService.stop`, `WatchNotifications` (Task 6); widgets (Task 7).
- Produces:
  - `enum class CarAction { START_WATCH, STOP_WATCH, SHOW_STOPPED_NOTE, HIDE_STOPPED_NOTE }`
  - `CarPolicy` with `GRACE_MS`, `present` and `checkAtMs`, and the events:
    - `onCarAppeared()`
    - `onCarGone(nowMs)`
    - `onTick(nowMs)`
    - `onOwnerStop()`
    - `onWatchStarted()`
  - `CarController.init(context)`, `carAppeared(source, autoStart = true)`, `carGone(source)`,
    `ownerStopped()` and `watchStarted()`
  - `CarLink(context)` with `associate(...)`, `observe(info)`, `observeAll()`,
    `stopObservingAll()`, `unlink()` and `CarLink.address(info)`
  - `CarSettings(s, change)` (replaced in Task 10)

**Behaviour (spec §5.1, §5.3):**
- **The car arrives:** watching starts.
- **The car is gone for 30 s:** watching stops. A return within 30 s changes nothing.
- **Stop while the car is there:** shows "SpeedBeep stopped · [Start]". The note goes away
  when the car leaves.
- **Stop, then a drop-out within 30 s:** watching does not restart. Only an arrival after
  a real absence restarts it.

The note is hidden at once when the car leaves, not after 30 s: without watching running,
nothing keeps the process alive long enough to hide it later.

- [ ] **Step 1: Write the failing test**

`app/src/test/java/io/github/lrosa2/speedbeep/engine/CarPolicyTest.kt`:

```kotlin
package io.github.lrosa2.speedbeep.engine

import io.github.lrosa2.speedbeep.engine.CarAction.HIDE_STOPPED_NOTE
import io.github.lrosa2.speedbeep.engine.CarAction.SHOW_STOPPED_NOTE
import io.github.lrosa2.speedbeep.engine.CarAction.START_WATCH
import io.github.lrosa2.speedbeep.engine.CarAction.STOP_WATCH
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class CarPolicyTest {
    private val policy = CarPolicy()
    private val nothing = emptyList<CarAction>()

    @Test
    fun `the car arriving starts watching`() {
        assertEquals(listOf(START_WATCH), policy.onCarAppeared())
    }

    @Test
    fun `a short drop-out changes nothing`() {
        policy.onCarAppeared()
        assertEquals(nothing, policy.onCarGone(0))
        assertEquals(nothing, policy.onCarAppeared())
        assertNull(policy.checkAtMs)
        assertEquals(nothing, policy.onTick(40_000))
    }

    @Test
    fun `the car gone for 30 s stops watching`() {
        policy.onCarAppeared()
        policy.onCarGone(1_000)
        assertEquals(31_000L, policy.checkAtMs)
        assertEquals(nothing, policy.onTick(30_999))
        assertEquals(listOf(STOP_WATCH), policy.onTick(31_000))
        assertFalse(policy.present)
        assertNull(policy.checkAtMs)
    }

    @Test
    fun `after Stop a drop-out does not restart watching`() {
        policy.onCarAppeared()
        assertEquals(listOf(SHOW_STOPPED_NOTE), policy.onOwnerStop())
        assertEquals(listOf(HIDE_STOPPED_NOTE), policy.onCarGone(0))
        assertEquals(listOf(SHOW_STOPPED_NOTE), policy.onCarAppeared())
    }

    @Test
    fun `after Stop the next real arrival starts watching again`() {
        policy.onCarAppeared()
        policy.onOwnerStop()
        policy.onCarGone(0)
        assertEquals(listOf(STOP_WATCH), policy.onTick(30_000))
        assertEquals(listOf(START_WATCH), policy.onCarAppeared())
    }

    @Test
    fun `Stop without the car shows no note`() {
        assertEquals(nothing, policy.onOwnerStop())
    }

    @Test
    fun `starting again hides the stopped note`() {
        policy.onCarAppeared()
        policy.onOwnerStop()
        assertEquals(listOf(HIDE_STOPPED_NOTE), policy.onWatchStarted())
        assertEquals(nothing, policy.onWatchStarted())
    }
}
```

- [ ] **Step 2: Run it and watch it fail**

Run: `GW :app:testDebugUnitTest`
Expected: FAIL at compile time with `Unresolved reference 'CarPolicy'`.

- [ ] **Step 3: Write the policy**

`app/src/main/java/io/github/lrosa2/speedbeep/engine/CarPolicy.kt`:

```kotlin
package io.github.lrosa2.speedbeep.engine

enum class CarAction { START_WATCH, STOP_WATCH, SHOW_STOPPED_NOTE, HIDE_STOPPED_NOTE }

/**
 * When the linked car comes and goes (spec §5.1, §5.3). Times are monotonic milliseconds;
 * call [onTick] at [checkAtMs]. Transport-free: the companion route and the §5.1 fallback
 * both feed it.
 */
class CarPolicy {
    companion object {
        const val GRACE_MS = 30_000L
    }

    var present = false
        private set
    private var goneSinceMs: Long? = null
    private var stoppedByOwner = false

    /** When [onTick] must next run, or null. */
    val checkAtMs: Long? get() = goneSinceMs?.plus(GRACE_MS)

    fun onCarAppeared(): List<CarAction> {
        if (goneSinceMs != null) { // back within the grace period
            goneSinceMs = null
            return if (stoppedByOwner) listOf(CarAction.SHOW_STOPPED_NOTE) else emptyList()
        }
        if (present) return emptyList()
        present = true
        stoppedByOwner = false
        return listOf(CarAction.START_WATCH)
    }

    fun onCarGone(nowMs: Long): List<CarAction> {
        if (!present || goneSinceMs != null) return emptyList()
        goneSinceMs = nowMs
        return if (stoppedByOwner) listOf(CarAction.HIDE_STOPPED_NOTE) else emptyList()
    }

    fun onTick(nowMs: Long): List<CarAction> {
        val since = goneSinceMs ?: return emptyList()
        if (nowMs - since < GRACE_MS) return emptyList()
        present = false
        goneSinceMs = null
        stoppedByOwner = false
        return listOf(CarAction.STOP_WATCH)
    }

    fun onOwnerStop(): List<CarAction> {
        if (!present) return emptyList()
        stoppedByOwner = true
        return if (goneSinceMs == null) listOf(CarAction.SHOW_STOPPED_NOTE) else emptyList()
    }

    fun onWatchStarted(): List<CarAction> {
        if (!stoppedByOwner) return emptyList()
        stoppedByOwner = false
        return listOf(CarAction.HIDE_STOPPED_NOTE)
    }
}
```

- [ ] **Step 4: Run it and watch it pass**

Run: `GT :app:testDebugUnitTest`
Expected: `app: 66 tests, 0 failures, 0 errors` (59 + 7).

- [ ] **Step 5: Write the Android side**

`app/src/main/java/io/github/lrosa2/speedbeep/car/CarController.kt`:

```kotlin
package io.github.lrosa2.speedbeep.car

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import io.github.lrosa2.speedbeep.Graph
import io.github.lrosa2.speedbeep.engine.CarAction
import io.github.lrosa2.speedbeep.engine.CarPolicy
import io.github.lrosa2.speedbeep.watch.WatchNotifications
import io.github.lrosa2.speedbeep.watch.WatchService
import io.github.lrosa2.speedbeep.watch.WatchStarter

/** Runs [CarPolicy] on the main thread and carries out its actions. */
object CarController {
    private val policy = CarPolicy()
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var app: Context
    private var autoStart = true
    private val check = Runnable { run(policy.onTick(now())) }

    fun init(context: Context) {
        app = context.applicationContext
    }

    /** [autoStart] false: the §5.1 fallback, which asks with a notification instead of starting. */
    fun carAppeared(source: String, autoStart: Boolean = true) = onMain {
        Graph.events.log("car appeared ($source)")
        this.autoStart = autoStart
        run(policy.onCarAppeared())
    }

    fun carGone(source: String) = onMain {
        Graph.events.log("car gone ($source)")
        run(policy.onCarGone(now()))
    }

    fun ownerStopped() = onMain { run(policy.onOwnerStop()) }

    fun watchStarted() = onMain { run(policy.onWatchStarted()) }

    private fun run(actions: List<CarAction>) {
        for (action in actions) {
            when (action) {
                CarAction.START_WATCH ->
                    if (autoStart) WatchStarter.start(app, "car connected", fromBackground = true)
                    else WatchNotifications.showCarConnected(app)
                CarAction.STOP_WATCH -> {
                    WatchNotifications.hideCarConnected(app)
                    WatchService.stop(app, "car gone", byUser = false)
                }
                CarAction.SHOW_STOPPED_NOTE -> WatchNotifications.showStopped(app)
                CarAction.HIDE_STOPPED_NOTE -> WatchNotifications.hideStopped(app)
            }
        }
        handler.removeCallbacks(check)
        policy.checkAtMs?.let { handler.postDelayed(check, (it - now()).coerceAtLeast(0)) }
    }

    private fun onMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else handler.post(block)
    }

    private fun now() = SystemClock.elapsedRealtime()
}
```

`app/src/main/java/io/github/lrosa2/speedbeep/car/CarLink.kt`:

```kotlin
package io.github.lrosa2.speedbeep.car

import android.app.Activity
import android.companion.AssociationInfo
import android.companion.AssociationRequest
import android.companion.BluetoothDeviceFilter
import android.companion.CompanionDeviceManager
import android.companion.ObservingDevicePresenceRequest
import android.content.Context
import android.content.IntentSender
import android.os.Build

/** Links the car through CompanionDeviceManager and observes its presence (spec §5.1, D12). */
class CarLink(context: Context) {
    companion object {
        fun address(info: AssociationInfo): String? = info.deviceMacAddress?.toString()?.uppercase()
    }

    private val cdm = context.getSystemService(CompanionDeviceManager::class.java)

    fun associations(): List<AssociationInfo> = cdm.myAssociations

    /** Android's own dialog lists the Bluetooth devices; the owner picks the car. */
    fun associate(
        activity: Activity,
        onPending: (IntentSender) -> Unit,
        onLinked: (AssociationInfo) -> Unit,
        onFailed: (String) -> Unit,
    ) {
        val request = AssociationRequest.Builder()
            .addDeviceFilter(BluetoothDeviceFilter.Builder().build())
            .setSingleDevice(false)
            .build()
        cdm.associate(request, activity.mainExecutor, object : CompanionDeviceManager.Callback() {
            override fun onAssociationPending(intentSender: IntentSender) {
                onPending(intentSender)
            }

            override fun onAssociationCreated(associationInfo: AssociationInfo) {
                onLinked(associationInfo)
            }

            override fun onFailure(error: CharSequence?) {
                onFailed(error?.toString() ?: "unknown error")
            }
        })
    }

    /** Presence events then reach [CarPresenceService]. */
    fun observe(info: AssociationInfo) {
        if (Build.VERSION.SDK_INT >= 36) {
            cdm.startObservingDevicePresence(ObservingDevicePresenceRequest.Builder().setAssociationId(info.id).build())
        } else {
            @Suppress("DEPRECATION")
            cdm.startObservingDevicePresence(address(info) ?: error("association ${info.id} has no address"))
        }
    }

    fun observeAll() = associations().forEach { observe(it) }

    fun stopObservingAll() = associations().forEach { stopObserving(it) }

    fun unlink() {
        for (info in associations()) {
            runCatching { stopObserving(info) }
            cdm.disassociate(info.id)
        }
    }

    private fun stopObserving(info: AssociationInfo) {
        if (Build.VERSION.SDK_INT >= 36) {
            cdm.stopObservingDevicePresence(ObservingDevicePresenceRequest.Builder().setAssociationId(info.id).build())
        } else {
            @Suppress("DEPRECATION")
            cdm.stopObservingDevicePresence(address(info) ?: return)
        }
    }
}
```

`app/src/main/java/io/github/lrosa2/speedbeep/car/CarPresenceService.kt`:

```kotlin
package io.github.lrosa2.speedbeep.car

import android.companion.AssociationInfo
import android.companion.CompanionDeviceService
import android.companion.DevicePresenceEvent
import io.github.lrosa2.speedbeep.Graph

/** Bound by Android while the linked car is present (spec §5.1). */
class CarPresenceService : CompanionDeviceService() {

    // API 34–35 call these; API 36 calls onDevicePresenceEvent instead.
    @Deprecated("Replaced by onDevicePresenceEvent on API 36")
    override fun onDeviceAppeared(associationInfo: AssociationInfo) {
        CarController.carAppeared("companion")
    }

    @Deprecated("Replaced by onDevicePresenceEvent on API 36")
    override fun onDeviceDisappeared(associationInfo: AssociationInfo) {
        CarController.carGone("companion")
    }

    override fun onDevicePresenceEvent(event: DevicePresenceEvent) {
        when (event.event) {
            DevicePresenceEvent.EVENT_BT_CONNECTED, DevicePresenceEvent.EVENT_BLE_APPEARED ->
                CarController.carAppeared("companion event ${event.event}")
            DevicePresenceEvent.EVENT_BT_DISCONNECTED, DevicePresenceEvent.EVENT_BLE_DISAPPEARED ->
                CarController.carGone("companion event ${event.event}")
            else -> Graph.events.log("companion event ${event.event} ignored")
        }
    }
}
```

`app/src/main/java/io/github/lrosa2/speedbeep/ui/CarSettings.kt`:

```kotlin
package io.github.lrosa2.speedbeep.ui

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import io.github.lrosa2.speedbeep.Graph
import io.github.lrosa2.speedbeep.car.CarLink
import io.github.lrosa2.speedbeep.engine.Settings

/** "Linked car: Link / Unlink" (spec §7.3). */
@Composable
fun CarSettings(s: Settings, change: ((Settings) -> Settings) -> Unit) {
    val context = LocalContext.current
    val link = remember { CarLink(context) }
    var message by remember { mutableStateOf<String?>(null) }
    val chooser = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { }

    Section("Car")
    Text(s.carName?.let { "Linked car: $it" } ?: "No car linked")
    if (s.carAddress == null) {
        BigButton("Link my car") {
            message = null
            link.associate(
                context as Activity,
                onPending = { chooser.launch(IntentSenderRequest.Builder(it).build()) },
                onLinked = { info ->
                    runCatching { link.observe(info) }.onFailure { e -> Graph.events.log("observe failed: ${e.message}") }
                    Graph.events.log("car linked: ${info.displayName} ${CarLink.address(info)}")
                    change { it.copy(carAddress = CarLink.address(info), carName = info.displayName?.toString() ?: "Car") }
                },
                onFailed = { error ->
                    message = "Linking failed: $error"
                    Graph.events.log("car link failed: $error")
                },
            )
        }
    } else {
        OutlinedButton(
            onClick = {
                link.unlink()
                Graph.events.log("car unlinked")
                change { it.copy(carAddress = null, carName = null) }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Unlink car") }
    }
    message?.let { Notice(it) }
}
```

- [ ] **Step 6: Wire it in**

In `App.kt`, add the import `io.github.lrosa2.speedbeep.car.CarController`, and add
`CarController.init(this)` after `Graph.init(this)`.

In `WatchService.kt`, add the import `io.github.lrosa2.speedbeep.car.CarController`, then:
- in `startSession`, after `StartStopTile.refresh(this)`, add `CarController.watchStarted()`;
- in `end`, after `StartStopTile.refresh(this)`, add `if (byUser) CarController.ownerStopped()`.

In `AppScreens.kt`, replace `car = {}` with `car = { CarSettings(s, change) }`.

In `AndroidManifest.xml`, insert before `</application>`:

```xml
        <service
            android:name=".car.CarPresenceService"
            android:exported="true"
            android:permission="android.permission.BIND_COMPANION_DEVICE_SERVICE">
            <intent-filter>
                <action android:name="android.companion.CompanionDeviceService" />
            </intent-filter>
        </service>
```

- [ ] **Step 7: Build and check on the emulator**

Run: `GT :app:testDebugUnitTest` then `GW :app:assembleDebug`
Expected: `app: 66 tests, 0 failures, 0 errors`, then `BUILD SUCCESSFUL`.

Install, open Settings, and tap `Link my car`. Expected:
- `ADB shell dumpsys activity top | grep ACTIVITY` names a `companiondevicemanager`
  activity (Android's device chooser).
- Back out of it with keyevent 4. The app keeps running, and the log may show
  `car link failed: …`.

The emulator has no car, so presence events are not testable here. They are checked in
the car (`docs/IN-CAR-TEST.md`).

- [ ] **Step 8: Commit**

```bash
git -C E:/AndroidProj/SpeedBeep add app
git -C E:/AndroidProj/SpeedBeep commit -m "Car link through CompanionDeviceManager, with 30 s grace and stopped note

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 10: The notification fallback, switchable (D16)

**Files:**
- Create: `car/AclReceiver.kt`
- Replace: `ui/CarSettings.kt`
- Modify: `AndroidManifest.xml`, and the spec: §2 (D16), §5.1 and §7.3

**Interfaces:**
- Consumes:
  - `Settings.carStart`, `carAddress` and `carName` (Task 2)
  - `CarController.carAppeared(source, autoStart = false)` and `carGone` (Task 9)
  - `WatchNotifications.hideCarConnected` (Task 6)
- Produces: the `AclReceiver` manifest receiver, and the setting "When the car connects:
  Start watching / Ask with a notification".

**Rulings in this task:**
- **The §5.1 fallback is compiled in now** rather than only after a failed spike. The owner
  asked to finish before the car test, and one trip can then try both routes.
- **The default stays the companion route (D12).** D14 confirms or swaps it.
- **In fallback mode, companion presence observation is switched off**, so the car never
  starts watching twice.
- **The car can be picked from the paired devices** when the companion dialog does not list
  it. That needs "Nearby devices" (BLUETOOTH_CONNECT), which the checklist asks for
  (Task 7).

- [ ] **Step 1: Record D16 in the spec**

In `docs/superpowers/specs/2026-11-25-speedbeep-design.md`:

1. Add this row after D15:
   ```
   | D16 | The §5.1 notification fallback is built in from the start, chosen in settings ("When the car connects: Start watching / Ask with a notification"); the default is the companion route. One car trip can then test both, and D14 decides the default. | owner's request 2026-11-25 to finish the app before the car test |
   ```
2. In §5.1, replace the line
   `- **Fallback, used only if the spike (§9) shows the companion route doesn't work on the`
   and the line `  phone:**` after it with:
   `- **Fallback, built in and chosen in settings (D16). The default is the companion route:**`
   Also add a bullet at the end of that list:
   `  - The car can also be picked from the paired devices, which needs "Nearby devices".`
3. In the §7.3 table, after the `Linked car` row, add:
   `| When the car connects | Start watching | Start watching / Ask with a notification |`

- [ ] **Step 2: Write the receiver**

`app/src/main/java/io/github/lrosa2/speedbeep/car/AclReceiver.kt`:

```kotlin
package io.github.lrosa2.speedbeep.car

import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.lrosa2.speedbeep.Graph
import io.github.lrosa2.speedbeep.engine.CarStart
import io.github.lrosa2.speedbeep.watch.WatchNotifications
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Spec §5.1 fallback (D16): Bluetooth ACL broadcasts for the linked car, used only when
 * "When the car connects" is "Ask with a notification". ACL broadcasts come only from the
 * system, and receiving them needs BLUETOOTH_CONNECT.
 */
class AclReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java) ?: return
        val action = intent.action
        val pending = goAsync()
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val s = Graph.settings.settings.first()
                if (s.carStart != CarStart.NOTIFICATION || !device.address.equals(s.carAddress, ignoreCase = true)) return@launch
                when (action) {
                    BluetoothDevice.ACTION_ACL_CONNECTED -> CarController.carAppeared("bluetooth", autoStart = false)
                    BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                        WatchNotifications.hideCarConnected(context)
                        CarController.carGone("bluetooth")
                    }
                }
            } finally {
                pending.finish()
            }
        }
    }
}
```

In `AndroidManifest.xml`, insert before `</application>`:

```xml
        <receiver
            android:name=".car.AclReceiver"
            android:exported="true">
            <intent-filter>
                <action android:name="android.bluetooth.device.action.ACL_CONNECTED" />
                <action android:name="android.bluetooth.device.action.ACL_DISCONNECTED" />
            </intent-filter>
        </receiver>
```

- [ ] **Step 3: Replace the car settings**

`app/src/main/java/io/github/lrosa2/speedbeep/ui/CarSettings.kt`:

```kotlin
package io.github.lrosa2.speedbeep.ui

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothManager
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import io.github.lrosa2.speedbeep.Graph
import io.github.lrosa2.speedbeep.car.CarLink
import io.github.lrosa2.speedbeep.engine.CarStart
import io.github.lrosa2.speedbeep.engine.Settings

/** "Linked car" and "When the car connects" (spec §7.3, D16). */
@Composable
fun CarSettings(s: Settings, change: ((Settings) -> Settings) -> Unit) {
    val context = LocalContext.current
    val link = remember { CarLink(context) }
    var message by remember { mutableStateOf<String?>(null) }
    var picking by remember { mutableStateOf(false) }
    val chooser = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { }
    val nearby = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        message = if (granted) null else "Without \"Nearby devices\" SpeedBeep cannot see the car connect."
    }
    val nearbyGranted = SetupProbe.granted(context, Manifest.permission.BLUETOOTH_CONNECT)

    Section("Car")
    Text(s.carName?.let { "Linked car: $it" } ?: "No car linked")
    if (s.carAddress == null) {
        BigButton("Link my car") {
            message = null
            link.associate(
                context as Activity,
                onPending = { chooser.launch(IntentSenderRequest.Builder(it).build()) },
                onLinked = { info ->
                    if (s.carStart == CarStart.AUTOMATIC) {
                        runCatching { link.observe(info) }.onFailure { e -> Graph.events.log("observe failed: ${e.message}") }
                    }
                    Graph.events.log("car linked: ${info.displayName} ${CarLink.address(info)}")
                    change { it.copy(carAddress = CarLink.address(info), carName = info.displayName?.toString() ?: "Car") }
                },
                onFailed = { error ->
                    message = "Linking failed: $error"
                    Graph.events.log("car link failed: $error")
                },
            )
        }
        if (s.carStart == CarStart.NOTIFICATION) {
            OutlinedButton(
                onClick = { if (nearbyGranted) picking = true else nearby.launch(Manifest.permission.BLUETOOTH_CONNECT) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Pick from paired devices") }
        }
    } else {
        OutlinedButton(
            onClick = {
                link.unlink()
                Graph.events.log("car unlinked")
                change { it.copy(carAddress = null, carName = null) }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Unlink car") }
    }
    Choice(
        "When the car connects",
        listOf("Start watching" to (s.carStart == CarStart.AUTOMATIC), "Ask with a notification" to (s.carStart == CarStart.NOTIFICATION)),
    ) { i ->
        val mode = CarStart.entries[i]
        if (mode == CarStart.NOTIFICATION) {
            runCatching { link.stopObservingAll() }
            if (!nearbyGranted) nearby.launch(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            runCatching { link.observeAll() }.onFailure { e -> Graph.events.log("observe failed: ${e.message}") }
        }
        Graph.events.log("car start: $mode")
        change { it.copy(carStart = mode) }
    }
    message?.let { Notice(it) }
    if (picking) {
        PairedDevicesDialog(
            context,
            onPick = { name, address ->
                picking = false
                Graph.events.log("car picked: $name $address")
                change { it.copy(carAddress = address, carName = name) }
            },
            onDismiss = { picking = false },
        )
    }
}

/** Shown only once BLUETOOTH_CONNECT is granted. */
@SuppressLint("MissingPermission")
@Composable
private fun PairedDevicesDialog(context: Context, onPick: (String, String) -> Unit, onDismiss: () -> Unit) {
    val devices = remember {
        context.getSystemService(BluetoothManager::class.java).adapter?.bondedDevices.orEmpty()
            .map { (it.name ?: it.address) to it.address.uppercase() }
            .sortedBy { it.first }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        title = { Text("Paired devices") },
        text = {
            Column {
                if (devices.isEmpty()) Text("No paired devices")
                for ((name, address) in devices) {
                    TextButton(onClick = { onPick(name, address) }) { Text(name) }
                }
            }
        },
    )
}
```

- [ ] **Step 4: Build and check on the emulator**

Run: `GT :app:testDebugUnitTest` then `GW :app:assembleDebug`
Expected: `app: 66 tests, 0 failures, 0 errors`, then `BUILD SUCCESSFUL`.

Install, then in Settings tap `Ask with a notification`. Expected:
- the permission dialog for nearby devices appears;
- after allowing it, `Pick from paired devices` shows;
- the log shows `car start: NOTIFICATION`.

Tap `Start watching` to switch back. Expected: `car start: AUTOMATIC`.

`ADB shell dumpsys package io.github.lrosa2.speedbeep.debug | grep -A3 "android.bluetooth.device.action.ACL_CONNECTED"`
lists the receiver. The broadcast is protected, so it cannot be sent from the shell; the car
test covers it.

- [ ] **Step 5: Commit**

```bash
git -C E:/AndroidProj/SpeedBeep add app docs/superpowers/specs/2026-11-25-speedbeep-design.md
git -C E:/AndroidProj/SpeedBeep commit -m "Switchable notification fallback for the car link (D16)

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 11: End-to-end check on the emulator

**Files:**
- Modify: none, unless a check fails. A failure goes through
  superpowers:systematic-debugging, and its fix gets a test where the cause is in `engine`.

**Interfaces:**
- Consumes: everything above.
- Produces: ledger lines with the results, including the `geo fix` finding (§9 step 4).

- [ ] **Step 1: Notification buttons during a replay**

Push `long-over.csv` as `replay.csv`, clear the log, and start through the activity extra,
as in Task 6 Step 5. Once the log shows `main beep`:
1. `ADB shell cmd statusbar expand-notifications`, then dump with uiautomator. Expected:
   the buttons `Quiet this stretch`, `Pause 10 min` and `Stop`.
2. Tap `Quiet this stretch`. Expected: `beep stopped`, then `state OVER → QUIET`.
3. Expand and dump again, then tap `Pause 10 min`. Expected: `state QUIET → PAUSED`, and
   the notification text reads `Paused until HH:MM`, about 10 minutes from now.
4. Tap `Resume`. Expected: `state PAUSED → ARMED`, then `→ OVER` with a new `main beep`
   within about 2 s, because the replay is still at 60.
5. Tap `Stop`. Expected: `watching stopped (notification, by the owner)`, and no SpeedBeep
   notification is left.

- [ ] **Step 2: Does `geo fix` carry speed? (spec §9 step 4)**

1. Remove `replay.csv`, then start watching (the source is now GPS).
2. Run `ADB emu help geo` (or `ADB emu geo`) and note the `fix` arguments.
3. Send five fixes, one per second, using the speed argument if the help lists one, for
   example `ADB emu geo fix 8.555848 47.356325 400 8 32`.
4. Read the notification text.

Record in the ledger whether a speed arrives (`NN km/h · …`) or not (`Waiting for GPS` /
`No GPS`). Use replay where it does not.

- [ ] **Step 3: Recording and sharing**

1. In Settings, turn on `Record drives`.
2. Replay `town-over` as in Task 6.
3. Run `ADB shell ls /sdcard/Android/data/io.github.lrosa2.speedbeep.debug/files/drives/`.
   Expected: one `drive-*.csv`. Pull it: it starts with `# SpeedBeep drive v1` and has 30
   data rows.
4. Back in Settings, expected: `Share last drive (drive-….csv)`. Tap it. Expected: the
   share sheet (`ADB shell dumpsys activity top | grep ACTIVITY` shows a chooser or
   resolver activity).
5. Back out, and tap `Share event log`. Expected: the same.

- [ ] **Step 4: Full suite and stop the emulator**

Run: `GT test`
Expected: the `app`, `core` and `roadbuilder` lines all show `0 failures, 0 errors`
(app 66, core and roadbuilder as at the end of Plan 1).

Run `ADB emu kill`.

- [ ] **Step 5: Ledger**

Write the results of Steps 1–4 into the ledger. No commit unless a fix was needed.

---

### Task 12: Release build, in-car test protocol, review and hand-off

**Files:**
- Create: `docs/IN-CAR-TEST.md`
- Local only, never committed: `keystore.properties`, copied from CarCare

**Interfaces:**
- Consumes: the finished app.
- Produces:
  - a signed release APK
  - the owner's one-trip protocol
  - the final review
  - the hand-off message

- [ ] **Step 1: Sign the release build**

```bash
test -f /e/AndroidProj/CarCare/keystore.properties && cp /e/AndroidProj/CarCare/keystore.properties /e/AndroidProj/SpeedBeep/keystore.properties
git -C E:/AndroidProj/SpeedBeep check-ignore keystore.properties
```

Expected: `check-ignore` prints `keystore.properties`. Never print the file's contents. If
CarCare has no such file, skip signing and say so in the hand-off.

Run: `GW :app:assembleRelease`, then
`/c/Android/sdk/build-tools/<newest>/apksigner.bat verify --print-certs app/build/outputs/apk/release/app-release.apk | grep "SHA-256"`
Expected: `BUILD SUCCESSFUL` and one certificate digest line.

Copy the APKs for the owner to `C:\work\tmp\speedbeep-apk\`:
- `app-release.apk` → `SpeedBeep-0.1.0.apk`
- the spike's `app-debug.apk` → `SpeedBeep-spike.apk`

- [ ] **Step 2: Write the in-car protocol**

`docs/IN-CAR-TEST.md`:

```markdown
# SpeedBeep: the one-trip car test

About 10 minutes parked and one drive of about 20 minutes, with some town streets and a
stretch of motorway or 80 road. Afterwards, send back the two logs and the recorded drive.

Files: `C:\work\tmp\speedbeep-apk\SpeedBeep-spike.apk` and `SpeedBeep-0.1.0.apk`.
Copy them to the phone and open each one to install it (allow installing from that source).

## Part A: the spike, parked (decides D14)

Follow `C:\work\tmp\speedbeep-spike\SPIKE-TEST-PROTOCOL.md`, steps 1–7. Then uninstall the
spike app, so it does not also react to the car.

If time is short, skip Part A: SpeedBeep's own event log records the same things.

## Part B: SpeedBeep, parked

1. Open SpeedBeep. Work through the checklist on the main screen:
   - notifications: allow
   - location: allow, then "Allow all the time"
   - link your car: Settings → Link my car → pick the car in Android's list
   - battery optimisation: turn off
2. Settings → turn on **Record drives**.
3. **Test beep, phone speaker.** Settings → Output: Phone speaker → Test beep.
   - Heard? Loud enough?
4. **Test beep, car speakers.** Turn the radio on with music over Bluetooth. Then
   Output: Car speakers → Test beep.
   - Heard through the car?
   - Did the music get quieter during the beep?
   - Set the output you prefer.
5. **Tile.** Pull down quick settings, edit, and add the **SpeedBeep** tile. Tap it: the
   SpeedBeep notification appears. Tap it again: it goes away.
6. **Lock screen.** Tap Start in the app, then lock the phone. On the lock screen, without
   unlocking:
   - the notification shows speed and state;
   - **Pause 10 min** shows "Paused until …";
   - **Resume** works;
   - **Stop**: watching ends, and a small "SpeedBeep stopped · Start" note stays;
   - **Start** on that note starts watching again.

## Part C: auto-start and the drive

1. Swipe SpeedBeep away from the recent apps, lock the phone, and turn the car off. Wait
   one minute.
2. Turn the car on. Within about 10 s the SpeedBeep notification should appear by itself.
   - If it does not: open SpeedBeep → Settings → **When the car connects: Ask with a
     notification** and allow "Nearby devices".
   - Then turn the car off and on again: a "Car connected · Start" notification should
     appear. Tap Start.
   - Note which of the two worked.
3. **Drive.**
   - **In town:** go a little above 53. A beep within about 2 s, then a short reminder
     every 10 s while over. Once use **Quiet this stretch**.
   - **On the motorway or an 80 road:** silent after about 5 s at speed.
   - **Back in town:** beeps again above 53.
4. Park and turn the car off. After about 30 s the notification should go away.

## Send back

- Settings → **Share event log**
- Settings → **Share last drive**
- the spike log from Part A, if you did it
- one line per numbered step above: worked / did not work / what happened
```

- [ ] **Step 3: Commit**

```bash
git -C E:/AndroidProj/SpeedBeep add docs/IN-CAR-TEST.md
git -C E:/AndroidProj/SpeedBeep commit -m "In-car test protocol for the owner

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

- [ ] **Step 4: Final whole-branch review**

Build the review package from `91bcb61` (the Plan 1 review's head) to `HEAD`. That way it
also covers the unreviewed built-up measurement commit `bfd2947` and the spec commits.
Dispatch a fresh reviewer on the most capable model, with:
- this plan's Review Focus;
- spec §3–§9;
- the ledger's `Ruling:` lines.

Then run one fix pass: Critical and Important findings get RED→GREEN tests where the cause
is in `engine`, and an emulator re-check where it is Android-only. Minor findings go to the
ledger.

- [ ] **Step 5: Hand off**

The message to the owner covers:
- **What works:** the test counts and the emulator results of Tasks 6–11.
- **What only the car can show:** auto-start, the audio routes, lock-screen actions, and
  the API 35/36 presence path (the emulator is API 34).
- **The rulings list and the deferred minors.**
- **Where the APKs are, and `docs/IN-CAR-TEST.md`.**
- **Still open:**
  - D14 from the spike or app log;
  - tuning the 20 m, 30° and 5/2-reading values from the recorded drive (§9 step 5);
  - merging `plan1-core` and `plan2-app` into `main`;
  - creating the private repo `LRosa2/SpeedBeep` (only on the owner's OK).
