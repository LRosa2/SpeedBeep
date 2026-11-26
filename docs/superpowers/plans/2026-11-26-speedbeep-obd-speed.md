# SpeedBeep OBD adapter speed — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** In simple mode, take the speed from a paired ELM327 OBD-II adapter, with GPS as the fallback (spec §4.5, D17).

**Architecture:**
- **Pure engine code, JVM-tested:**
  - the ELM327 protocol over an `ObdLink` byte-stream interface;
  - the switch between car and GPS speed inside `WatchSession`;
  - settings, drive CSV v2 and the status text.
- **Android shell:**
  - an `ObdSource` thread that owns the link, with a classic-Bluetooth `RfcommLink`;
  - a simulated adapter in debug builds;
  - the settings section and the `WatchService` wiring.

**Tech Stack:** as Plan 2 (Kotlin 2.3.21, AGP 9.4.1, Compose BOM 2026.09.00, JUnit 6). No new dependencies.

**Spec:** `docs/superpowers/specs/2026-11-25-speedbeep-design.md` (§4.5, D17, §7.3)

## Global Constraints

- **Simple mode only:** the car's speed is used in simple mode; smart mode stays on GPS speed (§4.5).
- **Car speed first:** a GPS fix is ignored while the last car reading is ≤ 3 s old (`CAR_FRESH_MS = 3_000`).
- **Polling:** speed request `010D` once a second; reconnect every 5 s after a failure.
- **Setup commands:** `ATZ`, `ATE0`, `ATL0`, `ATS0`, `ATH0`, `ATSP0`. Several speeds in one reply → the highest counts (D10).
- **Failed requests:** one failed request is a missing reading; three in a row drop the connection.
- **Connection:** classic Bluetooth SPP only, UUID `00001101-0000-1000-8000-00805F9B34FB`. No `BLUETOOTH_SCAN` and no `cancelDiscovery()`.
- **Foreground service:** stays type `location` only.
- **Background thread:** Bluetooth IO never runs on the main thread. Readings and log lines are posted to the main thread (`EventLog`, `WatchSession` and `SpeedRules` are single-threaded).
- **Event log stays bounded:** repeated identical adapter problems are logged once, not every 5 s.
- **Compatibility:** v1 drive files still parse, and settings saved by 0.1.0 still load.
- **Version:** `versionCode = 2`, `versionName = "0.2.0"`, so the APK installs over 0.1.0.

## Review Focus

1. **Threshold boundary:** a car reading of exactly 53 must count as at the threshold. No m/s round trip may turn it into 52.99. (Task 2 test.)
2. **Adapter drops while OVER:** GPS takes over within 4 s, with no NO_GPS and no second main beep. (Task 2 test.)
3. **Adapter never reachable:** unplugged, or ignition off for the whole drive. GPS must work as in 0.1.0, and the event log must not grow every 5 s. (Task 4, `problem()` dedup.)
4. **Cheap copies:** echo, spaces, `SEARCHING...`, `?` replies to settings, NUL bytes, several ECUs, headers left on. (Task 1 tests.)
5. **Stop while the OBD thread is blocked:** in connect or read, watching must stop at once, with no reading after stop and no crash. (Task 4: close plus interrupt plus the `running` check on the main thread.)

## File structure

- `engine/Elm327.kt`: `ObdLink`, `ObdException`, `SpeedReply`, `Elm327`. Pure.
- `engine/WatchSession.kt`: adds `SpeedSource`, `WatchStatus.speedSource`, `onCarSpeed`, and the GPS ignore rule.
- `engine/StatusText.kt`: the "· car" marker.
- `engine/Settings.kt`: `obdAddress` and `obdName`, plus the codec.
- `engine/DriveCsv.kt`: v2 with `obd_kmh`. `engine/DriveRecorder.kt`: passes it through.
- `engine/Checklist.kt`: the Nearby-devices title now covers the adapter too.
- `obd/RfcommLink.kt`, `obd/ObdSource.kt`, `obd/ObdTest.kt`: Android.
- `src/debug/.../obd/ObdLinks.kt` and `SimulatedObdLink.kt`; `src/release/.../obd/ObdLinks.kt`.
- `ui/ObdSettings.kt` (new). Modified: `ui/CarSettings.kt` (dialog made internal and generalised), `ui/AppScreens.kt`, `ui/SetupProbe.kt`.
- `watch/WatchService.kt`: starts and stops the adapter, feeds `onCarSpeed`, records `obd_kmh`, logs source switches.

All paths below are relative to `app/src/main/java/io/github/lrosa2/speedbeep/` unless they start with `app/`.

---

### Task 1: ELM327 protocol

**Files:**
- Create: `engine/Elm327.kt`
- Test: `app/src/test/java/io/github/lrosa2/speedbeep/engine/Elm327Test.kt`

**Interfaces:**
- Produces:
  - `interface ObdLink { fun send(command: String); fun readReply(timeoutMs: Long): String; fun close() }`
  - `class ObdException(message: String) : Exception`
  - `sealed interface SpeedReply { Speed(kmh: Int), NoData, Failed(text: String) }`
  - `class Elm327(link: ObdLink, log: (String) -> Unit)` with:
    - `fun open(): String`, which returns the banner;
    - `fun speedKmh(): Int?`;
    - `companion fun parseSpeed(reply: String): SpeedReply`.

- [ ] **Step 1: Write the failing test**

```kotlin
package io.github.lrosa2.speedbeep.engine

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/** Answers each command with [answer]; null is a timeout. */
private class FakeObdLink(private val answer: (String) -> String?) : ObdLink {
    val sent = mutableListOf<String>()
    private var reply: String? = null

    override fun send(command: String) {
        sent += command
        reply = answer(command)
    }

    override fun readReply(timeoutMs: Long) = reply ?: throw ObdException("no reply to ${sent.last()} within $timeoutMs ms")

    override fun close() = Unit
}

class Elm327Test {
    private fun adapter(speed: () -> String = { "41 0D 32\r\r" }, setup: (String) -> String = { "OK\r\r" }) = FakeObdLink { c ->
        when (c) {
            "ATZ" -> "ATZ\r\r\rELM327 v1.5\r\r"
            "010D" -> speed()
            "ATDPN" -> "A6\r\r"
            else -> setup(c)
        }
    }

    @Test
    fun `a plain speed reply is read as km per h`() = assertEquals(SpeedReply.Speed(58), Elm327.parseSpeed("410D3A"))

    @Test
    fun `echo, spaces, searching and line breaks are ignored`() =
        assertEquals(SpeedReply.Speed(50), Elm327.parseSpeed("010D\rSEARCHING...\r41 0D 32 \r\r"))

    @Test
    fun `with headers left on the speed is still found`() =
        assertEquals(SpeedReply.Speed(50), Elm327.parseSpeed("7E8 03 41 0D 32\r"))

    @Test
    fun `with several control units the highest speed counts`() =
        assertEquals(SpeedReply.Speed(52), Elm327.parseSpeed("41 0D 32\r41 0D 34\r"))

    @Test
    fun `stray NUL bytes from cheap copies are ignored`() =
        assertEquals(SpeedReply.Speed(50), Elm327.parseSpeed("\u000041 0D 32\r"))

    @Test
    fun `no data and errors are told apart`() {
        assertEquals(SpeedReply.NoData, Elm327.parseSpeed("NO DATA\r\r"))
        assertTrue(Elm327.parseSpeed("SEARCHING...\rUNABLE TO CONNECT\r") is SpeedReply.Failed)
        assertTrue(Elm327.parseSpeed("?\r") is SpeedReply.Failed)
        assertTrue(Elm327.parseSpeed("") is SpeedReply.Failed)
    }

    @Test
    fun `open sets the adapter up in order and returns its banner`() {
        val link = adapter()
        val log = mutableListOf<String>()
        assertEquals("ELM327 v1.5", Elm327(link, log::add).open())
        assertEquals(listOf("ATZ", "ATE0", "ATL0", "ATS0", "ATH0", "ATSP0", "010D", "ATDPN"), link.sent)
        assertTrue(log.contains("adapter: first speed reply 41 0D 32"), log.toString())
    }

    @Test
    fun `a copy that refuses a setting still opens, and the refusal is logged`() {
        val log = mutableListOf<String>()
        Elm327(adapter(setup = { if (it == "ATL0") "?\r\r" else "OK\r\r" }), log::add).open()
        assertTrue(log.contains("adapter: ATL0 → ?"), log.toString())
    }

    @Test
    fun `open fails when the car does not answer`() {
        val e = assertThrows<ObdException> { Elm327(adapter(speed = { "SEARCHING...\rUNABLE TO CONNECT\r\r" })) {}.open() }
        assertTrue(e.message!!.contains("UNABLE TO CONNECT"), e.message)
    }

    @Test
    fun `open fails when the adapter is silent`() {
        assertThrows<ObdException> { Elm327(FakeObdLink { null }) {}.open() }
    }

    @Test
    fun `speed requests give km per h, null when missing, and fail after three errors in a row`() {
        val replies = ArrayDeque(listOf("41 0D 32\r", "41 0D 35\r", "NO DATA\r", "?\r", "?\r", "41 0D 36\r", "?\r", "?\r", "?\r"))
        val elm = Elm327(adapter(speed = { replies.removeFirst() })) {}
        elm.open() // takes the first reply
        assertEquals(53, elm.speedKmh())
        assertNull(elm.speedKmh()) // NO DATA
        assertNull(elm.speedKmh()) // first failure
        assertNull(elm.speedKmh()) // second failure
        assertEquals(54, elm.speedKmh()) // a reading resets the count
        assertNull(elm.speedKmh())
        assertNull(elm.speedKmh())
        assertThrows<ObdException> { elm.speedKmh() }
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests '*Elm327Test*'`
Expected: FAIL, compilation error "Unresolved reference 'ObdLink'".

- [ ] **Step 3: Write the implementation**

```kotlin
package io.github.lrosa2.speedbeep.engine

/** A connection to an ELM327 adapter: classic Bluetooth, or simulated in debug builds. Blocking: never on the main thread. */
interface ObdLink {
    /** Sends one command; the link adds the carriage return. */
    fun send(command: String)

    /** The reply up to the adapter's `>` prompt, without it. Throws [ObdException] after [timeoutMs]. */
    fun readReply(timeoutMs: Long): String

    fun close()
}

class ObdException(message: String) : Exception(message)

/** What one vehicle-speed request ("010D") returned. */
sealed interface SpeedReply {
    data class Speed(val kmh: Int) : SpeedReply

    /** The car sent no speed this time. */
    data object NoData : SpeedReply

    data class Failed(val text: String) : SpeedReply
}

/**
 * The part of the ELM327 protocol SpeedBeep needs (spec §4.5): reset and set up the adapter,
 * then ask for the vehicle speed, mode 01 PID 0D, one byte in km/h. [log] gets the adapter's
 * replies during setup, for the event log.
 */
class Elm327(private val link: ObdLink, private val log: (String) -> Unit) {
    companion object {
        const val RESET_TIMEOUT_MS = 5_000L
        const val COMMAND_TIMEOUT_MS = 2_000L

        /** The first request makes the adapter search for the car's protocol, which can take several seconds. */
        const val SEARCH_TIMEOUT_MS = 15_000L
        const val MAX_FAILURES_IN_A_ROW = 3
        private val SETUP = listOf("ATE0", "ATL0", "ATS0", "ATH0", "ATSP0")

        /**
         * Reads a reply to "010D". Tolerates the echo, spaces, NUL bytes, line breaks,
         * "SEARCHING...", headers and several control units; with several speeds the highest counts (D10).
         */
        fun parseSpeed(reply: String): SpeedReply {
            val lines = reply.uppercase().split('\r', '\n')
                .map { line -> line.filter { it > ' ' } }
                .filter { it.isNotEmpty() && it != "SEARCHING..." && it != "010D" }
            val speeds = lines.mapNotNull { line ->
                val at = line.indexOf("410D")
                if (at >= 0 && line.length >= at + 6) line.substring(at + 4, at + 6).toIntOrNull(16) else null
            }
            return when {
                speeds.isNotEmpty() -> SpeedReply.Speed(speeds.max())
                "NODATA" in lines -> SpeedReply.NoData
                else -> SpeedReply.Failed(lines.joinToString(" ").ifEmpty { "empty reply" })
            }
        }

        private fun String.oneLine() =
            lines().map { it.trim() }.filter { it.isNotEmpty() }.joinToString(" | ").ifEmpty { "(empty)" }
    }

    private var failuresInARow = 0

    /** Resets and sets up the adapter and wakes the car's bus. Returns the adapter's banner, such as "ELM327 v1.5". */
    fun open(): String {
        val banner = command("ATZ", RESET_TIMEOUT_MS).lines().map { it.trim() }
            .lastOrNull { it.isNotEmpty() && it != "ATZ" } ?: "unknown adapter"
        log("adapter: $banner")
        for (c in SETUP) {
            val reply = command(c, COMMAND_TIMEOUT_MS)
            // Cheap copies skip some settings; the speed parser copes with what they leave on.
            if ("OK" !in reply.uppercase()) log("adapter: $c → ${reply.oneLine()}")
        }
        val first = command("010D", SEARCH_TIMEOUT_MS)
        log("adapter: first speed reply ${first.oneLine()}")
        if (parseSpeed(first) is SpeedReply.Failed) throw ObdException("the car does not answer: ${first.oneLine()}")
        log("adapter: protocol ${command("ATDPN", COMMAND_TIMEOUT_MS).oneLine()}")
        return banner
    }

    /** One speed request: km/h, or null when there is no reading this time. Throws [ObdException] after [MAX_FAILURES_IN_A_ROW] failures. */
    fun speedKmh(): Int? = when (val reply = parseSpeed(command("010D", COMMAND_TIMEOUT_MS))) {
        is SpeedReply.Speed -> {
            failuresInARow = 0
            reply.kmh
        }
        SpeedReply.NoData -> {
            failuresInARow = 0
            null
        }
        is SpeedReply.Failed -> {
            failuresInARow++
            if (failuresInARow >= MAX_FAILURES_IN_A_ROW) {
                throw ObdException("speed request failed $failuresInARow times: ${reply.text}")
            }
            null
        }
    }

    private fun command(c: String, timeoutMs: Long): String {
        link.send(c)
        return link.readReply(timeoutMs)
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests '*Elm327Test*'`
Expected: PASS (11 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/io/github/lrosa2/speedbeep/engine/Elm327.kt app/src/test/java/io/github/lrosa2/speedbeep/engine/Elm327Test.kt
git commit -m "ELM327 protocol: setup, speed request, tolerant reply parsing"
```

---

### Task 2: Car speed in the watch session

**Files:**
- Modify: `engine/WatchSession.kt`, `engine/StatusText.kt`
- Test: `app/src/test/java/io/github/lrosa2/speedbeep/engine/CarSpeedTest.kt` (new), `StatusTextTest.kt` (one test added)

**Interfaces:**
- Produces:
  - `enum class SpeedSource { GPS, CAR }`
  - `WatchStatus.speedSource: SpeedSource = GPS`, added as the last parameter
  - `WatchSession.onCarSpeed(kmh: Int, nowMs: Long): WatchUpdate`
  - `WatchSession.CAR_FRESH_MS = 3_000L`

- [ ] **Step 1: Write the failing tests**

`CarSpeedTest.kt`:

```kotlin
package io.github.lrosa2.speedbeep.engine

import io.github.lrosa2.speedbeep.core.rules.Fix
import io.github.lrosa2.speedbeep.core.rules.Phase
import io.github.lrosa2.speedbeep.core.rules.Sound
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CarSpeedTest {
    private val simple = Settings(mode = Mode.SIMPLE)

    private fun gps(t: Long, kmh: Double) = Fix(t, 47.0, 8.0, (kmh / 3.6).toFloat(), 0.5f, 5f, 90f)

    @Test
    fun `in simple mode the car's speed drives the beep, and the threshold itself counts`() {
        val s = WatchSession(simple, 0)
        val sounds = listOf(45, 53, 53).mapIndexed { i, kmh -> s.onCarSpeed(kmh, i * 1_000L).sound }
        assertEquals(listOf(Sound.NONE, Sound.NONE, Sound.MAIN), sounds)
        assertEquals(53.0, s.status.speedKmh)
        assertEquals(SpeedSource.CAR, s.status.speedSource)
    }

    @Test
    fun `GPS is ignored while the car's speed is fresh`() {
        val s = WatchSession(simple, 0)
        s.onCarSpeed(40, 0)
        val sounds = listOf(500L, 1_500L, 2_500L).map { t -> s.onFix(gps(t, 80.0), t).sound }
        assertEquals(listOf(Sound.NONE, Sound.NONE, Sound.NONE), sounds)
        assertEquals(40.0, s.status.speedKmh)
        assertEquals(Phase.ARMED, s.status.phase)
    }

    @Test
    fun `when the adapter falls silent GPS takes over within 4 s, still OVER and without a second main beep`() {
        val s = WatchSession(simple, 0)
        val carSounds = listOf(60, 60).mapIndexed { i, kmh -> s.onCarSpeed(kmh, i * 1_000L).sound }
        assertEquals(listOf(Sound.NONE, Sound.MAIN), carSounds)
        // The adapter stops after t = 1 s; the service ticks and GPS keeps sending once a second.
        val updates = (2..6).flatMap { sec ->
            val t = sec * 1_000L
            listOf(s.onTick(t), s.onFix(gps(t, 60.0), t))
        }
        assertTrue(updates.none { it.sound == Sound.MAIN })
        assertTrue(updates.none { it.status.phase == Phase.NO_GPS })
        assertEquals(Phase.OVER, s.status.phase)
        assertEquals(SpeedSource.GPS, s.status.speedSource)
    }

    @Test
    fun `the car's speed takes over again as soon as the adapter answers`() {
        val s = WatchSession(simple, 0)
        s.onFix(gps(0, 30.0), 0)
        assertEquals(SpeedSource.GPS, s.status.speedSource)
        s.onCarSpeed(31, 500)
        assertEquals(SpeedSource.CAR, s.status.speedSource)
        assertEquals(31.0, s.status.speedKmh)
    }

    @Test
    fun `smart mode ignores the car's speed`() {
        val s = WatchSession(Settings(), 0)
        assertEquals(Sound.NONE, s.onCarSpeed(80, 0).sound)
        assertNull(s.status.speedKmh)
        s.onFix(gps(500, 30.0), 500)
        assertEquals(SpeedSource.GPS, s.status.speedSource)
    }
}
```

Add to `StatusTextTest.kt`:

```kotlin
    @Test
    fun `simple mode shows when the speed comes from the car`() =
        assertEquals("52 km/h · simple mode · car", line(status(Phase.ARMED, simple = true).copy(speedSource = SpeedSource.CAR)))
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests '*CarSpeedTest*' --tests '*StatusTextTest*'`
Expected: FAIL, compilation error "Unresolved reference 'onCarSpeed'" / "'SpeedSource'".

- [ ] **Step 3: Implement**

In `engine/WatchSession.kt`, add the imports `io.github.lrosa2.speedbeep.core.road.RoadMatch` and `io.github.lrosa2.speedbeep.core.rules.Reading`. Then:

```kotlin
/** Where the speed in use comes from (spec §4.5). */
enum class SpeedSource { GPS, CAR }
```

Add `val speedSource: SpeedSource = SpeedSource.GPS,` as the last `WatchStatus` parameter.

In `WatchSession`, replace `onFix` with the block below and add the rest:

```kotlin
    companion object {
        /** The car's speed counts while its last reading is at most this old (spec §4.5). */
        const val CAR_FRESH_MS = 3_000L
    }

    private var lastCarMs: Long? = null
    private var source = SpeedSource.GPS

    /** A GPS fix. In simple mode it is ignored while the car's speed is fresh (spec §4.5). */
    fun onFix(fix: Fix, nowMs: Long): WatchUpdate {
        if (carIsFresh(nowMs)) return WatchUpdate(status, Sound.NONE)
        source = SpeedSource.GPS
        return apply(rules.onReading(FixEvaluator.evaluate(fix, nowMs, settings.simpleMode, roads)))
    }

    /** One reading from the car's OBD adapter, in whole km/h. Smart mode ignores it (spec §4.5). */
    fun onCarSpeed(kmh: Int, nowMs: Long): WatchUpdate {
        if (!settings.simpleMode) return WatchUpdate(status, Sound.NONE)
        lastCarMs = nowMs
        source = SpeedSource.CAR
        // Straight to the rules in whole km/h, so 53 stays 53; simple mode needs no position.
        return apply(rules.onReading(Reading(nowMs, kmh.toDouble(), RoadMatch.UNKNOWN)))
    }

    private fun carIsFresh(nowMs: Long) = settings.simpleMode && lastCarMs?.let { nowMs - it <= CAR_FRESH_MS } == true
```

In `apply`, pass `source` as the last `WatchStatus` argument:

```kotlin
    private fun apply(out: RulesOutput): WatchUpdate {
        status = WatchStatus(
            out.phase, out.speedKmh, out.roadContext, out.noRoadData, out.pausedUntilMs,
            settings.thresholdKmh, settings.simpleMode, settings.pauseMin, source,
        )
        return WatchUpdate(status, out.sound)
    }
```

In `engine/StatusText.kt`, replace the simple-mode line:

```kotlin
            s.simpleMode -> if (s.speedSource == SpeedSource.CAR) "$speed · simple mode · car" else "$speed · simple mode"
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :app:testDebugUnitTest`
Expected: PASS (all app tests, including the 5 new CarSpeedTest tests and the 11 StatusTextTest tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/io/github/lrosa2/speedbeep/engine/WatchSession.kt app/src/main/java/io/github/lrosa2/speedbeep/engine/StatusText.kt app/src/test/java/io/github/lrosa2/speedbeep/engine/CarSpeedTest.kt app/src/test/java/io/github/lrosa2/speedbeep/engine/StatusTextTest.kt
git commit -m "Watch session: the car's speed first in simple mode, GPS as fallback"
```

---

### Task 3: Settings, drive recording and checklist

**Files:**
- Modify: `engine/Settings.kt`, `engine/DriveCsv.kt`, `engine/DriveRecorder.kt`, `engine/Checklist.kt`
- Test: `SettingsCodecTest.kt`, `DriveCsvTest.kt`, `DriveRecorderTest.kt`

**Interfaces:**
- Produces:
  - `Settings.obdAddress: String?` and `Settings.obdName: String?`;
  - `DriveCsv.HEADER = "# SpeedBeep drive v2"`;
  - `DriveCsv.line(fix, startMs, obdKmh: Int? = null)`;
  - `DriveRecorder.record(fix, wallNowMs, obdKmh: Int? = null)`.

- [ ] **Step 1: Write the failing tests**

Add to `SettingsCodecTest.kt`:

```kotlin
    @Test
    fun `the OBD adapter survives a save and load, and settings saved by 0_1_0 have none`() {
        val s = Settings(mode = Mode.SIMPLE, obdAddress = "00:1D:A5:68:98:8B", obdName = "OBDII")
        assertEquals(s, SettingsCodec.decode(SettingsCodec.encode(s)))
        val saved010 = SettingsCodec.decode(mapOf("thresholdKmh" to 55, "mode" to "SIMPLE"))
        assertNull(saved010.obdAddress)
        assertEquals(55, saved010.thresholdKmh)
    }
```

(Add `import org.junit.jupiter.api.Assertions.assertNull` if it is missing.)

In `DriveCsvTest.kt`, change the two existing tests and add two:

```kotlin
    @Test
    fun `missing values are empty fields`() {
        assertEquals("1000,47.0000000,8.0000000,,,,,", DriveCsv.line(bare, 1_000))
    }

    @Test
    fun `a broken line is reported`() {
        val e = assertThrows<IllegalArgumentException> { DriveCsv.parse("0,47.0,8.0") }
        assertTrue(e.message!!.contains("7 or 8 fields"))
    }

    @Test
    fun `the car's speed is the last field`() {
        val line = DriveCsv.line(full, 1_000, obdKmh = 52)
        assertTrue(line.endsWith(",52"), line)
        assertEquals(listOf(full.copy(elapsedMs = 0)), DriveCsv.parse(line))
    }

    @Test
    fun `version 1 drives still read`() {
        val v1 = "# SpeedBeep drive v1\nt_ms,lat,lon,speed_mps,speed_acc_mps,h_acc_m,bearing_deg\n0,47.3563251,8.5558482,16.7,0.5,4.0,147.0"
        assertEquals(listOf(full.copy(elapsedMs = 0)), DriveCsv.parse(v1))
    }
```

Add to `DriveRecorderTest.kt`:

```kotlin
    @Test
    fun `the car's speed is recorded next to the GPS fix`() {
        val recorder = DriveRecorder(dir)
        recorder.record(Fix(0, 47.0, 8.0, 16.7f, 0.5f, 4f, 90f), 0, obdKmh = 61)
        recorder.close()
        val lines = recorder.file!!.readLines()
        assertEquals(DriveCsv.HEADER, lines[0])
        assertTrue(lines[2].endsWith(",61"), lines[2])
    }
```

(Add `import org.junit.jupiter.api.Assertions.assertTrue`.)

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests '*SettingsCodecTest*' --tests '*DriveCsvTest*' --tests '*DriveRecorderTest*'`
Expected: FAIL, compilation error "No parameter with name 'obdAddress'" / "'obdKmh'".

- [ ] **Step 3: Implement**

`engine/Settings.kt`, new last fields of `Settings`:

```kotlin
    /** The paired ELM327 adapter (spec §4.5), upper case; null when none is chosen. */
    val obdAddress: String? = null,
    val obdName: String? = null,
```

In `SettingsCodec.encode`, after `carName`:

```kotlin
        s.obdAddress?.let { put("obdAddress", it) }
        s.obdName?.let { put("obdName", it) }
```

In `SettingsCodec.decode`, after `carName = str("carName"),`:

```kotlin
            obdAddress = str("obdAddress"),
            obdName = str("obdName"),
```

`engine/DriveCsv.kt`:

```kotlin
/**
 * One GPS fix per line; times relative to the first fix. Empty field = not reported.
 * v2 adds the car's speed from the OBD adapter (spec §4.5); v1 files still read.
 */
object DriveCsv {
    const val HEADER = "# SpeedBeep drive v2"
    const val COLUMNS = "t_ms,lat,lon,speed_mps,speed_acc_mps,h_acc_m,bearing_deg,obd_kmh"

    /** [obdKmh]: the car's speed when a fresh adapter reading exists, else null. */
    fun line(fix: Fix, startMs: Long, obdKmh: Int? = null): String = listOf(
        (fix.elapsedMs - startMs).toString(),
        "%.7f".format(Locale.ROOT, fix.lat),
        "%.7f".format(Locale.ROOT, fix.lon),
        fix.speedMps.field(),
        fix.speedAccuracyMps.field(),
        fix.horizontalAccuracyM.field(),
        fix.bearingDeg.field(),
        obdKmh?.toString() ?: "",
    ).joinToString(",")
```

and in `parseLine`:

```kotlin
        require(f.size == 7 || f.size == 8) { "record $record: expected 7 or 8 fields, got ${f.size}" }
```

`engine/DriveRecorder.kt`:

```kotlin
    /** [obdKmh]: the car's speed next to this GPS fix, when the adapter has a fresh reading (spec §4.5). */
    fun record(fix: Fix, wallNowMs: Long, obdKmh: Int? = null) {
        if (error != null) return
        try {
            val w = writer ?: open(fix, wallNowMs)
            w.write(DriveCsv.line(fix, startMs, obdKmh))
```

(The rest of `record` is unchanged.)

`engine/Checklist.kt`:

```kotlin
    NEARBY_DEVICES("Allow nearby devices, so SpeedBeep can reach the car and the OBD adapter", "Allow"),
```

and the `SetupState.bluetoothNeeded` comment becomes `/** The §5.1 notification fallback or an OBD adapter is chosen; both need BLUETOOTH_CONNECT. */`.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :app:testDebugUnitTest`
Expected: PASS (all app tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/io/github/lrosa2/speedbeep/engine app/src/test/java/io/github/lrosa2/speedbeep/engine
git commit -m "OBD adapter setting, drive file v2 with the car's speed, checklist wording"
```

---

### Task 4: Adapter connection, settings section and service wiring

**Files:**
- Create: `obd/RfcommLink.kt`, `obd/ObdSource.kt`, `obd/ObdTest.kt`, `ui/ObdSettings.kt`
- Create: `app/src/debug/java/io/github/lrosa2/speedbeep/obd/ObdLinks.kt` and `SimulatedObdLink.kt`
- Create: `app/src/release/java/io/github/lrosa2/speedbeep/obd/ObdLinks.kt`
- Modify: `watch/WatchService.kt`, `ui/CarSettings.kt`, `ui/AppScreens.kt`, `ui/SetupProbe.kt`, `app/build.gradle.kts`

**Interfaces:**
- Consumes:
  - Task 1: `ObdLink`, `ObdException`, `Elm327`.
  - Task 2: `WatchSession.onCarSpeed`, `WatchSession.CAR_FRESH_MS`, `SpeedSource`.
  - Task 3: `Settings.obdAddress`/`obdName`, `DriveRecorder.record(..., obdKmh)`.
- Produces:
  - `ObdLinks.open(context, address): ObdLink`;
  - `ObdLinks.extraChoices: List<Pair<String, String>>` (name to address);
  - `ObdSource(context, address, events, onSpeed)`, with `start()` and `stop()`;
  - `ObdTest.run(context, address, events, onDone)`.

This task is Android glue. The pure logic it uses is tested in Tasks 1–3. The check is the build plus the emulator run with the simulated adapter (Step 6).

- [ ] **Step 1: Bluetooth link**

`obd/RfcommLink.kt`:

```kotlin
package io.github.lrosa2.speedbeep.obd

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.os.SystemClock
import io.github.lrosa2.speedbeep.engine.ObdException
import io.github.lrosa2.speedbeep.engine.ObdLink
import java.io.IOException
import java.util.UUID

/** Classic Bluetooth serial (SPP) link to an ELM327 adapter paired in Android's settings (spec §4.5). */
class RfcommLink private constructor(private val socket: BluetoothSocket) : ObdLink {
    companion object {
        private val SPP: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

        /** Blocks while connecting (up to about 12 s). Throws [ObdException]. */
        @SuppressLint("MissingPermission") // checked first: without "Nearby devices" Bluetooth calls throw
        fun open(context: Context, address: String): RfcommLink {
            if (context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                throw ObdException("\"Nearby devices\" is not allowed")
            }
            val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
                ?: throw ObdException("this phone has no Bluetooth")
            if (!adapter.isEnabled) throw ObdException("Bluetooth is off")
            val device = adapter.getRemoteDevice(address)
            return try {
                RfcommLink(connect(device.createRfcommSocketToServiceRecord(SPP)))
            } catch (first: IOException) {
                // Some cheap copies only accept an unauthenticated connection.
                try {
                    RfcommLink(connect(device.createInsecureRfcommSocketToServiceRecord(SPP)))
                } catch (second: IOException) {
                    throw ObdException("cannot connect to the adapter (${second.message ?: first.message})")
                }
            }
        }

        private fun connect(socket: BluetoothSocket): BluetoothSocket = try {
            socket.connect()
            socket
        } catch (e: IOException) {
            runCatching { socket.close() }
            throw e
        }
    }

    private val input = socket.inputStream
    private val output = socket.outputStream

    override fun send(command: String) {
        try {
            while (input.available() > 0) input.read() // drop what is left of an earlier reply
            output.write("$command\r".toByteArray(Charsets.US_ASCII))
            output.flush()
        } catch (e: IOException) {
            throw ObdException("connection lost (${e.message})")
        }
    }

    override fun readReply(timeoutMs: Long): String {
        val reply = StringBuilder()
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        try {
            while (true) {
                while (input.available() > 0) {
                    val b = input.read()
                    if (b < 0) throw ObdException("connection closed")
                    if (b == '>'.code) return reply.toString()
                    if (b != 0) reply.append(b.toChar())
                }
                if (SystemClock.elapsedRealtime() > deadline) {
                    throw ObdException("no reply within $timeoutMs ms (got \"${reply.toString().trim()}\")")
                }
                Thread.sleep(10)
            }
        } catch (e: IOException) {
            throw ObdException("connection lost (${e.message})")
        }
    }

    override fun close() {
        runCatching { socket.close() }
    }
}
```

`app/src/release/java/io/github/lrosa2/speedbeep/obd/ObdLinks.kt`:

```kotlin
package io.github.lrosa2.speedbeep.obd

import android.content.Context
import io.github.lrosa2.speedbeep.engine.ObdLink

/** Release builds talk to real adapters only. */
object ObdLinks {
    val extraChoices: List<Pair<String, String>> = emptyList()

    fun open(context: Context, address: String): ObdLink = RfcommLink.open(context, address)
}
```

`app/src/debug/java/io/github/lrosa2/speedbeep/obd/ObdLinks.kt`:

```kotlin
package io.github.lrosa2.speedbeep.obd

import android.content.Context
import io.github.lrosa2.speedbeep.engine.ObdLink

/** Debug builds also offer a simulated adapter, so the whole path runs on the emulator. */
object ObdLinks {
    const val SIMULATED = "SIMULATED"

    val extraChoices: List<Pair<String, String>> = listOf("Simulated adapter (debug)" to SIMULATED)

    fun open(context: Context, address: String): ObdLink =
        if (address == SIMULATED) SimulatedObdLink() else RfcommLink.open(context, address)
}
```

`app/src/debug/java/io/github/lrosa2/speedbeep/obd/SimulatedObdLink.kt`:

```kotlin
package io.github.lrosa2.speedbeep.obd

import android.os.SystemClock
import io.github.lrosa2.speedbeep.engine.ObdLink

/**
 * A pretend ELM327 for emulator checks. The speed follows a 90 s loop: 0 → 56 km/h over
 * 15 s, 60 until 45 s, down by 2 km/h a second to 30, then 30 until the loop restarts.
 */
class SimulatedObdLink(private val clock: () -> Long = SystemClock::elapsedRealtime) : ObdLink {
    private val startMs = clock()
    private var reply = ""

    override fun send(command: String) {
        reply = when (command) {
            "ATZ" -> "\r\rELM327 v1.5 (simulated)\r\r"
            "010D" -> "41 0D %02X\r\r".format(speedKmh((clock() - startMs) / 1_000 % 90))
            "ATDPN" -> "A6\r\r"
            else -> "OK\r\r"
        }
    }

    override fun readReply(timeoutMs: Long): String {
        Thread.sleep(50)
        return reply
    }

    override fun close() = Unit

    private fun speedKmh(s: Long): Int = when {
        s < 15 -> (s * 4).toInt()
        s < 45 -> 60
        s < 60 -> (60 - (s - 45) * 2).toInt()
        else -> 30
    }
}
```

- [ ] **Step 2: Reading thread and one-shot test**

`obd/ObdSource.kt`:

```kotlin
package io.github.lrosa2.speedbeep.obd

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import io.github.lrosa2.speedbeep.engine.Elm327
import io.github.lrosa2.speedbeep.engine.EventLog
import io.github.lrosa2.speedbeep.engine.ObdLink

/**
 * Reads the car's speed from the OBD adapter on its own thread, once a second (spec §4.5), and
 * hands each reading to [onSpeed] on the main thread. Reconnects every 5 s after a failure.
 */
class ObdSource(
    context: Context,
    private val address: String,
    private val events: EventLog,
    private val onSpeed: (Int) -> Unit,
) {
    companion object {
        const val POLL_MS = 1_000L
        const val RETRY_MS = 5_000L
    }

    private val context = context.applicationContext
    private val main = Handler(Looper.getMainLooper())

    @Volatile private var running = false

    @Volatile private var link: ObdLink? = null
    private var thread: Thread? = null

    // OBD thread only.
    private val setupLines = HashSet<String>()
    private var lastProblem: String? = null

    fun start() {
        running = true
        thread = Thread(::loop, "SpeedBeep-OBD").apply {
            isDaemon = true
            start()
        }
    }

    /** Main thread. No reading reaches [onSpeed] after this returns. */
    fun stop() {
        running = false
        link?.close() // unblocks a read
        thread?.interrupt()
        thread = null
        main.removeCallbacksAndMessages(null)
    }

    private fun loop() {
        while (running) {
            try {
                connectAndRead()
            } catch (e: InterruptedException) {
                return
            } catch (e: Exception) { // ObdException, or SecurityException / IllegalArgumentException from Bluetooth
                if (running) problem(e.message ?: e.javaClass.simpleName)
            } finally {
                link?.close()
                link = null
            }
            try {
                Thread.sleep(RETRY_MS)
            } catch (e: InterruptedException) {
                return
            }
        }
    }

    private fun connectAndRead() {
        val l = ObdLinks.open(context, address)
        link = l
        if (!running) return
        // Setup replies are logged once per watch, not on every reconnect.
        val elm = Elm327(l) { if (setupLines.add(it)) post(it) }
        val banner = elm.open()
        post("adapter connected ($banner)")
        lastProblem = null
        while (running) {
            val started = SystemClock.elapsedRealtime()
            elm.speedKmh()?.let { kmh -> main.post { if (running) onSpeed(kmh) } }
            Thread.sleep((POLL_MS - (SystemClock.elapsedRealtime() - started)).coerceAtLeast(0))
        }
    }

    /** Logged once per kind of problem, not every 5 s while the adapter stays out of reach. */
    private fun problem(text: String) {
        if (text != lastProblem) post("adapter: $text; retrying every ${RETRY_MS / 1_000} s")
        lastProblem = text
    }

    private fun post(text: String) {
        main.post { events.log(text) }
    }
}
```

`obd/ObdTest.kt`:

```kotlin
package io.github.lrosa2.speedbeep.obd

import android.content.Context
import android.os.Handler
import android.os.Looper
import io.github.lrosa2.speedbeep.engine.Elm327
import io.github.lrosa2.speedbeep.engine.EventLog

/** "Test adapter" in settings (spec §7.3): connect, set up, read one speed. [onDone] runs on the main thread. */
object ObdTest {
    fun run(context: Context, address: String, events: EventLog, onDone: (String) -> Unit) {
        val app = context.applicationContext
        val main = Handler(Looper.getMainLooper())
        fun log(text: String) {
            main.post { events.log(text) }
        }
        Thread({
            val result = try {
                val link = ObdLinks.open(app, address)
                try {
                    val elm = Elm327(link, ::log)
                    val banner = elm.open()
                    val speed = elm.speedKmh()
                    "Connected to $banner. " +
                        (speed?.let { "Car speed: $it km/h." } ?: "The car sent no speed: is the ignition on?")
                } finally {
                    link.close()
                }
            } catch (e: Exception) {
                "Adapter test failed: ${e.message ?: e.javaClass.simpleName}"
            }
            log("adapter test: $result")
            main.post { onDone(result) }
        }, "SpeedBeep-OBD-test").apply { isDaemon = true }.start()
    }
}
```

- [ ] **Step 3: Settings section**

In `ui/CarSettings.kt`, replace the `PairedDevicesDialog` header and its device list and title:

```kotlin
/** Shown only once BLUETOOTH_CONNECT is granted. [extra] adds (name, address) choices that are not paired devices. */
@SuppressLint("MissingPermission")
@Composable
internal fun PairedDevicesDialog(
    context: Context,
    onPick: (String, String) -> Unit,
    onDismiss: () -> Unit,
    title: String = "Paired devices",
    extra: List<Pair<String, String>> = emptyList(),
) {
    val devices = remember {
        context.getSystemService(BluetoothManager::class.java).adapter?.bondedDevices.orEmpty()
            .map { (it.name ?: it.address) to it.address.uppercase() }
            .sortedBy { it.first } + extra
    }
```

and `title = { Text("Paired devices") }` becomes `title = { Text(title) }`.

`ui/ObdSettings.kt`:

```kotlin
package io.github.lrosa2.speedbeep.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
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
import io.github.lrosa2.speedbeep.engine.Settings
import io.github.lrosa2.speedbeep.obd.ObdLinks
import io.github.lrosa2.speedbeep.obd.ObdTest
import io.github.lrosa2.speedbeep.watch.WatchBus

/** "OBD adapter" (spec §4.5, §7.3): choose, test, remove. */
@Composable
fun ObdSettings(s: Settings, change: ((Settings) -> Settings) -> Unit) {
    val context = LocalContext.current
    var picking by remember { mutableStateOf(false) }
    var testing by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<String?>(null) }
    val nearby = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) picking = true else result = "Without \"Nearby devices\" SpeedBeep cannot reach the adapter."
    }

    Section("OBD adapter")
    Text(
        s.obdName?.let { "Adapter: $it. Simple mode uses the car's speed from it." }
            ?: "No adapter. Pair an ELM327 in Android's Bluetooth settings first (PIN 1234 or 0000), then choose it here.",
    )
    val address = s.obdAddress
    if (address == null) {
        OutlinedButton(
            onClick = {
                result = null
                if (SetupProbe.granted(context, Manifest.permission.BLUETOOTH_CONNECT)) {
                    picking = true
                } else {
                    nearby.launch(Manifest.permission.BLUETOOTH_CONNECT)
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Choose adapter") }
    } else {
        BigButton(if (testing) "Testing the adapter…" else "Test adapter") {
            when {
                testing -> Unit
                WatchBus.running -> result = "Stop watching first: the adapter takes one connection at a time."
                else -> {
                    testing = true
                    result = null
                    ObdTest.run(context, address, Graph.events) {
                        testing = false
                        result = it
                    }
                }
            }
        }
        OutlinedButton(
            onClick = {
                Graph.events.log("OBD adapter removed")
                result = null
                change { it.copy(obdAddress = null, obdName = null) }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Remove adapter") }
    }
    result?.let { Notice(it) }
    if (picking) {
        PairedDevicesDialog(
            context,
            onPick = { name, picked ->
                picking = false
                Graph.events.log("OBD adapter chosen: $name $picked")
                change { it.copy(obdAddress = picked, obdName = name) }
            },
            onDismiss = { picking = false },
            title = "Choose the OBD adapter",
            extra = ObdLinks.extraChoices,
        )
    }
}
```

In `ui/AppScreens.kt`: `car = { CarSettings(s, change) }` becomes `car = { CarSettings(s, change); ObdSettings(s, change) }`.

In `ui/SetupProbe.kt`: `bluetoothNeeded = s.carStart == CarStart.NOTIFICATION,` becomes `bluetoothNeeded = s.carStart == CarStart.NOTIFICATION || s.obdAddress != null,`.

In `app/build.gradle.kts`: `versionCode = 2`, `versionName = "0.2.0"`.

- [ ] **Step 4: Service wiring**

In `watch/WatchService.kt`:
- Add the imports `io.github.lrosa2.speedbeep.engine.SpeedSource` and `io.github.lrosa2.speedbeep.obd.ObdSource`.
- Add these fields after `recordingFailed`:

```kotlin
    private var obd: ObdSource? = null
    private var obdAddress: String? = null
    private var lastCarKmh: Int? = null
    private var lastCarMs = 0L
    private var lastSource: SpeedSource? = null
```

In `startSession`, replace the `events.log("watching started …")` line with:

```kotlin
        startObd(settings)
        events.log("watching started ($reason) with ${src.name}${settings.obdName?.let { " and OBD adapter $it" } ?: ""}")
```

In `applySettings`, after `settings = new`:

```kotlin
        if (session != null) startObd(new)
```

In `onFix`, the recorder call becomes `r.record(fix, System.currentTimeMillis(), freshCarKmh())`.

New methods:

```kotlin
    /** Connects to the chosen OBD adapter, or drops it when the choice changed (spec §4.5). */
    private fun startObd(s: Settings) {
        if (s.obdAddress == obdAddress) return
        obd?.stop()
        obd = null
        obdAddress = s.obdAddress
        lastCarKmh = null
        val address = s.obdAddress ?: return
        obd = ObdSource(this, address, events, ::onCarSpeed).also { it.start() }
    }

    private fun onCarSpeed(kmh: Int) {
        val s = session ?: return
        lastCarKmh = kmh
        lastCarMs = now()
        handle(s.onCarSpeed(kmh, now()))
    }

    /** The car's speed for the drive recording, when the adapter's last reading is fresh. */
    private fun freshCarKmh() = lastCarKmh?.takeIf { now() - lastCarMs <= WatchSession.CAR_FRESH_MS }
```

In `handle`, after the phase-change logging block:

```kotlin
        if (status.speedSource != lastSource) {
            if (lastSource != null) {
                events.log(if (status.speedSource == SpeedSource.CAR) "speed from the car" else "speed from GPS (adapter silent)")
            }
            lastSource = status.speedSource
        }
```

In `end`, after `source = null`:

```kotlin
        obd?.stop()
        obd = null
        obdAddress = null
        lastCarKmh = null
        lastSource = null
```

- [ ] **Step 5: Build and run all tests**

Run: `./gradlew :app:testDebugUnitTest :app:assembleDebug :app:assembleRelease`
Expected: BUILD SUCCESSFUL; all app tests pass.

- [ ] **Step 6: Emulator check with the simulated adapter**

1. Install the debug APK.
2. In Settings, set Mode: Simple, then OBD adapter → Choose adapter, allow Nearby devices, and pick "Simulated adapter (debug)".
3. Tap **Test adapter**.
   Expected: "Connected to ELM327 v1.5 (simulated). Car speed: N km/h."
4. Start watching.
   Expected: the notification reads "N km/h · simple mode · car", rising.
5. Expected in the event log:
   - "adapter connected (ELM327 v1.5 (simulated))";
   - "speed from the car";
   - "main beep at 56 km/h" or "main beep at 60 km/h", about 15 s after start.
6. Remove the adapter while watching.
   Expected: within about 5 s the status leaves "· car". With no GPS on the emulator it shows No GPS. Watching keeps running.
7. Stop.
   Expected: "watching stopped", and no adapter lines after it.

- [ ] **Step 7: Commit**

```bash
git add app docs
git commit -m "OBD adapter over classic Bluetooth: reading thread, settings, simulated adapter, service wiring"
```
