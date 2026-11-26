# SpeedBeep — design

Date: 2026-11-25 · Status: approved 2026-11-25. Plan 1 is
`docs/superpowers/plans/2026-11-25-speedbeep-plan1-core.md`.

## 1. Purpose

The owner keeps getting speeding fines in 50 km/h zones because of small moments of
inattention. Google Maps only warns visually. SpeedBeep is a map-less Android app that
**beeps** when the car goes over a set speed (default 53 km/h by GPS). In normal mode it
stays silent on motorways and other roads posted above 50, using OpenStreetMap road data
shipped inside the app. A simple mode ignores road data and beeps above the threshold
everywhere.

Success means:

- It beeps within about 2 s of crossing the threshold on a town road.
- It stays silent on motorways, Autostrassen and 60/80 roads.
- Over a whole drive it needs no attention: it starts and stops with the car, and pausing
  is one tap from the lock screen.

### Existing apps (checked 2026-11-24)

| App | Why not |
|---|---|
| Velociraptor (`pluscubed/velociraptor`, GPL-3.0) | Last change Dec 2020. Needs Firebase and Fabric, and Fabric is shut down. Reviving it costs more than a small new app. |
| Speed/Limit (Itomic) | Closed source, Play Store only, custom thresholds are paid, no developer activity since Apr 2026. |
| Dash (`its-ash/dash`) | Fixed-limit beep only, no highway exclusion, no Bluetooth start, no license. |
| OsmAnd | A full map app that warns from the posted limit. Not shown to beep without an active route. |
| Roam, Status Bar Speedometer (F-Droid) | No alarm. |

## 2. Decisions log

| # | Decision | Source |
|---|---|---|
| D1 | Normal ("smart") mode: beep above one fixed threshold (default 53 km/h), except on fast roads (motorway, trunk, or posted above 50). The app does **not** warn against each road's posted limit. | user choice |
| D2 | Road data comes from an offline file built on the PC from an OpenStreetMap extract and shipped in the APK. Driving needs no network, and no location is sent anywhere. | user choice |
| D3 | Coverage: Switzerland only. | user choice |
| D4 | Starts and stops automatically with the linked car's Bluetooth, and can also be started and stopped by hand. | user choice |
| D5 | Beeping: a main beep of settable length on crossing, then a short reminder every N s while still over. It stops as soon as the speed is back under. | user choice |
| D6 | Audio output is a setting: phone speaker as an alarm (default) or car speakers as a navigation prompt. | user choice |
| D7 | Pause kinds: Quiet this stretch, Pause N min, and Stop. Stop merges "pause until I resume" and "stop for this drive". | user choice, merge confirmed |
| D8 | Target phone runs Android 15/16, so minSdk is 34. | user answer |
| D9 | Simple mode: road data ignored, beep above the threshold everywhere. It can be chosen by hand and is used automatically wherever there is no road data. | user request 2026-11-25 |
| D10 | Fail toward beeping. An unknown or unmatched road counts as town. The app is slow to go silent on a fast road and quick to start beeping again. | design (a wrong silence costs a fine, a wrong beep only annoys) |
| D11 | Speed is the GPS-reported (Doppler) speed, not distance divided by time. | design |
| D12 | Auto-start goes through CompanionDeviceManager. A plain ACL_CONNECTED receiver cannot start a location foreground service from the background on Android 12+. | Android docs, see §8 |
| D13 | Fast-road hold: a reading at or above T that itself matches a fast road does not trigger the beep before FAST is confirmed. Speed validity and position accuracy are judged separately. | design review (keeps town exits, on-ramps and tunnel exits silent without weakening D10) |
| D14 | Reserved for the Bluetooth auto-start spike result (plan 1, Task 1). | pending |
| D15 | Main roads with no speed information stay TOWN (they beep), also outside built-up areas. Using land-use outlines as town limits was measured and rejected: it would also silence about a quarter of the road length really posted 50 or less. See §6.2. | user choice 2026-11-25, after measurement |
| D16 | The §5.1 notification fallback is built in from the start, chosen in settings ("When the car connects: Start watching / Ask with a notification"); the default is the companion route. One car trip can then test both, and D14 decides the default. | owner's request 2026-11-25 to finish the app before the car test |
| D17 | Simple mode can take the speed from an ELM327 OBD adapter over classic Bluetooth. The car's speed comes first and GPS is the fallback. Smart mode stays on GPS. See §4.5. | owner's request 2026-11-26 |

## 3. Components

New project `E:\AndroidProj\SpeedBeep`.

- **Package:** `io.github.lrosa2.speedbeep`.
- **Toolchain:** Kotlin and Compose, the same as the sibling projects. Copy
  `gradle/libs.versions.toml` from CarCare: AGP 9.4.1, Kotlin 2.3.21, Gradle 9.6.0, JDK 21.
- **SDK levels:** compileSdk 37, targetSdk 36, minSdk 34.

| Unit | What it does | Depends on |
|---|---|---|
| `SpeedRules` (pure Kotlin, `:core` JVM module) | State machine. Takes a speed reading, the road class, user actions and the clock, and returns beep commands and a display state. Holds all the decision logic. | nothing |
| `RoadFile` + `RoadMatcher` (`:core`) | Read the road file and answer "fast / town / unknown / not covered" for a position and heading. | nothing (works on a `ByteBuffer`) |
| `LocationSource` (interface, app) | Supplies a stream of fixes. `GpsSource` uses `LocationManager` GPS at 1 Hz with no Play Services. `ReplaySource` (debug builds only) plays back a recorded drive. | Android |
| `WatchService` (app) | Foreground service of type `location`. Wires the location source → matcher → rules → beeper, owns the notification, and records drives when that is turned on. | all of the above |
| `Beeper` (app) | Plays the chosen sound with the chosen audio attributes, cuts it to length, and handles audio focus. | Android audio |
| `CarLink` (app) | Links the car through CompanionDeviceManager and observes its presence. A `CompanionDeviceService` starts and stops `WatchService`. | Android CDM |
| `StartStopTile` (app) | Quick-settings tile that starts or stops watching. | `WatchService` |
| UI (app, Compose) | Main screen, settings, setup checklist, About. | DataStore settings |
| `roadbuilder` (JVM CLI module in the repo) | Turns `switzerland-latest.osm.pbf` (Geofabrik) into `roads-ch.bin`, which goes into `app/src/main/assets/`. | an OSM PBF reader library from Maven Central, chosen in the plan |

`:core` holds the rules, the matcher and the road-file format. The app and `roadbuilder`
both use it, so the writer and reader share one format definition.

Settings live in DataStore Preferences. Runtime state lives only in the service's memory.
There is no database.

## 4. When it beeps

### 4.1 Readings

- The **speed** of a reading is usable if it is ≤ 3 s old and has a speed, and, when the
  phone reports a speed accuracy, that accuracy is ≤ 2 m/s (about 7 km/h). Only speed
  usability decides whether the rules run.
- The **position** of a reading is used for road matching only if its horizontal accuracy
  is ≤ 25 m. A reading with a usable speed but a poor position still drives the threshold.
  Its road match is *neutral* (§4.2). Poor accuracy in narrow streets therefore never
  silences the app.
- With no usable speed for 5 s, the state is **No GPS**. The app is silent and any
  running beep stops.
- The heading is used for road matching only when the speed is ≥ 15 km/h. Below that,
  GPS bearing is unreliable.

### 4.2 Road class (smart mode)

For each reading, `RoadMatcher` looks for the nearest road segment that is within
**20 m** of the position and runs within **30°** of the heading, in either direction.
The result is one of four:

- **FAST:** the segment's class is fast (§6.2).
- **TOWN:** the segment's class is town.
- **UNKNOWN:** no segment matched, inside the covered area.
- **NOT COVERED:** none of the cells in the ±30 m box around the position (§6.4) exists
  in the file, or the file is missing.
- **NEUTRAL:** the position accuracy is worse than 25 m, so no match is attempted.

The road context used by the rules has hysteresis:

- It switches to **FAST** after **5 consecutive** FAST readings (about 5 s).
- It switches back to **NORMAL** after **2 consecutive** TOWN or UNKNOWN readings.
- TOWN and UNKNOWN both count as NORMAL, so the app beeps.
- NEUTRAL readings neither count toward a switch nor break a run. After **10 s** of only
  NEUTRAL readings, the context becomes NORMAL. A GPS gap of 5 s or more ends a NEUTRAL
  streak, so the 10 s never count time spent in a tunnel.
- NOT COVERED makes the app behave as in **simple mode** (§4.4), and the notification
  says so.
- **At service start** the context is NORMAL. During NO_GPS the last context is kept,
  because it changes only through readings.

### 4.3 Speed state machine

Values (T and R are described in §7):

- **T** = threshold, default 53 km/h.
- **R** = re-arm margin, 3 km/h, a constant.
- **Debounce** = 2 consecutive readings.

| State | Entered when | Sound |
|---|---|---|
| **ARMED** | start; speed ≤ T − R from OVER or QUIET; road context leaves FAST; a pause ends | none |
| **OVER** | in ARMED with road context NORMAL, speed ≥ T on 2 consecutive readings, **and the latest reading's own match is not FAST** (the fast-road hold, see below) | main beep (settable length) on entry, then a reminder beep (one short tone) every N s **while speed ≥ T**. Between T − R and T no reminders sound, but the state stays OVER, so going back above T only resumes reminders and does not repeat the main beep. |
| **QUIET** | "Quiet this stretch" pressed while OVER | none; returns to ARMED when speed ≤ T − R. Survives NO_GPS: when GPS returns above T − R, the state goes back to QUIET |
| **PAUSED(until)** | "Pause N min" | none; GPS stays on and speed is shown; returns to ARMED at `until` or on Resume |
| **FAST_ROAD** | road context switches to FAST (from ARMED, OVER or QUIET) | none; a running beep stops at once |
| **NO_GPS** | no usable reading for 5 s | none |
| **STOPPED** | "Stop", or the car is gone | service ended, GPS off |

**Fast-road hold.** Confirming FAST takes about 5 s, but the beep needs only 2 readings.
Without a hold, the app would beep:
- every time you accelerate past an 80 sign when leaving a town,
- on every motorway on-ramp,
- when GPS returns on a motorway after a tunnel.

So while in ARMED, a reading at or above T whose *own* match is FAST, but where the FAST
context is not yet confirmed, holds the beep for that reading. A NEUTRAL reading in the
middle of a run of FAST readings also holds, because it does not break the run (§4.2).
The first TOWN or UNKNOWN reading at or above T ends the hold. With the debounce already satisfied, the main beep
then sounds at once.

The hold only delays entering OVER. Once OVER, only a confirmed FAST context silences the
app, so a single stray FAST match cannot cut off a running warning.

The road context keeps being tracked in every state. PAUSED, NO_GPS and STOPPED take
precedence over FAST_ROAD. When a pause ends on a fast road, the state goes straight to
FAST_ROAD.

When the road context leaves FAST, the state goes to ARMED. Readings on the fast road
already count toward the 2-reading debounce. So leaving a motorway onto a town road at
70 km/h gives a main beep about 2 s after the exit ramp ends, on the reading that switches
the context back to NORMAL.

### 4.4 Simple mode

- Turned on by a switch on the main screen and in settings, or automatically when the
  position is NOT COVERED.
- Road matching is skipped and the road context is always NORMAL. Everything else in the
  state machine is unchanged.
- It uses the same threshold T. Changing T is one tap from the main screen, see §7.

### 4.5 Speed from the car's OBD adapter (simple mode, D17)

- An ELM327 OBD-II adapter, paired in Android's Bluetooth settings, can supply the speed.
  It is chosen in settings; there is none by default.
- While watching with an adapter chosen, SpeedBeep connects over classic Bluetooth (serial
  port profile). It resets and sets up the adapter (`ATZ`, `ATE0`, `ATL0`, `ATS0`, `ATH0`,
  `ATSP0`) and then asks for the vehicle speed (mode 01, PID 0D, whole km/h) once a second.
  After a failure it reconnects every 5 s.
- In simple mode the car's speed comes first. A GPS fix is ignored while the last car reading
  is at most 3 s old. Without a car reading for 3 s, GPS takes over until the car answers
  again. The rules get the car's reading as a usable speed (§4.1), with no position.
- Smart mode keeps the GPS speed, because road matching needs the GPS position.
- When several control units answer, the highest speed counts (D10). One failed request is
  a missing reading; three in a row drop the connection.
- GPS keeps running in both modes. It is the fallback, and it fills the drive recording,
  which gets a column with the car's speed next to each GPS fix.
- The status line adds "· car" while the car's speed is in use. Switches between the car and
  GPS go to the event log, as do the adapter's replies while it is set up.
- Not covered: adapters that only speak Bluetooth LE, and Wi-Fi adapters.

## 5. Start, stop, pause

### 5.1 Car link (automatic)

- **Setup:** "Link my car" calls `CompanionDeviceManager.associate()` with a
  `BluetoothDeviceFilter` and `setSingleDevice(false)`, so Android's own dialog lists the
  devices and the user picks the car.
- **Presence:**
  - API 36: `startObservingDevicePresence(ObservingDevicePresenceRequest)`, handled in
    `CompanionDeviceService.onDevicePresenceEvent`.
  - API 34–35: the address-based `startObservingDevicePresence(String)` with
    `onDeviceAppeared` / `onDeviceDisappeared`.
- **Car appears:** start `WatchService` with
  `ServiceCompat.startForeground(…, FOREGROUND_SERVICE_TYPE_LOCATION)`.
- **Car gone for 30 s:** stop `WatchService`. Short Bluetooth drop-outs do not end the drive.
- **Manifest permissions:**
  - `REQUEST_COMPANION_START_FOREGROUND_SERVICES_FROM_BACKGROUND`
  - `REQUEST_OBSERVE_COMPANION_DEVICE_PRESENCE`
  - `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_LOCATION`
  - `ACCESS_FINE_LOCATION`, plus **`ACCESS_BACKGROUND_LOCATION`** ("Allow all the time"),
    which is required for a location service started while the app is in the background
  - `POST_NOTIFICATIONS`
  - `BLUETOOTH_CONNECT`, needed only by the fallback receiver, because receiving
    `ACTION_ACL_CONNECTED` requires it on API 31+
  - `uses-feature android.software.companion_device_setup`
- **Fallback, built in and chosen in settings (D16). The default is the companion route:**
  - A manifest receiver for `ACTION_ACL_CONNECTED`, filtered to the linked car's address,
    posts a high-priority "Car connected · Start" notification.
  - Tapping it starts the service. Notification taps are exempt from the background-start
    rules.
  - The car can also be picked from the paired devices, which needs "Nearby devices".

### 5.2 Manual

- **Start / Stop button** on the main screen.
- **Quick-settings tile** that toggles watching. If Android refuses to start the service
  from the tile, the tile opens the app with an auto-start intent instead. The spike checks
  this.

### 5.3 Notification

- Ongoing and visible on the lock screen. All actions work without unlocking.
- Content: current speed and state, for example:
  - `52 km/h · town`
  - `118 km/h · fast road, silent`
  - `61 km/h · OVER 53`
  - `Paused until 14:32`
  - `Simple mode · no road data`
  - `No GPS`

| State | Buttons |
|---|---|
| OVER | [Quiet this stretch] [Pause N min] [Stop] |
| ARMED / FAST_ROAD / QUIET / NO_GPS | [Pause N min] [Stop] |
| PAUSED | [Resume] [Stop] |

- **Stop:**
  - Ends the service and turns GPS off.
  - If the car is still linked and present, a silent low-priority notification
    "SpeedBeep stopped · [Start]" stays, so restarting is one tap. It disappears when the
    car disconnects.
  - The next time the car connects, watching starts normally.

## 6. Road file

### 6.1 Build (PC)

```
gradlew :roadbuilder:run --args="build <path>\switzerland-latest.osm.pbf app/src/main/assets/roads-ch.bin"
```

- **Input:** the Geofabrik Switzerland extract, about 500 MB. Download it to
  `C:\work\tmp\speedbeep-osm\`. It is never committed.
- **Three passes:**
  1. Read the built-up land-use multipolygons (for the report only).
  2. Collect the drivable ways, the built-up land-use ways, and their node IDs.
  3. Read the coordinates of only those nodes.
- **Output:** `roads-ch.bin`, which **is committed**. It is rebuilt about once a year.
- **Report** printed at the end:
  - kilometres of road by class
  - kilometres of primary/secondary/tertiary/unclassified road with no speed information
    (the "known gap")
  - the same main roads split into inside / within 30 m of / outside built-up areas, by
    speed information (see §6.2; the road file does not depend on it)
  - file size
  - OSM data timestamp

### 6.2 What goes in and how it is classed

**Kept:** ways with `highway` ∈ {motorway, trunk, primary, secondary, tertiary,
unclassified, residential, living_street, road, and every `*_link`}.

**Dropped:**
- `area=yes`
- `access=no` or `motor_vehicle=no`, unless `motorcar=yes`
- every other `highway` value, such as service, track and path

**Effective limit,** taken from the first source that gives one:
1. `maxspeed`
2. if both `maxspeed:forward` and `maxspeed:backward` are given, the **lower** of the two
3. `maxspeed:type`, `zone:maxspeed` or `source:maxspeed`

A directional value only ever **lowers** the limit, never raises it (D10), because a road
is stored once for both directions. A `maxspeed:forward` or `maxspeed:backward` lower than
the result of steps 1–3 wins, and a lone directional value above 50 is ignored.

**Value parsing:**

| Value | Limit (km/h) |
|---|---|
| a number, with or without "km/h" | that number |
| `CH:urban` | 50 |
| `CH:rural` | 80 |
| `CH:trunk` | 100 |
| `CH:motorway` | 120 |
| `CH:zone30` / `CH:30` | 30 |
| `walk`, `CH:zone20` | 20 |
| `none`, `signals`, `variable`, anything not understood | no limit |

**Class:**
- **FAST** if the effective limit is > 50.
- **FAST** if there is no limit and `highway` ∈ {motorway, motorway_link, trunk, trunk_link}.
- **TOWN** in every other case, including a missing limit on other road types. That case
  is the known gap, and "Quiet this stretch" covers it.

**The known gap, measured** (build of 2026-11-25, OSM data of the same day): 51.8 % of
the primary, secondary, tertiary and unclassified road length has no speed information.
The report splits that main-road length by position against built-up areas. These are
the `landuse` = residential, commercial, industrial or retail outlines (28,455 of them;
inner rings ignored), with a 30 m edge band because outlines often stop at the roadside,
sampled every 50 m:

| Main roads | Inside | Within 30 m | Outside | Share outside |
|---|---|---|---|---|
| No speed information | 2,271 km | 1,469 km | 17,928 km | 82.7 % |
| Posted 50 or less | 4,775 km | 1,954 km | 2,326 km | 25.7 % |
| Posted above 50 | 533 km | 1,200 km | 9,337 km | 84.3 % |

- Of the 17,928 km without speed information outside built-up areas, 14,391 km are
  `unclassified`, 2,942 km tertiary and 595 km primary or secondary.
- A rule "no speed information and outside built-up areas → FAST" would silence that
  length. But a quarter of the length really posted 50 or less also lies outside, so the
  rule would most likely silence a similar share of real 50 roads, against D10.
- Caveat: tagged roads are not a random sample, and the untagged ones are mostly
  `unclassified`, a different mix. Where the posted-50 roads outside lie (village edges,
  hamlets) was not measured.
- Decision D15: the rule is not used. Those roads stay TOWN.

### 6.3 Format (version 1)

All values are little-endian.

- **Header:**
  - magic `SBRD`, format version
  - OSM data timestamp, build date
  - grid origin, and cell size of 0.01° × 0.01° (about 1.1 × 0.75 km)
  - grid width and height
  - cell count
- **Cell index:** sorted `(cellId, offset, length)` entries, searched with binary search.
  A cell missing from the index is **not covered**.
- **Cell payload:** a list of polylines. Each one is a class byte, a point count, and its
  points as zig-zag varint deltas in units of 1e-6 degree (about 0.1 m). A way crossing
  several cells is stored in each of them.
- **Size target:** under 40 MB. The estimate is 10–30 MB.
- **Loading on the phone:**
  - On first start of each app version, copy the asset to `filesDir`, then memory-map it
    read-only.
  - A missing or corrupt file (bad magic or version) puts the app in simple mode, shows a
    banner, and keeps it working.

### 6.4 Matching

- **Cells read:** the bounding box of the position ± 30 m, at most 4 cells.
- **Distance:** point-to-segment distance in a local equirectangular projection.
- **Choice:** the nearest segment within 20 m whose bearing lies within 30° of the
  heading, compared modulo 180°.
- **Below 15 km/h:** no heading check is applied.

## 7. Sound and settings

### 7.1 Output

- **Phone speaker (default):**
  - `AudioAttributes` `USAGE_ALARM` with `CONTENT_TYPE_SONIFICATION`.
  - Plays even when the radio isn't on Bluetooth audio, and is not muted by normal
    Do Not Disturb settings.
- **Car speakers:**
  - `USAGE_ASSISTANCE_NAVIGATION_GUIDANCE`, with transient audio focus
    `AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK` so music is ducked like a Maps prompt.
  - Whether it is actually heard depends on the car radio, and the in-car test checks it.

### 7.2 Sounds

- **Built-in:** three tones generated as PCM through `AudioTrack`, with no asset files:
  - Beep: 1 kHz
  - Double beep
  - Chime: low then high
- **Phone alarm sounds:** any of the phone's alarm sounds, picked through `RingtoneManager`
  (`TYPE_ALARM`).
- **Main beep:** the chosen sound repeated or cut to the set length.
- **Reminder:** one short tone, about 0.4 s.

### 7.3 Settings

| Setting | Default | Range |
|---|---|---|
| Threshold T | 53 km/h | 20–150 |
| Mode | Smart | Smart / Simple |
| Main beep length | 2 s | 0.5–10 s |
| Reminder interval | 10 s | 0 (off), 3–60 s |
| Sound | Beep | built-ins and phone alarm sounds |
| Volume | 80 % | 0–100 % (app gain on top of the stream volume) |
| Output | Phone speaker | Phone speaker / Car speakers |
| Pause length N | 10 min | 1–60 min |
| Linked car | none | Link / Unlink |
| When the car connects | Start watching | Start watching / Ask with a notification |
| OBD adapter (simple mode, §4.5) | none | Choose from paired devices / Remove |
| Keep screen on while app is open | on | on/off |
| Record drives (debug) | off | on/off |

- **Test beep** button: plays the main beep with the current sound, output and volume.
- **Test adapter** button: connects to the OBD adapter, reads one speed and shows the result
  (§4.5). It needs watching to be stopped, because the adapter takes one connection at a time.
- **Setup checklist** on the main screen until everything is granted. Each item shows a
  fix button:
  - notifications
  - location "Allow all the time"
  - car linked
  - battery optimisation off, a recommendation for aggressive phone makers

### 7.4 Main screen

- **Current speed**, very large.
- **Status line:** state plus road context.
- **Large buttons:** Start/Stop, Pause N min, and Quiet this stretch while OVER.
- **Mode switch:** Smart / Simple.
- **Threshold stepper:** − / +.

The screen is usable in a phone holder at arm's length.

### 7.5 About

- OSM attribution: "Road data © OpenStreetMap contributors, ODbL".
- Road file date.
- App version.

## 8. Platform facts this design relies on

Read on 2026-11-24 from developer.android.com: FGS background-start restrictions, FGS
service types, companion device pairing, and the broadcast exceptions.

- Since API 31, an app in the background can't start a foreground service unless an
  exemption applies. A Bluetooth ACL broadcast is not one of the exemptions.
- `ACTION_ACL_CONNECTED` is still delivered to manifest receivers.
- A companion-app association together with
  `REQUEST_COMPANION_START_FOREGROUND_SERVICES_FROM_BACKGROUND` is an exemption.
- A location-type service started while the app is in the background also needs
  `ACCESS_BACKGROUND_LOCATION`. Without it, `startForeground()` throws a
  `SecurityException` on targetSdk 34+.
- On API 36 the string-based presence API is deprecated in favour of
  `ObservingDevicePresenceRequest` / `onDevicePresenceEvent`.
- **Not yet verified:** whether presence fires for a classic-Bluetooth car head unit, and
  how the user's phone maker treats the service. The spike (§9) checks both.

## 9. Delivery order and testing

1. **Spike on the user's phone.** A throwaway minimal app containing only the
   CompanionDeviceManager link, `CompanionDeviceService`, a location service that logs
   speed in its notification, and the quick-settings tile.
   - **Pass:**
     - With the app swiped away and the phone locked, connecting the car (or any paired
       Bluetooth headset as a stand-in) shows the notification within 10 s.
     - Disconnecting removes it. The spike stops at once; the app's 30 s grace period
       comes in Plan 2.
     - The tile starts and stops the service.
   - **If it fails:** use the §5.1 fallback.
   - The spike code is thrown away. The car link is built properly in step 4.
   - Steps 2 and 3 do not depend on the spike and can proceed while it waits for the
     phone. Only the car link in step 4 waits for its result.
2. **`:core`, written test-first:**
   - `SpeedRules` scenario tests:
     - crossing, debounce, hysteresis between T − R and T, reminders
     - Quiet, Pause, Resume
     - FAST entry and exit, including exiting onto a town road at 70
     - the fast-road hold: accelerating past an 80 sign out of town, a motorway on-ramp,
       GPS returning on a motorway after a tunnel
     - poor-accuracy (NEUTRAL) readings in town still beep
     - NO_GPS, simple mode, NOT COVERED
   - maxspeed parser and classifier tests.
   - Road-file round trip (write, then read).
   - `RoadMatcher` on synthetic layouts:
     - a town road 25 m from and parallel to a motorway
     - a town road under a motorway bridge, crossing it
     - an exit ramp
     - a cell edge
3. **`roadbuilder`:** run it on the Swiss extract, then spot-check known points: on the
   A1 → FAST, a Zürich city street → TOWN, a rural 80 road → FAST, a 30 zone → TOWN,
   a point in Germany → NOT COVERED. The plan picks the coordinates.
4. **App:** service, beeper, notification, UI, settings, car link and tile.
   - Emulator check with `ReplaySource` playing a synthetic drive. Also check whether
     `adb emu geo fix` can inject speed, and use it if so.
5. **In-car tests by the user:**
   - Test beep on both outputs.
   - Every notification action works from the lock screen without unlocking.
   - One real drive with "Record drives" on. The recording becomes a replay test case for
     tuning the 20 m, 30°, 5- and 2-reading values.
6. **Release:** private repo `LRosa2/SpeedBeep`. Release APK signed with the shared key
   through a git-ignored `keystore.properties`, installed directly on the phone.

## 10. Out of scope

- maps
- speed cameras
- warnings against each road's posted limit
- trip history and statistics, apart from the debug recording
- countries other than Switzerland
- Play Store
- Android Auto
- iOS
- vibration (the phone sits in a holder)
