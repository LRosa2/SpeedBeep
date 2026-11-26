# SpeedBeep: the one-trip car test

About 10 minutes parked and one drive of about 20 minutes, with some town streets and a
stretch of motorway or 80 road. Afterwards, send back the two logs and the recorded drive.

Files: on the phone, open the release page
https://github.com/LRosa2/SpeedBeep/releases/tag/v0.1.0 (sign in to GitHub first: the repo
is private). Download `SpeedBeep-0.1.0.apk`, and `SpeedBeep-spike.apk` if you do Part A.
Open each one to install it (allow installing from that source).

## Part A: the spike, parked (decides D14)

Follow [SPIKE-TEST.md](SPIKE-TEST.md), steps 1–7. Then uninstall the
spike app, so it does not also react to the car.

If time is short, skip Part A: SpeedBeep's own event log records the same things.

## Part B: SpeedBeep, parked

1. Open SpeedBeep. Work through the checklist on the main screen:
   - notifications: allow
   - location: allow, then "Allow all the time"
   - link your car: first make the car visible (the head unit's "add device" or pairing
     mode), then Settings → Link my car → pick the car in Android's list. While the car is
     not visible, it does not appear in the list.
   - battery optimisation: turn off

   If the car is on while you link it, watching may start by itself at once. That already
   shows auto-start works: note it, then press **Stop** in the app before going on.
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
   - **Stop**: watching ends.

## Part C: auto-start and the drive

1. Swipe SpeedBeep away from the recent apps, lock the phone, and turn the car off. Wait
   one minute.
2. Turn the car on. Within about 10 s the SpeedBeep notification should appear by itself.
   - If it does not: open SpeedBeep → Settings → **When the car connects: Ask with a
     notification** and allow "Nearby devices".
   - Then turn the car off, wait one minute, and turn it on again: a "Car connected · Start"
     notification should appear. Tap Start.
   - SpeedBeep also shows "Car connected · Start" by itself when Android refuses the
     automatic start. If that happens in step 2, note it: it answers the same question.
   - Note which of the two worked.
3. **Stop while the car is connected.** Once watching has started by itself, tap **Stop**
   on the lock screen. A small "SpeedBeep stopped · Start" note should stay. Tap **Start**
   on it: watching starts again.
4. **Drive.**
   - **In town:** go a little above 53. A beep within about 2 s, then a short reminder
     every 10 s while over. Once use **Quiet this stretch**.
   - **On the motorway or an 80 road:** silent after about 5 s at speed.
   - **Back in town:** beeps again above 53.
5. Park and turn the car off. After about 30 s the notification should go away.

## Part D: OBD adapter (version 0.2.0 only)

1. Plug the adapter into the car's OBD port (under the dashboard, driver's side). Pair it
   in Android's Bluetooth settings (PIN usually 1234 or 0000).
2. SpeedBeep → Settings:
   - Mode: **Simple**
   - **Record drives**: on
   - OBD adapter → **Choose adapter** → pick it.
3. Parked, ignition on: tap **Test adapter**. Expected: "Connected to … Car speed: 0 km/h."
   If it fails, note the text it shows.
4. Start watching and drive. The status line shows "· car" while the car's speed is in use.
   Beeps as in Part C.
5. Send back the event log and the last drive: the drive file has the car's speed next to
   the GPS speed, to compare the two.

## Send back

- Settings → **Share event log**
- Settings → **Share last drive**
- the spike log from Part A, if you did it
- one line per numbered step above: worked / did not work / what happened
